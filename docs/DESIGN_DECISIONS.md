# Design Decisions

## Scope and stack

This is a single deployable Spring Boot 3 application with Java 21, Maven, Spring MVC, Spring Data JPA, PostgreSQL, Flyway, Bean Validation, Actuator, and Springdoc OpenAPI. Spring Boot 3.5.16 is the selected maintained 3.x patch line; its official system requirements include Java 21. Springdoc OpenAPI 2.10.0 documents compatibility with Spring Boot 3.5.x. The scope does not justify microservices, messaging, a cache, or an AI service.

## Business decisions

1. **Cancellation status:** `CANCELLED` is added as a terminal state because cancellation must remain visible after the order stops being pending. The four required statuses remain unchanged.
2. **Money:** `BigDecimal` is used end-to-end. Input accepts at most two fractional digits and non-negative prices, amounts are normalized to scale 2, and calculated totals are rounded with `HALF_UP`. The assignment does not name a currency, so values are currency-neutral; multi-currency support would require a currency field and currency-specific scale rules.
3. **Price snapshot:** each line persists the submitted item name, quantity, and unit price. There is no catalog lookup or later repricing.
4. **Public lifecycle:** only `PENDING → PROCESSING → SHIPPED → DELIVERED` is accepted by the status API. A separate cancellation route permits only `PENDING → CANCELLED`.
5. **Administrative calls:** status and cancellation endpoints are described as administrative. No authentication or authorization system was specified, so neither is implemented; this is an explicit limitation, not a claim of protection.

## Persistence and transaction boundaries

`OrderEntity` is the aggregate root. Items cascade from the order and use orphan removal because their lifecycle is wholly contained in the order and no endpoint edits or removes them independently. A database foreign key with `ON DELETE CASCADE` matches that lifecycle.

Order creation saves the parent and all line items in one transaction. Status changes and cancellation acquire a pessimistic write lock on the order row before reading its current state. The scheduled worker uses the same row-locking discipline for a bounded pending batch. Thus a cancel/process race is serialized by PostgreSQL; the transaction that changes `PENDING` first wins. `@Version` adds a stale-write check if a writer bypasses the lock path.

One scheduler call handles no more than the configured batch size (100 by default) in one transaction. Failures roll back that batch; the scheduler adapter logs the failure and will try again on a later tick. Database locks coordinate row writes across instances but do not elect a single scheduler instance. Two instances can run concurrently and contend on rows; no distributed lock or exactly-once claim is made.

Detail retrieval uses a repository entity graph for items. Paged listing returns order summaries and database pagination rather than fetching the item collection for every order. Sorting accepts only `createdAt` and `asc`/`desc`, making the API predictable and avoiding arbitrary sort expressions.

Flyway migration `V1` owns the schema. Hibernate runs with `ddl-auto: validate`, not schema update. PostgreSQL stores instants as `TIMESTAMPTZ`; application timestamps come from an injected UTC `Clock`.

## Error contract

Known errors are mapped to stable HTTP status codes and RFC-style `ProblemDetail` bodies. Validation errors include field messages, while unknown failures return a generic message and are logged server-side. Database messages and stack traces are not sent to clients. Missing orders return `404`; business and concurrent-state conflicts return `409`.

## Patterns and principles

- Spring Data repositories provide the repository abstraction.
- API DTOs and `OrderApiMapper` isolate HTTP shape from persistence entities.
- Constructor injection and the injected `Clock` keep dependencies explicit and behavior testable.
- `OrderStatusTransitionPolicy` encapsulates the transition table. A State pattern would add classes without making these four rules clearer.
- A Strategy is not appropriate because the worker has one policy and the brief gives no genuine variation.
- Controllers handle HTTP only; services own transaction boundaries; domain policy owns state rules; the scheduler is an adapter only.

## Operational trade-offs and follow-ups

- The batch size and cron can be tuned, but one invocation processes one chunk only. Add a backlog drain loop with short transactions if requirements change.
- For high-volume multi-instance processing, consider a claim query using `FOR UPDATE SKIP LOCKED`, a durable job coordinator, metrics, and retry/dead-letter policy. Current locks provide conflict safety, not exactly-once job execution.
- Add authentication/authorization before exposing administrative routes.
- Add idempotency support if clients may retry order creation after uncertain network outcomes.
- Add an explicit ISO currency code if the service must support more than a single deployment-wide currency.
- Add stock reservation, catalog integration, payment, and an isolated optional AI assistant only when the product requirements call for them.

## Build and verification environment

The workspace targets Java 21, Spring Boot 3.5.16, and Maven Wrapper 3.9.16. `mvnw.cmd clean verify` completed successfully with 18 tests, zero failures, zero errors, and zero skips. The 10 API integration tests ran against PostgreSQL 17.11 through Testcontainers. Docker Compose configuration validation, image build, service startup, Actuator health (`UP`), and OpenAPI metadata were also checked successfully; the temporary containers and test database volume were removed afterward.

The OpenAI assistance disclosure and the optional AI extension boundary are documented in the README and [`AI_EXTENSION_PROPOSAL.md`](AI_EXTENSION_PROPOSAL.md).
