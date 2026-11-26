# ADR-007: Exposing Metrics

## Status

Accepted — [#77](https://github.com/Kanban4sem/MTOGO-Backend/issues/77)

## Context

Stage and prod run on one droplet behind Caddy ([ADR-003](003-deployment-pipeline.md)). Passing tests and a successful deployment don't show whether MTOGO works while it runs. A Prometheus on the droplet ([#78](https://github.com/Kanban4sem/MTOGO-Backend/issues/78)) needs the backend to expose metrics: the rate, errors and duration of API requests, and how orders and deliveries are going. Until now the backend exposed only `/actuator/health`, on the API port 8080. We need to decide how the metrics are exposed, who can read them and how they are measured.

## Decision

- The backend exposes metrics in the Prometheus format through Micrometer at `/actuator/prometheus`.
- The actuator runs on its own management port, `8081`. Only `health` and `prometheus` are exposed there. The API stays on `8080`, which serves no actuator endpoint.
- Port `8081` is published neither by the container nor by Caddy. It is reachable only on the Docker networks, so the endpoints on it need no login. Spring Security permits exactly `/actuator/health` and `/actuator/prometheus`, so an endpoint exposed later needs its own decision.
- The Docker health check calls `/actuator/health` on `8081`.
- API requests are measured by Spring's `http.server.requests` with fixed buckets: 25, 50, 100, 250 and 500 ms, and 1, 2.5 and 5 s. Requests to `/actuator` are left out, so the measurements describe the API only.
- The order feature's metrics are recorded after the transaction commits, so work that rolls back is never counted. A metric that describes the current state, such as the number of unassigned orders, is read from the database when Prometheus collects it, not kept in memory.

## Alternatives

- **Actuator on port 8080, blocked in Caddy:** One port fewer, but a mistake in the Caddyfile would publish the metrics. The API port would also need a security exception for the actuator.
- **A login on the metrics endpoint:** Protects the endpoint, but Prometheus would need a secret, and the port is already unreachable from outside.
- **Micrometer's generated percentile buckets:** Finer percentiles, but around 30 buckets for every URI, method and status. Prometheus on the droplet has 256 MB, and the generated buckets have no edge at the 500 ms target.
- **Pushing metrics to a gateway:** Prometheus pulls by default, and the backend runs continuously, so a push gateway would be one more service with no gain.
- **Metrics kept in memory for current state:** No database query on each scrape, but the count is wrong after a restart and can drift.

## Consequences

- Prometheus must run on a Docker network the backend is on and scrape `<env>-backend:8081`.
- `/actuator/health` is no longer on 8080. Tests that start the application set the management port to `0`, so test JVMs running at the same time don't share 8081.
- Counters start from zero on every restart. Prometheus's `rate()` and `increase()` handle the reset.
- Percentiles are interpolated between the fixed buckets. The share of requests under 500 ms is exact.
- Each scrape runs one count query per environment.
