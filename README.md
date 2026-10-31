# Finance Tracker

Personal finance tracker with built-in SQL analytics: import bank statements,
categorise spending, and see where the money goes.

> Work in progress. See the roadmap below.

## Stack

- Java 25, Spring Boot 3.5
- SQLite + Flyway migrations
- JUnit 5 / Spring Boot Test
- React (frontend, planned)

## Run

```bash
mvn spring-boot:run
```

Health check: <http://localhost:8080/actuator/health>

## Test

```bash
mvn test
```

## Roadmap

- [x] 1. Project skeleton, SQLite + Flyway schema
- [ ] 2. Accounts, categories, transactions (REST CRUD, validation, Swagger)
- [ ] 3. Authentication (JWT)
- [ ] 4. CSV statement import with deduplication
- [ ] 5. SQL analytics (window functions, month-over-month, top-N)
- [ ] 6. Budgets, anomaly detection, forecast
- [ ] 7. React dashboard
- [ ] 8. Jupyter analysis notebook
- [ ] 9. Docker, CI, demo data

## Database

Money is stored as integer minor units (kopecks) to avoid floating-point errors.
Expenses are negative, income is positive. Schema: `src/main/resources/db/migration`.
