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

## Analytics

All reports are plain SQL executed by SQLite (see `ReportRepository`): grouping, window functions and CTEs
run inside the database in a single pass instead of looping over transactions in Java.

| Endpoint | What it shows | SQL techniques |
|----------|---------------|----------------|
| `GET /api/v1/reports/spending-by-category` | Expenses per category and each category's share of the total | `GROUP BY`, `SUM() OVER ()` |
| `GET /api/v1/reports/monthly` | Income, expenses, net, running net, month-over-month change, 3-month moving average, savings rate | recursive CTE, `LAG`, `SUM/AVG() OVER (... ROWS BETWEEN 2 PRECEDING ...)` |
| `GET /api/v1/reports/top-merchants?limit=10` | Where the money goes: expenses grouped by description | `GROUP BY`, `ORDER BY ... LIMIT` |
| `GET /api/v1/reports/month-comparison?month=2025-02` | Per-category expenses vs the previous month | conditional aggregation `SUM(CASE ...)` |

| `GET /api/v1/reports/anomalies?threshold=2` | Unusually large expenses: z-score against the *other* expenses of the same category | window frame `EXCLUDE CURRENT ROW` (leave-one-out mean and variance) |
| `GET /api/v1/reports/forecast` | Next month's expenses: 3-month moving average and linear trend | least-squares line over the last 6 months |

Common filters: `accountId`, `currency`, `from`, `to`. Notes:

- Reports are always in **one currency**; with accounts in several currencies pass `currency` or `accountId`
  (otherwise `400`), so amounts in different currencies are never added together.
- `monthly` includes months without transactions as zeros; otherwise "previous month" and the moving average
  would silently skip gaps.
- Percentages are `null` when the base is zero (e.g. a category bought for the first time).

- **Anomalies** compare each expense with the *other* expenses of its category, so a single huge purchase
  cannot hide itself by inflating the average it is measured against. A category needs at least 5 expenses,
  and when the history is perfectly uniform (e.g. identical rent payments) the spread is floored at 5 % of
  the mean to avoid infinite scores. Deviation is computed from the whole history; `from`/`to` only choose
  which expenses are reported.
- The **forecast** is intentionally simple and says which months it used. The trend needs at least 3 months
  and is floored at 0. It does not model seasonality.

Try it on the demo data: import `samples/statement-sample.csv`, then open `/swagger-ui.html`.

## Budgets

A budget is a monthly limit for one category in one currency.

- `GET/POST /api/v1/budgets`, `GET/PUT/DELETE /api/v1/budgets/{id}`
- `GET /api/v1/budgets/status?month=2025-02` returns, per budget, what is spent and remaining, the percentage
  used, a state (`OK`, `WARNING` from 80 %, `EXCEEDED` above 100 %) and a **projection** of the month's total
  at the current daily pace (`projectedToExceed` warns before the limit is actually hit). Past and future
  months are not extrapolated. Without `month`, the current month is used.
- A budget counts only expenses in its own currency, and is removed automatically if its category is deleted.

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
- [x] 5. SQL analytics (window functions, month-over-month, top-N)
- [x] 6. Budgets, anomaly detection, forecast
- [ ] 7. React dashboard
- [ ] 8. Jupyter analysis notebook
- [ ] 9. Docker, CI, demo data

## Database

Money is stored as integer minor units (kopecks) to avoid floating-point errors.
Expenses are negative, income is positive. Schema: `src/main/resources/db/migration`.
