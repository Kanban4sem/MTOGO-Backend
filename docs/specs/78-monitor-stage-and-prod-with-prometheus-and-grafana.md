# Monitor stage and prod with Prometheus and Grafana

**Version:** 1.0 · **Date:** 2026-11-26 · **Issue:** #78 · **Feature flag:** None

## Acceptance criteria

Copied from #78. The issue is the source: change the issue first, then this copy.

- [ ] Prometheus shows `up` = 1 for stage-backend (`env="stage"`), prod-backend (`env="prod"`), cAdvisor and Node Exporter
- [ ] `https://grafana.mtogo.saldic.dev` asks for a login, refuses anonymous access and sign-up, and shows the MTOGO dashboard from the provisioned JSON
- [ ] A Viewer account can see the dashboard but not save changes to it
- [ ] The dashboard shows p95 latency, 5xx rate and availability against 500 ms, 1% and 99.9% for stage and prod side by side
- [ ] The dashboard shows orders placed per hour, rejection share, unassigned orders and average and p95 delivery time for stage and prod
- [ ] The dashboard shows CPU and memory for each container, by name, and for the host
- [ ] No monitoring container publishes a port, and `https://mtogo.saldic.dev/actuator/prometheus` and the stage equivalent don't return metrics
- [ ] 24 hours after setup, no container has been OOM-killed, `free -m` shows at least 256 MiB available, and stage and prod are healthy
- [ ] ADR-008 is Accepted

## Behaviour

### Collecting

1. Prometheus collects the backend's metrics from stage and from prod every 30 seconds and keeps each environment apart.
2. Prometheus collects metrics for every container on the droplet, the frontends, databases and Caddy included, and for the droplet itself.
3. When a backend doesn't answer, for example while it is being deployed, Prometheus records it as down for that environment, and its availability drops.
4. Prometheus keeps metrics for 7 days. Older metrics are deleted, and so are the oldest ones if the stored metrics ever reach 1 GB.

### Access

1. A team member can open the dashboard at `https://grafana.mtogo.saldic.dev` after logging in with their own Viewer account.
2. A Viewer can look at the dashboard and change its time range, but can't save a change to the dashboard or the data source.
3. Only the developer who holds the admin account can create, change and remove accounts.
4. Nobody can see the dashboard without logging in, and nobody can create an account on their own.
5. Prometheus, cAdvisor and Node Exporter can't be reached from outside the droplet, and neither can the backend's metrics. `https://mtogo.saldic.dev/actuator/prometheus` and `https://stage.mtogo.saldic.dev/actuator/prometheus` return no metrics.
6. Adding Grafana to Caddy doesn't change how stage and prod answer. Both still answer on `/v1/features`.

### Dashboard

1. The dashboard shows stage and prod side by side, with both selected when it opens.
2. For each environment it shows:
   - the p95 latency of API requests, against a target line at 500 ms
   - the 5xx rate of API requests, against a target line at 1%
   - the availability over the dashboard's time range, against 99.9%
   - the request rate
3. For each environment it shows orders placed per hour, the rejection share, the number of unassigned orders, and the average and p95 delivery time.
4. Once, for the droplet, it shows CPU and memory for each container by name, memory against the limit of each container that has one, and CPU, memory, available memory and swap for the host.
5. When an environment has had no requests or orders in a window, the panels that need them show "No data" instead of a wrong number.
6. A change to the dashboard reaches Grafana only from the JSON in the repository. A change made in Grafana is never saved.

### Memory

1. The monitoring containers stay within their limits: Prometheus 256 MB, Grafana 192 MB, cAdvisor 128 MB and Node Exporter 32 MB.
2. Running the monitoring stack leaves at least 256 MiB available on the droplet, and no container is OOM-killed.

## Implementation decisions

- **ADR:** [ADR-008 Monitoring with Prometheus and Grafana](../adr/008-monitoring-with-prometheus-and-grafana.md), Proposed in this issue and Accepted before delivery. [ADR-007](../adr/007-exposing-metrics.md) applies unchanged: the backends expose metrics on `<env>-backend:8081` at `/actuator/prometheus` without a login. [ADR-003](../adr/003-deployment-pipeline.md) applies unchanged.
- **Stack:** its own compose project in `deploy/monitoring/`, in `/opt/mtogo/monitoring` on the droplet, set up and updated by hand like the Caddy proxy. `deploy.sh` and the workflows don't change. Images are pinned to exact current stable versions of `prom/prometheus`, `grafana/grafana`, `ghcr.io/google/cadvisor` and `prom/node-exporter`, chosen during implementation. Grafana stays on the newest line that runs within its limit. cAdvisor comes from GHCR, because `gcr.io/cadvisor/cadvisor` has no releases after v0.55.1.
- **Networks and ports:** Prometheus and Grafana join the external `web` network, Grafana with the alias `monitoring-grafana`. cAdvisor and Node Exporter are only on the project's own network, which Prometheus also joins. No monitoring container has `ports:`.
- **Memory limits:** `mem_limit` Prometheus 256m, Grafana 192m, cAdvisor 128m and Node Exporter 32m. The backends keep 768m.
- **Prometheus:**
  - Global `scrape_interval: 30s` and `scrape_timeout: 10s`.
  - `--storage.tsdb.retention.time=7d` and `--storage.tsdb.retention.size=1GB`, with data in the named volume `prometheus-data`.
  - Job `backend`: `metrics_path: /actuator/prometheus`, targets `stage-backend:8081` with `env="stage"` and `prod-backend:8081` with `env="prod"`.
  - Jobs `cadvisor` and `node`, without `env`.
- **cAdvisor:**
  - Runs with `--docker_only`, `--housekeeping_interval=30s` and the metric groups the dashboard doesn't use turned off.
  - Doesn't run `privileged`.
  - Mounts `/var/run/docker.sock`, `/sys` and `/` read-only, and `/run/containerd/containerd.sock`, because Docker on the droplet uses the containerd image store (driver `overlayfs`) and cAdvisor then names containers only through containerd. `:ro` on the sockets is no protection (ADR-008).
  - Doesn't mount `/var/lib/docker` if it still gives container names and memory without it. The implementation note records the result. If it needs the mount, it gets it read-only, and the note says why.
- **Node Exporter:** reads the host's `/proc`, `/sys` and `/` from read-only mounts, without host networking.
- **Grafana:**
  - `GF_SERVER_ROOT_URL=https://grafana.mtogo.saldic.dev`.
  - Anonymous access off, sign-up off, brute-force login protection left on.
  - `GOMEMLIMIT` below the container limit and no preinstalled plugins, so it stays within 192 MB.
  - The admin password comes from `grafana.env` on the droplet. `grafana.env.example` is committed with empty values, like `backend.env.example`.
  - The data source (Prometheus, by its name on the project network) and the MTOGO dashboard are provisioned from files in `deploy/monitoring/` and can't be edited in the UI.
  - Viewer accounts are created by hand by the admin and live in the named volume `grafana-data`. Nothing about users is in the repository.
- **Dashboard:**
  - Variable `env` with `stage` and `prod`, both selected by default. Each API and order panel repeats for each `env`, with stage and prod side by side, and the container and host panels come once below them.
  - The panels:

    | Panel                         | Measured from                                                                                        | Window          | Target |
    | ----------------------------- | ---------------------------------------------------------------------------------------------------- | --------------- | ------ |
    | p95 latency                   | `histogram_quantile(0.95, …)` over `http_server_requests_seconds_bucket` with `uri` under `/v1/`     | 5 min           | 500 ms |
    | 5xx rate                      | requests under `/v1/` with a 5xx status ÷ all requests under `/v1/`                                  | 5 min           | 1%     |
    | Availability                  | `avg_over_time(up{job="backend"})`                                                                   | dashboard range | 99.9%  |
    | Request rate                  | requests under `/v1/` per second                                                                     | 5 min           | –      |
    | Orders placed per hour        | `increase(mtogo_orders_placed_total[1h])`                                                            | 1 h             | –      |
    | Rejection share               | `mtogo_orders_rejected_total` ÷ `mtogo_orders_placed_total`, increase over the window                | 1 h             | –      |
    | Unassigned orders             | `mtogo_orders_unassigned`                                                                            | current value   | –      |
    | Average and p95 delivery time | `_sum` ÷ `_count`, and `histogram_quantile(0.95, …)` over `mtogo_order_delivery_time_seconds_bucket` | 1 h             | –      |
    | Container CPU and memory      | cAdvisor, memory against the limit of each container that has one                                    | 5 min           | –      |
    | Host CPU and memory           | Node Exporter, with available memory and swap                                                        | 5 min           | –      |

- **Caddy and DNS:**
  - A Cloudflare `A` record for `grafana.mtogo.saldic.dev`, DNS only like the existing records.
  - The Caddyfile gets a `grafana.mtogo.saldic.dev` site that reverse-proxies to `monitoring-grafana:3000`. The stage and prod sites don't change, and neither does Caddy's `compose.yml`.
  - Caddy is reloaded after `caddy validate`.
- **API contract:** no change to `/v1`. The frontend is not affected.
- **Documentation:**
  - A Monitoring section in the deployment guide, which the README links to, on setting up and updating the stack on the droplet: copy `deploy/monitoring/`, fill in `grafana.env`, `promtool check config`, `docker compose up -d`, validate and reload Caddy, check stage and prod on `/v1/features`, create Viewer accounts, bump image versions.
  - Glossary: **Availability** and **Rejection share** are added. The glossary is shared, and Kanban4sem/MTOGO-Frontend#70 copies it after this issue.

## Testing decisions

The issue changes only configuration on the droplet and no Java code, so there are no automated tests and no CI change. The checks are run by hand and recorded in the implementation note.

- **Before an update:**
  - `promtool check config` on `prometheus.yml` in the Prometheus container passes.
  - `caddy validate` on the new Caddyfile in the Caddy container passes before Caddy is reloaded.
- **Targets:** Prometheus's targets show `up` = 1 for `backend` with `env="stage"`, `backend` with `env="prod"`, `cadvisor` and `node`.
- **Caddy still serves stage and prod:** after the reload, `GET https://stage.mtogo.saldic.dev/v1/features` and `GET https://mtogo.saldic.dev/v1/features` answer 200.
- **Access**, as a decision table of who × action:
  - Not logged in, open the dashboard URL → redirected to the login
  - Not logged in, sign up → not offered
  - Viewer, open the MTOGO dashboard → shown
  - Viewer, save a change to the dashboard → not possible
  - Viewer, open the administration of users or data sources → refused
  - Anyone, `https://mtogo.saldic.dev/actuator/prometheus` and `https://stage.mtogo.saldic.dev/actuator/prometheus` → no metrics
- **No published ports:** `docker ps` shows no port mapping on a monitoring container, and Caddy is still the only container with published ports.
- **Dashboard:** every panel for stage and prod shows data, or "No data" only where the environment had no requests or orders in the window. Placing and rejecting an order on stage shows up in orders placed per hour and the rejection share within a minute. Containers appear by name.
- **cAdvisor without `/var/lib/docker`:** container names and memory appear without the mount. The result is recorded in the implementation note.
- **Memory:**
  - Before monitoring (measured by the developer): about 570 MB used by all containers, 1.0 GiB available.
  - After setup and again 24 hours later: `docker stats --no-stream` and `free -m` are recorded here, with no OOM kill (`docker inspect` shows `OOMKilled` false and no restarts), at least 256 MiB available, and stage and prod healthy.

## Out of scope

- Alerting: #80.
- Availability seen from outside through Caddy, with a Blackbox Exporter: #100.
- Host network traffic, which would need Node Exporter with host networking.
- A socket proxy in front of the Docker socket, the alternative in ADR-008.
- Deploying the monitoring stack from a workflow.
- Copying the glossary to the frontend: Kanban4sem/MTOGO-Frontend#70.

## Further notes

No feature flag: the backend doesn't change, users see no change, and the stack can be stopped with `docker compose down` without affecting stage or prod. The targets 500 ms, 1% and 99.9% are explained in ADR-008, not in the glossary.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-26 - #78

- **Built:**
  - The monitoring stack in `deploy/monitoring/`: Prometheus v3.16.0, Grafana 12.4.16, cAdvisor 0.61.1 and Node Exporter v1.13.1, within 256, 192, 128 and 32 MB, with no published ports.
  - Prometheus collects `backend` (stage and prod, by `env`), `cadvisor` and `node` every 30 s and keeps 7 days, at most 1 GB.
  - Grafana provisions the read-only Prometheus data source and the MTOGO dashboard. Anonymous access, sign-up and creating organizations are off.
  - The dashboard has an API row and an Orders row with stage and prod side by side, and a Droplet row with CPU and memory for each container by name, memory against each limit, and host CPU, memory, available memory and swap.
  - The latency, 5xx and request-rate panels count only requests with a `uri` under `/v1/`, so the API documentation on stage, scanners' requests to unknown paths and the management port don't affect them.
  - The 5xx panels have an axis from 0 that shows at least 0–2% and at most 100%, with the 1% target as a dashed line. Without a range, a flat 0% series gave an axis up to 10000% shaded red on the droplet.
  - The memory-against-limit panel shows only containers that have a memory limit. Containers without one, such as Caddy, the databases and the frontends, appear only in the memory panel.
  - Settings beyond the spec: Grafana sets `GF_SECURITY_COOKIE_SECURE`, assigns new users the Viewer role explicitly (`GF_USERS_AUTO_ASSIGN_ORG_ROLE`), and turns off analytics reporting and update checks. cAdvisor runs with `--store_container_labels=false`, so container labels and environment variables never become metric labels.
  - The dashboard JSON is mounted read-only at `/etc/grafana/dashboards`, outside the `grafana-data` volume.
  - Caddy has a `grafana.mtogo.saldic.dev` site.
  - ADR-008 is Accepted, as agreed by the team on 2026-11-26.
  - The install and update guide is in the deployment guide. It is written for the droplet's admin user, who has `sudo` but can't run `docker` or write to `/opt/mtogo`: files go to the home directory and are copied in with `sudo cp`, the monitoring stack belongs to and runs as `deploy`, with `grafana.env` mode `600`, and the Caddy proxy runs with `sudo docker compose`. The Caddyfile is copied onto the existing file, so its bind mount keeps the inode.
- **Deviations:** agreed with the developer during implementation, and the spec above already says them:
  - **Grafana 12.4.16 instead of the current stable 13.3.2.** Under 192 MB, Grafana 13.3.2 was OOM-killed even with `GOMEMLIMIT`, and without a limit it settled at about 320–370 MiB. 12.4.16, patched on the same day as 13.3.2, settled at 140 MiB under the limit, with `GOMEMLIMIT=160MiB`. That figure is from the comparison of versions, once Grafana had settled. The 105 MiB in the checks below is from right after the final stack started.
  - **cAdvisor from `ghcr.io/google/cadvisor`**, because `gcr.io` stops at v0.55.1.
  - **cAdvisor mounts `/run/containerd/containerd.sock`.** The droplet's Docker uses `overlayfs`, the containerd image store, and cAdvisor ≥ 0.54.1 doesn't register its Docker factory there without containerd (google/cadvisor#3772). ADR-008's paragraph on the socket covers both sockets.
  - **Panels repeat side by side** rather than as one row per environment, so stage and prod sit next to each other, as the acceptance criterion asks.
  - **The guide is in the deployment guide**, not the README, at the developer's request. The README links to it.
  - **Node Exporter** turns off `netdev` and `netstat`, which without host networking would describe its own container. Its `/` mount has no `rslave`, which Docker Desktop refuses and the droplet doesn't need.
- **Tests:** manual checks, as the testing decisions say.
  - **Locally, on Docker 29.9.3 with `overlayfs`, with the stack and a stub `stage-backend` that serves the backend's metric names:**
    - `promtool check config` passed, and `caddy validate` gave `Valid configuration`.
    - The targets `cadvisor`, `node` and stub stage showed `up` = 1, and prod showed 0 with no backend, as expected.
    - Every dashboard query ran without an error. With traffic it gave values, and the delivery time without deliveries gave "No data".
    - cAdvisor named every container without `/var/lib/docker`, once it had the containerd socket. Without it, it named none.
    - Access: without a login, the dashboard redirected to the login (302) and the API answered 401. Sign-up answered "User signup is disabled".
    - A Viewer saw the provisioned dashboard with `canSave` false. Saving answered 403, the organization's users 403 and editing the data source 403.
    - Even the admin couldn't save the dashboard ("Cannot save provisioned dashboard") or edit the data source ("Cannot update read-only data source").
    - `docker ps` showed only exposed ports, none published.
    - Memory right after the stack started: Prometheus 64 MiB, Grafana 105 MiB, cAdvisor 22 MiB and Node Exporter 14 MiB, with no OOM kill.
  - **On the droplet, installed by the developer on 2026-11-26 following the deployment guide:**
    - Before: `free -m` showed 1015 MiB available and 139 MiB swap used, and the containers used about 550 MiB.
    - The driver was `overlayfs`, and `/run/containerd/containerd.sock` existed.
    - `promtool check config` gave `SUCCESS`. All four containers ran, with no published ports.
    - Targets: `cadvisor`, `node` and the stage backend showed `up` = 1. The prod backend showed 0, because prod still runs backend 0.1.0 from before #77 and has no port 8081. The prod target becomes 1 with the next backend release, and the `up` = 1 criterion for prod is checked again then.
    - cAdvisor listed all 11 containers by name, without `/var/lib/docker`.
    - The Caddyfile was copied with `sudo cp` and kept its inode. `caddy validate` gave `Valid configuration`, and the reload succeeded. Stage and prod answered `200` on `/v1/features`, and `/actuator/prometheus` from outside returned no metric lines on either.
    - Grafana showed the login page without sign-up. The admin login worked, and the MTOGO dashboard showed stage and prod side by side. The installed dashboard had the `uri` filter in its three HTTP queries, and after the 5xx axis fix the 5xx panels showed 0–2% with the dashed 1% line.
    - Two Viewer accounts were created, one for a team member and one for testing, separate from the admin login. As a Viewer, the dashboard opened, **Save** and **Administration** weren't offered, and `/admin/users` was refused. The other team members get their accounts when they ask.
    - After setup: `free -m` showed 831 MiB available and 204 MiB swap used. Prometheus used 155.8 of 256 MiB, Grafana 132.4 of 192 MiB, cAdvisor 40.5 of 128 MiB and Node Exporter 24.9 of 32 MiB. No container was OOM-killed or restarted.
    - The measurement 24 hours after setup is still open. The developer posts it as a comment on #78.
- **Follow-up:** None.

## Changelog

| Version | Date       | Change        | Issue |
| ------- | ---------- | ------------- | ----- |
| 1.0     | 2026-11-26 | First version | #78   |
