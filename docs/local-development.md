# Local Development

How to configure and run the backend on your own machine, sign in as seeded users and try the API. Set up the dev container first, as described in [Getting started](getting-started.md).

## Configuration

Configuration lives in `src/main/resources/application.properties` and contains no secrets. Copy `.env.example` to `.env` and fill in real values. The `.env` file is never committed.

## Run the backend

With PostgreSQL running and the `mtogo` database created, open `Main.java` and choose **Run**. When running inside the development container, set `DB_URL=jdbc:postgresql://host.docker.internal:5432/mtogo` in your ignored `.env`; when running Java directly on your host, use localhost instead. Set `DB_USER` and `DB_PASSWORD` for that PostgreSQL instance.

Flyway applies migrations at startup and Hibernate validates their tables. In pgAdmin, refresh **mtogo → Schemas → public → Tables** to see the tables and `flyway_schema_history`. Restarting does not recreate tables or reset data. Table creation does not depend on the feature flags.

## Feature flags and login

Unfinished features are behind feature flags that are off by default, as described in [CONTRIBUTING](../CONTRIBUTING.md#feature-flags). To try them locally, enable them in your own `.env` and restart the backend. No flag is declared at the moment.

The backend does not start without `JWT_SECRET`, a Base64 key generated with `openssl rand -base64 32`. Set it in your `.env` and never commit the key.

Send the access token from `POST /v1/users/login` as `Authorization: Bearer <accessToken>`. It expires after 15 minutes by default (`JWT_LIFETIME_SECONDS`). The backend does not use authentication cookies or sessions. The authentication decision is [ADR-005](adr/005-account-authentication.md).

Two services are mocked locally:

- **Payment:** the payment token `decline` is declined and any other token is confirmed.
- **SMS:** when an order's status changes and the customer has a phone number, the mock SMS provider logs the message at debug level.

## Try the API

Every endpoint, request and response is documented in Swagger UI at `/swagger-ui/index.html`, and the OpenAPI specification is at `/v3/api-docs`. Both are open without authentication, like `GET /v1/features`. Every other endpoint requires a login. Health and metrics are not on the API port: `GET /actuator/health` and `GET /actuator/prometheus` are on management port 8081, open without a login and reachable only on the Docker network (ADR-007).

The behaviour behind the endpoints is in the specs in [`docs/specs/`](specs/), such as [customer accounts](specs/13-add-customer-accounts.md) and [placing and paying for an order](specs/15-place-and-pay-for-an-order.md).

To send requests from the editor, open the files in [`src/main/resources/http/`](../src/main/resources/http/) in JetBrains HTTP Client. [customerOrders.http](../src/main/resources/http/customerOrders.http) walks through a customer's orders.

## Seeded users

On every startup the backend creates these users for each role whose seed password is set, as decided in [ADR-006](adr/006-seeded-user-credentials.md). Their passwords are never in the repository.

| Email                    | Role           | Restaurant   | Name           | Seed password                  |
| ------------------------ | -------------- | ------------ | -------------- | ------------------------------ |
| `customer@mtogo.example` | CUSTOMER       | None         | Test Customer  | `SEED_CUSTOMER_PASSWORD`       |
| `kebab@mtogo.example`    | RESTAURANT     | MTOGO Kebab  | MTOGO Kebab    | `SEED_RESTAURANT_PASSWORD`     |
| `bakery@mtogo.example`   | RESTAURANT     | MTOGO Bakery | MTOGO Bakery   | `SEED_RESTAURANT_PASSWORD`     |
| `agent@mtogo.example`    | DELIVERY_AGENT | None         | Delivery Agent | `SEED_DELIVERY_AGENT_PASSWORD` |
| `admin@mtogo.example`    | ADMIN          | None         | MTOGO Admin    | `SEED_ADMIN_PASSWORD`          |

- **Locally:** set the variables in your `.env` and restart. Each user logs in with their email and their role's password. An unset or blank variable skips that role. A password shorter than 8 characters or longer than 72 UTF-8 bytes stops the startup.
- **On stage:** the passwords are in the droplet's `backend.env`. The team has no shared secret store yet, so ask a team member with access to the droplet.
- **On prod:** the variables stay unset, so prod has no seeded users. `deploy.sh` refuses to deploy to prod while one is set.
- **Existing users:** seeding never changes a user whose email already exists, including a customer who registered with a seeded email. It logs a warning and skips that user.
- **Rotating a password:** delete the user, change the variable and restart.

## Try an order conflict

Start the backend with the `local` Spring profile against your development database:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The dev container forwards only port 8080 to your machine. To read the health or the metrics on management port 8081, run `curl http://localhost:8081/actuator/prometheus` in a terminal inside the dev container.

The profile seeds **Sold-out kebab** in the MTOGO Kebab menu and **Closed restaurant meal** at MTOGO Closed Restaurant. To try both `409` responses, enable authentication, seed a customer, and open [orderConflicts.http](../src/main/resources/http/orderConflicts.http) in JetBrains HTTP Client.

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

## Local database browser (pgAdmin)

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

After about a minute `docker ps` shows the container as `healthy`. Docker checks `GET /actuator/health` on management port 8081 inside the container, which is open without authentication. To read the health or the metrics from your machine, add `-p 8081:8081` and open `http://localhost:8081/actuator/health` or `http://localhost:8081/actuator/prometheus`.
