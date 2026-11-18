# Add customer accounts

**Version:** 1.1 · **Date:** 2026-11-18 · **Issue:** #13 · **Feature flag:** `authEnabled`, off by default

## Acceptance criteria

Copied from #13. The issue is the source: change the issue first, then this copy.

- [ ] A customer can register, log in and log out
- [ ] Each user can only use the parts of the API that belong to their role

## Behaviour

1. A customer registers with firstName, lastName, email, password and confirmPassword. Email is normalized to lowercase. Duplicate normalized emails return 409.
2. Password and confirmPassword must match. Passwords contain at least 8 characters and at most 72 UTF-8 bytes. Passwords are never stored or returned in plaintext.
3. The profile accepts optional phoneNumber and preferredLanguage (da or en, default da). postalCode, city and country are optional but must be supplied together. These are the fields currently offered by the frontend; no street field is invented.
4. Registration always creates an active CUSTOMER account, regardless of any role supplied by the client.
5. A user logs in with email and password. Unknown emails, incorrect passwords and inactive accounts return the same 401 detail.
6. Login returns an accessToken, tokenType (Bearer), expiresAt and user profile. A token is valid for 15 minutes by default.
7. A user can read only their own profile through the current-user endpoint.
8. Logout requires authentication and invalidates all existing tokens for that account, across all devices. A new login works afterward.
9. Invalid, altered, expired or revoked tokens and inactive accounts cannot authenticate.
10. Customer, restaurant and delivery-agent API scopes reject other roles with 403. Account endpoints require authentication except registration and login.
11. When authEnabled is false, account endpoints return 404 and tokens do not authenticate. The existing flags endpoint remains public.

## Implementation decisions

- **Architecture:** the user, address and role features follow [ADR-004](../adr/004-backend-structure.md). User owns User and UserProfile; address owns Address and PostalCode, its validator, mapper and response DTO; role owns Role, RoleName and RoleRepository. Entities contain state and JPA mapping; repositories provide database access; services enforce rules; the mapper builds separate request/response records.
- **Authentication:** [ADR-005](../adr/005-account-authentication.md) proposes Bearer JWT authentication and account-wide logout through a database session version. No cookie authentication, refresh tokens or additional session entity. A configured Base64 key is required when the flag is enabled.
- **Schema:** Flyway creates the five tables, relationships, unique email/profile constraints and the three roles. A profile has an optional address and an address references its postal area. Postal area values are stored per registration; deduplication is not part of this slice.
- **API:** POST /v1/users with RegisterUserDTO returns 201 and UserDTO. POST /v1/users/login with LoginUserDTO returns 200 with AuthenticatedUserDTO. GET /v1/users/me returns 200 with UserDTO. POST /v1/users/logout returns 204. Responses never include password hashes. Invalid input returns 400, duplicate data 409, missing/invalid credentials 401 and forbidden roles 403, using ProblemDetail.
- **Roles:** /v1/customer/**, /v1/restaurant/** and /v1/delivery-agent/** are restricted to CUSTOMER, RESTAURANT and DELIVERY_AGENT respectively. No business endpoints are added for those future scopes.
- **Flag:** use the existing authEnabled / FEATURE_AUTH_ENABLED flag, as confirmed by the developer and issue #35. Its removal remains issue #31.

## Testing decisions

- Real HTTP tests with REST Assured, Spring Boot and PostgreSQL through the shared test container with `@SharedDatabase`, following SecurityConfigTest. No mocks, and each test class has its own database.
- Register: EP for required fields and language, BVA for password character and UTF-8 byte limits, decision tables for confirmation and optional address groups; register-fields.
- Login: EP and decision table for unknown, incorrect and inactive credentials; login-credentials.
- Authorization: role by requested scope decision table; role-access. A permitted but unimplemented scope returns 404; other roles receive 403.
- Tokens: EP and BVA for altered, malformed, expired and incomplete tokens and inactive accounts; invalid-tokens.
- Lifecycle: registration persists the full graph and a BCrypt hash, profile output excludes secrets, logout revokes two independent tokens and a subsequent login succeeds.
- Default/off flag: SecurityConfigTest verifies all account endpoints are unavailable. AppPropertiesTest covers unset, enabled, disabled and invalid configuration.
- Anonymous account access is covered by account-access. The OpenAPI test checks that login input and token output use distinct schemas.

## Out of scope

- Creating or seeding restaurant and delivery-agent accounts. Removed from issue #13 by the developer. Role authorization tests use fixtures only.

- Frontend changes, including storing tokens and wiring the forms (Kanban4sem/MTOGO-Frontend#6).
- Password reset, email verification, refresh tokens, profile editing, account deletion, multiple addresses and street-level delivery information.
- Migrating the existing feature flag package to the new structure.
- Restaurant, order and delivery business endpoints. Their ownership rules must be implemented with those features.

## Further notes

JWT authentication is proposed for developer review. No deployed server or existing account credentials are changed. Local IntelliJ on Windows uses localhost for PostgreSQL; development containers use host.docker.internal. A missing JWT key is generated locally in the ignored .env so Main can start with authentication enabled. The backend implementation does not modify the frontend repository.

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

## Changelog

| Version | Date       | Change                                                          | Issue |
| ------- | ---------- | --------------------------------------------------------------- | ----- |
| 1.0     | 2026-11-15 | First version                                                   | #13   |
| 1.1     | 2026-11-18 | Tests use the shared test container, older notes are historical | #48   |
