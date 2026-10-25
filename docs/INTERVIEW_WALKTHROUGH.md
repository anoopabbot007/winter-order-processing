# Interview Walkthrough

## Five-minute introduction

“This project is a small Spring Boot modular monolith for order processing. The HTTP layer validates request DTOs and maps them to application commands. The application service calculates the total from `BigDecimal` item snapshots and persists the order aggregate in one transaction. A dedicated domain policy owns the forward lifecycle and cancellation rule. PostgreSQL and Flyway provide durable state and schema management. The scheduled adapter invokes a testable processor that locks and updates at most one configured batch every five minutes. Pessimistic row locks serialize cancellation against processing, and an optimistic version is an additional stale-write safeguard. The design stays intentionally small: no catalog, payment, microservices, queue, or AI service is necessary for the brief. Administrative operations are not authenticated in this assignment scaffold, which is a known limitation.”

## Request lifecycle: create order

1. `OrderController.create` receives a request record with Bean Validation constraints on customer reference, item list, item name, quantity, and price.
2. `OrderApiMapper` converts the HTTP DTO into `CreateOrderCommand`; API code does not expose JPA entities.
3. `OrderApplicationService.create` calculates each `unitPrice × quantity` and sums the lines using `BigDecimal`; it never reads a client total.
4. The service creates a UUID aggregate in `PENDING`, stores the submitted unit-price snapshots, and gets timestamps from the injected UTC `Clock`.
5. One transaction persists the parent and cascaded item rows. The database constraints backstop key quantity/price rules.
6. The mapper builds the response, and the controller returns `201 Created` with a `Location` header.

## Scheduled processing flow

1. `@EnableScheduling` activates `PendingOrderProcessingJob`; its cron is configurable and defaults to every five minutes in UTC.
2. The job calls `PendingOrderProcessor.processNextBatch(batchSize)`. It does not contain lifecycle logic.
3. In one transaction, the processor selects a bounded, creation-ordered set of `PENDING` rows using a pessimistic write lock.
4. `OrderStatusTransitionPolicy` validates `PENDING → PROCESSING`; all selected rows receive the same injected-clock timestamp.
5. The transaction commits the batch together. A failure rolls back the batch and is logged by the job.
6. Tests call `processNextBatch` or the job entry point directly. No test waits five minutes.

Across multiple nodes, database row locks protect competing changes, but there is no distributed single-execution lock. Instances can overlap and contend; the design does not claim exactly-once scheduling.

## Cancellation and concurrency

`OrderStatusService.cancel` starts a transaction, locks the order row, and asks the transition policy to require `PENDING`. It changes the state to `CANCELLED` only after that check. The scheduled worker takes a write lock on eligible pending rows too. When cancellation and processing race, one transaction gets the row lock first and changes the status; the other then sees a non-pending status and fails with a business conflict. The entity version is also checked on writes. This is a database-backed serialization decision rather than a controller-level check that could race.

## Design patterns and principles

- **Repository:** Spring Data JPA hides database access behind `OrderRepository`.
- **DTO/Mapper:** API records are separate from entities; `OrderApiMapper` owns boundary conversion.
- **Dependency injection:** services receive dependencies via constructors. `Clock` is injectable and deterministic in tests.
- **Transition policy:** the rules are encapsulated in a small policy component. The State pattern would add unnecessary classes for this finite lifecycle.
- **Strategy:** omitted because no alternate processing policy is required.
- **SOLID:** controllers, application services, persistence, scheduling, and transition rules have separate responsibilities. The application depends on repository interfaces, not a concrete controller or global static service.

## Important classes

| Class | Responsibility |
| --- | --- |
| `OrderController` | Versioned REST endpoints, input binding, HTTP status and Location header |
| `OrderApiMapper` | Converts API records to commands and persistence data to response DTOs |
| `OrderApplicationService` | Creates, retrieves, and pages orders within transaction boundaries |
| `OrderStatusService` | Locks an order and applies transition/cancellation commands |
| `OrderStatusTransitionPolicy` | Defines allowed forward transitions and pending-only cancellation |
| `OrderEntity` / `OrderItemEntity` | JPA aggregate and immutable line snapshots |
| `OrderRepository` | Database operations, detail fetching, paging, pessimistic locks |
| `PendingOrderProcessor` | Transactional bounded processing batch, callable from tests |
| `PendingOrderProcessingJob` | Cron adapter and operational logging |
| `ApiExceptionHandler` | Consistent ProblemDetail responses |

## Likely interviewer questions

**Why use `BigDecimal`?**

Binary floating-point cannot represent many decimal fractions exactly. Currency-like values are represented as decimal values, restricted to two input fraction digits here, and multiplied/summed without float or double. A currency code is not present because the assignment did not specify one.

**Why is the total not accepted from the client?**

The total is derived from persisted line snapshots on the server, so a caller cannot set a lower order amount. The parent total is stored for retrieval and indexed queries without recomputing all lines each time.

**Why not use the State pattern?**

There are three forward transitions and one separate terminal cancellation rule. A small policy map is explicit and easy to test. Per-state classes would add indirection without a meaningful behavior difference.

**How does cancellation avoid racing the scheduler?**

Both paths acquire a PostgreSQL write lock on the order row inside transactions before checking and changing the status. The first transition away from `PENDING` commits; the other observes the updated status and returns `409`. `@Version` also detects stale writes on paths that do not take the lock.

**Does the scheduler execute once across all replicas?**

No. Row locks coordinate conflicting order updates, but the application does not use a leader election or distributed scheduler lock. Multiple instances can run and contend. If one global execution is needed, add a well-supported lock/lease or durable job coordinator and test its failure semantics.

**Why use both JPA and Flyway?**

Flyway version-controls production schema changes. Hibernate validates the mapped shape at startup but does not mutate the schema.

**How is N+1 avoided?**

Order detail retrieval has an entity graph for items. The list endpoint returns summaries and does not access the lazy item collection for each result; filtering and paging happen in the database.

**What is missing before production?**

Authentication and authorization, an explicit currency model, idempotency, operational metrics/alerts, and a documented backlog/lock strategy for high-volume replicas. Product catalog, stock, and payment integration also remain out of scope.

## Limitations and improvements

The assignment leaves currency and identity policy unspecified, so amounts are currency-neutral and administrative routes are not authenticated. The worker commits one bounded batch per tick and uses ordinary pessimistic locking rather than a distributed singleton lease. Before production, define currency and authorization semantics, run the PostgreSQL integration suite and operational load checks, set real secrets, add metrics and retry/backlog handling, and decide whether multi-instance claiming needs `SKIP LOCKED` or a dedicated coordinator.

## AI assistance disclosure

OpenAI coding assistance generated the initial implementation, tests, and documentation from the user-provided assignment. The generated result was checked against the requirements and the available command output. The author should rerun the build and PostgreSQL tests, then explain the trade-offs in their own words before using this walkthrough in an interview.
