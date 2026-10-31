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

## CSV import

`POST /api/v1/accounts/{id}/import` with a multipart `file` field imports a bank statement.

```bash
curl -H "Authorization: Bearer $TOKEN" -F "file=@samples/statement-sample.csv" \
     http://localhost:8080/api/v1/accounts/1/import
```

- Required columns: `date`, `amount`. Optional: `description`, `category` (Russian names `Дата`, `Сумма`,
  `Описание`, `Категория` also work). Unknown categories are created automatically.
- Tolerant of real-world exports: delimiter `,` `;` or tab, UTF-8 or windows-1251, dates `yyyy-MM-dd` or
  `dd.MM.yyyy`, amounts like `-1250.50`, `-1 250,50`, `1,250.50`.
- A bad row never aborts the import: the response lists it (`errors[].row`) and the valid rows are stored.
- **Re-uploading is safe.** Every row gets a fingerprint (date, amount, description, and which occurrence of
  that exact row it is), stored in `transactions.external_hash` under a unique index. Two identical coffees on
  one day are both kept, but importing the same file twice adds nothing.
- Limit: 2 MB per file.

`samples/statement-sample.csv` is a generated demo statement (30 rows, 3 months).

## Listing transactions

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
- [x] 4. CSV statement import with deduplication
- [ ] 5. SQL analytics (window functions, month-over-month, top-N)
- [ ] 6. Budgets, anomaly detection, forecast
- [ ] 7. React dashboard
- [ ] 8. Jupyter analysis notebook
- [ ] 9. Docker, CI, demo data

## Database

Money is stored as integer minor units (kopecks) to avoid floating-point errors.
Expenses are negative, income is positive. Schema: `src/main/resources/db/migration`.
