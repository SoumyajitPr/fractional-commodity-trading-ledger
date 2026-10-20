# Design notes

How the Fractional Commodity Trading Ledger keeps money consistent, and how to explain it.

## 1. The problem

A user with ₹10,000 buys ₹2,000 of gold. Four things must change together:

1. wallet: ₹10,000 → ₹8,000
2. holding: +0.16 g gold (at ₹12,500/g)
3. a trade record is stored
4. the ledger records where the value came from and where it went

If any one of them is saved without the others, the books are wrong. Everything below exists
to stop that from happening — on failure, under concurrency, and with rounding.

## 2. Architecture: Controller → Service → Repository

```mermaid
flowchart LR
    C[Client / Postman / Swagger] -->|HTTP + Basic auth| S[Spring Security]
    S --> CT[Controller<br/>TradeController]
    CT -->|TradeRequest DTO, @Valid| SV[Service<br/>TradeService<br/>@Transactional]
    SV --> L[LedgerService<br/>@Transactional MANDATORY]
    SV --> R[Repositories<br/>Spring Data JPA]
    L --> R
    R -->|Hibernate SQL| DB[(PostgreSQL)]
```

| Layer | In this project |
|---|---|
| Controller | Maps HTTP to service calls; validates the body with `@Valid`; picks the status code (201 new trade, 200 idempotent replay). No business logic. |
| Service | Business rules and the transaction boundary: locks the wallet, checks funds/holdings, computes quantity and cost, updates wallet + holding, stores the trade, writes the journal. |
| Repository | Spring Data JPA interfaces. Includes the `SELECT … FOR UPDATE` lock queries and the native SQL used by the ledger audit. |
| Database | PostgreSQL with Flyway-managed schema, CHECK and UNIQUE constraints, and an append-only trigger on the ledger. |

Services return DTOs and `spring.jpa.open-in-view` is off, so no lazy loading leaks into the
web layer.

## 3. What happens on `POST /api/trades/buy`

```mermaid
sequenceDiagram
    participant C as Client
    participant TC as TradeController
    participant TS as TradeService (@Transactional)
    participant DB as PostgreSQL
    C->>TC: {"userId":1,"commodity":"GOLD","amount":2000}
    TC->>TS: buy(request)  (after @Valid)
    TS->>DB: BEGIN; SELECT wallet ... FOR UPDATE
    TS->>DB: idempotency check (clientOrderId)
    TS->>DB: SELECT commodity price
    Note over TS: quantity = floor(2000 / 12500, 6 dp) = 0.160000<br/>cost = 0.16 × 12500 = 2000.00<br/>balance ≥ cost ?
    TS->>DB: SELECT holding ... FOR UPDATE (or create)
    TS->>DB: INSERT trade
    TS->>DB: INSERT ledger_transaction + 2 ledger_entries
    TS->>DB: UPDATE wallet, UPDATE holding (flushed at commit)
    TS->>DB: COMMIT
    TS-->>TC: TradeExecutionResponse
    TC-->>C: 201 Created
```

Any exception before COMMIT (insufficient funds, constraint violation, a bug) → ROLLBACK,
and nothing above is visible.

## 4. Double-entry ledger

Every business event writes one **ledger transaction** (a journal) with two or more
**ledger entries**. Each entry DEBITs or CREDITs a positive amount to one account.
`LedgerService.record` refuses to save a journal whose debits ≠ credits.

### Chart of accounts

| Account | Kind | Meaning | Reconciles with |
|---|---|---|---|
| `USER_CASH:{user}` | asset | rupees in the wallet | `wallets.balance` |
| `USER_COMMODITY:{user}:{symbol}` | asset | commodity held, at cost | `holdings.cost_basis` |
| `EXTERNAL_FUNDING:{user}` | equity side | money paid in / taken out | — |
| `REALIZED_PNL:{user}` | equity side | gains (credit) and losses (debit) on sales | sum of `trades.realized_pnl` |

Assets increase with a debit, so the net debit balance of `USER_CASH` must equal the wallet.

### Journals

| Event | Debit | Credit |
|---|---|---|
| Deposit ₹10,000 | USER_CASH 10,000 | EXTERNAL_FUNDING 10,000 |
| Buy 0.16 g gold for ₹2,000 | USER_COMMODITY:GOLD 2,000 | USER_CASH 2,000 |
| Sell 0.08 g at ₹13,000/g (cost ₹1,000) | USER_CASH 1,040 | USER_COMMODITY:GOLD 1,000 · REALIZED_PNL 40 |
| Sell at a loss (proceeds 900, cost 1,000) | USER_CASH 900 · REALIZED_PNL 100 | USER_COMMODITY:GOLD 1,000 |
| Withdraw ₹500 | EXTERNAL_FUNDING 500 | USER_CASH 500 |

Sales use the **average-cost method**: the cost released is
`cost_basis × sold_qty / held_qty`; selling the whole position releases the whole cost, so
no paisa of rounding residue is ever left behind.

### Integrity audit — `GET /api/ledger/verify`

1. every journal balances (debits = credits);
2. total debits = total credits (trial balance);
3. each wallet balance = net of its `USER_CASH` account;
4. each holding's cost basis = net of its `USER_COMMODITY` account.

It returns `"consistent": true` plus lists of any mismatches.

### Append-only

Ledger rows are never updated or deleted. `LedgerEntry` is `@Immutable` in Hibernate, all
ledger columns are `updatable = false`, and on PostgreSQL a trigger raises an error on any
`UPDATE`/`DELETE` of `ledger_transactions` or `ledger_entries`. Corrections would be new,
reversing journals.

## 5. `@Transactional` — all or nothing

- `TradeService.buy/sell` and `WalletService.deposit/withdraw` are `@Transactional`: wallet,
  holding, trade and journal are written in **one** database transaction.
- `LedgerService.record` is `@Transactional(propagation = MANDATORY)`: it can only run inside
  an existing transaction. Writing a journal on its own would be a bug, so it fails fast.
- `TransactionRollbackTest` proves it: the ledger is mocked to throw *after* the wallet was
  debited and the trade inserted; afterwards the wallet is still ₹10,000 and no trade or
  holding exists.
- Runtime exceptions trigger rollback by default; all business exceptions here are unchecked.

## 6. Concurrency — no double spending

Two requests for the same user arriving together could both read ₹10,000 and both spend it.
Prevention:

- **Pessimistic row lock**: the first statement of every money-moving operation is
  `SELECT … FROM wallets WHERE user_id = ? FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`). The second
  request waits until the first commits, then sees the new balance.
- **Lock ordering**: always wallet first, then holding, so two transactions cannot deadlock.
- Different users never block each other.
- **Database constraints** as the final safety net: `CHECK (balance >= 0)`,
  `CHECK (quantity >= 0)`, `UNIQUE (user_id, commodity_id)` on holdings.
- `ConcurrentTradingTest`: 10 parallel ₹2,000 buys on ₹10,000 → exactly 5 succeed, balance 0.00.

Why pessimistic rather than optimistic (`@Version`)? Contention is per user and short; a lock
gives a correct answer first time instead of failing and making the client retry.

## 7. Idempotency

Networks time out; clients retry. An optional `clientOrderId` is stored on the trade with
`UNIQUE (user_id, client_order_id)`. A repeat request returns the original trade
(`"replayed": true`, HTTP 200) instead of buying twice. The check runs **after** the wallet
lock, so two identical concurrent requests cannot both execute.

## 8. Money: `BigDecimal` and rounding

`0.1 + 0.2 != 0.3` with `double`. All money and quantities are `BigDecimal`, stored as
`NUMERIC`. The rules live in one class, `MoneyMath`:

| Value | Scale | Rounding | Why |
|---|---|---|---|
| Money (₹) | 2 | HALF_EVEN (banker's) | unbiased over many operations |
| Quantity (g) | 6 | DOWN | the user never receives more than they paid for |

Buying by amount: `quantity = floor(amount / price, 6)`, `cost = round(quantity × price, 2)`.
Because quantity is rounded down, cost ≤ amount always (fuzz-checked over 200,000 random cases).
Compare with `compareTo`, not `equals` (`2.0` and `2.00` are not `equals`).

## 9. Security

Stateless HTTP Basic with two in-memory users (BCrypt-hashed at startup):
`TRADER` for normal operations, `ADMIN` additionally for price changes and the ledger audit.
CSRF is disabled because the API uses no cookies or sessions.

**Known simplification:** authentication is per role, not per end user — a trader can act on any
`userId`. A production version would authenticate end users (e.g. JWT) and derive `userId`
from the token.

## 10. What I would add next

- Per-user authentication (JWT / OAuth2) and ownership checks
- Price feed instead of admin-set prices; a quoted price with expiry so the user trades at the
  price they saw
- Fees and taxes as extra journal lines
- Pagination for trade and ledger history
- Testcontainers so integration tests run on real PostgreSQL
- Outbox events (trade executed) for notifications and analytics

---

## Interview walkthrough (~90 seconds)

> One of my projects is a **Fractional Commodity Trading Ledger** — a Spring Boot backend that
> lets a user invest any rupee amount, say ₹500, in gold or silver, and keeps the financial
> records exactly right.
>
> It's Java 17 and Spring Boot with REST APIs, PostgreSQL through JPA/Hibernate, Flyway for
> schema migrations and Spring Security for role-based access. I followed a
> Controller–Service–Repository structure: controllers validate and map HTTP, services hold the
> business rules and the transaction boundaries, repositories handle database access.
>
> The core problem is consistency. A buy changes the wallet, the holding, the trade history and
> the ledger. I put all of that in a single `@Transactional` service method, and made the ledger
> writer `MANDATORY`-propagation so it can never run outside that transaction. I wrote a test
> that forces the ledger to fail after the wallet is debited and checks that everything rolls back.
>
> The ledger is double-entry: every event is a journal whose debits equal its credits — a buy
> debits the commodity account and credits the cash account; a sale also books realised profit
> or loss. An audit endpoint checks every journal balances and that each wallet and holding
> matches its ledger account. The ledger is append-only, enforced by a database trigger.
>
> For concurrency, I lock the user's wallet row with `SELECT FOR UPDATE`, so ten simultaneous
> ₹2,000 orders on a ₹10,000 wallet give exactly five fills — that's also a test. Money uses
> `BigDecimal` with a single rounding policy, and quantities are rounded down so a user never
> gets more than they paid for. Orders can carry an idempotency key so client retries don't
> double-buy.

## Likely questions

**Why not just update the balance? Why a ledger?**
A balance tells you *what* you have, not *how* you got there. The ledger is an audit trail you
can recompute balances from; mismatches reveal bugs instead of hiding them.

**What does `@Transactional` actually do?**
Spring wraps the bean in a proxy that opens a DB transaction before the method and commits after
it, or rolls back on a runtime exception. Caveats: it only works through the proxy (calling a
`@Transactional` method from the same class bypasses it) and by default checked exceptions do not
roll back.

**What is propagation `MANDATORY`?**
Join the caller's transaction; throw if there isn't one. `REQUIRED` (default) would silently start
a new one, which for a ledger write would hide a bug.

**How do you prevent double spending?**
Row-level `SELECT … FOR UPDATE` on the wallet at the start of every money operation, fixed lock
order, plus a `CHECK (balance >= 0)` constraint as the last line of defence.

**Optimistic vs pessimistic locking?**
Optimistic (`@Version`) detects a conflict at commit and fails → client retries; good when conflicts
are rare. Pessimistic blocks the second writer until the first finishes; good for short,
contended, must-succeed updates like a wallet.

**Why `BigDecimal`? Why HALF_EVEN?**
Binary floating point can't represent 0.1 exactly. HALF_EVEN (banker's rounding) avoids the
upward bias HALF_UP accumulates over many roundings.

**How is the sale cost computed?**
Average cost: cost released = cost basis × sold / held. Realised P&L = proceeds − cost released.

**What if the client retries a timed-out request?**
Send a `clientOrderId`; the second request returns the first trade instead of executing again.

**Why Flyway instead of `ddl-auto=update`?**
Versioned, reviewable, repeatable migrations; Hibernate never changes production schema implicitly.
