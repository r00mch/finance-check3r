# Finance Tracker

Personal finance tracker with built-in SQL analytics: import bank statements,
categorise spending, and see where the money goes.

> Work in progress. See the roadmap below.

## Stack

- Java 25, Spring Boot 3.5, Spring Data JPA, Spring Security (JWT)
- SQLite + Flyway migrations
- springdoc-openapi (Swagger UI)
- JUnit 5 / Spring Boot Test
- React (frontend, planned)

## Run

```bash
mvn spring-boot:run
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health check: <http://localhost:8080/actuator/health>

## Authentication

`POST /api/v1/auth/register` and `POST /api/v1/auth/login` (JSON `{"email", "password"}`) return a JWT.
Send it as `Authorization: Bearer <accessToken>` on every other endpoint. In Swagger UI use the
**Authorize** button. Passwords are stored as BCrypt hashes; each user only ever sees their own data.

The signing key comes from the `JWT_SECRET` environment variable (at least 32 bytes). A development
default is built in so the app starts locally; **always set your own value outside local development**.

## API

| Resource | Endpoints |
|----------|-----------|
| Accounts | `GET/POST /api/v1/accounts`, `GET/PUT/DELETE /api/v1/accounts/{id}` (response includes the computed balance) |
| Categories | `GET/POST /api/v1/categories`, `GET/PUT/DELETE /api/v1/categories/{id}` |
| Transactions | `GET/POST /api/v1/transactions`, `GET/PUT/DELETE /api/v1/transactions/{id}` |

`GET /api/v1/transactions` accepts `accountId`, `categoryId`, `from`, `to` (ISO dates),
and paging/sorting (`page`, `size`, `sort`). Default order: newest first.

Errors are returned as RFC 7807 problem details; validation failures include a per-field `errors` map.

## Test

```bash
mvn test
```

## Roadmap

- [x] 1. Project skeleton, SQLite + Flyway schema
- [x] 2. Accounts, categories, transactions (REST CRUD, validation, Swagger)
- [x] 3. Authentication (JWT)
- [ ] 4. CSV statement import with deduplication
- [ ] 5. SQL analytics (window functions, month-over-month, top-N)
- [ ] 6. Budgets, anomaly detection, forecast
- [ ] 7. React dashboard
- [ ] 8. Jupyter analysis notebook
- [ ] 9. Docker, CI, demo data

## Database

Money is stored as integer minor units (kopecks) to avoid floating-point errors.
Expenses are negative, income is positive. Schema: `src/main/resources/db/migration`.
