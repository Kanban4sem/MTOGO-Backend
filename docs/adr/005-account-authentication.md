# ADR-005: Account Authentication

## Status

Proposed — [#13](https://github.com/Kanban4sem/MTOGO-Backend/issues/13)

## Context

Customers must register, log in and log out. The React frontend needs an authentication contract, and logout must invalidate credentials already issued by the backend.

## Decision

Use the existing Spring Security and JJWT dependencies. Passwords are hashed with BCrypt. Login returns a signed Bearer JWT valid for 15 minutes by default. The frontend sends it explicitly in the Authorization header; the backend does not authenticate cookies or sessions. Tokens contain a user ID and session version, not profile data or passwords.

Each authenticated request checks the account's current active state, role and session version in the database. Logout increments the version atomically, invalidating all previous tokens for that account on every device. There is no refresh token in this slice.

Public registration always assigns CUSTOMER. Authentication always runs. It was released behind the temporary authEnabled feature flag, which [#31](https://github.com/Kanban4sem/MTOGO-Backend/issues/31) removed. Follow [ADR-004](004-backend-structure.md) for package responsibilities.

## Alternatives

- **Server sessions:** Built-in logout support, but requires cookie handling and CSRF protection in the frontend.
- **JWT without server-side revocation:** Fewer database lookups, but a logged-out token remains usable until expiration.

## Consequences

- JWT_SECRET must be a Base64 signing key with at least 32 random bytes. The backend does not start without it.
- Every authenticated request performs a database lookup; logout affects every device.
- The frontend must handle token expiration and clear its token on logout. Its integration is separate work in frontend issue #6.
- The account model has five entities: User, UserProfile, Address, PostalCode and Role. City is a field on PostalCode. Street addresses and multiple saved addresses remain outside this slice.
