# ADR-010: Maintenance Mode

## Status

Proposed — [#99](https://github.com/Kanban4sem/MTOGO-Backend/issues/99)

## Context

A production incident can require operators to stop customers from using one released feature. A rollback may also remove unrelated changes. Customers need a message explaining why the affected flow is temporarily unavailable.

## Decision

- PostgreSQL stores a pause per feature and per environment. Both features start available. Each relevant request reads the current pause state from the database, so a committed change applies to subsequent requests across backend instances and survives a restart or redeploy.
- An admin with a valid Bearer token uses `PUT /v1/maintenance/{feature}` with `{"paused": true}` to pause or `{"paused": false}` to resume. The endpoint returns `204`. Repeating the same request sets the same state.
- Operators with write access to the environment database can change the same rows directly. This permits recovery even when login is paused and every admin token has expired. To reopen login and registration, run `UPDATE feature_maintenance SET is_paused = false WHERE feature = 'AUTHENTICATION';` in the affected environment.
- `ORDERING` pauses `POST /v1/orders`. Customers can still see existing orders, and restaurant managers and delivery agents can complete them.
- `AUTHENTICATION` pauses `POST /v1/users` and `POST /v1/users/login`. Existing tokens remain subject to their normal expiration and revocation rules. Users with valid tokens can still access protected endpoints and log out.
- A paused endpoint returns `503 Service Unavailable` with `application/problem+json`. The `detail` field contains the same message as the public status API. The pause is checked before authentication and request validation on these three endpoints.
- Public `GET /v1/maintenance` returns a list of entries with `feature`, `paused` and `message`. The message is `null` while the feature is available. The response has `Cache-Control: no-store`. The frontend can read the list on page load and use the `503` detail if a pause begins before a request reaches the backend.

## Alternatives

- **Rollback:** restores an earlier release and can remove unrelated fixes.
- **Startup feature flags:** changing a flag from [ADR-001](001-feature-flags.md) requires restarting the backend.
- **In-memory pause:** loses the state when the backend restarts and can give different answers across instances.
- **Database-only control:** requires database access for every pause and resumption. The admin endpoint lets admins handle incidents with their existing credentials.

## Consequences

- Registration, login and order placement each make one indexed lookup for pause state. A request that already passed the check may finish after an admin pauses the feature.
- Operators need database write access to recover an authentication pause when there is no valid admin token.
- Maintenance mode has no release flag of its own. Frontend integration is tracked in [MTOGO-Frontend#69](https://github.com/Kanban4sem/MTOGO-Frontend/issues/69).
