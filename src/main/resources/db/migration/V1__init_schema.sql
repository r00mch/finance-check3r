-- Money is stored as integer minor units (kopecks/cents) to avoid floating-point errors.
-- Sign convention for transactions.amount_minor: expense < 0, income > 0.
-- Dates are stored as ISO-8601 text (YYYY-MM-DD), which sorts and groups correctly in SQLite.

CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE accounts (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    currency   TEXT NOT NULL DEFAULT 'RUB' CHECK (length(currency) = 3),
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (user_id, name)
);

CREATE TABLE categories (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name    TEXT NOT NULL,
    UNIQUE (user_id, name)
);

CREATE TABLE transactions (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id    INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    category_id   INTEGER REFERENCES categories (id) ON DELETE SET NULL,
    amount_minor  INTEGER NOT NULL CHECK (amount_minor <> 0),
    occurred_on   TEXT NOT NULL,
    description   TEXT,
    -- hash of the source CSV row, used to skip duplicates on re-import
    external_hash TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (account_id, external_hash)
);

-- Reports filter by account/category and group by date, so index them together.
CREATE INDEX idx_transactions_account_date  ON transactions (account_id, occurred_on);
CREATE INDEX idx_transactions_category_date ON transactions (category_id, occurred_on);
