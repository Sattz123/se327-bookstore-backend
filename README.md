# Bookstore backend

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

## Stop local services

Stop the backend using IntelliJ's Stop button or Ctrl+C in its terminal. Stop PostgreSQL without deleting its stored data:

```powershell
docker compose stop
```
