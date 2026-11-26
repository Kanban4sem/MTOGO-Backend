# Add customer accounts

**Version:** 4.0 · **Date:** 2026-11-27 · **Issue:** #13 · **Feature flag:** None (`authEnabled` removed in #31)

## Acceptance criteria

Copied from #13. The issue is the source: change the issue first, then this copy.

- [x] A customer can register, log in and log out
- [x] Each user can only use the parts of the API that belong to their role

## Behaviour

1. A customer registers with firstName, lastName, email, password and confirmPassword. Email is normalized to lowercase. Duplicate normalized emails return 409.
2. Password and confirmPassword must match. Passwords contain at least 8 characters and at most 72 UTF-8 bytes. Passwords are never stored or returned in plaintext.
3. The profile accepts optional phoneNumber and preferredLanguage (da or en, default da). postalCode, city and country are optional but must be supplied together. These are the fields currently offered by the frontend; no street field is invented.
4. Registration always creates an active CUSTOMER account, regardless of any role supplied by the client.
5. A user logs in with email and password. Unknown emails, incorrect passwords and inactive accounts return the same 401 detail.
6. Login returns an accessToken, tokenType (Bearer), expiresAt and user profile. A token is valid for 15 minutes by default.
7. A user can read only their own profile through the current-user endpoint.
8. Logout requires authentication and invalidates all existing tokens for that account, across all devices. The user can log in again when login is available.
9. Invalid, altered, expired or revoked tokens and inactive accounts cannot authenticate.
10. Customer, restaurant and delivery-agent API scopes reject other roles with 403. Account endpoints require authentication except registration and login.
11. During an authentication pause, registration and login return `503` with a message explaining that they are temporarily unavailable. Existing tokens continue to authenticate under their normal expiration and revocation rules. See [ADR-010](../adr/010-maintenance-mode.md).
12. The address in the profile response includes a nullable `street`. Checkout can save a complete default delivery address as described in the [order spec](15-place-and-pay-for-an-order.md).

## Implementation decisions

- **Architecture:** the user, address and role features follow [ADR-004](../adr/004-backend-structure.md). User owns User and UserProfile; address owns Address and PostalCode, its validator, mapper and response DTO; role owns Role, RoleName and RoleRepository. Entities contain state and JPA mapping; repositories provide database access; services enforce rules; the mapper builds separate request/response records.
- **Authentication:** [ADR-005](../adr/005-account-authentication.md) proposes Bearer JWT authentication and account-wide logout through a database session version. No cookie authentication, refresh tokens or additional session entity. A configured Base64 key is always required, and the backend does not start without one.
- **Schema:** Flyway creates the five tables, relationships, unique email/profile constraints and the three roles. A profile has an optional address and an address references its postal area. Postal area values are stored per registration; deduplication is not part of this slice.
- **API:** POST /v1/users with RegisterUserDTO returns 201 and UserDTO. POST /v1/users/login with LoginUserDTO returns 200 with AuthenticatedUserDTO. GET /v1/users/me returns 200 with UserDTO. POST /v1/users/logout returns 204. Responses never include password hashes. Invalid input returns 400, duplicate data 409, missing/invalid credentials 401 and forbidden roles 403, using ProblemDetail.
- **Roles:** /v1/customer/**, /v1/restaurant/** and /v1/delivery-agent/** are restricted to CUSTOMER, RESTAURANT and DELIVERY_AGENT respectively. No business endpoints are added for those future scopes.
- **Flag:** none. #13 released authentication behind the temporary `authEnabled` / `FEATURE_AUTH_ENABLED` flag, as confirmed by the developer and issue #35, and #31 removed it as described in the [coding standards](../coding-standards.md#feature-flags).

## Testing decisions

- Real HTTP tests with REST Assured, Spring Boot and PostgreSQL through the shared test container with `@SharedDatabase`, following SecurityConfigTest. No mocks, and each test class has its own database.
- Register: EP for required fields and language, BVA for password character and UTF-8 byte limits, decision tables for confirmation and optional address groups; register-fields.
- Login: EP and decision table for unknown, incorrect and inactive credentials; login-credentials.
- Authorization: role by requested scope decision table; role-access. A permitted but unimplemented scope returns 404; other roles receive 403.
- Tokens: EP and BVA for altered, malformed, expired and incomplete tokens and inactive accounts; invalid-tokens.
- Lifecycle: registration persists the full graph and a BCrypt hash, profile output excludes secrets, logout revokes two independent tokens and a subsequent login succeeds.
- Signing key: the backend refuses to start without a valid `JWT_SECRET`. EP for unset, blank and non-Base64 secrets, and BVA for 31, 32 and 33 bytes; startup-secret in JwtServiceTest. The account endpoints have no flag, so the endpoint tests run with no `FEATURE_*` variable set.
- Anonymous account access is covered by account-access. The OpenAPI test checks that login input and token output use distinct schemas.
- Authentication pause is covered by the `post-paused-endpoint` decision table in `MaintenanceControllerTest`. It also checks that an existing token can still read the current profile.

## Out of scope

- Creating or seeding restaurant and delivery-agent accounts. Removed from issue #13 by the developer. Role authorization tests use fixtures only.

- Frontend changes, including storing tokens and wiring the forms (Kanban4sem/MTOGO-Frontend#6).
- Password reset, email verification, refresh tokens, general profile editing, account deletion and multiple addresses.
- Migrating the existing feature flag package to the new structure.
- Restaurant, order and delivery business endpoints. Their ownership rules must be implemented with those features.

## Further notes

JWT authentication is proposed for developer review. No deployed server or existing account credentials are changed. Local IntelliJ on Windows uses localhost for PostgreSQL; development containers use host.docker.internal. A missing JWT key is generated locally in the ignored .env so Main can start, since the backend refuses to start without one. The backend implementation does not modify the frontend repository.

## Implementation notes

### Spec 1.0 - 2026-11-16 - #38

- **Built:** registration, login, current profile, account-wide logout, role scopes, validation, errors and a Flyway migration. The developer approved separate user, address and role features. Address mapping and validation belong to address; password rules and token error translation stay in security. Email normalization is reused through the user feature's util package.
- **Deviations:** Lombok annotation processing must be explicitly enabled for Java 25; the existing dependency and version are retained.
- **Tests:** UserServiceTest covers persisted user/profile/address mapping, CUSTOMER assignment, normalized duplicate email, generic login failures, current account state/role and account-isolated session revocation. Its CSV sets are register-profile, register-conflicts, login-credentials and authenticate-account. AddressValidatorTest uses validate-completeness for the optional address decision table. PasswordServiceTest uses hash-utf8-bytes for the exact BCrypt byte boundary. UserControllerTest retains lifecycle, role-access, invalid-tokens and account-access checks, and covers registration/login input classes and length boundaries with register-fields, register-lengths, login-fields and login-lengths. The register-role set checks that client-supplied roles cannot grant privileges. Focused error mapping and two-user profile isolation checks complement service coverage. The full login matrix moved from HTTP to service level. Existing configuration and disabled-auth tests remain in place.
- **Validation:** On 2026-11-16, the full backend suite passed in the dev container against isolated PostgreSQL Testcontainers: 161 tests, no failures, errors or skipped tests. The fix-all and trunk-worthy checks passed, including clean verify, Java formatting, Markdown, spelling and Prettier. JaCoCo was inspected for remaining gaps in scoped behaviour; the SonarQube Cloud quality gate has not been run in this session.
- **Follow-up:** frontend integration remains in frontend issue #6; flag removal remains #31. ADR-005 awaits developer approval.

### Spec 1.1 - 2026-11-18 - #48

- **Built:** nothing new. The tests now get their database from the shared PostgreSQL test container through `@SharedDatabase`, one database per test class, as described under Test database in the coding standards.
- **Deviations:** None.
- **Tests:** the test classes above moved to `@SharedDatabase`. Earlier notes that mention a container per test class or isolated Testcontainers describe the setup at that time and are historical.
- **Follow-up:** None.

### Spec 1.2 - 2026-11-19 - #53

- **Built:** nothing new. The account endpoints' `404` comes only from the `FeatureGate` in `UserGateConfig`, and `UserService` no longer checks the auth flag. `JwtAuthenticationFilter` still reads the flag so that tokens do not authenticate while it is off, and `JwtService` reads it for its startup secret check. Email normalization moved from the user feature's util package into its domain package.
- **Deviations:** None.
- **Tests:** the disabled-auth rows in endpoint-access, run by SecurityConfigTest, cover the `404` from the gate. The 1.0 note's mention of the util package is historical.
- **Follow-up:** None.

### Spec 2.0 - 2026-11-22 - #61

- **Built:** profile address responses expose the street saved at checkout. Newly registered and seeded profiles can still contain a postal area without a street.
- **Deviations:** None.
- **Tests:** the checkout flow in `OrderControllerTest` reads the saved address from `GET /v1/users/me`. Test execution is left to the developer.
- **Follow-up:** frontend checkout integration.

### Spec 3.0 - 2026-11-25 - #31

- **Built:** the `authEnabled` flag is gone from AppProperties, `application.properties`, `.env.example`, `deploy/backend.env.example`, FeatureFlagsDTO, FeatureFlagService, `docs/local-development.md` and the `.http` files. UserGateConfig is deleted, so the account endpoints have no FeatureGate. `JwtAuthenticationFilter` always authenticates Bearer tokens. `JwtService` always requires `JWT_SECRET` and no longer falls back to a random key. FeatureGate and FeatureGateFilter stay for future flags, with no gate declared. ADR-005 no longer puts authentication behind a flag and requires `JWT_SECRET` always. Notes for earlier versions that mention the flag are historical.
- **Deviations:** None.
- **Tests:** the disabled-auth `404` rows in `request-endpoint-access` and `bind-auth-enabled` in AppPropertiesTest are removed. `startup-secret` in JwtServiceTest drops the flag column and its decision-table rows: an unset or blank secret always stops the backend. The test `application.properties` sets a test signing key, so the test classes no longer set `FEATURE_AUTH_ENABLED` or `JWT_SECRET`. The testing decisions name `startup-secret` in place of the flag tests.
- **Follow-up:** remove `FEATURE_AUTH_ENABLED` and `FEATURE_ORDERING_ENABLED` from each environment's `.env`, and make sure `JWT_SECRET` is set there. The frontend side is Kanban4sem/MTOGO-Frontend#22.

### Spec 3.1 - 2026-11-25 - #72

- **Built:** no code change. The acceptance criteria copied from #13 are ticked, as they are on the issue.
- **Deviations:** None.
- **Tests:** None needed.
- **Follow-up:** None.

### Spec 4.0 - 2026-11-27 - #99

- **Built** — Maintenance mode pauses login and registration together. Each returns a `503` problem detail while paused. Valid existing tokens still authenticate.
- **Deviations** — None.
- **Tests** — `MaintenanceControllerTest` covers the pause and resumption. The tests are unrun.
- **Follow-up** — [MTOGO-Frontend#69](https://github.com/Kanban4sem/MTOGO-Frontend/issues/69).

## Changelog

| Version | Date       | Change                                                          | Issue |
| ------- | ---------- | --------------------------------------------------------------- | ----- |
| 1.0     | 2026-11-15 | First version                                                   | #13   |
| 1.1     | 2026-11-18 | Tests use the shared test container, older notes are historical | #48   |
| 1.2     | 2026-11-19 | Auth flag checked only by the gate, email helper in the domain  | #53   |
| 2.0     | 2026-11-22 | Expose the saved delivery street in the profile address         | #61   |
| 3.0     | 2026-11-25 | Remove the auth feature flag                                    | #31   |
| 3.1     | 2026-11-25 | Acceptance criteria ticked to match the issue                   | #72   |
| 4.0     | 2026-11-27 | Pause login and registration at runtime                         | #99   |
