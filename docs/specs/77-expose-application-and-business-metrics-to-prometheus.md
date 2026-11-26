# Expose application and business metrics to Prometheus

**Version:** 1.0 · **Date:** 2026-11-26 · **Issue:** #77 · **Feature flag:** None

## Acceptance criteria

Copied from #77. The issue is the source: change the issue first, then this copy.

- [ ] `GET /actuator/prometheus` and `GET /actuator/health` on port 8081 answer 200 without authentication
- [ ] Port 8080 returns no metrics or health for `/actuator/prometheus` and `/actuator/health`
- [ ] The Docker health check uses port 8081 and passes when the image deploys to stage
- [ ] Placing an order with a confirmed payment adds 1 to `mtogo_orders_placed_total`. A declined payment adds nothing
- [ ] Rejecting an order adds 1 to `mtogo_orders_rejected_total`
- [ ] `mtogo_orders_unassigned` equals the number of unassigned orders
- [ ] Delivering an order records its delivery time in `mtogo_order_delivery_time_seconds`, with buckets from 10 to 120 minutes
- [ ] `http_server_requests_seconds` has buckets including 500 ms, and leaves out requests to `/actuator`
- [ ] ADR-007 is Accepted

## Behaviour

### Exposure

1. Anyone on the Docker network can read the metrics from `GET /actuator/prometheus` on port 8081, without logging in.
2. Anyone on the Docker network can read the health from `GET /actuator/health` on port 8081, without logging in.
3. Port 8081 exposes no actuator endpoint other than health and prometheus. Any other actuator path on it, such as `/actuator/info`, answers 401 without a token and 404 with a valid token.
4. Port 8080, the API port, never answers 200 to `/actuator/health` or `/actuator/prometheus` and never returns metrics, with or without a token.
5. Port 8081 is reachable only inside the droplet. `https://stage.mtogo.saldic.dev/actuator/prometheus` and `https://mtogo.saldic.dev/actuator/prometheus` return no metrics.
6. Docker decides whether the backend is healthy from `/actuator/health` on port 8081, so a deployment keeps a new version only when it starts healthy, as before.

### Metrics on API requests

1. Every API request on port 8080 is measured in `http_server_requests_seconds`, by method, URI template, status and outcome.
2. The request metric has buckets at 25, 50, 100, 250 and 500 ms and at 1, 2.5 and 5 s, so Prometheus can calculate p95 and the share of requests under 500 ms.
3. Requests to `/actuator`, including Prometheus's own scrapes, are not measured.

### Order metrics

1. When an order is placed and its payment is confirmed, `mtogo_orders_placed_total` grows by 1.
2. When a payment is declined, or a request to place an order is refused, `mtogo_orders_placed_total` doesn't change.
3. When a restaurant manager rejects an order, `mtogo_orders_rejected_total` grows by 1.
4. When a restaurant manager accepts an order, or an order moves to any status other than Rejected, `mtogo_orders_rejected_total` doesn't change.
5. A metric changes only when the change to the order is saved. Work that is rolled back is never counted.
6. `mtogo_orders_unassigned` is the number of unassigned orders when Prometheus reads it.
7. An order counts as unassigned only while it is in Ready and no delivery agent has taken it. It stops counting when a delivery agent takes it, and orders in any other status never count.
8. When a delivery agent delivers an order, its delivery time is recorded once in `mtogo_order_delivery_time_seconds`.
9. The delivery time is exactly the time between the order reaching Placed and reaching Delivered, as stored in its order history.
10. Picking up an order, or any other status change, records no delivery time.
11. The delivery-time metric has buckets at 10, 15, 20, 30, 45, 60, 90 and 120 minutes, and a count and sum, so Prometheus can calculate the average and p95 delivery time.
12. Counters and the delivery-time metric start from zero when the backend restarts. The unassigned-orders metric doesn't, because it is read from the orders.
13. A metric never makes a request fail. If a delivered order has no Placed time in its order history, no delivery time is recorded and a warning is logged, and the order is still delivered.

## Implementation decisions

- **Dependency:** `micrometer-registry-prometheus`, with its version managed by Spring Boot 4.1.2.
- **Management port:** the actuator runs on `management.server.port=8081`, with web exposure `health,prometheus`. Health keeps `show-details=never`. The API stays on 8080 and serves no actuator endpoint. Follows [ADR-007](../adr/007-exposing-metrics.md).
- **Access:** `SecurityConfig` replaces its `/actuator/health` permit with a `GET` permit for exactly `/actuator/health` and `/actuator/prometheus`. The same filter chain guards both ports, so every other actuator path still requires a login. No `EndpointRequest.toAnyEndpoint()` permit, so an endpoint exposed later needs its own decision (ADR-007).
- **HTTP metrics:** `http.server.requests` gets service-level-objective buckets 25 ms, 50 ms, 100 ms, 250 ms, 500 ms, 1 s, 2.5 s and 5 s through `management.metrics.distribution.slo`, not a generated percentile histogram. Requests to `/actuator` are left out because the management port measures no requests, so Prometheus's scrapes never reach `http.server.requests`. No `MeterFilter` is added.
- **Order metrics module:** an order-metrics component in the order feature's domain layer owns the four meters and their names:
  - `mtogo.orders.placed`: counter, exported as `mtogo_orders_placed_total`
  - `mtogo.orders.rejected`: counter, exported as `mtogo_orders_rejected_total`
  - `mtogo.orders.unassigned`: gauge, exported as `mtogo_orders_unassigned`
  - `mtogo.order.delivery.time`: timer with SLO buckets 10, 15, 20, 30, 45, 60, 90 and 120 minutes, exported as `mtogo_order_delivery_time_seconds`
- **Recording after commit:** `OrderService` publishes an order-status-changed event through `ApplicationEventPublisher` for every move it makes: placing, the restaurant manager's changes and the delivery agent's pick up and deliver. It publishes this for every move, not only when the customer has a phone number like the SMS event. The event carries the new status and, for Delivered, the order's Placed and Delivered times from its order history. The order-metrics component listens with `@TransactionalEventListener` (after commit), the same way the SMS sender does. It records Placed and Rejected on the counters and records Delivered minus Placed on the timer, or skips the timer and logs a warning when the order has no Placed time. It never calls `now()`, so the delivery time matches the order history exactly. The `Order` entity and how it stamps status changes don't change.
- **Unassigned gauge:** a function gauge calls a new `OrderRepository` count of orders in Ready with no delivery agent each time Prometheus scrapes, every 30 s per environment (#78). It keeps no state.
- **Container:** the Dockerfile health check calls `http://127.0.0.1:8081/actuator/health`, and the Dockerfile adds `EXPOSE 8081`. `compose.yml`, the Caddyfile and `deploy.sh` don't change: no port is published, Caddy still forwards only `/v1/*` (and the API documentation on stage), and `deploy.sh` follows the Docker health check through `docker compose up --wait`.
- **Tests' ports:** the test-only properties set `management.server.port=0`, so test JVMs running at the same time never share 8081. `UserSeedInitializerTest`, which starts the application with its own arguments, adds `--management.server.port=0` next to `--server.port=0`.
- **API contract:** no change to `/v1`. The frontend is not affected.
- **Documentation:**
  - README and the local development guide no longer list `GET /actuator/health` among the open endpoints on the API. They say that health and metrics are on management port 8081 without a login, and that Docker checks health on 8081.
  - Glossary: **Metric** and **Unassigned order** are added, and **Figure** no longer avoids "Metric". **Delivery time** already exists. The glossary is shared, and Kanban4sem/MTOGO-Frontend#70 copies it.
- **ADRs:** [ADR-007 Exposing metrics](../adr/007-exposing-metrics.md), Proposed in this issue and Accepted before delivery. [ADR-003](../adr/003-deployment-pipeline.md) and [ADR-004](../adr/004-backend-structure.md) apply unchanged.

## Testing decisions

- **Order metrics**, `OrderMetricsTest` in the order feature's domain package. It drives `OrderService` through its public methods with real collaborators, the context's real `MeterRegistry` and `@SharedDatabase`, and the mock payment provider for declined payments. It has no test transaction, so the after-commit listener fires. Every counter, gauge and timer is asserted as a delta: the value before plus the expected change equals the value after, because the registry outlives a single test. Prior art: `OrderServiceTest`. Rule sets under `testcases/order/order-metrics/`:
  - `place-counted` (EP): confirmed payment adds 1, declined payment adds 0, refused request (for example a menu item from two restaurants) adds 0.
  - `update-restaurant-order-status-rejected` (EP): moving to Rejected adds 1, moving to Accepted adds 0. A rejection that rolls back adds 0.
  - `unassigned-orders-count` (EP and decision table over status × delivery agent): the test creates its own order and asserts the delta. Ready without an agent adds 1. Ready taken by an agent, Placed, Accepted, PickedUp, Delivered and Rejected add 0. Having an agent take an order that was counted lowers the gauge by 1.
  - `update-delivery-agent-order-delivery-time` (EP): delivering adds 1 to the timer's count. Its total grows by exactly the Delivered time minus the Placed time, both read back from the saved order's history, compared in microseconds. Picking up adds 0 to the count. Delivering an order with no Placed time adds 0 and still delivers it. No wall-clock or fixed `Clock` is involved.
- **Endpoint access per port**, `SecurityConfigTest` with REST Assured on both the server and the management port. New rule set `request-management-access.csv`, a decision table of port × path × token:
  - 8081, `/actuator/health`, no token → `200`
  - 8081, `/actuator/prometheus`, no token → `200`
  - 8081, `/actuator/info`, no token → `401`
  - 8081, `/actuator/info`, admin token → `404`, which proves the endpoint isn't exposed
  - 8080, `/actuator/health`, no token → not `200`
  - 8080, `/actuator/prometheus`, no token → not `200`, and no metrics in the body
  - 8080, `/actuator/prometheus`, admin token → `404`

  The spec doesn't know whether a permitted path that has no handler on 8080 answers `404` or `401`, because `/error` isn't permitted. The implementation pins the status it observes in the CSV and records it in the implementation notes. The `/actuator/health` → `200` row leaves `request-endpoint-access.csv`, and its `/actuator/info` → `401` row stays.

- **Metrics output**, `PrometheusMetricsTest`, a `@SpringBootTest` with metrics export enabled (Spring Boot's metrics test auto-configuration) and `@SharedDatabase`. It makes one API request, places and delivers one order, then reads `/actuator/prometheus` on the management port and checks that the output:
  - contains the four `mtogo_*` meters
  - has `http_server_requests_seconds_bucket` with `le="0.5"`
  - has `mtogo_order_delivery_time_seconds_bucket` with `le="600.0"` and `le="7200.0"`, plus `mtogo_order_delivery_time_seconds_count` and `_sum`
  - has no `http_server_requests` series with a `/actuator` URI
- **Manual checks on stage after deliver:**
  - The backend container becomes `healthy`, so the health check on 8081 works and `deploy.sh` keeps the version.
  - On the droplet, `docker compose exec backend wget -qO- http://127.0.0.1:8081/actuator/prometheus` shows `mtogo_orders_placed_total`, `mtogo_orders_rejected_total`, `mtogo_orders_unassigned` and `mtogo_order_delivery_time_seconds`.
  - From outside, `https://stage.mtogo.saldic.dev/actuator/prometheus` returns no metrics.

## Out of scope

- Running Prometheus, Grafana, cAdvisor and Node Exporter, the dashboard and ADR-008: #78.
- Alerting: #80.
- One status-tagged counter for every order status. The grilling kept the two counters the issue asks for.
- Stamping status changes with the `Clock` bean. `Order` keeps `Instant.now()`.
- Copying the glossary to the frontend: Kanban4sem/MTOGO-Frontend#70.

## Further notes

No feature flag: users see no change, and the endpoint is internal. Glossary terms keep a **Figure** on the dashboard, calculated from the orders when it's shown, separate from a **Metric** that Prometheus collects.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-26 - #77

- **Built:** The actuator runs on management port 8081 and exposes only health and prometheus, both open without a login. The API port 8080 serves no actuator endpoint. The Docker health check calls 8081. API requests are measured with the fixed buckets. The management port measures no requests, so `/actuator` URIs stay out without a meter filter. `OrderMetrics` in the order domain owns the four meters. `OrderService` publishes an order-status-changed event for every move, and `OrderMetrics` records it after commit. A delivered order without a Placed time records no delivery time and logs a warning, so a metric never makes a request fail. The actuator permit is `GET` only. The unassigned-orders gauge counts Ready orders without a delivery agent through `OrderRepository` on every scrape. ADR-007 is Accepted: the team accepted it on 2026-11-26.
- **Deviations:**
  - The 2.5 s bucket is written as `2500ms`, because Spring can't bind `2.5s`. The buckets are the ones in the spec.
  - Port 8080 answers `404` to `/actuator/health` and `/actuator/prometheus` without a token, because the permit lets the request through to a missing handler. This is the status pinned in `request-management-access.csv`. With a token it also answers `404`.
  - The tests add `spring-boot-micrometer-metrics-test`. It turns metrics export off in tests, so `SecurityConfigTest` and `PrometheusMetricsTest` use `@AutoConfigureMetrics` to get the Prometheus endpoint.
  - The `MeterFilter` the spec first named was removed in review: the management port measures no requests, so it had nothing to filter. The spec now says so, and `PrometheusMetricsTest` (renamed from `MetricsConfigTest`, since the class it was named after is gone) checks that no `/actuator` series exists.
  - The actuator permit is `GET` only, narrower than the spec's first wording. The spec now says so.
  - The `expected` column of the `OrderMetricsTest` CSV files holds a meter delta, such as `1` or `0`. The Test data files table in the coding standards defines `expected` for service tests only as `ACCEPT` or an exception's simple name, so meter deltas are a gap in the coding standards.
  - `unassigned-orders-count.csv` is named after the meter it reads, because the gauge has no method under test.
  - The event is the record `OrderMetrics.OrderStatusChanged`, next to its listener, as `OrderStatusSmsSender.OrderStatusSms` is.
- **Tests:**
  - `OrderMetricsTest`, with `place-counted.csv`, `update-restaurant-order-status-rejected.csv`, `unassigned-orders-count.csv` and `update-delivery-agent-order-delivery-time.csv` under `testcases/order/order-metrics/`. Three single tests: taking an order lowers the gauge by 1; a rejection inside a transaction that rolls back adds 0 and leaves the order Placed, which fails with a plain `@EventListener`; delivering an order without a Placed time records no delivery time and still delivers it.
  - `SecurityConfigTest`, with `request-management-access.csv`. The health row left `request-endpoint-access.csv`.
  - `PrometheusMetricsTest`, which reads `/actuator/prometheus` on the management port.
  - Manual checks on stage after deliver, as listed under Testing decisions: the container becomes `healthy`, the metrics are visible inside the droplet, and the public URL returns no metrics.
- **Follow-up:** None

## Changelog

| Version | Date       | Change        | Issue |
| ------- | ---------- | ------------- | ----- |
| 1.0     | 2026-11-26 | First version | #77   |
