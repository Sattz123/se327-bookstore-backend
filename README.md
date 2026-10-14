# Bookstore backend

## Simulated payment methods (Stage 10)

Authenticated `GET /api/payment-methods`, `POST /api/payment-methods`, `PUT /api/payment-methods/{id}/default`, and `DELETE /api/payment-methods/{id}` manage only the current user's records. Create returns 201, delete 204, and missing/foreign IDs 404. The existing controller/service/DAO/repository structure is retained.

Create JSON: `provider` (required, max 50), `cardholderName` (required, max 100), `lastFour` (exactly four digits), `expiryMonth` (1–12), `expiryYear` (2000–9999), and `isDefault` (optional boolean, default false). Past expiry months are rejected; the current UTC month remains valid. Responses include the ID, provider/name, `cardNumberMasked`, expiry, default flag, and creation time. This is a simulated payment flow: no gateway, full card number, CVV, or payment credentials are collected/stored.

Migration V8 adds `payment_methods`. A partial unique index and the same user-row locking used for addresses enforce at most one default per user. Default changes are transactional and check ownership before clearing the previous selection. Deleting the default leaves none; other methods are not promoted. Repeatedly selecting the same default is safe. Restarting the backend applies V8; existing migrations remain unchanged.

Surefire limits each test-context connection pool to three connections with no minimum idle pool. This prevents the growing suite of isolated schemas from exhausting PostgreSQL's default connection limit; normal application pools are unchanged.

## Address book (Stage 9)

Authenticated users manage their own delivery addresses through the existing lab-style layers. Flyway `V7__create_addresses.sql` adds `addresses` with an owner foreign key and a PostgreSQL partial unique index allowing at most one default per user.

| Request | Result |
| --- | --- |
| `GET /api/addresses` | Current user's addresses, default first then newest |
| `POST /api/addresses` | Create an address (201) |
| `PUT /api/addresses/{id}` | Edit an owned address |
| `PUT /api/addresses/{id}/default` | Select an owned address as default |
| `DELETE /api/addresses/{id}` | Delete an owned address (204) |

JSON fields are `label` (required, max 50), `fullName` (required, max 200), `addressLine` (required, max 500), optional `phone` (20), `city` (100), `province` (100), `postalCode` (10), and `isDefault` (boolean, false when omitted). Text is trimmed; blank optional fields become null. Responses also include `id`, `createdAt`, and `updatedAt`, with no owner or password data. The original label/province fields are retained; no location is silently filled in.

The owner always comes from the authenticated principal. Missing or another user's IDs return 404; invalid IDs/body fields return 400; guests receive 401. Mutations lock the owning user row and run in one transaction, so concurrent default changes are serialized. The unique index also protects the rule at the database level. Ownership is checked before clearing an old default. Deleting a default address, or editing it with `isDefault: false`, leaves no default until the user chooses one; other addresses are not automatically promoted. Selecting the same default repeatedly is safe.

`AddressControllerTests` exercises the authenticated API with JWT and PostgreSQL in a separate `address_api_test` schema; fixtures roll back. Existing local startup instructions still apply, and the next application startup applies V7 automatically.

Stage 9 regression tests cover required/overlong fields, invalid IDs, empty and private lists, cross-user edit/delete/default attempts, switching/repeating/clearing defaults, and deleting default/non-default addresses. `AddressPersistenceTests` checks the PostgreSQL partial unique index and user-deletion cascade in its own schema. `AddressConcurrencyTests` runs two real transactions simultaneously and verifies both default changes succeed with exactly one final default; it removes its committed test user afterward.

Spring method validation (used when a route also validates its path ID) now returns the same `errors: [{field, message}]` shape as request-body validation. This keeps create and edit form error messages consistent.

Spring Boot backend for the bookstore reimplementation. Requires JDK 21 and Docker Desktop with Linux containers. Maven is provided by the wrapper.

## Local setup

Open this folder as a Maven project in IntelliJ and choose JDK 21 as the project SDK and Maven runner JRE. Reload Maven after dependency changes.

Run the following PowerShell commands from this backend folder:

```powershell
# First setup only; keep your own .env if it already exists.
Copy-Item .env.example .env
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

Alternatively, run `BookstoreBackendApplication` in IntelliJ after starting the database. Set the run configuration's working directory to this backend folder so Spring Boot can read `.env`.

The backend uses `http://localhost:8080`. Bookstore API routes use `/api/**`; opening `/` still returns a 404.

If the terminal uses another Java version, set JDK 21 for that terminal first (adjust the installation path if necessary):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Environment settings

Both Docker Compose and Spring Boot read this backend folder's `.env`. Commit `.env.example`; keep `.env` out of Git. Use simple `KEY=value` lines without shell commands or quotes.

| Setting | Example | Purpose |
| --- | --- | --- |
| `POSTGRES_HOST` | `localhost` | Database host used by the backend running on your computer |
| `POSTGRES_PORT` | `5433` | Local database port mapped to container port 5432 |
| `POSTGRES_DB` | `bookstore` | Database name |
| `POSTGRES_USER` | `bookstore` | Database user |
| `POSTGRES_PASSWORD` | `bookstore_dev` | Local development password |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | Exact frontend origin allowed by CORS, without a trailing slash |
| `SPRING_PROFILES_ACTIVE` | `dev` | Enable the local sample catalog and its cover images; leave unset in production/hosted CI |

`FRONTEND_ORIGIN` defaults to `http://localhost:5173` when absent. If Vite starts on a different port or hostname, update this setting and restart the backend. The frontend calls port 8080 directly; it has its own environment configuration.

PostgreSQL data is stored in a Docker named volume and survives container restarts and `docker compose down`. Database name/user/password settings initialize a new volume; changing `.env` later does not change an existing database's credentials. Keep the original settings or explicitly update the database to match.

Flyway runs versioned SQL migrations from `src/main/resources/db/migration` when the application starts. JPA uses `ddl-auto=validate` to check the resulting schema rather than create or update tables itself.

### Catalog data model

The first migration, `V1__create_catalog_tables.sql`, adds four tables:

| Table | Purpose |
| --- | --- |
| `categories` | Category name, image reference, visibility, and creation time |
| `products` | Category reference, book name/description, author, decimal price, stock, visibility, and timestamps |
| `product_images` | Up to five ordered image references per book |
| `product_formats` | Available Paperback, Hardcover, E-book, and Audiobook formats |

`Category` and `Product` are JPA entities in the `entity` folder. Images and formats use `@ElementCollection`, similar to the lab's image lists. Prices use `BigDecimal` and PostgreSQL `NUMERIC(10, 2)`. Database constraints reject negative prices/stock, unknown formats, and invalid image positions. Category deletion is blocked while products still reference it; deleting a product removes its own image/format rows.

The author and formats replace the original JSON attributes with typed fields and a related table. Visibility and stock remain separate fields. Uploads are added in a later stage.

### Public catalog API

| Request | Response |
| --- | --- |
| `GET /api/products?q=...&categoryId=...&page=1` | A page of visible books; 15 per page |
| `GET /api/products/featured` | The newest eight visible books for the home page |
| `GET /api/products/{id}` | One visible book, or a ProblemDetail 404 when absent/hidden |
| `GET /api/categories` | Visible categories ordered by ID, including empty categories |
| `GET /api/categories/{id}` | One visible category; absent/hidden categories return 404 |

All product routes require a visible category as well as a visible book. Out-of-stock books remain listed with `stockQuantity: 0`. Invalid or nonpositive IDs return 400.

Stage 4 changes the product list response from a plain array to `{content, page, size, totalElements, totalPages}`. Pages start at **1**, use a fixed size of **15**, and sort by creation time descending then ID descending for ties. A page past the last returns empty `content` with the real totals. Empty results have `totalPages: 0`. Page numbers outside 1–1,000,000 or nonnumeric values return 400.

Optional `q` searches title or author using a case-insensitive substring (trimmed, maximum 100 characters). `%`, `_`, and backslashes are literal search characters. `categoryId` combines with search; a missing/hidden category returns 404. For example, `/api/products?q=harry&categoryId=2&page=1`. The featured section uses the same newest-first ordering, capped at eight, following the original home controller rather than adding a separate editorial flag. No schema migration is needed for these queries.

The lab-style flow is `ProductController` → `ProductService`/`ProductServiceImpl` → `ProductDao`/`ProductDaoImpl` → `ProductRepository`. The service maps entities to DTOs inside a read-only transaction, so lazy collections work with Open Session in View disabled. Responses include `id`, `name`, `description`, `price`, `author`, `stockQuantity`, a nested category (`id`, `name`, `imageUrl`), ordered `images`, and `formats` using the enum names. Internal visibility flags and persistence timestamps are not exposed.

Image references such as `/images/products/fiction-01.png` are relative to the backend origin, not the frontend's origin. Local sample images require the `dev` profile.

The first startup creates these tables in an empty application database and records the migration in `flyway_schema_history`. Later startups validate the migration history and only apply new versions. Keep applied migrations unchanged; add a new numbered migration for later schema changes. Existing databases with manually created catalog tables need an explicit migration plan rather than enabling Flyway baselining or dropping data automatically.

### Development seed data

`application-dev.properties` adds `classpath:dev/db/migration` to Flyway's normal migration location. Its `V2__seed_bookstore_catalog.sql` adapts only the original catalog: five categories, 60 books (12 per category), 60 cover references, and 180 available-format rows. Original book IDs, category IDs, titles, authors, descriptions, prices, stock, and visibility are preserved. PostgreSQL identity sequences are advanced so new records do not collide with the original IDs.

The sample covers are copied unchanged into `src/main/resources/dev/static/images/products`. They are served as `/images/products/<filename>` only with the `dev` profile. The original SQL's five category JPG files were not present in the source project, so those optional category image values are `NULL`. Users, passwords, orders, and payment records from the old SQL are not imported.

For local development, `.env.example` enables `SPRING_PROFILES_ACTIVE=dev`; the local `.env` prepared for this project does too. Start normally with `spring-boot:run` or IntelliJ. For an existing clone without this setting, add it to your own `.env` before starting. A temporary override is:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Flyway applies the seed once. Restarts do not insert duplicates or reset later book edits. The seed expects an empty catalog; it will fail on conflicting IDs rather than overwrite existing data. Keep using the `dev` profile for a database initialized this way. Switching that same database to a profile that cannot see its applied development migration causes Flyway validation to fail. Production and hosted CI use separate databases without the `dev` profile and run schema migrations only.

Future shared schema migrations stay under `db/migration`, with new versions such as `V3__...`; keep the version numbers unique across both migration folders.

## Tests and build

Start PostgreSQL before running all tests, because the application-context and catalog persistence tests connect to the real local database:

```powershell
docker compose up -d --wait
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

The web tests can run separately without PostgreSQL:

```powershell
.\mvnw.cmd "-Dtest=WebFoundationTests,CorsOriginOverrideTests" test
```

They check request validation, malformed/missing JSON, missing routes, unsupported methods/content types, unexpected errors, and allowed/rejected CORS origins, methods, and headers. They also verify changing the allowed origin and preserving CORS headers on error responses. Their example endpoints and mocked dependency only exist during tests.

`CatalogPersistenceTests` verifies the migrated schema with PostgreSQL, entity/collection persistence, stock/visibility updates, database constraints, and repeat migration behavior. Its test data is rolled back after each test.

`DevelopmentSeedTests` activates the development profile in a separate `development_seed_test` schema. It verifies the seed counts, original details, format/image references, identity sequences, and that a repeated migration does not reset edits. Temporary edits are rolled back, and these tests do not seed the application's normal schema when CI uses the default profile.

`ProductCatalogTests` runs the real controller, service, DAO, and repository with MockMvc and PostgreSQL in a separate `catalog_api_test` schema. Test fixtures roll back after each test. It covers public DTOs and ordered collections, list/detail success, missing/invalid IDs, book/category visibility, out-of-stock books, optional fields, empty results, and CORS. `ProductControllerFailureTests` simulates service failures and checks safe 500 responses. Hosted CI runs all of these through the existing Maven job.

`CatalogDiscoveryTests` uses the isolated `discovery_api_test` schema to verify 15-item pagination and stable ordering, combined search/category/page requests, literal wildcard characters, empty results, invalid pages/filters, category visibility, and the newest-eight featured selection. Its fixtures also roll back after each test.

### JUnit, Mockito, and coverage

The Spring Boot web MVC test starter already provides JUnit Jupiter, Mockito, and Spring's MVC test tools. Versions are managed by Spring Boot. `@MockitoBean` replaces a dependency in the web test context so a test can simulate an unexpected failure and check the public error response.

Maven Surefire loads both Mockito's Java agent (for Java 21 inline mocking) and JaCoCo's coverage agent. The Maven dependency plugin resolves the Mockito jar path; JaCoCo supplies its agent argument separately so both remain active. IntelliJ's native JUnit runner does not use Surefire settings; use the Maven commands for this project's coverage reports.

Run `clean verify` for a fresh build, all tests, and coverage reports. `test` runs tests and collects execution data, but HTML/XML reports are generated in the later `verify` phase.

| Output | Path |
| --- | --- |
| Test results | `target/surefire-reports/` |
| HTML coverage report | `target/site/jacoco/index.html` |
| XML coverage report for later SonarQube setup | `target/site/jacoco/jacoco.xml` |
| CSV coverage counters | `target/site/jacoco/jacoco.csv` |

Reports stay under the Git-ignored `target` folder. No production classes are excluded and no Maven coverage threshold is imposed. The local SonarQube analysis below checks the selected server-side quality gate.

JaCoCo reports bytecode instruction, line, and branch coverage; its instruction counter is not a Java source statement counter. If a report has no branch counter, report branch coverage as not applicable rather than 100%. Coverage at this stage only describes the implemented foundation and catalog model, not the future bookstore workflows.

References: [JaCoCo Maven plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html), [JaCoCo counters](https://www.jacoco.org/jacoco/trunk/doc/counters.html), and [Mockito Java 21 agent setup](https://javadoc.io/static/org.mockito/mockito-core/5.23.0/org.mockito/org/mockito/Mockito.html#0.3).

## GitHub Actions CI

`.github/workflows/backend-ci.yml` runs on pushes to any branch and on pull requests targeting `develop` or `main`.

The single job checks out the backend, sets up Temurin JDK 21 with Maven caching, and starts a temporary PostgreSQL 17 service. It runs `sh ./mvnw --batch-mode --no-transfer-progress clean verify` to compile, test, package the application, and generate JaCoCo reports. Invoking the wrapper through `sh` also works when its executable bit was not set in Git on Windows.

Database settings come from the workflow's environment. The CI password is only for the temporary service created during that run; no repository secrets or local `.env` are needed.

After committing this change, push `chore/quality-setup` using Fork. Open the backend repository's **Actions** tab on GitHub and select **Backend CI**. Review the **build-and-test** job and download **backend-test-reports** to inspect the test results and HTML/XML coverage reports. Reports are retained for seven days, and the upload step also runs after test failures so available failure reports can be inspected.

A failed build or test fails the job. This hosted workflow does not connect to local SonarQube. Save an actual successful GitHub run as CI evidence after pushing; local verification alone is not a GitHub Actions result.

Reference: [GitHub's Maven CI guide](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven) and [PostgreSQL service containers](https://docs.github.com/en/actions/tutorials/use-containerized-services/create-postgresql-service-containers).

## Local SonarQube analysis

This project uses a local SonarQube Community Build server, separate from GitHub-hosted CI. The existing Docker container is named `sonarqube`; open `http://localhost:9000`, or start it with `docker start sonarqube` if it is stopped. A fresh machine needs its own SonarQube installation first.

First setup:

1. Log into SonarQube and create a local project with key `se327-bookstore-backend` and name `SE327 Bookstore Backend`.
2. Select the built-in **Sonar way** quality gate for the project. Record the actual conditions shown in SonarQube for the report; they are tool settings, not a course-specified coverage minimum.
3. Under **My Account > Security**, generate a **Project Analysis Token** for this backend project. Give it an expiry date suitable for the project.
4. Copy `.env.sonar.example` to `.env.sonar` and paste the token after `SONAR_TOKEN=`. Use unquoted `KEY=value` lines. `.env.sonar` is ignored by Git; never put the token in the POM, screenshots, or report.

With JDK 21 selected, start the application database and run from this backend folder:

```powershell
docker compose up -d --wait
.\scripts\sonar.ps1
```

The script loads local Sonar settings, runs `clean verify`, and then uses the pinned SonarScanner for Maven. It imports `target/site/jacoco/jacoco.xml`. `sonar.qualitygate.wait=true` makes a failed quality gate fail the command. The script restores the previous token/server environment variables when it finishes.

Community Build has one analysis stream per project, without separate feature-branch or pull-request analysis. Analyze the integrated `develop` snapshot consistently after merging. A pre-merge baseline scan can inspect your current working branch, but it replaces the same Sonar project snapshot; the Sonar label is not proof that your Git `main` branch was analyzed. Record the actual Git branch, commit, and any uncommitted source changes with the results.

GitHub-hosted runners cannot reach a SonarQube server on your laptop through `localhost`. Hosted build/test CI stays enabled; this scan is manual. Automatic Sonar analysis would require a separately configured self-hosted runner or a reachable server. If that is added later, store its token in GitHub Actions secrets rather than committing `.env.sonar`.

After a successful scan, open the backend project dashboard and save its gate result, coverage, findings, new-code definition, and selected gate conditions as evidence. Keep genuine findings for review; this setup does not suppress rules or exclude production classes to obtain a passing gate. Until the scan actually runs and returns a result, analysis is only configured.

References: [SonarScanner for Maven](https://docs.sonarsource.com/sonarqube-community-build/analyzing-source-code/scanners/sonarscanner-for-maven) and [SonarQube token management](https://docs.sonarsource.com/sonarqube-community-build/user-guide/managing-tokens).

## API errors and frontend access

Spring MVC errors use Spring's `ProblemDetail` format with `application/problem+json`. For example, a future endpoint using `@Valid @RequestBody` can return:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Please correct the invalid fields.",
  "instance": "/api/example",
  "errors": [
    { "field": "name", "message": "Name is required." }
  ]
}
```

The `errors` array is provided for request-body field validation. Other MVC errors use the same standard fields, with details appropriate to the error. Unexpected exceptions return a generic 500 message; the exception is logged on the backend.

`WebConfig` permits the configured frontend origin on `/api/**`, including JSON and Authorization headers. Browser preflight requests are handled automatically. CORS rejection is handled separately by Spring and may return a plain 403 response. CORS does not replace authentication or authorization. Cookie credentials are not enabled.

References: [Spring error responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) and [Spring MVC CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html).

## Shopping cart (Stage 11)

Authenticated cart endpoints derive the customer from the JWT:

| Method | Endpoint | Body |
| --- | --- | --- |
| GET | `/api/cart` | — |
| POST | `/api/cart/items` | `productId`, `format`, `quantity` |
| PUT | `/api/cart/items/{id}` | `quantity` (replacement value) |
| DELETE | `/api/cart/items/{id}` | — |

All successful operations return `{ items, totalQuantity, total }`; adding returns 201, the others 200. Items include the saved format, current unit price, line total, stock, and availability. Money is calculated using `BigDecimal` and current catalog prices. Client prices and owner IDs are never used.

Adding the same product/format increases its quantity; different formats are separate rows. Quantities must be positive integers, the format must be offered, and the customer's combined quantity across all formats cannot exceed the product's shared stock (409). Cart changes do not reserve or deduct stock. Per-customer locking prevents concurrent additions from losing quantities. Foreign or missing item IDs return 404. Existing unavailable items remain visible with `available: false` and can be removed; stock and prices must be rechecked at checkout in Stage 12.

Migration V9 adds cart storage and enforces one row per customer/product/format.

## Order storage (Stage 12)

Migration V10 adds `orders` and `order_items`. Order lines preserve the purchased title, format, quantity, unit price and line total. Recipient, delivery address, phone, and masked payment display are also snapshots; changing or deleting a saved address, card, or catalog product cannot change an existing order. Product IDs in order lines are references without a foreign key so product removal does not erase purchase history.

Cash on delivery uses `PENDING`; simulated card completion uses `COMPLETED`. No real payment is processed. A unique checkout UUID per customer prevents duplicate order records.

## Transactional checkout (Stage 12)

`GET /api/checkout/review` returns `{ cart, cartFingerprint }` with current server prices. `POST /api/checkout` accepts `addressId`, `paymentType` (`CASH_ON_DELIVERY` or `SIMULATED_CARD`), optional `paymentMethodId` (required for card, absent for cash), a UUID `checkoutKey`, and the review's `cartFingerprint`. The result is an order snapshot (201). `GET /api/checkout/orders/{id}` reloads an owner's confirmation (foreign/missing orders return 404).

The server verifies address/card ownership and expiry, locks the customer and products, checks shared stock across formats, compares the reviewed cart and prices, and computes totals using `BigDecimal`. Changed carts/prices, unavailable selections, empty carts and insufficient stock return 409 without purchasing. Product locks are acquired in ascending ID order. Order creation, stock deduction and cart deletion share one database transaction; failures roll everything back.

Retry the **same payload with the same checkout key** after a lost response: it returns the original order, even after the cart is cleared. Reusing a successful key for changed details returns 409. Keys are scoped to the customer; a new purchase needs a new key and review. Cash orders are `PENDING`; simulated card orders are `COMPLETED`. Both consume stock and clear the cart. The simulated card path only uses saved masked display metadata and never contacts a payment gateway.

## Customer order history (Stage 13)

Authenticated `GET /api/orders` lists the current customer's orders, newest first, with ID, status, saved payment display, total and creation date. A new customer receives `[]`. `GET /api/orders/{id}` returns the saved order, delivery and item snapshots using the same ownership rules as checkout confirmation. Missing or foreign orders return 404; invalid IDs return 400. Catalog prices and edited addresses/cards do not replace purchase snapshots.

## Administrator access (Stage 14)

All `/api/admin/**` endpoints require the database-backed `ADMIN` role: missing/expired JWTs return 401 and customer accounts return 403. `GET /api/admin/session` returns the signed-in administrator's profile for the back office. The same `/api/auth/login` endpoint authenticates both roles; public registration always creates `CUSTOMER` accounts and does not accept a role.

The existing `dev` profile seed supplies a local demonstration administrator. For a separate administrator without demo data, add these settings to your ignored backend `.env`: `APP_ADMIN_BOOTSTRAP_ENABLED=true`, `APP_ADMIN_BOOTSTRAP_EMAIL`, `APP_ADMIN_BOOTSTRAP_USERNAME` (optional), and `APP_ADMIN_BOOTSTRAP_PASSWORD`. Start the backend once, then remove the bootstrap settings. Email, username and password follow registration validation; the password is BCrypt-hashed. Bootstrap is disabled by default, refuses to promote an existing customer, and leaves an existing administrator's password/profile unchanged. There is no public administrator-registration API.

## Stop local services

Stop the backend using IntelliJ's Stop button or Ctrl+C in its terminal. Stop PostgreSQL without deleting its stored data:

```powershell
docker compose stop
```
