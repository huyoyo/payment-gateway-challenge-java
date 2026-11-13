# Payment Gateway — Offline Take-Home Exercise

A Java 17 / Spring Boot 3.1.5 payment gateway that accepts card payments, routes them to an
acquiring-bank simulator, applies idempotency and card-data protection, and exposes a small
OpenAPI-first public API.

This README documents **why** the implementation looks the way it does, along with the assumptions,
scope boundaries, test strategy, and production evolution path.

---

## 1. My Main Design Choices

### 1.1 Contract-first public API

The public contract lives in
[`payment-gateway.yaml`](gateway-app/src/main/resources/openapi/payment-gateway.yaml). The Spring
controller implements the generated `PaymentsApi`; request/response models and Bean Validation
annotations are generated from the same YAML.

I chose this because the exercise is as much about a maintainable API boundary as about processing a
payment. A handwritten DTO layer would drift from the documentation and from validation.

### 1.2 Thin controller, use-case-oriented operation

`PaymentController` only adapts HTTP to the generated API. `PaymentOperation` owns:

- validation orchestration;
- bank-id routing through `PaymentsConnectorRegistry`;
- idempotency reservation;
- connector invocation;
- persistence and response mapping.

This keeps business sequencing out of the controller and makes the bank connector substitutable.

### 1.3 Three response builders, separated by exposure rules

I split response mapping into:

| Builder | Used by | Card-number exposure |
| --- | --- | --- |
| `PaymentResponseBuilder` | Shared mapping | Does not add a number; GET helper adds the mask |
| `CreatePaymentResponseBuilder` | `POST /payments` | No `source.number` |
| `GetPaymentResponseBuilder` | `GET /payment/{id}` | Adds masked `source.number` |

`GetPaymentResponseBuilder` reuses the shared builder and enriches the stored response **in place**
(no deep copy). A POST replay strips any mask that a previous GET may have added, so POST responses
never expose a card number.

### 1.4 Encryption at rest for card data

The repository never stores a plaintext card number or CVV. On `save`:

- AES-GCM with a random 12-byte IV;
- per-payment key derived with `HMAC-SHA256(masterKey, paymentId)`;
- card number and CVV encrypted separately;
- only ciphertext and non-sensitive response fields are persisted.

On GET, only the card number is decrypted in memory, then reduced to the last four digits and
masked as `**** **** **** 1234`. The CVV is never decrypted by the read path.

### 1.5 Idempotency keyed by a client-generated UUID

`requestKey` is mandatory and is the idempotency key. The gateway reserves it atomically before
calling the bank:

- completed request → replay the same payment, do not call the bank again;
- in-progress request → `409 REQUEST_IN_PROGRESS`;
- failed request → replay the terminal failure, do not call the bank again;
- same key with a different payload fingerprint → `409 IDEMPOTENCY_KEY_REUSED`.

This is deliberately stricter than blindly retrying a timed-out POST. If the first bank outcome is
unknown, replaying the stored failure prevents a duplicate charge; production reconciliation handles
the ambiguous case.

### 1.6 Stable connector SPI

`connector-api` contains the bank-agnostic SPI and error contract. `connector-bank` implements it
with Jersey 2.35. `gateway-app` never imports the concrete bank connector.

Adding a bank means adding a connector module and registering its runtime dependency; the
application/controller/operation flow does not need to change.

### 1.7 Simple, visible security boundary

The take-home uses a lightweight Basic token check. It compares the configured Base64 token
exactly; it does not decode a username/password pair:

```http
Authorization: Basic bWVyY2hhbnQ6c2VjcmV0
```

Only `/payments` and `/payment/**` are protected. Health checks, OpenAPI YAML, and Swagger UI remain
public. This is intentionally simple for the exercise; it is not a production IAM design.

### 1.8 Explicit Spring bean names

Stereotypes use readable, qualified bean names:

```java
@Component("gateway.app.connector.payments-connector-registry")
@Component("gateway.app.api.builder.payment-response-builder")
@Repository("gateway.app.repository.in-memory-payment-repository")
@Service("gateway.app.domain.payment-operation")
```

This makes the application context and dependency graph easier to inspect during debugging.

---

## 2. Architecture

```mermaid
flowchart LR
    Merchant["Merchant / API client"]
    Web["gateway-web<br/>RequestIdFilter<br/>BasicTokenAuthenticationFilter<br/>Spring MVC"]
    App["gateway-app<br/>PaymentController<br/>PaymentOperation"]
    Registry["PaymentsConnectorRegistry"]
    BankConn["connector-bank<br/>Jersey 2.35 client"]
    Simulator["Bank Simulator<br/>Docker :8080"]
    Store[("Encrypted in-memory<br/>payment store")]
    Idem[("Idempotency store<br/>requestKey state machine")]
    OpenAPI["OpenAPI public contract<br/>PaymentsApi + generated models"]

    Merchant -->|HTTPS + Basic token| Web
    Web --> App
    App --> Registry
    Registry -->|IPaymentProcessor| BankConn
    BankConn --> Simulator
    App --> Store
    App --> Idem
    OpenAPI -.->|generates| App
    OpenAPI -.->|validates| Web
```

### Modules

| Module | Responsibility |
| --- | --- |
| `gateway-web` | Executable Spring Boot app, filters, Swagger/Actuator wiring |
| `gateway-app` | Public API, controller, operation, builders, validation, repository |
| `connector-api` | Stable SPI, commands/outcomes, shared error envelope |
| `connector-bank` | Bank simulator adapter, generated bank POJOs, Jersey client |

Dependency direction:

```text
gateway-web ──▶ gateway-app ──▶ connector-api
     └──▶ connector-bank (runtime) ──▶ connector-api
```

`gateway-app` has no compile-time dependency on a concrete connector.

---

## 3. Request Flows

### 3.1 POST /payments

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant W as gateway-web
    participant O as PaymentOperation
    participant I as Idempotency
    participant B as Bank Connector
    participant R as Repository

    C->>W: POST /payments + Basic + requestKey
    W->>W: Validate Basic token
    W->>O: perform(request)
    O->>O: Bean/domain validation
    O->>O: Resolve bankId + build PaymentCommand
    O->>I: start(requestKey, fingerprint)

    alt requestKey completed
        I-->>O: stored PaymentResponse
        O-->>C: 201 replay, same payment id
    else requestKey in progress
        I-->>O: conflict
        O-->>C: 409 REQUEST_IN_PROGRESS
    else requestKey failed
        I-->>O: stored terminal exception
        O-->>C: same 4xx/5xx, no bank call
    else new requestKey
        O->>B: performPayments(command)
        alt bank authorized/declined
            B-->>O: PaymentOutcome
            O->>R: save(response, cardNumber, cvv)
            O->>I: complete(requestKey, response)
            O-->>C: 201 Authorized / Declined
        else bank timeout / 5xx / integration error
            B-->>O: exception
            O->>I: fail(requestKey, exception)
            O-->>C: 503 / 502, no automatic bank retry
        end
    end
```

### 3.2 GET /payment/{id}

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant W as gateway-web
    participant O as PaymentOperation
    participant R as Repository
    participant X as Crypto
    participant G as GetPaymentResponseBuilder

    C->>W: GET /payment/{id} + Basic
    W->>O: getPayment(id)
    O->>R: findById(id)
    R->>X: decrypt card number for this payment id
    X-->>R: plaintext card number
    R-->>O: PaymentRecord(payment, cardNumber)
    O->>G: build(payment, cardNumber)
    G->>G: lastFour = lastFour(cardNumber)
    G->>G: source.number = **** **** **** + lastFour
    G-->>O: same response instance
    O-->>C: 200 PaymentResponse
```

The card number is used only in memory to derive the mask. The CVV remains encrypted and is never
returned.

### 3.3 Idempotency state machine

```mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS: new requestKey
    IN_PROGRESS --> COMPLETED: bank outcome persisted
    IN_PROGRESS --> FAILED: terminal exception
    COMPLETED --> COMPLETED: retry returns stored response
    FAILED --> FAILED: retry returns stored failure
    IN_PROGRESS --> IN_PROGRESS: duplicate request -> 409
```

### 3.4 Card-data protection

```mermaid
flowchart LR
    Card["Full card number + CVV"] --> Key["HMAC-SHA256(masterKey, paymentId)"]
    Key --> AES["AES-GCM<br/>random IV"]
    Card --> AES
    AES --> Cipher["Base64(IV || ciphertext)"]
    Cipher --> Store[("In-memory store")]
    Store --> Read["GET: decrypt card number only"]
    Read --> Mask["last four<br/>**** **** **** 1234"]
    Mask --> Response["API response"]
```

The master key is supplied through `payment.crypto.key` / `PAYMENT_CRYPTO_KEY`. The checked-in value
is a local development default; production must use a secret manager or KMS.

---

## 4. Public API

### 4.1 Endpoints

| Method | Path | Result |
| --- | --- | --- |
| `POST` | `/payments` | Create/process payment; `201 Authorized` or `Declined` |
| `GET` | `/payment/{id}` | Fetch by payment id; `200` or `404` |

There is no `GET /payments/{id}` alias and requestKey is not a lookup key.

### 4.2 Authentication

```http
Authorization: Basic bWVyY2hhbnQ6c2VjcmV0
```

- Local default token: base64 of `merchant:secret`.
- Config: `gateway.security.basic.token` / `GATEWAY_BASIC_TOKEN`.
- Missing or invalid token: `401 UNAUTHORIZED` with `WWW-Authenticate: Basic realm="payments"`.

### 4.3 Create request

```json
{
  "requestKey": "8f14e45f-ceea-467a-9d0b-1c0a7c3a9e21",
  "bankId": "bank-simulator",
  "source": {
    "type": "CREDIT_CARD",
    "brand": "VISA",
    "number": "4543474002249996",
    "expiryMonth": 6,
    "expiryYear": 2030,
    "cvv": "956",
    "billingAddress": {
      "addressLine1": "123 High St.",
      "city": "London",
      "zip": "SW1A 1AA",
      "country": "GB"
    }
  },
  "amount": { "value": 6540, "currency": "USD" },
  "paymentContext": {
    "captureMode": "SINGLE",
    "transactionInitiatorType": "CUSTOMER",
    "processing": {
      "schema": "CardProcessing",
      "routingNetwork": "CARTES_BANCAIRES"
    }
  }
}
```

`source` is a discriminator: `CREDIT_CARD` or `WALLET`.
`paymentContext.processing` is a discriminator: `CardProcessing` or `WalletProcessing`.

### 4.4 POST response — no card number

```json
{
  "id": "8f14e45f-ceea-467a-9d0b-1c0a7c3a9e21",
  "amount": { "value": 6540, "currency": "USD" },
  "bankId": "bank-simulator",
  "status": "Authorized",
  "source": {
    "type": "CREDIT_CARD",
    "brand": "VISA",
    "cardNumberLastFour": "9996",
    "expiryMonth": 6,
    "expiryYear": 2030
  },
  "processedOn": "2026-11-13T08:00:00Z",
  "processing": { "authCode": "auth-123" }
}
```

### 4.5 GET response — masked card number

```json
{
  "id": "8f14e45f-ceea-467a-9d0b-1c0a7c3a9e21",
  "amount": { "value": 6540, "currency": "USD" },
  "bankId": "bank-simulator",
  "status": "Authorized",
  "source": {
    "type": "CREDIT_CARD",
    "number": "**** **** **** 9996",
    "cardNumberLastFour": "9996",
    "expiryMonth": 6,
    "expiryYear": 2030
  },
  "processedOn": "2026-11-13T08:00:00Z",
  "processing": { "authCode": "auth-123" }
}
```

`processing.authCode` is the gateway field for the bank's `authorization_code`. It is populated only
when the bank returns a non-blank authorization code; Declined payments omit `processing`.

### 4.6 Error envelope

```json
{
  "id": "fa5dac65-91e9-4ad4-a4ef-a17dc7cf61ed",
  "status": "Rejected",
  "timestamp": "2026-11-13T07:13:27.310880Z",
  "code": "UNAUTHORIZED",
  "message": "Missing or invalid Basic token",
  "errors": []
}
```

Every error uses this envelope. `id` is an error-correlation id and is also logged. Error responses
never include card data.

---

## 5. Validation and Supported Values

OpenAPI/Bean Validation enforces:

| Field | Rule |
| --- | --- |
| `requestKey` | Required UUID |
| `bankId` | Required, declared OpenAPI enum value |
| `source` | Required; discriminator `CREDIT_CARD` / `WALLET` |
| `source.number` | 14–19 digits |
| `source.expiryMonth` | 1–12 |
| `source.expiryYear` | 1000–9999 |
| `source.cvv` | 3–4 digits |
| `source.token` | Required for wallet sources |
| `amount.value` | Positive integer |
| `amount.currency` | `GBP`, `USD`, `EUR` only |
| `processing.schema` | `CardProcessing` / `WalletProcessing` |

`PaymentValidator` adds only the cross-field rule that a card expiry must not be in the past. I did
not duplicate field-level rules there; OpenAPI-generated Bean Validation owns them.

The currency allow-list is deliberately capped at three codes to match the exercise requirement.

---

## 6. Idempotency in Detail

`requestKey` is required for POST. The gateway computes a SHA-256 fingerprint of the request and uses
an atomic `putIfAbsent` reservation.

| State / condition | Result |
| --- | --- |
| Same key + same payload, `COMPLETED` | `201`, same payment id, bank not called |
| Same key + same payload, `IN_PROGRESS` | `409 REQUEST_IN_PROGRESS` |
| Same key + same payload, `FAILED` | Same terminal 4xx/5xx, bank not called |
| Same key + different payload | `409 IDEMPOTENCY_KEY_REUSED` |

On a bank timeout or gateway terminal failure, the failure is stored as `FAILED`. A retry therefore
does not blindly issue a second bank POST. This is a deliberate duplicate-charge safeguard.

Current limitation: the idempotency store is an in-memory `ConcurrentHashMap`. It is single-instance,
has no TTL/lease, and is lost on restart. A crash between bank success and state persistence leaves
the request in `IN_PROGRESS`, requiring reconciliation in production.

---

## 7. Offline Take-Home Assumptions

I treated this exercise as an **offline take-home** rather than a production deployment.

1. **Single merchant / single static token.** Basic auth identifies one caller; there is no
   merchant registry, scopes, tenant isolation, or key rotation.
2. **In-memory is acceptable for the exercise.** Payments and idempotency records are not durable
   and are not shared across instances.
3. **Bank simulator is the only acquiring bank.** `bankId` is an OpenAPI enum with
   `bank-simulator`; a real bank would add a connector module and contract.
4. **Wallet is contract-only.** Wallet source and `WalletProcessing` are modeled, but the simulator
   connector is card-only; a valid wallet request is rejected with `400 PAYMENT_REJECTED` and
   `source.type`.
5. **No external network in automated tests.** The bank client and web tests use local in-process
   HTTP servers. Docker is required only for the manual end-to-end run.
6. **No production database.** The repository is an in-memory adapter; encryption is therefore
   process-lifetime only.
7. **Test data only.** No real card data is used. Full PAN/CVV are encrypted at rest and never
   logged or returned in clear.
8. **No 3DS, capture, refund, void, payout, webhook, or reconciliation.** The exercise focuses on
   payment creation and lookup.
9. **No rate limiting, WAF, mTLS, OAuth2, or scopes.** The Basic filter is a simple boundary, not a
   production security stack.
10. **Currency scope is intentionally limited** to `GBP`, `USD`, and `EUR`.

---

## 8. Implementation Boundaries

### In scope

- Two public endpoints: `POST /payments`, `GET /payment/{id}`.
- OpenAPI-first public and bank contracts.
- Card and wallet request polymorphism.
- Card processing through a bank connector SPI.
- Bean Validation plus one cross-field expiry rule.
- Basic64 token validation for payment endpoints.
- Idempotency with requestKey, fingerprint, replay, conflict, and failure replay.
- AES-GCM encryption of card number/CVV at rest.
- Masked card number on GET only.
- Deterministic error envelope and request-id correlation.
- Unit, connector-integration, and web-integration tests.

### Out of scope

- Production persistence, distributed idempotency, leases, and reconciliation.
- Merchant identity, authorization scopes, and tenant-scoped data.
- Rate limiting, circuit breakers, bulkheads, and distributed tracing.
- PCI certification, KMS/HSM integration, key rotation, and tokenization.
- Wallet acquiring, 3DS, capture/refund, webhooks, and settlement.
- Multi-instance deployment and high-availability guarantees.

---

## 9. Test Strategy

```mermaid
flowchart TB
    Unit["Unit tests<br/>validator, builders, idempotency, crypto"]
    Connector["Connector tests<br/>Jersey client + generated bank POJOs<br/>JDK HttpServer"]
    Web["Web integration tests<br/>MockMvc + in-process bank<br/>Basic auth, JSON, status codes"]
    Manual["Manual E2E<br/>Docker bank simulator<br/>real HTTP + Basic token"]

    Unit --> Connector
    Connector --> Web
    Web --> Manual
```

### What is tested

| Layer | Coverage |
| --- | --- |
| `gateway-app` | Bean/domain validation, builder separation, idempotency state machine, encrypted read model, routing, operation orchestration |
| `connector-bank` | Generated request mapping, response mapping, Jersey HTTP 200/400/503, network failure, malformed responses |
| `gateway-web` | Basic auth 401/valid access, POST/GET flows, requestKey replay/conflict, masked number, error envelope, OpenAPI YAML |
| Crypto | AES-GCM round-trip and payment-id-bound ciphertext |
| Manual E2E | Real bank simulator, authorized/declined/error paths, no full PAN in logs |

### Current result

**78 tests pass**, split as:

- `gateway-app`: 33
- `connector-bank`: 15
- `gateway-web`: 30

```bash
./mvnw clean verify
```

The automated suite does **not** require Docker. The bank simulator is used only for the manual E2E
check described below.

---

## 10. Running Locally

### Prerequisites

- JDK 17
- Docker (for the bank simulator)
- Maven Wrapper (included)

### Start the bank simulator

```bash
docker compose up -d
```

The simulator listens on `http://localhost:8080`.

### Build and run

```bash
./mvnw clean verify
./mvnw -pl gateway-web -am spring-boot:run
```

The gateway listens on `http://localhost:8090`.

### Basic token

Local default:

```text
merchant:secret
base64: bWVyY2hhbnQ6c2VjcmV0
```

Production override:

```bash
export GATEWAY_BASIC_TOKEN="$(printf 'merchant:secret' | base64)"
```

### Example request

```bash
curl -i -X POST http://localhost:8090/payments \
  -H 'Authorization: Basic bWVyY2hhbnQ6c2VjcmV0' \
  -H 'Content-Type: application/json' \
  -d '{
    "requestKey": "8f14e45f-ceea-467a-9d0b-1c0a7c3a9e21",
    "bankId": "bank-simulator",
    "source": {
      "type": "CREDIT_CARD",
      "brand": "VISA",
      "number": "2222405343248877",
      "expiryMonth": 6,
      "expiryYear": 2030,
      "cvv": "956"
    },
    "amount": { "value": 100, "currency": "GBP" }
  }'
```

### Swagger UI

```text
http://localhost:8090/swagger-ui/index.html
```

The UI reads `/openapi.yaml`. Health and documentation endpoints are public; payment endpoints
require the Basic token.

### Simulator behavior

The bank simulator uses the card's final digit:

| Final digit | Result |
| --- | --- |
| Odd | `Authorized` (201) |
| Even, non-zero | `Declined` (201) |
| Zero | Simulator 503 → gateway 503 |

---

## 11. Configuration

| Property | Default | Purpose |
| --- | --- | --- |
| `server.port` | `8090` | Gateway HTTP port |
| `gateway.security.basic.token` | local `merchant:secret` Base64 | Basic token validation |
| `payment.crypto.key` | local development key | AES master key; override in production |
| `connector.bank.simulator.base-url` | `http://localhost:8080` | Bank simulator URL |
| `connector.bank.simulator.connect-timeout` | `2s` | Jersey connect timeout |
| `connector.bank.simulator.read-timeout` | `3s` | Jersey read timeout |
| `spring.jackson.default-property-inclusion` | `non_null` | Omit absent response fields |

Production must replace the local Basic token and encryption key with secrets from a secret
manager/KMS.

---

## 12. Future Outlook

If this were extended beyond the exercise, I would prioritize:

1. **Durable state**
   - Payments, idempotency records, and outbox in PostgreSQL.
   - Unique `(merchant_id, request_key)`.
   - `IN_PROGRESS` lease/TTL, `PENDING`/`UNKNOWN` payment states, reconciliation job.

2. **Production security**
   - OAuth2 client credentials or hashed API keys with scopes.
   - Merchant identity and tenant-scoped payment/idempotency lookups.
   - Per-merchant rate limiting (Redis token bucket), auth-failure limits, bank bulkheads.
   - mTLS to acquiring banks, WAF, secret rotation, KMS/HSM.

3. **Resilience**
   - Circuit breaker around the bank connector.
   - Bounded concurrency and queueing.
   - Explicit timeout/ambiguous-outcome handling rather than automatic POST retries.

4. **Domain coverage**
   - Wallet-capable connector.
   - 3DS/SCA, capture, refund, void, webhooks, settlement/reconciliation.

5. **Observability**
   - Metrics for authorization rate, bank latency, idempotency replay/conflict, 401/429.
   - Distributed tracing and audit logs keyed by `X-Request-Id` and error `id`.

6. **Contract and performance**
   - Contract tests against the generated API.
   - Load tests for bank timeout, duplicate requestKey, and Basic auth failures.
   - Chaos/fault injection for bank 5xx and network timeouts.

---

## 13. Project Layout

```text
pom.xml
mvnw / mvnw.cmd
docker-compose.yml

gateway-web/
  Spring Boot entry point, Basic filter, request-id filter, Swagger/Actuator

gateway-app/
  src/main/resources/openapi/payment-gateway.yaml
  Controller, Operation, request/response builders, validation, repository

gateway-connector/
  connector-api/    IPaymentProcessor SPI, command/outcome, shared errors
  connector-bank/   Bank simulator adapter, Jersey client, generated bank POJOs

docs/DESIGN.md      Architecture, API contract, trade-offs and evolution
```

Generated OpenAPI code is produced during `generate-sources` under each module's
`target/generated-sources/openapi` and is not committed.

---

## 14. Summary

The implementation is intentionally small but structured around boundaries that matter in a payment
gateway:

- contract-first API;
- thin HTTP layer and explicit use case;
- replaceable bank connector;
- idempotency before side effects;
- encrypted sensitive data;
- different POST/GET card-data exposure;
- deterministic errors and testable state transitions.

The main deliberate compromises are in-memory persistence, a single static Basic token, no rate
limiting, and no production reconciliation. Those are called out above rather than hidden behind
the happy path.
