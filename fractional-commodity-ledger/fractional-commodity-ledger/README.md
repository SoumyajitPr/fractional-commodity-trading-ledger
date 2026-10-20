# Fractional Commodity Trading Ledger

A Spring Boot backend for buying and selling **fractional** quantities of commodities
(e.g. "invest ₹500 in gold") while keeping wallets, holdings, trades and a
**double-entry ledger** consistent with each other.

| | |
|---|---|
| Language / framework | Java 17, Spring Boot 3.3 |
| API | REST (JSON), Swagger UI |
| Database | PostgreSQL 16, schema managed by Flyway |
| Persistence | Spring Data JPA / Hibernate |
| Security | Spring Security, HTTP Basic, roles `TRADER` and `ADMIN` |
| Tests | JUnit 5, MockMvc, H2 (PostgreSQL mode) — no Docker needed for tests |

---

## 1. Run it

You need **either** Docker **or** Java 17 + Maven + PostgreSQL.

### Option A — Docker only (easiest)

```bash
cd fractional-commodity-ledger
docker compose up --build
```

The first build downloads Maven dependencies (a few minutes). When you see
`Started CommodityLedgerApplication`, open **http://localhost:8080** (redirects to Swagger UI).

Stop with `Ctrl+C`; `docker compose down -v` also deletes the database volume.

### Option B — Run from your machine / IDE

1. **Start PostgreSQL** — either with Docker:
   ```bash
   docker compose up -d postgres
   ```
   or use a local PostgreSQL and create the database once:
   ```sql
   CREATE USER ledger WITH PASSWORD 'ledger';
   CREATE DATABASE commodity_ledger OWNER ledger;
   ```
2. **Run the app**
   ```bash
   mvn spring-boot:run
   ```
   or build a jar and run it:
   ```bash
   mvn clean package
   java -jar target/fractional-commodity-ledger.jar
   ```
   In **IntelliJ IDEA**: *File → Open* the folder (it imports `pom.xml`, Maven is bundled),
   then run `CommodityLedgerApplication`.

Flyway creates all tables and seeds three commodities on first start.

### Credentials

| User | Password | Can do |
|---|---|---|
| `trader` | `trader123` | users, wallets, trades, portfolio, user ledger |
| `admin` | `admin123` | everything above + change prices + ledger audit |

In Swagger UI click **Authorize** and enter one of these.

### Configuration (environment variables)

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/commodity_ledger` |
| `DB_USERNAME` / `DB_PASSWORD` | `ledger` / `ledger` |
| `PORT` | `8080` |
| `TRADER_USERNAME` / `TRADER_PASSWORD` | `trader` / `trader123` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` |

Port 5432 already taken by a local PostgreSQL? Run `DB_PORT=5433 docker compose up -d postgres`
and start the app with `DB_URL=jdbc:postgresql://localhost:5433/commodity_ledger`.

---

## 2. Try it (the ₹10,000 → buy ₹2,000 → ₹8,000 example)

**Postman:** import `postman/Fractional-Commodity-Ledger.postman_collection.json` and run the
requests in order (or with the Collection Runner). It captures the new user id automatically.

**IntelliJ / VS Code:** open `http/requests.http`.

**curl** (macOS/Linux/Git Bash; on Windows PowerShell use `curl.exe`):

```bash
# 1. create a user (note the "id" in the response; assumed to be 1 below)
curl -u trader:trader123 -H "Content-Type: application/json" \
     -d '{"name":"Reeck","email":"reeck@example.com"}' http://localhost:8080/api/users

# 2. deposit ₹10,000
curl -u trader:trader123 -H "Content-Type: application/json" \
     -d '{"amount":10000}' http://localhost:8080/api/users/1/wallet/deposit

# 3. buy ₹2,000 of gold (by amount) -> walletBalance 8000.00, quantity 0.160000 g
curl -u trader:trader123 -H "Content-Type: application/json" \
     -d '{"userId":1,"commodity":"GOLD","amount":2000}' http://localhost:8080/api/trades/buy

# 4. portfolio and ledger
curl -u trader:trader123 http://localhost:8080/api/users/1/portfolio
curl -u trader:trader123 http://localhost:8080/api/users/1/ledger

# 5. audit the whole ledger (admin)
curl -u admin:admin123 http://localhost:8080/api/ledger/verify
```

Response of step 3 (abridged):

```json
{
  "trade": {
    "tradeId": 1, "side": "BUY", "commodity": "GOLD", "unit": "GRAM",
    "quantity": 0.160000, "pricePerUnit": 12500.00, "grossAmount": 2000.00,
    "ledgerEntries": [
      { "account": "USER_COMMODITY:1:GOLD", "direction": "DEBIT",  "amount": 2000.00 },
      { "account": "USER_CASH:1",           "direction": "CREDIT", "amount": 2000.00 }
    ]
  },
  "walletBalance": 8000.00,
  "holdingQuantity": 0.160000,
  "replayed": false
}
```

---

## 3. API

All endpoints are under `/api` and need HTTP Basic auth.

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/users` | TRADER | Create user + empty INR wallet |
| GET | `/api/users` | TRADER | List users with balances |
| GET | `/api/users/{userId}` | TRADER | Get user |
| GET | `/api/users/{userId}/wallet` | TRADER | Wallet balance |
| POST | `/api/users/{userId}/wallet/deposit` | TRADER | `{"amount": 10000}` |
| POST | `/api/users/{userId}/wallet/withdraw` | TRADER | `{"amount": 500}` |
| GET | `/api/commodities` | TRADER | Commodities and prices |
| GET | `/api/commodities/{symbol}` | TRADER | One commodity |
| PUT | `/api/commodities/{symbol}/price` | ADMIN | `{"pricePerUnit": 13000}` |
| POST | `/api/trades/buy` | TRADER | Buy (see body below) |
| POST | `/api/trades/sell` | TRADER | Sell (same body) |
| GET | `/api/trades/{tradeId}` | TRADER | Trade + its ledger entries |
| GET | `/api/users/{userId}/trades` | TRADER | Trade history |
| GET | `/api/users/{userId}/portfolio` | TRADER | Holdings at market value, P&L |
| GET | `/api/users/{userId}/ledger` | TRADER | All journals of the user |
| GET | `/api/ledger/verify` | ADMIN | Integrity audit of the whole ledger |

Public: `/swagger-ui.html`, `/v3/api-docs`, `/actuator/health`.

**Trade body** — give exactly one of `quantity` (units, ≤ 6 decimals) or `amount` (rupees, ≤ 2 decimals):

```json
{ "userId": 1, "commodity": "GOLD", "amount": 500, "clientOrderId": "order-0001" }
```

`clientOrderId` is optional. Sending the same one again returns the original trade with
`"replayed": true` and HTTP 200 instead of trading twice.

**Errors** use RFC 7807 problem details with a machine-readable `code`:

| HTTP | code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | invalid body; field messages in `errors` |
| 401 / 403 | — | missing credentials / wrong role |
| 404 | `NOT_FOUND` | unknown user, commodity or trade |
| 409 | `DUPLICATE` | email already registered, clientOrderId reused for the other side |
| 422 | `INSUFFICIENT_FUNDS`, `INSUFFICIENT_HOLDINGS`, `AMOUNT_TOO_SMALL`, `TRADE_VALUE_TOO_SMALL` | business rule broken |

---

## 4. Tests

```bash
mvn test
```

Tests use an in-memory H2 database in PostgreSQL mode with the same Flyway migrations, so they
need neither Docker nor PostgreSQL.

| Test | Proves |
|---|---|
| `MoneyMathTest` | BigDecimal rounding policy; amount-based buys never cost more than the amount |
| `TradeFlowIntegrationTest` | buy/sell update wallet, holding, trade and ledger; realised P&L; insufficient funds changes nothing; idempotency |
| `TransactionRollbackTest` | a ledger failure *after* the wallet was debited rolls the whole trade back (`@Transactional`) |
| `ConcurrentTradingTest` | 10 simultaneous ₹2,000 buys on ₹10,000 → exactly 5 succeed, balance exactly 0 |
| `TradeApiTest` | HTTP status codes, security roles, validation and JSON shape |

---

## 5. Project structure

```
fractional-commodity-ledger/
├── pom.xml
├── Dockerfile                     # multi-stage build: Maven -> JRE image
├── docker-compose.yml             # PostgreSQL + app
├── postman/                       # Postman collection (20 requests)
├── http/requests.http             # IntelliJ / VS Code HTTP client requests
├── docs/DESIGN.md                 # design decisions + interview notes
└── src
    ├── main
    │   ├── java/com/commodityledger
    │   │   ├── CommodityLedgerApplication.java
    │   │   ├── config/            # SecurityConfig, OpenApiConfig
    │   │   ├── controller/        # REST endpoints (receive requests)
    │   │   ├── service/           # business logic + @Transactional boundaries
    │   │   ├── repository/        # Spring Data JPA (database access, row locks)
    │   │   ├── entity/            # JPA entities + enums
    │   │   ├── dto/               # request/response records + validation
    │   │   ├── exception/         # API exceptions + global handler
    │   │   └── util/MoneyMath.java# all BigDecimal rounding rules
    │   └── resources
    │       ├── application.yml
    │       └── db
    │           ├── migration/     # V1 schema, V2 seed data (portable SQL)
    │           └── vendor/postgresql/  # V3 append-only ledger trigger
    └── test
        ├── java/...               # unit, integration, concurrency, API tests
        └── resources/application-test.yml
```

See **[docs/DESIGN.md](docs/DESIGN.md)** for how the ledger, transactions, locking and rounding
work, and for an interview walkthrough of the project.

---

## 6. Troubleshooting

| Problem | Fix |
|---|---|
| `Connection refused` to PostgreSQL | Start it (`docker compose up -d postgres`) or check `DB_URL` |
| `release version 17 not supported` | Install JDK 17+ and make sure `java -version` / `JAVA_HOME` point to it |
| `mvn: command not found` | Use IntelliJ (bundled Maven), install Maven, or use Option A (Docker) |
| Port 8080 in use | `PORT=8081 mvn spring-boot:run`, or `APP_PORT=8081 docker compose up --build` |
| Want a clean database | `docker compose down -v` |
