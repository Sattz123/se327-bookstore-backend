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

The backend uses `http://localhost:8080`. There are no bookstore endpoints yet, so opening `/` returns a 404. Future API routes use `/api/**`.

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

`FRONTEND_ORIGIN` defaults to `http://localhost:5173` when absent. If Vite starts on a different port or hostname, update this setting and restart the backend. The frontend calls port 8080 directly; it has its own environment configuration.

PostgreSQL data is stored in a Docker named volume and survives container restarts and `docker compose down`. Database name/user/password settings initialize a new volume; changing `.env` later does not change an existing database's credentials. Keep the original settings or explicitly update the database to match.

JPA currently uses `ddl-auto=none`: it connects without creating or deleting tables. Versioned migrations will be introduced with the product data model.

## Tests and build

Start PostgreSQL before running all tests, because the generated application-context test connects to the real local database:

```powershell
docker compose up -d --wait
.\mvnw.cmd test
.\mvnw.cmd package
```

The web tests can run separately without PostgreSQL:

```powershell
.\mvnw.cmd "-Dtest=WebFoundationTests" test
```

They check request validation, malformed JSON, missing routes, unexpected errors, and allowed/rejected CORS origins. Their example endpoints only exist during tests.

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
