# MTOGO-Backend

MTOGO is a food delivery platform. Customers order food from local restaurants and pay online, restaurants accept and prepare the orders, and delivery agents bring them to the customer's door. The customer can follow each order from payment to delivery.

This repository holds the backend: the REST API behind the MTOGO website. The [MTOGO-Frontend](https://github.com/Kanban4sem/MTOGO-Frontend) is the website that customers, restaurants and delivery agents use, and it gets all its data from this API.

## What the backend does

- **Accounts:** customers register, log in and manage their details and delivery addresses. Restaurant users and delivery agents sign in with a role.
- **Restaurants and menus:** the restaurants that accept orders and their menu items and prices.
- **Orders and payment:** customers place and pay for orders. Payment goes through a mocked payment service.
- **Order handling:** restaurants accept or reject new orders and mark them as ready. Delivery agents take ready orders, pick them up and deliver them.
- **Tracking and notifications:** customers see the status of their orders and get a notification each time it changes.
- **Management dashboard:** admins follow open orders, orders per 24 hours, sales and delivery times, for all of MTOGO and per restaurant.
- **Feature flags:** the backend tells the frontend which features are switched on.

What the first release contains, and the order status lifecycle, are described in the [minimum viable product](docs/minimum-viable-product.md). The words we use for the domain are in the [glossary](docs/glossary.md).

## Technology

Java 25 and Spring Boot 4, with PostgreSQL and Flyway for the database. The API is documented with OpenAPI. The backend runs as a Docker image on [stage](https://stage.mtogo.saldic.dev) and [prod](https://mtogo.saldic.dev).

## API documentation

With the backend running, Swagger UI is at `/swagger-ui/index.html` and the OpenAPI specification at `/v3/api-docs`. Both are open without authentication, like `GET /v1/features`. Every other endpoint requires a login. Health and metrics are not on the API port: `GET /actuator/health` and `GET /actuator/prometheus` are on management port 8081, open without a login and reachable only on the Docker network (ADR-007).

The order endpoints that a role uses live under that role's path, and each role sees only its own orders:

- **Customer:** `POST /v1/orders` places and pays for an order. `GET /v1/customer/orders` and `GET /v1/customer/orders/{id}` show the customer's orders and their status. `/v1/customer/orders/notifications` lists the status changes and marks them as read.
- **Restaurant:** `GET /v1/restaurant/orders` lists the restaurant's orders. `PATCH /v1/restaurant/orders/{id}` accepts or rejects a placed order, or marks an accepted order as ready.
- **Delivery agent:** `GET /v1/delivery-agent/orders` lists the ready orders no one has taken, and the agent's own orders. `PATCH /v1/delivery-agent/orders/{id}` takes a ready order, or marks it as picked up or delivered.
- **Admin:** `GET /v1/admin/dashboard` shows the management dashboard.

Why a role's path is singular is in the [coding standards](docs/coding-standards.md).

## Getting started

We work with Kanban and trunk-based development using [TakT](https://github.com/devx-cafe/gh-tt) and [InSitu](https://github.com/devx-cafe/gh-insitu). Every change starts as an issue on our [board](https://github.com/orgs/Kanban4sem/projects/1) and ends as exactly one commit on `main`.

- [Getting started](docs/getting-started.md): set up your machine and the dev container
- [Local development](docs/local-development.md): configuration, running the backend, feature flags, seeded users, pgAdmin and the Docker image
- [Build and test](docs/build-and-test.md): running the tests, test JVMs and the SonarQube Cloud analysis
- [Deployment](docs/deployment.md): stage, releasing to prod, rolling back and monitoring

## How we work

- [CONTRIBUTING](CONTRIBUTING.md): issues, workflow, commit messages, quality gates and ADRs
- [Coding standards](docs/coding-standards.md) and [Definition of Done](docs/definition-of-done.md)
- [AI workflow](docs/ai-workflow.md) and [`AGENTS.md`](AGENTS.md): how we work with AI agents
- [Architecture decisions](docs/adr/)
