# Jackpot Service

Backend service managing jackpot pool contributions and reward evaluations. Built with event sourcing and domain-driven design.

## Prerequisites

- Docker

## Running

One command:

```bash
docker-compose up --build
```

This starts Kafka and the application. The service is available at `http://localhost:8080`.

Alternatively, run without Docker for the app (requires Java 21 installed):

```bash
docker-compose up kafka -d
./gradlew bootRun
```

H2 console available at `/h2-console` (JDBC URL: `jdbc:h2:mem:jackpotdb`).

## Running Tests

```bash
./gradlew test
```

Tests use embedded Kafka and in-memory H2 — no external dependencies needed. The suite includes:

- Unit tests for domain logic (aggregate, policies, event replay)
- Integration tests with real Kafka broker (embedded) and H2
- High-volume test processing 1000 bets to verify correctness at scale
- Rehydration performance test (500+ events under 200ms)
- Concurrency tests (20 threads hitting same jackpot simultaneously)
- End-to-end Kafka flow: publish → consume → contribute → evaluate → verify reward

## Seeded Data

Two jackpots are created on startup:

| Jackpot ID | Contribution | Reward |
|---|---|---|
| `00000000-0000-0000-0000-000000000001` | Fixed 10% of bet | Fixed 5% chance |
| `00000000-0000-0000-0000-000000000002` | Variable (starts 20%, decays toward 4% at pool threshold 2000) | Variable (starts 2%, reaches 100% at pool limit 5000) |

## API

### Place a Bet

Publishes a bet to the `jackpot-bets` Kafka topic. The consumer processes it asynchronously: contributes to the jackpot pool and immediately evaluates for reward.

```bash
curl -X POST http://localhost:8080/api/bets \
  -H "Content-Type: application/json" \
  -d '{
    "betId": "11111111-1111-1111-1111-111111111111",
    "userId": "22222222-2222-2222-2222-222222222222",
    "jackpotId": "00000000-0000-0000-0000-000000000001",
    "amount": 50.00
  }'
```

Response: `202 Accepted`
```json
{
  "betId": "11111111-1111-1111-1111-111111111111",
  "status": "ACCEPTED"
}
```

### Get Reward Result

Queries whether a previously processed bet won a jackpot reward. This is a read-only lookup against the projection.

```bash
curl "http://localhost:8080/api/bets/11111111-1111-1111-1111-111111111111/reward?jackpotId=00000000-0000-0000-0000-000000000001"
```

Response (win):
```json
{
  "betId": "11111111-1111-1111-1111-111111111111",
  "userId": "22222222-2222-2222-2222-222222222222",
  "jackpotId": "00000000-0000-0000-0000-000000000001",
  "rewardAmount": 105.00,
  "won": true
}
```

Response (no win):
```json
{
  "betId": "11111111-1111-1111-1111-111111111111",
  "userId": null,
  "jackpotId": "00000000-0000-0000-0000-000000000001",
  "rewardAmount": 0,
  "won": false
}
```

## Architecture

### Bounded Contexts

```
com.sporty
├── betting/                    → Accepts bets via HTTP, publishes to Kafka
│   ├── BetController           → POST /api/bets
│   ├── BetService              → Orchestrates bet placement
│   ├── BetProducer             → Async Kafka producer (keyed by jackpotId)
│   └── BetMessage              → Kafka message DTO
│
├── jackpot/                    → Processes contributions, evaluates rewards
│   ├── BetConsumer             → Kafka listener
│   ├── JackpotService          → Orchestrates contribution + reward evaluation
│   ├── RewardController        → GET /api/bets/{betId}/reward (read-only)
│   ├── BetMessage              → Kafka message DTO (own copy)
│   ├── domain/                 → Pure domain model (no framework dependencies)
│   │   ├── Jackpot             → Aggregate root
│   │   ├── contribution/       → ContributionPolicy + implementations
│   │   ├── reward/             → RewardPolicy + implementations
│   │   ├── PolicyResolver      → Interface for policy deserialization
│   │   └── event/              → Sealed domain events
│   ├── eventsourcing/          → Event store + PolicyRegistry
│   │   └── deserializer/       → Spring-discovered policy deserializers
│   └── projection/             → Read models (contributions, rewards)
│
└── config/                     → Kafka topic, data seeding, error handling
```

The `betting` context has zero imports from `jackpot`. They communicate exclusively through Kafka. Each context owns its own `BetMessage` DTO.

### Event Sourcing

Jackpot state is reconstructed by replaying domain events. No mutable state is persisted directly — the `stored_events` table is the source of truth.

Events: `JackpotCreated`, `ContributionMade`, `RewardGranted`, `PoolReset`

Each bet is evaluated for reward immediately after contribution — the bet that pushes the pool over the threshold wins it. After a win, the pool resets to its initial value.

### Concurrency Control

- **Kafka partition ordering**: bets are keyed by `jackpotId`, ensuring sequential processing per jackpot at the consumer.
- **Lock striping**: Guava `Striped<Lock>` with 64 stripes serializes writes per jackpot within a single instance, with bounded memory.
- **Optimistic locking**: `UNIQUE(aggregate_id, version)` on the event store as a safety net for multi-instance deployments.
- **Spring Retry**: `@Retryable` with exponential backoff handles the rare conflict in distributed scenarios.

### Idempotency

Three layers of protection against duplicate processing:

| Layer | Mechanism |
|-------|-----------|
| Domain | `processedBetIds` set rebuilt during rehydration |
| Event store | `UNIQUE(aggregate_id, version)` constraint |
| Read model | `UNIQUE(betId, jackpotId)` on projection tables |

### Extensibility

Adding a new contribution or reward strategy:
1. Implement the policy interface (including `type()` and `config()` for serialization)
2. Add a `@Component` deserializer in `eventsourcing/deserializer/` — Spring auto-discovers it via `PolicyRegistry`

No changes to the aggregate, service, controller, or registry code.

## Design Decisions

- **Event sourcing over CRUD**: complete audit trail, natural idempotency via event replay, clean separation between write model (events) and read model (projections).
- **Domain owns behavior**: the `Jackpot` aggregate encapsulates all business rules. Services are thin orchestrators.
- **No framework in domain**: the `domain` package has zero Spring/JPA imports. Pure Java, unit-testable without application context.
- **Auto-evaluated rewards**: each bet is evaluated immediately after contribution to ensure the bet that crosses the threshold wins — not a later bet that happens to query first.
- **Synchronous projections**: updated inline after event persistence. Simpler than async at this scale, ensures read-after-write consistency.
- **No external ES framework**: hand-rolled event store (~80 lines) keeps the solution transparent.
- **Duplicated DTOs over shared library**: each bounded context owns its Kafka message contract independently.

## Production Considerations

- **Snapshots**: at current scale, replaying 500+ events takes < 200ms. For production workloads with thousands of events per aggregate, periodic snapshots would avoid replaying the full history. Snapshots must include the full aggregate state (pool, processedBetIds, rewardedBetIds, policies) — simply truncating old events would break idempotency guarantees.
- **Async projections**: decoupling projection updates from event persistence would prevent projection failures from rolling back committed events.
