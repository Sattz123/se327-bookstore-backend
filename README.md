# Bookstore Backend

Spring Boot 3 enterprise REST API powering the Bookstore reimplementation application. Built with Java 21, Spring Boot 3.5, Spring Security 6 (JWT authentication & RBAC), Spring Data JPA, Hibernate, PostgreSQL 17, Flyway database migrations, and JaCoCo code coverage.

---

## Quick Start: How to Run After Cloning

Follow these step-by-step instructions to run the backend on a clean machine from scratch.

### 1. Prerequisites

Ensure you have the following installed on your machine:
- **Java Development Kit (JDK):** Version 21 (Temurin, Oracle, or OpenJDK 21).
- **Docker Desktop:** With Linux containers running (for PostgreSQL 17).
- **Git:** For source control.
- *(Note: Maven is already included in this repository via the Maven Wrapper `mvnw` / `mvnw.cmd`)*.

Verify your Java version in your terminal:
```powershell
java -version
# Expected: java version "21.x.x"
```

If your terminal is pointing to an older Java version, set `JAVA_HOME` to JDK 21:
```powershell
# Windows PowerShell example (adjust to your JDK 21 install path)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

---

### 2. Clone and Navigate

```powershell
git clone <repository-url>
cd bookstore-reimplementation/bookstore-backend
```

---

### 3. Setup Environment Variables

Copy `.env.example` to `.env` in this backend project folder:

```powershell
# Windows PowerShell
Copy-Item .env.example .env

# macOS / Linux / Git Bash
cp .env.example .env
```

The default `.env` is pre-configured for local development:
```dotenv
POSTGRES_HOST=localhost
POSTGRES_PORT=5433
POSTGRES_DB=bookstore
POSTGRES_USER=bookstore
POSTGRES_PASSWORD=bookstore_dev
FRONTEND_ORIGIN=http://localhost:5173
SPRING_PROFILES_ACTIVE=dev
PRODUCT_IMAGE_DIRECTORY=./uploads/products
```

> [!NOTE]
> - `POSTGRES_PORT=5433` maps the container's internal port `5432` to host port `5433` to prevent conflicts with any locally installed PostgreSQL services.
> - `SPRING_PROFILES_ACTIVE=dev` automatically runs Flyway seed migrations that populate 5 categories, 60 books with cover images, and default Admin/Customer accounts.

---

### 4. Start PostgreSQL via Docker Compose

Run the following command to start the PostgreSQL 17 container in the background and wait until it is healthy:

```powershell
docker compose up -d --wait
```

*(This pulls `postgres:17-alpine`, mounts a named Docker volume `postgres_data`, and verifies connection health via `pg_isready`).*

---

### 5. Launch the Spring Boot Backend

Run the application using the Maven wrapper:

```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run

# macOS / Linux / Git Bash
./mvnw spring-boot:run
```

Alternatively, open the `bookstore-backend` folder in **IntelliJ IDEA**, ensure JDK 21 is selected as the Project SDK, set the run configuration working directory to the `bookstore-backend` folder, and run `BookstoreBackendApplication.java`.

---

### 6. Verify Backend Status

The server starts on port **8080**. All API routes are prefixed under `/api`:
- **API Base URL:** `http://localhost:8080/api`
- **Catalog Verification:** Open `http://localhost:8080/api/products` in your browser or run:
  ```powershell
  curl http://localhost:8080/api/products
  ```
  You should receive a `200 OK` JSON response containing 60 seeded books with pagination metadata.

---

## Default Seed Accounts (Dev Profile)

When `SPRING_PROFILES_ACTIVE=dev` is active, Flyway automatically seeds the following ready-to-use accounts:

| Role | Email | Password | Allowed Access |
| --- | --- | --- | --- |
| **Administrator** | `admin@bookstore.com` | `AdminPassword123!` | Full access to `/api/admin/**` (Products, Categories, Orders, Customers, Dashboard, Profile) |
| **Customer** | `customer@bookstore.com` | `CustomerPassword123!` | Public catalog, Cart, Checkout, Addresses, Payment Methods, Order History, Wishlist |

> [!TIP]
> You can also self-register new customer accounts via `POST /api/auth/register` or through the frontend registration page.

---

## Running Automated Tests

PostgreSQL must be running before running tests, as the integration test suites execute against real PostgreSQL schemas with automatic transaction rollback:

```powershell
# Ensure database is running
docker compose up -d --wait

# Run all JUnit 5 test suites (305 tests)
.\mvnw.cmd test

# Run tests with JaCoCo code coverage report generation
.\mvnw.cmd clean verify
```

Reports are generated under the `target/` directory:
- **Surefire Test Results:** `target/surefire-reports/`
- **JaCoCo HTML Coverage Report:** `target/site/jacoco/index.html`
- **JaCoCo XML Report (for SonarQube):** `target/site/jacoco/jacoco.xml`

---

## Stopping and Resetting Services

- **Stop the backend:** Press `Ctrl+C` in the running terminal.
- **Stop PostgreSQL:**
  ```powershell
  docker compose stop
  ```
  *(Preserves all database data in the Docker volume).*
- **Restart PostgreSQL:**
  ```powershell
  docker compose up -d --wait
  ```
- **Completely Wipe and Reset Database:**
  ```powershell
  docker compose down -v
  ```
  *(Deletes the `postgres_data` volume. Running `docker compose up -d --wait` followed by `.\mvnw.cmd spring-boot:run` will re-create the schema and re-seed from scratch).*

---

## SonarQube Static Analysis

This project maintains zero code smells, zero vulnerabilities, and >80% code coverage on SonarQube.

To run local analysis against a local SonarQube instance (`http://localhost:9000`):
1. Copy `.env.sonar.example` to `.env.sonar`.
2. Generate an analysis token in SonarQube under **My Account > Security** and paste it as `SONAR_TOKEN=<your-token>`.
3. Execute the analysis script:
   ```powershell
   .\scripts\sonar.ps1
   ```

---

## Quality Gate & Review Summary (Stage 22)

SonarQube analysis and regression testing confirm final quality gate metrics:
- **Automated tests:** 305 / 305 tests passing (100%) across 35 test classes.
- **JaCoCo code coverage:** 86.6% line coverage, 82.9% overall coverage, 82.7% new code coverage (exceeding the >= 80% Sonar way threshold).
- **Duplication density:** 0.4% overall, 0.19% on new code (well under the <= 3.0% threshold).
- **Reliability and security:** 0 bugs, 0 vulnerabilities, 0 security hotspots.
- **Quality improvements:** Extracted checkout validation helpers (`validateAddress`, `validateAndFormatPayment`, `validateAndLockCart`) in `CheckoutServiceImpl`. Cleaned up code smells across DAO/service layers (string constants, record array equality, simplified regex patterns, and cognitive complexity reduction in `ProductImageStorage`). Expanded unit test suites for `AdminOrderService`, `AdminCategoryService`, `AdminProductService`, `AdminDashboardService`, `ProductImageService`, and `ProductImageStorage`.

---

## Regression & Concurrency Guarantees (Stage 21)

PostgreSQL integration tests (`src/test/java/se327/bookstore/service/PostgreSqlCheckoutIntegrationTests.java`) verify critical database transaction semantics, isolation, ownership boundaries, and concurrent stock controls:
- **Ownership boundaries:** Checkout strictly verifies ownership of chosen addresses and payment methods, returning `404 Not Found` when a customer attempts to reference records owned by another user. Order confirmation lookups similarly reject cross-user access.
- **Snapshot isolation and audit immutability:** When an order is placed, delivery and line-item details are captured in database snapshots (`PurchaseOrder` and `OrderItem`). Subsequent modifications to the customer's address book or live product catalog (such as title, description, or price updates) do not alter the saved order snapshot.
- **Concurrent stock deduction with multiple formats:** When multiple concurrent transactions compete for shared book stock across different book formats (e.g. `PAPERBACK` and `HARDCOVER`), pessimistic locking (`SELECT ... FOR UPDATE`) ordered by product ID ensures race-condition-free stock deduction without database deadlocks. Transactions exceeding available stock receive a `409 Conflict`, while remaining inventory is accurately preserved.
- **Optimistic concurrency control:** Administrative entity updates enforce version tracking (`@Version`), preventing concurrent overwrite conflicts.

---

## Architecture & API Overview

### Layered Architecture
The backend follows the Spring Boot enterprise layered architecture:
`Controller` $\to$ `Service / ServiceImpl` $\to$ `DAO / DAOImpl` $\to$ `Spring Data JPA Repository` $\to$ `PostgreSQL Database`.

### Environment Configuration Settings

| Setting | Default / Example | Purpose |
| --- | --- | --- |
| `POSTGRES_HOST` | `localhost` | Host of the PostgreSQL database instance |
| `POSTGRES_PORT` | `5433` | Host port mapped to PostgreSQL container port 5432 |
| `POSTGRES_DB` | `bookstore` | Database name |
| `POSTGRES_USER` | `bookstore` | Database username |
| `POSTGRES_PASSWORD` | `bookstore_dev` | Database password |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | Exact frontend origin allowed by CORS policy |
| `SPRING_PROFILES_ACTIVE` | `dev` | Activates dev catalog seed, covers, and demo accounts |
| `PRODUCT_IMAGE_DIRECTORY`| `./uploads/products` | Storage path for administrator-uploaded images |

---

### Core Endpoint Summary

#### 1. Public Catalog & Discovery
- `GET /api/products?q=...&categoryId=...&page=1`: Paginated list of 15 books per page, newest first.
- `GET /api/products/featured`: 8 newest visible books for homepage featured banner.
- `GET /api/products/{id}`: Detailed book information, including available formats and image gallery.
- `GET /api/categories`: List of all visible categories.
- `GET /api/categories/{id}`: Category detail with visible products.

#### 2. Authentication & Security
- `POST /api/auth/register`: Register new customer account (`username`, `email`, `password`, `phone`).
- `POST /api/auth/login`: Authenticate customer or administrator; returns JWT bearer token.
- `POST /api/auth/forgot-password`: Generates secure password reset token.
- `POST /api/auth/reset-password`: Resets password using valid token.

#### 3. Customer Profile & Addresses
- `GET /api/profile`, `PUT /api/profile`: Read and update customer profile.
- `PUT /api/profile/password`: Change password with verification of current password.
- `GET /api/addresses`, `POST /api/addresses`: Manage shipping address book with default selection.
- `PUT /api/addresses/{id}`, `DELETE /api/addresses/{id}`: Edit or remove owned address.

#### 4. Simulated Payment Methods
- `GET /api/payment-methods`: List user's saved payment methods.
- `POST /api/payment-methods`: Save simulated card (`provider`, `cardholderName`, `lastFour`, `expiryMonth`, `expiryYear`). Full card numbers and CVVs are never stored.
- `PUT /api/payment-methods/{id}/default`, `DELETE /api/payment-methods/{id}`: Select default or delete card.

#### 5. Shopping Cart & Wishlist
- `GET /api/cart`: Current authenticated user's cart with server-calculated totals.
- `POST /api/cart/items`: Add book with format (`PAPERBACK`, `HARDCOVER`, `EBOOK`, `AUDIOBOOK`).
- `PUT /api/cart/items/{id}`: Update line item quantity.
- `DELETE /api/cart/items/{id}`: Remove item from cart.
- `GET /api/wishlist`, `POST /api/wishlist/{productId}`, `DELETE /api/wishlist/{productId}`: Wishlist operations.

#### 6. Transactional Checkout & Orders
- `GET /api/checkout/review`: Review cart totals, pricing fingerprint, and available shipping addresses.
- `POST /api/checkout`: Atomically executes order creation, pessimistic inventory deduction, cart clearing, and snapshot capture. Prevents duplicate orders via idempotent `checkoutKey`.
- `GET /api/checkout/orders/{id}`: Order confirmation receipt lookup (strictly ownership-checked).
- `GET /api/orders`, `GET /api/orders/{id}`: Customer purchase order history and detailed line items.

#### 7. Administrator Back-Office (`/api/admin/**`)
- `GET /api/admin/dashboard`: Metrics summary (total products, orders, customers, recent orders).
- `GET /api/admin/products`, `POST /api/admin/products`: Product catalog administration.
- `PUT /api/admin/products/{id}`, `DELETE /api/admin/products/{id}`: Edit or archive products with optimistic locking (`version`).
- `POST /api/admin/products/{id}/images`: Multipart image upload (PNG/JPEG, max 5 MB, stored in `PRODUCT_IMAGE_DIRECTORY`).
- `GET /api/admin/categories`, `POST /api/admin/categories`, `PUT /api/admin/categories/{id}`: Category curation.
- `GET /api/admin/orders`, `GET /api/admin/orders/{id}`: Order fulfillment management.
- `PATCH /api/admin/orders/{id}/status`: Transition status (`PENDING` $\to$ `PROCESSING` $\to$ `COMPLETED` / `CANCELLED`). Order cancellation atomically restores inventory quantities.
- `GET /api/admin/customers`: Paginated customer directory with order counts and spent totals.
- `GET /api/admin/profile`, `PUT /api/admin/profile`: Administrator account settings.

---

### Standard Error Format
All Spring MVC validation errors return standard RFC 7807 `ProblemDetail` JSON:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Please correct the invalid fields.",
  "instance": "/api/example",
  "errors": [
    { "field": "email", "message": "Email must be a valid email address." }
  ]
}
```
