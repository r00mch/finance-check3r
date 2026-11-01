-- A monthly spending limit for one category. The currency is part of the budget because expenses are
-- always summed within a single currency (see the reports), so "limit 30 000" must say 30 000 of what.
CREATE TABLE budgets (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id INTEGER NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    currency    TEXT NOT NULL CHECK (length(currency) = 3),
    limit_minor INTEGER NOT NULL CHECK (limit_minor > 0),
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (user_id, category_id, currency)
);
