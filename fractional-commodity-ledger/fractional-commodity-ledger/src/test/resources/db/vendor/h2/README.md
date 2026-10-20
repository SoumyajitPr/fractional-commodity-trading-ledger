H2-specific Flyway migrations would go here. None are needed: the common migrations in
`src/main/resources/db/migration` are portable. (The PostgreSQL-only append-only ledger
trigger lives in `src/main/resources/db/vendor/postgresql`.)
