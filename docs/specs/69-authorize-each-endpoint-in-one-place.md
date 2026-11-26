# Authorize each endpoint in one place

**Version:** 2.0 · **Date:** 2026-11-27 · **Issue:** #69 · **Feature flag:** None

## Acceptance criteria

Copied from #69. The issue is the source: change the issue first, then this copy.

- [x] `SecurityConfig` has no role-based URL matchers; it keeps only public paths, the default authenticated rule, authentication and the filters
- [x] Every endpoint method declares its access with its own `@PreAuthorize`, public ones with `permitAll()`
- [x] A test fails when an endpoint method has no `@PreAuthorize` of its own
- [x] An authenticated user without the required role gets `403`, not `500`
- [x] Every existing endpoint access test passes without changing its expected status codes; the URL scope test is deleted with the URL scopes

## Behaviour

1. Anyone can read the health check, feature flags, maintenance status, API documentation, restaurant list and restaurant menus. Registration and login are public when available.
2. An anonymous user who calls a protected endpoint gets `401` while the endpoint is available. A paused endpoint returns `503` before authentication under [ADR-010](../adr/010-maintenance-mode.md).
3. An anonymous user who calls a path that has no endpoint gets `401`.
4. A customer can use the customer endpoints: updating their own profile, managing their saved delivery addresses, placing an order, and reading their orders and order notifications.
5. A restaurant user can list and update their restaurant's orders.
6. A delivery agent can list and update the orders they deliver.
7. An admin can read the management dashboard and set the pause state of a released feature.
8. Any logged-in user can read their own profile and log out.
9. A logged-in user who sends a well-formed request to an endpoint their role doesn't allow gets `403` with a problem detail, never `500`.
10. A logged-in user without the role gets the request-shape error before the role check (see Further notes):
    - a malformed body or path variable gets `400`
    - a path with no endpoint gets `404`
    - an unsupported method gets `405`
11. Every request that reaches an endpoint gets the same access decision as before this issue.

## Implementation decisions

- **Single source of role rules:** each endpoint method declares its access with its own `@PreAuthorize`: `hasRole(...)`, `isAuthenticated()` or `permitAll()`. A controller class carries no `@PreAuthorize`. This follows Architecture rule 5 in the coding standards.
- **`SecurityConfig`** keeps only what isn't role based:
  - stateless Bearer authentication and the JWT and feature-gate filters
  - the 401 and 403 handlers
  - `permitAll()` for the public paths
  - `anyRequest().authenticated()` as the default rule

  It has no `hasRole` matchers.

- **Public paths are declared twice on purpose:** an anonymous request has to get through the URL rules before method security runs, so public paths stay as `permitAll()` in `SecurityConfig`. Their methods also carry `@PreAuthorize("permitAll()")`, so every endpoint declares its access the same way. If the two disagree, the stricter rule wins (anonymous `401`), and the anonymous access tests fail. A mismatch can't make an endpoint public by mistake. Health, actuator and the API documentation have no controller method and are declared only in `SecurityConfig`.
- **Controllers changed:**
  - `FeatureFlagController` gains `permitAll()`.
  - `CustomerOrderController` and `OrderNotificationController` move their class-level `hasRole('CUSTOMER')` to each endpoint method.

  The other controllers already declare access per method.

- **Denials from method security:** `GlobalExceptionHandler` maps `AccessDeniedException` to a `403` `ProblemDetail`. Without that mapping its catch-all handler would return `500`. The detail text is the constant `GlobalExceptionHandler.ACCESS_DENIED`, which `SecurityConfig`'s `403` handler also uses, so a `403` looks the same whichever layer denies it.
- **API contract:** paths, DTOs and the status codes of well-formed requests are unchanged. Only the error precedence in behaviour 10 changes. The frontend sends only requests for its own role, so it needs no change and no linked issue.
- **ADRs:**
  - [ADR-004](../adr/004-backend-structure.md) for package responsibilities
  - [ADR-005](../adr/005-account-authentication.md) for authentication

  No new ADR, since Architecture rule 5 in the coding standards already states the rule. The Backend errors table in the coding standards gains the `403` that `GlobalExceptionHandler` now returns.

## Testing decisions

- **Endpoint access, existing rule sets:** every controller's `*-access.csv` under `testcases/<feature>/<controller>/` stays unchanged and must pass. This proves the move kept each access decision. Their wrong-role rows, for example `RESTAURANT,403` in `customer-order-controller/get-all-access.csv`, are now answered by method security. They therefore also cover behaviour 9 (`403`, not `500`).
- **URL scopes, removed rule set:** `UserControllerTest.roleAccess` with `user-controller/get-scope-access.csv` is deleted. It sent wrong-role requests to `/v1/<scope>/probe`, a path with no endpoint, and expected the `403` of the URL scope matchers. Those matchers no longer exist. Behaviour 10 gives such a request `404`, and that order isn't pinned. Per-endpoint access stays covered by each controller's `*-access.csv`.
- **Endpoint access, new rule set:** the user feature's customer-only endpoints, updating the profile and managing saved delivery addresses, had no wrong-role rows. `UserControllerTest.customerAccess` with `user-controller/request-customer-access.csv` adds them, one row per role and endpoint, with a well-formed body so the role check decides the status.
- **Public paths and the default rule:** `SecurityConfigTest` with `security-config/request-endpoint-access.csv`, unchanged.
- **Guard test:** a new method in `SecurityConfigTest`.
  - It reads every handler method from the `RequestMappingHandlerMapping` bean, limited to the `mtogo` package.
  - It fails on any method without its own `@PreAuthorize`. A class-level annotation doesn't count.
  - It's a structural check over every handler, not a table of inputs, so it has no CSV.
  - It names the offending methods in the failure message.
- **Not tested:** the error precedence in behaviour 10. It follows from Spring MVC's order, and the spec records it rather than pinning it.

## Out of scope

- Opening every URL and letting method security decide everything (`anyRequest().permitAll()`). That would leave actuator and unknown paths unprotected, and a forgotten annotation would make an endpoint public.
- Building the public URL rules from the `permitAll()` annotations at startup. That's worth revisiting only if the API gains many public endpoints.
- Data-dependent access, such as "only your own orders". That stays in the services.

## Further notes

**Error precedence.** Spring checks a request in this order:

1. the URL rules in `SecurityConfig`
2. Spring MVC: finding the method, converting path variables and validating the body
3. `@PreAuthorize`

The role checks move from the first step to the last. A logged-in user without the role now gets a request-shape error (`400`, `404` or `405`) where they used to get `403`. The request is still refused, and nothing is read or changed. The only thing the caller learns is the request shape, which the public OpenAPI document already publishes. Anonymous users still get `401` at the first step. "Access stays the same" therefore means that every request reaching an endpoint gets the same decision.

While the URL matchers existed, method security never denied a request, because the URL rules always denied first. That is why the `AccessDeniedException` mapping is needed only now.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-26 - #69

- **Built:** `SecurityConfig` keeps public paths, `anyRequest().authenticated()`, Bearer authentication, the filters and the `401` and `403` handlers, with no role matchers. Every endpoint method declares its access with its own `@PreAuthorize`: `FeatureFlagController.getAll` gained `permitAll()`, and `CustomerOrderController` and `OrderNotificationController` moved `hasRole('CUSTOMER')` from the class to each method. `GlobalExceptionHandler` maps `AccessDeniedException` to a `403` `ProblemDetail` with the detail `GlobalExceptionHandler.ACCESS_DENIED`, which `SecurityConfig`'s 403 handler also uses. The Backend errors table in the coding standards (9.4) says so.
- **Deviations:** `UserControllerTest.roleAccess` and `get-scope-access.csv` are deleted, agreed with the developer and recorded in the fifth acceptance criterion of #69. Six of its rows expected `403` for a wrong role on a path with no endpoint. After the move they get `404`, as behaviour 10 states. The test checked the URL scopes this issue removes, so it was deleted instead of changing `expected`. The other access tests pass with unchanged status codes.
- **Tests:** the guard test `SecurityConfigTest.everyEndpointMethodDeclaresItsAccess` reads every `mtogo` handler from `RequestMappingHandlerMapping` and lists the methods without their own `@PreAuthorize`. Before the controller changes it named the five expected methods. Without the mapping, the 21 wrong-role rows in the controllers' `*-access.csv` files returned `500`. They now return `403`. `security-config/request-endpoint-access.csv` is unchanged. `user-controller/request-customer-access.csv` adds the restaurant, delivery agent and admin `403` rows and the customer rows for `PUT /v1/users/me` and the five saved delivery address methods.
- **Follow-up:** None

### Spec 2.0 - 2026-11-27 - #99

- **Built** — `GET /v1/maintenance` is public. Only admins can call `PUT /v1/maintenance/{feature}`. The maintenance filter returns `503` for the paused endpoints defined in ADR-010.
- **Deviations** — None.
- **Tests** — `MaintenanceControllerTest` covers public reads, role-based writes and paused endpoint responses. The tests are unrun.
- **Follow-up** — [MTOGO-Frontend#69](https://github.com/Kanban4sem/MTOGO-Frontend/issues/69).

## Changelog

| Version | Date       | Change                    | Issue |
| ------- | ---------- | ------------------------- | ----- |
| 1.0     | 2026-11-26 | First version             | #69   |
| 2.0     | 2026-11-27 | Define maintenance access | #99   |
