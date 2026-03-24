# Notification Service — Claude Code Prompt

You are building a **notification service** — a shared platform service that allows other teams to send email and webhook-based notifications to arbitrary targets. This service is both a useful platform component and a **reference implementation** of the foundation library, demonstrating correct usage of its patterns for teams adopting the framework.

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 25 |
| Framework | Spring Boot 4.x, Spring WebMVC |
| Build | Maven |
| Database | PostgreSQL 18 |
| Email Provider | Azure Communication Services |
| Async Retry Queue | Azure Service Bus (via foundation messaging module) |
| Templating | Thymeleaf (HTML email templates) |
| Auth | Azure Entra ID / OIDC (via foundation `api-auth` module) |
| Observability | Micrometer + OpenTelemetry (via foundation `api-observability` module) |
| Deployment | Kubernetes (AKS), Helm |
| API Contract | Contract-first — OpenAPI 3.1 YAML, generated server interfaces |

---

## Foundation Library

The service depends on the foundation library at `https://github.com/bkjohnson19/foundation`. Clone this repository and read its `README.md`, its `CLAUDE.md`, and the sample applications before writing any code. The library provides the framework this service builds on.

### Required Dependencies

```xml
<!-- BOM in dependencyManagement -->
<dependency>
    <groupId>dev.bkjohnson</groupId>
    <artifactId>foundation-bom</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- API starter -->
<dependency>
    <groupId>dev.bkjohnson</groupId>
    <artifactId>api-spring-boot-starter</artifactId>
</dependency>

<!-- Messaging starter (for Service Bus retry queue) -->
<dependency>
    <groupId>dev.bkjohnson</groupId>
    <artifactId>messaging-spring-boot-starter</artifactId>
</dependency>

<!-- Service Bus transport -->
<dependency>
    <groupId>dev.bkjohnson</groupId>
    <artifactId>messaging-servicebus</artifactId>
</dependency>

<!-- Test support -->
<dependency>
    <groupId>dev.bkjohnson</groupId>
    <artifactId>api-test-support</artifactId>
    <scope>test</scope>
</dependency>
```

### Foundation Features to Exercise

This service must demonstrate correct usage of the following foundation capabilities. Each one should appear in the codebase in a natural, motivated way — not as a checkbox exercise. If a feature doesn't fit a particular endpoint, don't force it.

| Foundation Feature | Where It Appears in This Service |
|--------------------|----------------------------------|
| `@ApiVersion("v1")` | All controllers. The service launches at v1 with `ACTIVE` lifecycle. |
| `@RequireScope` | Send endpoints require `notifications.send`; admin endpoints (template CRUD) require `notifications.admin`. |
| `@RequireRole` | Template management restricted to `NotificationAdmin` role. |
| `@RateLimit` | Send endpoints rate-limited to prevent notification spam (e.g., 60 requests per 60 seconds per consumer). |
| `@Idempotent` | Send endpoints accept `Idempotency-Key` header to prevent duplicate sends on retry. |
| `@FilterableFields` | Delivery history query endpoint filterable by `status`, `channel`, `recipientAddress`, `createdAt`. |
| `@ApiCacheable` | Template lookup by ID cached with tenant isolation (templates change infrequently). |
| `@ApiCacheEvict` | Template create/update/delete evicts the template cache. |
| `RequestContext` | Tenant ID extracted from JWT `tid` claim. All database queries, cache keys, and audit records are tenant-scoped. |
| `ApiException` hierarchy | `NotFoundException` for missing templates/deliveries, `ValidationException` for invalid payloads, `ConflictException` for duplicate template names. |
| `@RawResponse` | Health and readiness probes opt out of the `ApiResponse<T>` envelope. |
| Domain events (`api-events` + `messaging-core`) | Publish `notification.sent`, `notification.failed`, `notification.delivered` events to Service Bus for downstream consumers. |
| `@BulkOperation` | Bulk send endpoint accepts multiple notification requests with `PARTIAL_SUCCESS` strategy. |
| `MockAuthenticationBuilder` | All controller tests use the foundation test harness for simulated auth. |
| `ApiResponseAssert` | Integration tests use foundation custom assertions. |
| Testcontainers (PostgreSQL 18) | Integration tests use the foundation-provided container factory. |
| Feature flags (`api.feature-flags`) | Two service-specific flags backed by Azure App Configuration. See below. |

### Service-Specific Feature Flags

The foundation library provides 31 built-in feature flags for framework behavior (caching, rate limiting, idempotency, etc.). This service adds two application-level flags that control service-specific behavior at runtime without redeployment. These flags should use the foundation's built-in Azure App Configuration integration.

| Flag | Default | Purpose |
|------|---------|---------|
| `notification.feature.bulk-send-enabled` | `false` | Gates the bulk send endpoint (`@BulkOperation`). This is a high-risk surface — a misconfigured caller could blast hundreds of notifications in one request. Start disabled, enable per-tenant or globally once consumers have been validated. When disabled, the bulk endpoint returns `404 Not Found` as if it doesn't exist. |
| `notification.feature.dry-run-mode` | `false` | When enabled, send endpoints render templates, validate payloads, evaluate SSRF rules, and compute HMAC signatures — but skip actual dispatch (no email sent, no webhook POST made). Returns a normal `202 Accepted` with the delivery record marked `DRY_RUN`. Useful for callers testing template integrations against production data without sending real notifications. |

Note: Channel-level outage handling (e.g., Azure Communication Services or webhook target failures) is covered by the foundation's `api-http-client` circuit breaker primitives and does not need a manual feature flag.

---

## Enterprise Integration Standard Alignment

This service must align with the Enterprise Integration Standard. Key requirements from the standard:

**REST API conventions:**
- URL path segments use **kebab-case**: `/api/v1/notification-templates`, `/api/v1/notification-deliveries`
- JSON body fields use **camelCase**: `callbackUrl`, `recipientAddress`, `templateId`
- Path and query parameters use **camelCase**: `{deliveryId}`, `?pageSize=20`
- Offset-based pagination with `page`, `size`, `sort` parameters
- All timestamps in **UTC**, ISO 8601 format with `Z` suffix
- Error responses use **RFC 7807 ProblemDetails** (handled by the foundation's global exception handler)

**Security:**
- OIDC via Azure Entra ID (handled by the foundation `api-auth` module)
- Principle of least privilege — scoped permissions per endpoint
- APIM gateway integration (the service sits behind Azure APIM)

**Observability:**
- Prometheus metrics via `/actuator/prometheus`
- Structured JSON logging with correlation IDs (handled by foundation `RequestContext` → MDC)
- Distributed tracing via OpenTelemetry

**Webhook security (from the integration standard's webhook security section):**
- HMAC-SHA256 signature on every outbound callback (`X-Signature-256` header)
- Shared secret per webhook subscription, stored encrypted
- Timestamp header (`X-Webhook-Timestamp`) for replay prevention
- SSRF prevention — validate callback URLs at registration time, block private/internal network ranges
- HTTPS only — reject `http://` callback URLs at registration
- Retry with exponential backoff (handled via Service Bus redelivery)

**Performance targets:**
- P50 latency < 500ms, P95 < 1s for synchronous API calls (submit, query)
- Async delivery processing targets are looser — P95 end-to-end < 5 minutes including retries

---

## Service Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Layer                                 │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │  Send         │  │  Template        │  │  Delivery        │   │
│  │  Controller   │  │  Controller      │  │  Controller      │   │
│  └──────┬───────┘  └──────┬───────────┘  └──────┬───────────┘   │
│         │                  │                      │               │
│  ┌──────▼──────────────────▼──────────────────────▼───────────┐  │
│  │                    Service Layer                            │  │
│  │  NotificationService  TemplateService  DeliveryQueryService│  │
│  └──────┬────────────────────────────────────────┬────────────┘  │
│         │                                        │               │
│  ┌──────▼───────────┐                   ┌────────▼────────────┐  │
│  │  Service Bus      │                   │  PostgreSQL         │  │
│  │  (retry queue)    │                   │  (templates,        │  │
│  │                   │                   │   deliveries,       │  │
│  └──────┬───────────┘                   │   subscriptions)    │  │
│         │                                └────────────────────┘  │
│  ┌──────▼───────────────────────────────────┐                    │
│  │          Delivery Processor               │                    │
│  │  ┌─────────────┐  ┌────────────────────┐ │                    │
│  │  │ Email        │  │ Webhook            │ │                    │
│  │  │ Dispatcher   │  │ Dispatcher         │ │                    │
│  │  │ (ACS SDK)    │  │ (HMAC + HTTP POST) │ │                    │
│  │  └─────────────┘  └────────────────────┘ │                    │
│  └──────────────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

### Request Flow

1. **Caller submits a notification** via `POST /api/v1/notifications/send`. The request includes the channel (email or webhook), the target, and either a template reference with variables or a raw body.
2. **The API layer** validates the request, checks RBAC, deduplicates via `@Idempotent`, and persists a delivery record with status `QUEUED`.
3. **The service publishes a message** to a Service Bus queue for async processing and returns `202 Accepted` with the delivery ID and a status URL.
4. **The delivery processor** (a Service Bus consumer) picks up the message, resolves the template (if applicable), and dispatches via the appropriate channel:
   - **Email**: Renders the Thymeleaf template with the provided variables, sends via Azure Communication Services SDK.
   - **Webhook**: Serializes the payload, computes the HMAC-SHA256 signature, POSTs to the registered callback URL.
5. **On success**, the delivery record is updated to `DELIVERED` and a `notification.delivered` domain event is published.
6. **On failure**, the delivery record is updated to `FAILED` (with error details). The Service Bus retry policy handles redelivery with exponential backoff. After max retries, the message goes to the dead-letter queue and a `notification.failed` event is published.
7. **Callers query delivery status** via `GET /api/v1/notification-deliveries/{deliveryId}` or list deliveries with filtering/pagination.

---

## API Contract (Contract-First)

Produce the OpenAPI 3.1 YAML specification first, then generate server interfaces from it using the `openapi-generator-maven-plugin` with the `spring` generator. Implement the generated interfaces in the controllers.

The OpenAPI spec file should be at `src/main/resources/openapi/notification-service.yaml`.

### Resources and Endpoints

#### Notification Sending

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/api/v1/notifications/send` | Send a single notification (email or webhook) | `notifications.send` scope |
| `POST` | `/api/v1/notifications/send-bulk` | Send multiple notifications | `notifications.send` scope |

The send endpoint returns `202 Accepted` with a delivery ID, not `201 Created`. The actual delivery is async.

**Single send request body:**

```json
{
  "channel": "EMAIL",
  "recipient": {
    "address": "user@example.com",
    "name": "Jane Smith"
  },
  "templateId": "tmpl-welcome-email",
  "templateVariables": {
    "firstName": "Jane",
    "activationUrl": "https://app.example.com/activate?token=abc"
  },
  "callbackUrl": "https://caller.example.com/hooks/delivery-status",
  "metadata": {
    "correlationId": "order-12345",
    "source": "order-service"
  }
}
```

Alternative for raw content (no template):

```json
{
  "channel": "EMAIL",
  "recipient": {
    "address": "user@example.com"
  },
  "subject": "Your order has shipped",
  "body": "<html><body><p>Your order #12345 has shipped.</p></body></html>",
  "contentType": "text/html"
}
```

Webhook send request:

```json
{
  "channel": "WEBHOOK",
  "webhookSubscriptionId": "sub-abc-123",
  "payload": {
    "eventType": "order.completed",
    "orderId": "order-12345",
    "completedAt": "2026-03-22T14:30:00Z"
  }
}
```

**Send response (202 Accepted):**

```json
{
  "data": {
    "deliveryId": "del-uuid-here",
    "status": "QUEUED",
    "statusUrl": "/api/v1/notification-deliveries/del-uuid-here",
    "queuedAt": "2026-03-22T14:30:00Z"
  },
  "metadata": {
    "requestId": "req-uuid",
    "timestamp": "2026-03-22T14:30:00Z"
  }
}
```

#### Notification Templates

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/notification-templates` | List templates (paginated, filterable) | `notifications.admin` scope |
| `POST` | `/api/v1/notification-templates` | Create a template | `NotificationAdmin` role |
| `GET` | `/api/v1/notification-templates/{templateId}` | Get a template by ID | `notifications.admin` scope |
| `PUT` | `/api/v1/notification-templates/{templateId}` | Update a template | `NotificationAdmin` role |
| `DELETE` | `/api/v1/notification-templates/{templateId}` | Delete a template (soft) | `NotificationAdmin` role |

Templates have: `id`, `name` (unique per tenant), `channel` (EMAIL or WEBHOOK), `subject` (for email), `bodyTemplate` (Thymeleaf template string), `variables` (declared variable names with types for validation), `createdAt`, `updatedAt`.

#### Delivery History

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/notification-deliveries` | List deliveries (paginated, filterable) | `notifications.read` scope |
| `GET` | `/api/v1/notification-deliveries/{deliveryId}` | Get delivery status and details | `notifications.read` scope |

Filterable fields: `status`, `channel`, `recipientAddress`, `createdAt`, `templateId`.

Delivery statuses: `QUEUED` → `PROCESSING` → `DELIVERED` or `FAILED`.

#### Webhook Subscriptions

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/webhook-subscriptions` | List webhook subscriptions | `notifications.admin` scope |
| `POST` | `/api/v1/webhook-subscriptions` | Register a webhook subscription | `notifications.admin` scope |
| `GET` | `/api/v1/webhook-subscriptions/{subscriptionId}` | Get subscription details | `notifications.admin` scope |
| `PUT` | `/api/v1/webhook-subscriptions/{subscriptionId}` | Update a subscription | `notifications.admin` scope |
| `DELETE` | `/api/v1/webhook-subscriptions/{subscriptionId}` | Deactivate a subscription (soft) | `notifications.admin` scope |

Webhook subscriptions contain: `id`, `name`, `callbackUrl` (HTTPS only, validated against private ranges), `secret` (shared HMAC secret, write-only in API responses), `eventTypes` (optional filter), `active`, `createdAt`, `updatedAt`.

---

## Domain Model

### Database Tables

All tables include `tenant_id` (populated from `RequestContext`) and use soft deletes (`deleted_at` timestamp).

**notification_templates:**
`id` (UUID, PK), `tenant_id`, `name` (unique per tenant), `channel` (EMAIL/WEBHOOK), `subject`, `body_template`, `variables` (JSONB — declared variable names and types), `created_at`, `updated_at`, `deleted_at`

**webhook_subscriptions:**
`id` (UUID, PK), `tenant_id`, `name`, `callback_url`, `secret_encrypted` (encrypted at rest), `event_types` (JSONB array, nullable — null means all events), `active`, `created_at`, `updated_at`, `deleted_at`

**notification_deliveries:**
`id` (UUID, PK), `tenant_id`, `channel`, `recipient_address`, `template_id` (FK, nullable), `webhook_subscription_id` (FK, nullable), `status` (QUEUED/PROCESSING/DELIVERED/FAILED), `payload` (JSONB — the rendered content or webhook body), `error_detail`, `attempts`, `last_attempted_at`, `delivered_at`, `created_at`, `idempotency_key` (unique per tenant, nullable)

Use Liquibase for schema migrations. Use YAML-format changelogs in `src/main/resources/db/changelog/`. Create a `db.changelog-master.yaml` that includes individual changeset files.

---

## Async Processing

### Service Bus Integration

Use the foundation's `messaging-servicebus` module to produce and consume messages on a Service Bus queue named `notification-delivery`.

**Producer** (in `NotificationService.send()`): After persisting the delivery record with status `QUEUED`, publish a `DeliveryCommand` message containing the `deliveryId` and `tenantId`. The foundation's `MessageProducer` handles serialization, correlation ID propagation, and audit logging.

**Consumer** (a `@ServiceBusListener` in `DeliveryProcessor`): Receives `DeliveryCommand` messages, looks up the full delivery record from PostgreSQL, dispatches via the appropriate channel dispatcher, and updates the delivery status. On success, publish a `notification.delivered` domain event. On exception, let the Service Bus retry policy handle redelivery (the foundation's consumer base class handles acknowledgment and dead-lettering).

**Dead-letter handling**: Messages that exhaust retries land in the dead-letter queue. A separate scheduled job (or a DLQ consumer) marks the corresponding delivery record as `FAILED` and publishes a `notification.failed` domain event.

### Retry Policy

Configure the Service Bus queue with:
- Max delivery count: 5
- Lock duration: 60 seconds
- Exponential backoff between retries (handled by Service Bus)

---

## Email Dispatch

Use the Azure Communication Services Email SDK (`com.azure:azure-communication-email`).

1. Resolve the template (if `templateId` is provided) from the database.
2. Render the Thymeleaf template with the provided `templateVariables` using a `SpringTemplateEngine` configured with a `StringTemplateResolver` (templates are stored in the database, not on the filesystem).
3. Send via the ACS SDK: `EmailClient.beginSend(emailMessage)`.
4. Map ACS delivery status back to the service's delivery status.

---

## Webhook Dispatch

1. Look up the `WebhookSubscription` to get the callback URL and shared secret.
2. Validate the callback URL is still reachable (optional — could be a registration-time-only check).
3. Serialize the payload to JSON.
4. Compute the HMAC-SHA256 signature: `HMAC-SHA256(secret, timestamp + "." + payloadJson)`.
5. POST to the callback URL with headers:
   - `Content-Type: application/json`
   - `X-Signature-256: sha256=<hex-encoded-signature>`
   - `X-Webhook-Timestamp: <unix-epoch-seconds>`
   - `X-Webhook-Delivery-Id: <deliveryId>`
6. Success: HTTP 2xx response from the target.
7. Failure: Any non-2xx response or timeout — let the message return to the Service Bus queue for retry.

### SSRF Prevention

At webhook subscription registration time:
- Reject `http://` URLs (HTTPS only).
- Resolve the hostname and reject if it resolves to a private/internal IP range (10.x, 172.16-31.x, 192.168.x, 127.x, ::1, link-local).
- Reject URLs containing IP addresses directly (require hostnames).

---

## Project Structure

```
notification-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/enterprise/notification/
│   │   │   ├── NotificationServiceApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── SendController.java
│   │   │   │   ├── TemplateController.java
│   │   │   │   ├── DeliveryController.java
│   │   │   │   └── WebhookSubscriptionController.java
│   │   │   ├── service/
│   │   │   │   ├── NotificationService.java
│   │   │   │   ├── TemplateService.java
│   │   │   │   ├── DeliveryQueryService.java
│   │   │   │   └── WebhookSubscriptionService.java
│   │   │   ├── dispatch/
│   │   │   │   ├── DeliveryProcessor.java          (Service Bus consumer)
│   │   │   │   ├── EmailDispatcher.java             (ACS SDK)
│   │   │   │   ├── WebhookDispatcher.java           (HMAC + HTTP)
│   │   │   │   └── TemplateRenderer.java            (Thymeleaf)
│   │   │   ├── model/
│   │   │   │   ├── entity/                          (JPA entities)
│   │   │   │   ├── command/                         (Service Bus message types)
│   │   │   │   └── event/                           (Domain events)
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   │   └── SsrfValidator.java
│   │   │   └── config/
│   │   │       ├── ThymeleafConfig.java
│   │   │       └── AcsEmailConfig.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── openapi/
│   │       │   └── notification-service.yaml        (contract-first spec)
│   │       └── db/changelog/
│   │           └── db.changelog-master.yaml
│   └── test/
│       ├── java/com/enterprise/notification/
│       │   ├── controller/                          (MockMvc integration tests)
│       │   ├── service/                             (Unit tests)
│       │   ├── dispatch/                            (Unit tests with mocks)
│       │   └── integration/                         (Testcontainers end-to-end)
│       └── resources/
│           └── application-test.yml
├── helm/
│   └── notification-service/
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
└── Dockerfile
```

---

## Testing Requirements

This is a shared platform service — other teams depend on it. The test suite must be thorough enough that the team can deploy with confidence after any change. Study the foundation library's own test suite for the level of rigor expected. Every public method should be tested. Every failure path should be tested. Every security boundary should be tested.

### Coverage Requirements

**Unit test line coverage: 95% minimum. The build must fail if this threshold is not met.** Configure JaCoCo to enforce this as a hard gate in the Maven build — not a warning, a build failure. Branch coverage target is 90%. These thresholds match the foundation library's own standards and are non-negotiable for a shared service.

Exclude only auto-generated code (OpenAPI generator output, Liquibase migration classes) and the Spring Boot application entry point from coverage calculations.

### Unit Tests

Every service class, validator, dispatcher, and utility must have dedicated unit tests with mocked dependencies.

**Template management:**
- `TemplateService` — create, update, soft-delete, lookup by ID, lookup by name, duplicate name detection (expect `ConflictException`), missing template (expect `NotFoundException`), tenant isolation (service only returns templates belonging to the current tenant).
- `TemplateRenderer` — successful rendering with all variable types (strings, dates, lists, nested objects), missing required variables, malformed Thymeleaf syntax, HTML escaping of user-provided variables (XSS prevention), null/empty variable handling.

**Webhook subscription management:**
- `WebhookSubscriptionService` — create with HMAC secret generation, update, deactivate, lookup, tenant isolation.
- `SsrfValidator` — exhaustive coverage of blocked ranges: `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `::1`, IPv6 link-local (`fe80::/10`), `0.0.0.0`, Kubernetes internal DNS (`*.svc.cluster.local`, `*.pod.cluster.local`), `localhost`, hostnames that resolve to private IPs. Also test valid public URLs, URLs with non-standard ports, URLs with paths and query strings, `http://` rejection, malformed URLs.
- HMAC secret generation — verify secrets are at least 32 bytes, hex-encoded, generated via `SecureRandom`, and unique across invocations.

**Notification dispatch:**
- `EmailDispatcher` — successful send via ACS SDK (mocked), ACS SDK failure handling, malformed recipient addresses, empty recipient list.
- `WebhookDispatcher` — successful POST, HMAC signature computation verified independently (compute expected HMAC in the test and compare), timestamp header present, notification ID header present, correct `Content-Type`, HTTP error responses from target (4xx, 5xx), connection timeout, read timeout.
- `HmacSigner` — known-answer tests with fixed secret, timestamp, and body. Verify output matches a signature computed by an independent implementation (e.g., `javax.crypto.Mac` directly in the test). Test with empty body, unicode body, large body.

**Notification service:**
- `NotificationService` — submit email with template, submit email with raw body, submit webhook, missing template (expect `NotFoundException`), missing subscription (expect `NotFoundException`), inactive subscription (expect `ValidationException`), idempotent resubmission returns existing record, tenant isolation on queries.
- `DeliveryQueryService` — query by ID, query by ID with delivery attempts included, list with filtering, list with pagination, list with sort, tenant isolation, missing notification (expect `NotFoundException`).

**Feature flags:**
- Bulk send endpoint returns `404` when `bulk-send-enabled` is `false`.
- Dry-run mode skips dispatch, records `DRY_RUN` status, returns `202 Accepted`.

### Integration Tests (MockMvc)

All controller endpoints tested via `MockMvcTestBase` with `MockAuthenticationBuilder`. These tests exercise the full Spring context with an in-memory database and the foundation's in-memory providers (`InMemoryCacheProvider`, `InMemoryIdempotencyStore`, `InMemoryApiRateLimiter`).

**For every endpoint, test:**
- Happy path with valid auth, valid input, expected response status and body.
- Missing/expired auth token → 401.
- Valid token but missing required scope → 403.
- Valid token but missing required role → 403.
- Invalid request body → 422 with RFC 7807 `ProblemDetails` response.
- Resource not found → 404 with `ProblemDetails`.

**RBAC matrix — test every combination:**

| Endpoint Group | Required Scope | Required Role | Test: Has Scope | Test: Missing Scope | Test: Has Role | Test: Missing Role |
|----------------|---------------|---------------|-----------------|---------------------|----------------|-------------------|
| Template CRUD | `notifications.admin` | `NotificationAdmin` | 200/201 | 403 | 200/201 | 403 |
| Send notifications | `notifications.send` | — | 202 | 403 | — | — |
| Query deliveries | `notifications.read` | — | 200 | 403 | — | — |
| Webhook subscriptions | `notifications.admin` | `NotificationAdmin` | 200/201 | 403 | 200/201 | 403 |

**Foundation feature integration tests:**
- `@Idempotent` — submit the same notification with the same `Idempotency-Key` twice. First call returns `202`. Second call returns the same `202` with the same notification ID. No second message published to Service Bus.
- `@RateLimit` — submit requests exceeding the rate limit. Verify `429 Too Many Requests` with `Retry-After` header after the limit is exceeded.
- `@ApiCacheable` — fetch a template by ID twice. Verify the repository is called only once (second call served from cache). Update the template. Fetch again. Verify the cache was evicted and the repository is called.
- `@FilterableFields` — test every declared filterable field with `eq`, `like`, `neq` operators using `FilterRequestBuilder`. Test with an un-whitelisted field name and verify it's rejected.
- Pagination — test `page`, `size`, `sort` parameters. Verify response metadata (`totalElements`, `totalPages`, `hasNext`, `hasPrevious`). Test boundary: page beyond total returns empty content. Test `size` exceeding max (100) is clamped.
- Tenant isolation — create resources as tenant A. Query as tenant B. Verify empty results. Query as tenant A. Verify resources returned. This must be tested for every list endpoint and every get-by-ID endpoint.
- `@RawResponse` — verify send endpoints return a bare `202 Accepted` response (not wrapped in `ApiResponse<T>` envelope).
- `@BulkOperation` — submit a bulk send with a mix of valid and invalid notifications. Verify `207 Multi-Status` with `PARTIAL_SUCCESS` strategy (valid ones accepted, invalid ones failed with individual error details).
- Error response format — verify every error response matches RFC 7807 structure with `type`, `title`, `status`, `detail`, `timestamp`, and `traceId` fields.

**Webhook subscription-specific tests:**
- Registration with `http://` URL → 422.
- Registration with private IP URL → 422.
- Registration with `localhost` → 422.
- Registration with valid HTTPS URL → 201, response includes `hmacSecret`.
- GET subscription by ID → does NOT include `hmacSecret` in response.

### End-to-End Tests (Testcontainers)

Full lifecycle tests running against real infrastructure: PostgreSQL 18 via the foundation's Testcontainers factory, with the Service Bus consumer wired to an in-memory transport for deterministic testing.

- **Email notification lifecycle**: submit email notification with template → verify `QUEUED` record in database → trigger delivery processor → verify Thymeleaf template rendered with correct variables → verify ACS SDK called with rendered content (WireMock) → verify delivery attempt recorded → verify notification status updated to `DELIVERED` → verify `notification.delivered` domain event published.
- **Webhook notification lifecycle**: submit webhook notification → trigger delivery processor → verify outbound POST sent to subscription URL (WireMock) → verify HMAC signature header is correct (compute expected value in test) → verify timestamp header present → verify delivery attempt recorded → verify status updated to `DELIVERED`.
- **Retry on failure**: submit webhook notification → WireMock returns 503 on first attempt → verify delivery attempt recorded with `FAILURE` → Service Bus redelivers → WireMock returns 200 on second attempt → verify second delivery attempt recorded with `SUCCESS` → verify final status is `DELIVERED`.
- **Dead-letter after retry exhaustion**: submit webhook notification → WireMock returns 500 for all attempts → verify delivery attempts recorded for each retry → verify final status is `FAILED` → verify `notification.failed` domain event published.
- **Liquibase migrations**: verify changelogs apply cleanly against a fresh PostgreSQL 18 container. This test catches migration syntax errors and ordering issues before deployment.
- **Concurrent idempotency**: submit the same notification with the same `Idempotency-Key` from two threads simultaneously. Verify exactly one notification record created, exactly one Service Bus message published.

### Test Utilities and Patterns

Use the foundation's test utilities throughout — do not build custom equivalents:

| Utility | Usage |
|---------|-------|
| `MockAuthenticationBuilder` | Every controller test. Build auth contexts with specific tenants, roles, and scopes. |
| `MockMvcTestBase` | Base class for all controller test classes. Provides pre-configured `MockMvc`. |
| `ApiResponseAssert` | Assert on envelope structure, status, data content for non-`@RawResponse` endpoints. |
| `BulkResponseAssert` | Assert on bulk operation results (per-item success/failure). |
| `FilterRequestBuilder` | Build filter query strings for list endpoint tests. |
| `InMemoryCacheProvider` | Test caching behavior without Redis. |
| `InMemoryApiRateLimiter` | Test rate limiting without Redis. |
| `InMemoryIdempotencyStore` | Test idempotency without Redis. |
| `InMemoryDomainEventPublisher` | Capture and assert on domain events published during a test. |
| `WireMockServiceBuilder` / `DownstreamServiceSimulator` | Mock Azure Communication Services and webhook target endpoints. |
| Testcontainers (PostgreSQL 18) | Real database for end-to-end and migration tests. |

---

## Configuration

```yaml
# application.yml
api:
  auth:
    enabled: true
    require-apim-gateway: true
    public-paths:
      - /actuator/health
      - /actuator/info
  cache:
    enabled: true
  rate-limit:
    enabled: true
  idempotency:
    enabled: true
  audit:
    enabled: true
  events:
    enabled: true
  openapi:
    enabled: true
    title: Notification Service
    description: Shared platform service for email and webhook notifications
  feature-flags:
    store: azure-app-configuration
    endpoint: ${APPCONFIG_ENDPOINT}
    label: ${SPRING_PROFILES_ACTIVE:dev}

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:notifications}
    username: ${DB_USER:notifications}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Liquibase manages schema
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
  cloud:
    azure:
      active-directory:
        enabled: true
        credential:
          client-id: ${AZURE_CLIENT_ID}
        profile:
          tenant-id: ${AZURE_TENANT_ID}
          app-id-uri: api://notification-service

notification:
  feature:
    bulk-send-enabled: false
    dry-run-mode: false
  email:
    connection-string: ${ACS_CONNECTION_STRING}
    sender-address: ${ACS_SENDER_ADDRESS:noreply@notifications.example.com}
  webhook:
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
    max-retries: 5

messaging:
  servicebus:
    connection-string: ${SERVICEBUS_CONNECTION_STRING}
    queue-name: notification-delivery
```

---

## Build Verification

`mvn clean verify` must pass. This runs: compile → unit tests → integration tests → JaCoCo coverage check → Checkstyle (Google Java Style) → SpotBugs. A failure at any stage fails the build.

**JaCoCo configuration must enforce hard coverage gates:**

```xml
<rules>
    <rule>
        <element>BUNDLE</element>
        <limits>
            <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.95</minimum>
            </limit>
            <limit>
                <counter>BRANCH</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.90</minimum>
            </limit>
        </limits>
    </rule>
</rules>
```

These are not aspirational targets. The build breaks if coverage drops below 95% line / 90% branch. Exclude only OpenAPI-generated code, Liquibase migration artifacts, and the `main()` entry point from coverage calculation.

Configure Checkstyle, SpotBugs, and JaCoCo to match the foundation library's own build pipeline. Reference the foundation's parent POM or replicate its plugin configuration.

---

## What to Build First

1. **OpenAPI spec** (`notification-service.yaml`) — define all resources, schemas, and examples.
2. **Maven project setup** — POM with foundation BOM, OpenAPI generator plugin, Liquibase, Testcontainers.
3. **Database migrations** — Liquibase changelogs with all tables.
4. **JPA entities and repositories** — with `@TenantColumn` for tenant isolation.
5. **Template CRUD** — controllers, service, tests. This is the simplest resource and validates the foundation wiring.
6. **Webhook subscription CRUD** — including SSRF validation.
7. **Send endpoint** — validation, idempotency, Service Bus publish, 202 Accepted response.
8. **Delivery processor** — Service Bus consumer, email dispatcher, webhook dispatcher.
9. **Delivery query endpoints** — with filtering and pagination.
10. **Helm chart** — based on the foundation's `sample-api-helm`.

---

## Important Constraints

- **Do not reinvent foundation features.** If the foundation provides it (error handling, pagination, auth, rate limiting, etc.), use the foundation's implementation. Do not write custom middleware for things the library already handles.
- **Follow the foundation's extension patterns.** If you need to customize behavior (e.g., a custom `TenantResolver` or `CacheKeyGenerator`), define a `@Bean` that overrides the default via `@ConditionalOnMissingBean`, exactly as shown in the foundation README.
- **Keep it simple.** This is a platform service for an enterprise with hundreds of users and moderate notification volume. Do not over-engineer for hypothetical scale. Build the straightforward solution, instrument it, and optimize only if measurements prove it necessary.
- **The OpenAPI spec is the source of truth for the API contract.** Controllers implement generated interfaces. Do not add endpoints that aren't in the spec.
