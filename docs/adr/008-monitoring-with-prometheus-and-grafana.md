# ADR-008: Monitoring with Prometheus and Grafana

## Status

Accepted — [#78](https://github.com/Kanban4sem/MTOGO-Backend/issues/78)

## Context

Stage and prod run on one 2 GB droplet with 2 GB swap behind Caddy ([ADR-003](003-deployment-pipeline.md)). The backend exposes metrics on management port 8081, reachable only on the Docker networks ([ADR-007](007-exposing-metrics.md)). Nothing collects or shows them yet, so the team can't see whether stage and prod meet their targets. Before monitoring, all containers together used about 570 MB, and the droplet had 1.0 GiB available. We need to decide what collects the metrics, where they are shown, who can see them, and how much of the droplet that may use.

## Decision

- A monitoring stack runs on the same droplet as its own compose project in `deploy/monitoring/`. It is set up and updated by hand, like the Caddy proxy, not by `deploy.sh`. Images are pinned to exact versions.
- **Prometheus** scrapes every 30 s, with a 10 s timeout:
  - The job `backend` scrapes `stage-backend:8081` and `prod-backend:8081` at `/actuator/prometheus`, labelled `env="stage"` and `env="prod"`.
  - The job `cadvisor` scrapes cAdvisor and the job `node` scrapes Node Exporter, without `env`, because they describe the shared droplet.
  - It keeps data for 7 days, with a size cap of 1 GB as a safety limit.
- **cAdvisor** collects metrics for each container, the frontends' included. **Node Exporter** collects the host's metrics from read-only mounts of `/proc`, `/sys` and `/`, without host networking.
- **Grafana** is the only public part, at `https://grafana.mtogo.saldic.dev` through Caddy. Its Cloudflare record is DNS only, like the existing ones, so Caddy gets the certificate. There is no anonymous access and no sign-up. One admin account has its password in `grafana.env` on the droplet, and each team member gets their own Viewer account, created by hand. The data source and the MTOGO Grafana dashboard are provisioned from JSON in the repository.
- **Networks:** Grafana and Prometheus join the external `web` network, Grafana so that Caddy can reach it and Prometheus so that it can reach the backends. cAdvisor and Node Exporter are only on the monitoring project's own network. No monitoring container publishes a port.
- **Memory limits:** Prometheus 256 MB, Grafana 192 MB, cAdvisor 128 MB and Node Exporter 32 MB, 608 MB in all. cAdvisor runs only for Docker containers, with a 30 s housekeeping interval and its unused metric groups turned off. The backends keep their 768 MB limit.
- **Targets on the Grafana dashboard:**
  - p95 latency of API requests under 500 ms: a response the user notices as slow starts around half a second, and 500 ms is an exact bucket edge (ADR-007).
  - 5xx rate under 1%: at most one in a hundred API requests fails on the server's side.
  - Availability of at least 99.9%: about 43 minutes of downtime in 30 days, which leaves room for deployments and restarts on one droplet.
- **Availability** is the share of Prometheus's scrapes of an environment's backend that succeed, over the Grafana dashboard's time range. It is measured from inside the droplet, so it doesn't include Caddy, DNS or the frontend.
- **cAdvisor and the Docker socket:** cAdvisor mounts `/var/run/docker.sock` to name containers, and `/run/containerd/containerd.sock` too, because Docker on the droplet uses the containerd image store. Access to either socket is effectively root on the droplet. Mounting them `:ro` doesn't limit their APIs, so `:ro` is not a protection. The protection is that cAdvisor has no port reachable from outside, isn't on `web`, doesn't run `privileged` and uses a pinned image. cAdvisor doesn't mount `/var/lib/docker` when it can name containers and read their memory without it, because that directory holds every container's configuration, its secrets included.

## Alternatives

- **A managed service such as Grafana Cloud:** Nothing extra on the droplet, but Prometheus would have to push metrics out over the internet, and the team would depend on a free tier and one more account.
- **A larger droplet:** More headroom, but higher cost, while the measured baseline leaves room for 608 MB of monitoring.
- **Availability from a Blackbox Exporter that probes the public URLs:** Measures what users see, Caddy included, but adds a fifth container. Left as a follow-up in [#100](https://github.com/Kanban4sem/MTOGO-Backend/issues/100).
- **Availability from the share of requests that aren't 5xx:** No extra container, but it repeats the 5xx rate and says nothing when the backend doesn't answer at all.
- **A Docker socket proxy that allows only reading containers:** Real protection for the socket, but a fifth container, and nobody has confirmed that cAdvisor works through it. The one to pick if the droplet ever runs code we don't trust.
- **cAdvisor without the Docker socket:** No root-equivalent access, but containers show only as IDs, so the Grafana dashboard can't tell stage from prod.
- **One shared Grafana login:** Simpler, but nobody can be removed without changing the password for everyone, and everyone could change the Grafana dashboard.

## Consequences

- With limits on all containers together above 2 GB, the droplet relies on swap if every container reaches its limit at once. Available memory is checked 24 hours after setup.
- A change to `deploy/monitoring/` reaches the droplet only when someone copies it there and restarts the stack, as [Deployment](../deployment.md#monitoring) describes.
- The Viewer accounts live only in Grafana's volume. Deleting the volume deletes them, and they must be created again by hand.
- Counters restart at zero when a backend restarts, and a scrape fails while it is deployed, which lowers availability a little at every deployment.
- With little traffic, mostly on stage, p95 latency and the rejection share often have no data.
- Alerting isn't covered. It is a follow-up in [#80](https://github.com/Kanban4sem/MTOGO-Backend/issues/80).
