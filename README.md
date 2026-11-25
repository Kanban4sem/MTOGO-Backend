# MTOGO-Backend

The REST API for MTOGO, a food delivery platform. Built with Spring Boot 4 and Java 25.

We work with Kanban and trunk-based development using [TakT](https://github.com/devx-cafe/gh-tt) and [InSitu](https://github.com/devx-cafe/gh-insitu). Every change starts as an issue on our [board](https://github.com/orgs/Kanban4sem/projects/1) and ends as exactly one commit on `main`.

## Getting started

### 1. Install on your own machine (once)

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows: with WSL2)
- [VS Code](https://code.visualstudio.com/) with the **Dev Containers** extension
- [GitHub CLI](https://cli.github.com/) (`brew install gh` on Mac, `winget install GitHub.cli` on Windows)

### 2. Log in to GitHub with project access (once)

```bash
gh auth login -h github.com -p https -s project -w
```

Then let the dev container inherit your login. On Mac add this line to `~/.zprofile` (on Linux/WSL: `~/.profile`). Use the path that `which gh` prints:

```bash
export GH_TOKEN=$(/opt/homebrew/bin/gh auth token)
```

On Windows, run this once in PowerShell instead:

```powershell
[Environment]::SetEnvironmentVariable("GH_TOKEN", (gh auth token), "User")
```

Restart your terminal and VS Code afterwards.

### 3. Open the repository in the dev container

1. Clone the repository and open the folder in VS Code.
2. Press `Cmd+Shift+P` (Windows: `Ctrl+Shift+P`) and choose **Dev Containers: Reopen in Container**.
3. The first build takes a few minutes. It installs Java 25, Node, the GitHub CLI, TakT, InSitu, our linters, and Claude Code and Codex as extensions and on the command line. It also checks `.claude/skills` out as a symlink so Claude Code finds the repository's skills, also in a clone made on Windows. If your dev container already exists, rebuild it.
4. Log in to the AI agents once: run `claude` and `codex` in the terminal and follow the prompts. Your login lives in the Docker volumes `mtogo-claude` and `mtogo-codex`, so it survives rebuilds and also works in the MTOGO-Frontend dev container. The first rebuild that adds these volumes starts logged out.

   Both agents are installed by default. If you don't use one, set `MTOGO_SKIP_CLAUDE=1` or `MTOGO_SKIP_CODEX=1` as an environment variable on your own machine, restart VS Code and rebuild the dev container. The container then skips that command-line install. The VS Code extensions stay installed. Disable them for yourself in VS Code.

All commands below are run in the terminal **inside** the dev container.

### 4. Check that everything works

```bash
gh auth status                # must show the 'project' scope
gh insitu run trunk-worthy    # all checks must be green
```

If git asks who you are, set your name and email once:

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## Daily workflow

| Step    | Command                                                  | What happens                                                                        |
| ------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Start   | `gh tt workon -i <issue>` or `gh tt workon -t "<title>"` | Creates or reuses the issue branch and moves the issue to **In Progress**           |
| Save    | `gh tt wrapup -m "<message>"`                            | Runs the quality checks, commits and pushes. Use it as often as you like            |
| Sync    | `git pull --rebase origin main`                          | Needed before delivering if `main` has moved                                        |
| Deliver | `gh tt deliver`                                          | Squashes your work into one commit. CI verifies it and fast-forwards it onto `main` |

Rules:

- Never push directly to `main`.
- Fix formatting automatically with `gh insitu run fix-all`.
- Unknown but correct words for the spell checker go in `.dict/repo.dictionary`.

## Configuration

Configuration lives in `src/main/resources/application.properties` and contains no secrets. Copy `.env.example` to `.env` and fill in real values. The `.env` file is never committed.

### Local database browser (pgAdmin)

The backend uses `http://localhost:8080`. pgAdmin uses `http://localhost:5050`, so both can run at the same time. It starts automatically when the dev container starts, once `PGADMIN_PASSWORD` is set in `.env`. Rebuild an existing dev container once to activate this startup command.

To start it immediately, run:

```bash
bash .devcontainer/start-pgadmin.sh
```

Open [pgAdmin](http://localhost:5050) on your own machine and log in with `PGADMIN_EMAIL` and `PGADMIN_PASSWORD` from `.env` (default email: `admin@example.com`). Expand **Development → MTOGO local** and enter `DB_PASSWORD` from `.env` when connecting. Tables are under **Databases → mtogo → Schemas → public → Tables**; right-click a table and choose **View/Edit Data → All Rows**.

This uses the existing PostgreSQL database published on your machine's port `5432`; it does not create a database. pgAdmin reaches it through `host.docker.internal`, including when the backend runs inside the dev container. The server definition follows the [pgAdmin container configuration](https://www.pgadmin.org/docs/pgadmin4/latest/container_deployment.html). If your database uses another port or name, edit the registered server's connection in pgAdmin.

pgAdmin is published only on `127.0.0.1`. Its settings persist in a Docker volume. Set `PGADMIN_PORT` in `.env` to choose another port, then rerun the startup command. Login variables initialize the account only on first launch; change an existing password in pgAdmin itself.

To stop pgAdmin:

```bash
docker compose --env-file .env -f .devcontainer/compose.yml stop
```

## Customer accounts

The account model is split between `mtogo.feature.user`, `mtogo.feature.address` and `mtogo.feature.role`. Address owns its mapping and validation; password handling and token validation are centralized in security. User-specific email normalization lives in `user/domain/`.

### Start from VS Code

With PostgreSQL running and the `mtogo` database created, open `Main.java` and choose **Run**. When running inside the development container, set `DB_URL=jdbc:postgresql://host.docker.internal:5432/mtogo` in your ignored `.env`; when running Java directly on your host, use localhost instead. Set DB_USER and DB_PASSWORD for that PostgreSQL instance.

Flyway applies migrations at startup and Hibernate validates their tables. In pgAdmin, refresh **mtogo → Schemas → public → Tables** to see users, user_profiles, addresses, postal_codes, roles and flyway_schema_history. Restarting does not recreate tables or reset data. Table creation does not depend on the authentication flag.

Account endpoints are behind `FEATURE_AUTH_ENABLED`, off by default. To try them locally, enable the flag in your own `.env` and set `JWT_SECRET` to a Base64 key generated with `openssl rand -base64 32`. Restart the backend. Never commit the key.

| Method | Path               | Result                                               |
| ------ | ------------------ | ---------------------------------------------------- |
| POST   | `/v1/users`        | Register a CUSTOMER; returns 201 and its profile     |
| POST   | `/v1/users/login`  | Exchange email/password for an access token          |
| GET    | `/v1/users/me`     | Read the authenticated user's profile                |
| POST   | `/v1/users/logout` | Revoke all of the user's current tokens; returns 204 |

Registration uses the frontend field names: firstName, lastName, email, password, confirmPassword, phoneNumber, preferredLanguage, postalCode, city and country. The last three must all be supplied or all omitted. Preferred language defaults to da. The current form does not collect a street address.

Send the token as `Authorization: Bearer <accessToken>`. It expires after 15 minutes by default (`JWT_LIFETIME_SECONDS`). The backend does not use authentication cookies or sessions. Logout invalidates tokens across all devices; subsequent login creates a new token. Responses containing tokens and profiles must not be cached.

The API contract is documented in Swagger and the [account spec](docs/specs/13-add-customer-accounts.md); the authentication decision is [ADR-005](docs/adr/005-account-authentication.md). Frontend integration remains in frontend issue #6.

Customers can read their name and phone number with `GET /v1/users/me` and update them with `PUT /v1/users/me`. Send `firstName`, `lastName` and `phoneNumber`. Omit the phone number or send `null` to clear it.

Use `GET /v1/users/me/delivery-addresses` to list saved delivery addresses, including a complete checkout default. Use `POST` on the collection to add an address, or `GET`, `PUT` and `DELETE` on `/v1/users/me/delivery-addresses/{id}` to manage one address. Address writes take `street`, `postalCode` and `city`. The country is Denmark and the postal code has four digits.

Placing orders is behind `FEATURE_ORDERING_ENABLED`, off by default, and also needs `FEATURE_AUTH_ENABLED`. A logged-in customer places and pays for an order with `POST /v1/orders`. Payment is mocked: the payment token `decline` is declined and any other token is confirmed. The contract is in Swagger and the [order spec](docs/specs/15-place-and-pay-for-an-order.md).

At checkout, send `deliveryAddress` with `street`, `postalCode` and `city`. Add `saveAsDefault: true` to save it on the customer's profile after payment succeeds. To use that saved address, send `useDefaultAddress: true` and omit `deliveryAddress`. Read the current address from `GET /v1/users/me` at `profile.address`. A profile address from registration or seeding has a postal area and needs a street before it can be used as a delivery default.

To use a specific saved address, send its `deliveryAddressId` as the only address selection in the order request. A missing address or one belonging to another customer returns `404`. Each order retains the address used when it was placed.

The `201 Created` response includes a `Location` header with the URL of the new order, `/v1/customer/orders/{id}`.

With the same flags enabled, a customer can list their own orders at `GET /v1/customer/orders` and read order lines and the total at `GET /v1/customer/orders/{id}`. Both endpoints include the current status code, its Danish and English names in `statusName.danish` and `statusName.english`, and the delivery address recorded on the order. Try the flow with [customerOrders.http](src/main/resources/http/customerOrders.http) in JetBrains HTTP Client.

With the same flags enabled, the app can poll `GET /v1/customer/orders/notifications` for the signed-in customer's notifications, newest first. Each entry includes `id`, `orderId`, `status`, `statusName`, `changedAt` and `read`. Count entries with `read: false` for the unread badge and use `statusName` for Danish or English display. Send `PUT /v1/customer/orders/notifications/{id}/read` with the notification id to mark it as read. The response is `204`, including on repeats. An unknown notification or one belonging to another customer returns `404`.

Notifications cover every status change after placement, including rejection. Existing status history is available as unread notifications after the migration. When a customer has a phone number, the backend also simulates an SMS after the status change commits. The SMS provider is a mock and logs at debug level.

With the same flags enabled, a restaurant can list its orders with `GET /v1/restaurant/orders`. To update one, send `PATCH /v1/restaurant/orders/{id}` with a JSON `status` of `ACCEPTED` or `REJECTED` for a placed order, or `READY` for an accepted order. The response includes the updated order. A transition that the current status does not allow returns `409`. The restaurant is determined by the authenticated account.

With the same flags enabled, a delivery agent can list unassigned ready orders and their own orders, including completed ones, with `GET /v1/delivery-agent/orders`. The response includes `status`, `statusName` (Danish and English) and `assignedToMe` for grouping orders. Each listed order includes its full delivery address, its order lines and the order total. After an agent takes an order, it appears only in that agent's list.

Send `PATCH /v1/delivery-agent/orders/{id}` with a JSON `action` of `TAKE`, `PICK_UP` or `DELIVER`. `TAKE` assigns the agent and leaves the order `READY`. The assigned agent can then move it to `PICKED_UP` and `DELIVERED`. An unavailable assignment or an out-of-order transition returns `409`.

### Seeded users

On every startup the backend creates these users for each role whose seed password is set, as decided in [ADR-006](docs/adr/006-seeded-user-credentials.md). Their passwords are never in the repository.

| Email                    | Role           | Restaurant   | Name           | Seed password                  |
| ------------------------ | -------------- | ------------ | -------------- | ------------------------------ |
| `customer@mtogo.example` | CUSTOMER       | None         | Test Customer  | `SEED_CUSTOMER_PASSWORD`       |
| `kebab@mtogo.example`    | RESTAURANT     | MTOGO Kebab  | MTOGO Kebab    | `SEED_RESTAURANT_PASSWORD`     |
| `bakery@mtogo.example`   | RESTAURANT     | MTOGO Bakery | MTOGO Bakery   | `SEED_RESTAURANT_PASSWORD`     |
| `agent@mtogo.example`    | DELIVERY_AGENT | None         | Delivery Agent | `SEED_DELIVERY_AGENT_PASSWORD` |

- **Locally:** set the variables in your `.env` and restart. Each user logs in with their email and their role's password. An unset or blank variable skips that role. A password shorter than 8 characters or longer than 72 UTF-8 bytes stops the startup. Logging in needs `FEATURE_AUTH_ENABLED`; seeding does not.
- **On stage:** the passwords are in the droplet's `backend.env`. The team has no shared secret store yet, so ask a team member with access to the droplet.
- **On prod:** the variables stay unset, so prod has no seeded users. `deploy.sh` refuses to deploy to prod while one is set.
- **Existing users:** seeding never changes a user whose email already exists, including a customer who registered with a seeded email. It logs a warning and skips that user.
- **Rotating a password:** delete the user, change the variable and restart.

### Try an order conflict locally

Start the backend with the `local` Spring profile against your development database:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The profile seeds **Sold-out kebab** in the MTOGO Kebab menu and **Closed restaurant meal** at MTOGO Closed Restaurant. To try both `409` responses, enable authentication and ordering, seed a customer, and open [orderConflicts.http](src/main/resources/http/orderConflicts.http) in JetBrains HTTP Client.

In the repository root's `http-client.private.env.json`, create an `order-conflicts` environment with `seedCustomerPassword` set to the seeded customer's password and the numeric `unavailableItemId` and `closedRestaurantItemId` set from the SQL query in the HTTP file. Select **Run with → order-conflicts** and send the login request first. It saves the access token for both order requests.

For the frontend checkout check, put **Kebabmenu** from **MTOGO Kebab** in the basket. Open the Query Tool in pgAdmin against your local database and run:

```sql
UPDATE menu_items AS item
SET is_available = FALSE
FROM menus AS menu JOIN restaurants AS restaurant ON restaurant.id = menu.restaurant_id
WHERE item.menu_id = menu.id
  AND restaurant.name = 'MTOGO Kebab'
  AND menu.name = 'Menu'
  AND item.name = 'Kebabmenu'
RETURNING item.id, item.is_available;
```

Submit checkout with the item still in the basket to trigger the conflict. Run the same statement with `TRUE` in place of `FALSE` to restore availability. The update takes effect immediately.

## Build and test

```bash
./mvnw verify
```

`verify` also writes a JaCoCo coverage report to `target/site/jacoco/`. Open `index.html` in a browser to see which code the tests cover. SonarQube reads `jacoco.xml`.

The tests start PostgreSQL with Testcontainers, so Docker must be running.

### Test JVMs

By default the tests run in one JVM, which starts one PostgreSQL container. Each extra JVM starts its own Spring contexts and its own PostgreSQL container, so more JVMs finish faster but need more memory and cores. A higher value pays off on a machine with cores and memory to spare: try 2, then 4, and keep the value that makes `./mvnw verify` faster without slowing the machine down. Stay at 1 on a machine with little memory or a small Docker Desktop VM.

Set the number with the `MTOGO_TEST_FORKS` environment variable:

- **For every run, kept across rebuilds:** set it on your own machine, restart VS Code and rebuild the dev container.
  - Windows (PowerShell): `setx MTOGO_TEST_FORKS 4`
  - macOS (zsh): add `export MTOGO_TEST_FORKS=4` to `~/.zshrc`
- **Until the container restarts, without a rebuild:** run `export MTOGO_TEST_FORKS=4` in the dev container terminal.

For a single run, `./mvnw verify -Dsurefire.forkCount=4` overrides the variable. The ready flow on GitHub always uses 4.

## Code quality

[SonarQube Cloud](https://sonarcloud.io/project/overview?id=Kanban4sem_MTOGO-Backend) analyses the code with the JaCoCo coverage from the tests:

- The [ready flow](CONTRIBUTING.md#quality-gates) analyses each delivery after `trunk-worthy` and waits for the quality gate. It measures the delivery's new code against `main` with `sonar.newCode.referenceBranch=main`. A delivery whose new code fails the "Sonar way" gate doesn't reach `main`.
- Each commit on `main` is built, tested and analysed beside the stage deployment, so `main` stays the reference for new code. The analysis never holds back a deployment.

The SonarQube Cloud project is set up by hand:

- Automatic analysis is off, since it can't read the coverage and it blocks the CI analysis.
- The quality gate is the built-in "Sonar way".
- New code stays at the project default "Previous version". SonarQube Cloud has no reference branch option at project level, so the ready flow sets it for each delivery instead.
- The workflows read a token from the repository secret `SONAR_TOKEN`, created in SonarQube Cloud with permission to analyse the project.

The pre-commit hook and `trunk-worthy` don't run the analysis, so they work without a token. To run it yourself on your issue branch, create a personal token under My Account → Access tokens in SonarQube Cloud and run:

```bash
printf 'Sonar token: '; read -rs SONAR_TOKEN; echo
export SONAR_TOKEN="$(printf '%s' "$SONAR_TOKEN" | tr -d '[:space:]')"
./mvnw -B clean verify sonar:sonar -Dsonar.branch.name=$(git branch --show-current) -Dsonar.newCode.referenceBranch=main
unset SONAR_TOKEN
```

- `read -rs` keeps the token off the screen and out of your shell history.
- `tr` strips stray whitespace from a pasted token. A leading space makes the scanner fail with "invalid header value".
- Without `sonar.branch.name`, a local run is registered as `main`. The scanner detects the branch by itself only in GitHub Actions.
- `clean` keeps stale files in `target/` out of the analysis.
- The tests run in one JVM by default. With [`MTOGO_TEST_FORKS`](#test-jvms) above 1, each JVM starts its own PostgreSQL container, and Docker Desktop on your own machine needs about 6 GB of memory. With less, the build is killed with exit code 137.
- Use your personal token and never share it. CI uses its own token from the `SONAR_TOKEN` secret.

The analysis shows up in SonarQube Cloud under the branch you are on.

## Docker image

Build the jar first, then the image:

```bash
./mvnw package
docker build -t mtogo-backend .
```

Run it against your local PostgreSQL. Inside a container, `localhost` is the container itself, so use `host.docker.internal` to reach the database on your machine:

```bash
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/mtogo \
  -e DB_USER=mtogo -e DB_PASSWORD=<your password> \
  mtogo-backend
```

After about a minute `docker ps` shows the container as `healthy`. Docker checks `GET /actuator/health`, which is open without authentication.

## Deployment

Stage (`https://stage.mtogo.saldic.dev`) and prod (`https://mtogo.saldic.dev`) run on one droplet. The decisions behind the pipeline are in [ADR-003](docs/adr/003-deployment-pipeline.md), and the server side is in `deploy/`.

### Stage

Every commit that reaches `main` is deployed to stage by the **Deploy to stage** workflow. It builds the image, pushes it to GHCR as `ghcr.io/kanban4sem/mtogo-backend:<commit SHA>` and deploys it. Nothing has to be done by hand. Every commit gets its image, but only one stage deployment runs at a time. When several commits land while one is deploying, GitHub skips the ones in between and deploys the newest.

### Prod

Prod is deployed only when someone releases a version:

```bash
gh tt semver bump --patch   # or --minor, --major
git push origin --tags
```

The **Release to prod** workflow adds the version as a tag on the image `main` already built for that commit and deploys it. It doesn't rebuild. A tag on a commit that `main` hasn't built yet fails, so wait until **Deploy to stage** has finished for that commit.

### Health check

`deploy.sh` waits for the container's own health check and rolls back to the previous version on its own when it fails. When the new image can't be pulled, the previous version keeps running. After each deployment the workflow also calls `GET /v1/features` on the public host, and the run fails if it doesn't answer.

### Roll back

- **Stage:** open an earlier **Deploy to stage** run in the Actions tab and choose **Re-run all jobs**. The run deploys the image already built for that commit instead of building it again. GitHub keeps runs for 30 days.
- **Prod:** start **Release to prod** by hand (**Run workflow**) from `main` with the version to go back to, such as `1.2.3`. A manual start from any other branch fails before it deploys.

A rollback only switches the image. It doesn't undo database migrations.

### First deployment of a new package

GHCR creates a package as private on its first push, and the droplet can't pull a private image. Make the package public once under the organization's **Packages** → **Package settings** → **Change visibility**, then re-run the failed run.

## API documentation

With the backend running, Swagger UI is at `/swagger-ui/index.html` and the OpenAPI specification at `/v3/api-docs`. Both are open without authentication, like `GET /actuator/health` and `GET /v1/features`. Every other endpoint requires a login.

## How we work

- [CONTRIBUTING](CONTRIBUTING.md): issues, workflow, commit messages, quality gates and ADRs
- [Coding standards](docs/coding-standards.md) and [Definition of Done](docs/definition-of-done.md)
- [AI workflow](docs/ai-workflow.md) and [`AGENTS.md`](AGENTS.md): how we work with AI agents
- [Minimum viable product](docs/minimum-viable-product.md): what the first release contains and the order status lifecycle
