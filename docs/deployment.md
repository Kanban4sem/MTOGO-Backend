# Deployment

Stage (`https://stage.mtogo.saldic.dev`) and prod (`https://mtogo.saldic.dev`) run on one droplet. The decisions behind the pipeline are in [ADR-003](adr/003-deployment-pipeline.md), and the server side is in `deploy/`.

## Stage

Every commit that reaches `main` is deployed to stage by the **Deploy to stage** workflow. It builds the image, pushes it to GHCR as `ghcr.io/kanban4sem/mtogo-backend:<commit SHA>` and deploys it. Nothing has to be done by hand. Every commit gets its image, but only one stage deployment runs at a time. When several commits land while one is deploying, GitHub skips the ones in between and deploys the newest.

## Prod

Prod is deployed only when someone releases a version:

```bash
gh tt semver bump --patch   # or --minor, --major
git push origin --tags
```

The **Release to prod** workflow adds the version as a tag on the image `main` already built for that commit and deploys it. It doesn't rebuild. A tag on a commit that `main` hasn't built yet fails, so wait until **Deploy to stage** has finished for that commit.

## Health check

`deploy.sh` waits for the container's own health check and rolls back to the previous version on its own when it fails. When the new image can't be pulled, the previous version keeps running. After each deployment the workflow also calls `GET /v1/features` on the public host, and the run fails if it doesn't answer.

## Roll back

- **Stage:** open an earlier **Deploy to stage** run in the Actions tab and choose **Re-run all jobs**. The run deploys the image already built for that commit instead of building it again. GitHub keeps runs for 30 days.
- **Prod:** start **Release to prod** by hand (**Run workflow**) from `main` with the version to go back to, such as `1.2.3`. A manual start from any other branch fails before it deploys.

A rollback only switches the image. It doesn't undo database migrations.

## First deployment of a new package

GHCR creates a package as private on its first push, and the droplet can't pull a private image. Make the package public once under the organization's **Packages** → **Package settings** → **Change visibility**, then re-run the failed run.

## Monitoring

Prometheus and Grafana watch stage and prod from the same droplet ([ADR-008](adr/008-monitoring-with-prometheus-and-grafana.md)). Grafana is at `https://grafana.mtogo.saldic.dev`, behind a login. The stack is its own compose project in `deploy/monitoring/`, installed and updated by hand in `/opt/mtogo/monitoring`. No workflow and no `deploy.sh` touches it, and stopping it with `docker compose down` doesn't affect stage or prod.

The commands below run on the droplet, as your admin user with `sudo`, unless they start on your own machine. `<you>@<droplet>` is your SSH login. Your admin user can't run `docker` or write to `/opt/mtogo`:

- The monitoring stack runs as `deploy`, like stage and prod, so `docker` commands for it and on the containers run with `sudo -u deploy`, and `/opt/mtogo/monitoring` is owned by `deploy`.
- `/opt/mtogo/proxy` is owned by `root`, so the Caddy proxy runs with `sudo docker compose`.
- `scp` can't write to `/opt/mtogo`, so files go to your home directory first and are copied into place with `sudo cp`.

### Install

1. **DNS.** In Cloudflare, add an `A` record for `grafana.mtogo.saldic.dev` to the droplet's IP, **DNS only**, like the existing records. Caddy can only get the certificate when Cloudflare doesn't proxy the record.

2. **Check Docker's image store.** cAdvisor mounts containerd's socket, because with the containerd image store it names containers only through containerd.

   ```bash
   sudo -u deploy docker info --format '{{.Driver}}'   # overlayfs
   ls -l /run/containerd/containerd.sock                 # must exist
   ```

   When the socket doesn't exist, stop: Docker would create an empty directory in its place, and the Grafana dashboard would show containers without names.

3. **Measure before.** Record the output in the spec's implementation note (#78).

   ```bash
   sudo -u deploy docker stats --no-stream
   free -m
   ```

4. **Copy the files.** On your own machine, from the repository root, copy them to your home directory on the droplet:

   ```bash
   scp -r deploy/monitoring <you>@<droplet>:~/
   ```

   On the droplet, copy them into place, give them to `deploy` and remove your copy:

   ```bash
   sudo mkdir -p /opt/mtogo/monitoring
   sudo cp -r ~/monitoring/. /opt/mtogo/monitoring/
   sudo chown -R deploy: /opt/mtogo/monitoring
   rm -r ~/monitoring
   ```

   `/opt/mtogo/monitoring` now holds `compose.yml`, `prometheus.yml`, `grafana.env.example` and `grafana/`, all owned by `deploy`.

5. **Fill in `grafana.env`.** Pick an admin login other than `admin`, and keep the password in the team's password manager.

   ```bash
   cd /opt/mtogo/monitoring
   sudo install -o deploy -g deploy -m 600 grafana.env.example grafana.env
   openssl rand -base64 32               # the admin password
   sudo -u deploy nano grafana.env       # GF_SECURITY_ADMIN_USER and GF_SECURITY_ADMIN_PASSWORD
   ls -l grafana.env                     # -rw------- deploy deploy
   ```

   `install` creates the file owned by `deploy` with mode `600`, so only `deploy` can read the password, and it is never readable by others, even for a moment.

   Grafana reads them only on its first start. Change the password later under **Profile** in Grafana, not in the file.

6. **Check the Prometheus configuration.** It must print `SUCCESS`.

   ```bash
   sudo -u deploy docker compose run --rm --no-deps --entrypoint promtool prometheus \
     check config /etc/prometheus/prometheus.yml
   ```

7. **Start the stack.** All four services must be `running`, and none may show a published port such as `0.0.0.0:…->`.

   ```bash
   sudo -u deploy docker compose up -d
   sudo -u deploy docker compose ps
   ```

8. **Check the targets.** After a minute, each of the four targets must have the value `1`: `backend` with `env="stage"`, `backend` with `env="prod"`, `cadvisor` and `node`.

   ```bash
   sudo -u deploy docker compose exec prometheus promtool query instant http://localhost:9090 up
   sudo -u deploy docker compose exec prometheus promtool query instant http://localhost:9090 \
     'count by (name) (container_memory_working_set_bytes{name!=""})'
   ```

   The second query must list the containers by name, such as `stage-backend-1` and `prod-db-1`.

9. **Add the Grafana site to Caddy.** On your own machine, copy the Caddyfile to your home directory on the droplet:

   ```bash
   scp deploy/proxy/Caddyfile <you>@<droplet>:~/Caddyfile
   ```

   On the droplet, copy it onto the existing file in the directory the Caddy proxy runs from. Caddy's container bind-mounts that one file, and the mount follows the file's inode. `cp` onto an existing file rewrites it in place and keeps the inode. Moving or replacing the file gives it a new inode, which the container doesn't see.

   ```bash
   ls -i /opt/mtogo/proxy/Caddyfile      # note the inode
   sudo cp ~/Caddyfile /opt/mtogo/proxy/Caddyfile
   ls -i /opt/mtogo/proxy/Caddyfile      # must show the same inode
   rm ~/Caddyfile
   ```

   Check that Caddy's container sees the new file and that the file is valid. `validate` must end with `Valid configuration`.

   ```bash
   cd /opt/mtogo/proxy
   sudo docker compose exec caddy grep grafana /etc/caddy/Caddyfile
   sudo docker compose exec caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
   ```

   When `grep` finds nothing, the container still has the old file: run `sudo docker compose up -d --force-recreate caddy`, which interrupts stage and prod for a few seconds, and validate again. When `validate` fails, fix the file before you go on. The running Caddy keeps the old configuration until the reload.

10. **Reload Caddy.** The reload doesn't drop connections.

    ```bash
    sudo docker compose exec -w /etc/caddy caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
    ```

11. **Check that stage and prod still answer.** Run these from your own machine. The first two must print `200`, and the last two must print `0`, because the metrics aren't public (ADR-007).

    ```bash
    curl -sS -o /dev/null -w '%{http_code}\n' https://stage.mtogo.saldic.dev/v1/features
    curl -sS -o /dev/null -w '%{http_code}\n' https://mtogo.saldic.dev/v1/features
    curl -sS https://stage.mtogo.saldic.dev/actuator/prometheus | grep -c '^# TYPE'
    curl -sS https://mtogo.saldic.dev/actuator/prometheus | grep -c '^# TYPE'
    ```

12. **Log in as admin.** Open `https://grafana.mtogo.saldic.dev`. Without a login it redirects to the login page, and the page offers no sign-up. Log in with the admin login from `grafana.env` and open **Dashboards** → **MTOGO**. Stage and prod are side by side.

13. **Create a Viewer account for each team member.** In Grafana, go to **Administration** → **Users and access** → **Users** → **New user**. Enter their name, email and a temporary password, then **Create user**. New users get the role **Viewer** in the main organization, set by `GF_USERS_AUTO_ASSIGN_ORG_ROLE` in `compose.yml`. Check it on the user's page under **Organizations**, and change it to **Viewer** if it shows anything else. Send each person their password through the password manager, and ask them to change it under **Profile** → **Change password**.

    Log in once as a Viewer and check:
    - the MTOGO Grafana dashboard opens, and the time range can be changed
    - **Save dashboard** isn't offered
    - **Administration** isn't shown, and `https://grafana.mtogo.saldic.dev/admin/users` is refused

    The accounts live only in the `grafana-data` volume. `sudo -u deploy docker compose down -v` deletes them, so never use `-v` here.

14. **Measure after setup and again 24 hours later.** Record both in the spec's implementation note (#78). `free -m` must show at least 256 MiB `available`, and no container may have `oom=true` or restarts.

    ```bash
    sudo -u deploy docker stats --no-stream
    free -m
    sudo -u deploy docker inspect --format '{{.Name}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}' \
      $(sudo -u deploy docker ps -q)
    ```

### Update

1. Change the files in `deploy/monitoring/` or `deploy/proxy/Caddyfile` through an issue, like any other code.
2. After the delivery, copy them to the droplet as in step 4 or 9 of **Install**, including `chown` and the inode check. The copy doesn't touch `grafana.env`, which isn't in the repository. A file deleted from the repository stays on the droplet until you remove it with `sudo rm`.
3. Apply the change in `/opt/mtogo/monitoring`:
   - **`prometheus.yml`:** check it as in step 6, then `sudo -u deploy docker compose restart prometheus`.
   - **The Grafana dashboard JSON:** Grafana loads it within a minute, without a restart. A change made in Grafana itself can't be saved.
   - **The data source or the provisioning files:** `sudo -u deploy docker compose restart grafana`.
   - **`compose.yml`, such as a new image version:** `sudo -u deploy docker compose pull && sudo -u deploy docker compose up -d`, which recreates only the changed services.
   - **The Caddyfile:** steps 9 to 11 of **Install**.
4. Check the targets (step 8) and the Grafana dashboard.

Images are pinned to exact versions. To update one, change the tag in `compose.yml` to a newer release of the same image and check its release notes. Two exceptions:

- **Grafana** stays on the 12.4 line: Grafana 13 is OOM-killed within its 192 MB limit. Before moving to a new major, start it with the limit and check that it becomes healthy.
- **cAdvisor** comes from `ghcr.io/google/cadvisor`, because `gcr.io/cadvisor/cadvisor` has no releases after v0.55.1.
