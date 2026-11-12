# ADR-001: Feature Flags

## Status

Accepted

## Context

We deliver to main continuously, so unfinished features end up on main. AGENTS.md requires unfinished behaviour to stay behind a feature flag, but we haven't decided how a flag is built, configured or removed in the backend and the frontend.

## Decision

In order to focus on learning KanBan and getting Up To Date as a development team we have chosen to work with Feature Flags by using Environment Variables and a default false state in our application.properties file that's used by our Spring Boot application.

Example:

**.env file**
FEATURE_LOGIN_ENABLED=true

**application.properties file**
app.login-enabled=${FEATURE_LOGIN_ENABLED:false}

The frontend fetches UI flags from GET /v1/features once at startup and stores them in shared application memory. Features are controlled through conditional rendering. Missing flags and failed requests are treated as false. Reloading the page fetches the flags again.

## Alternatives

- Spring's @ConditionalOnProperty, which leaves the flagged bean or endpoint out entirely when the flag is off.

- A library such as Togglz, Unleash or OpenFeature, which can change flags at runtime and target specific users.

## Consequences

- Unfinished features can reach main while remaining disabled by default.
- Flag changes require restarting the backend with the updated environment.
- The startup configuration must pass .env values to Spring Boot.
- Features must be tested with their flags both enabled and disabled.
- In case a Feature Flag is temporary there has to be removal / follow-up issue regarding that once fully released.
