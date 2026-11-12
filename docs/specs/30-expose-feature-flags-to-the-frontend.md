# Expose feature flags to the frontend

**Version:** 1.3 · **Date:** 2026-11-12 · **Issue:** #30 · **Feature flag:** None

## Acceptance criteria

Copied from #30. The issue is the source: change the issue first, then this copy.

- [ ] A flag that isn't set is off
- [ ] A flag set to `true` is on after a restart, and a flag set to `false` is off
- [ ] An invalid flag value stops the backend at startup
- [ ] `GET /v1/features` returns every flag with its current value, without a login
- [ ] The response is never cached

## Behaviour

1. The `loginEnabled` flag is off when `FEATURE_LOGIN_ENABLED` is not set.
2. The `loginEnabled` flag is on when `FEATURE_LOGIN_ENABLED` is `true` when the backend starts.
3. The `loginEnabled` flag is off when `FEATURE_LOGIN_ENABLED` is `false` when the backend starts.
4. The backend refuses to start when `FEATURE_LOGIN_ENABLED` has a value that isn't a boolean.
5. A changed flag value takes effect only after the backend restarts.
6. Anyone can read every flag and its current value from `GET /v1/features`, without logging in.
7. A browser or proxy never caches the flags response, so a page reload always gets the current values.
8. Requests to `/v1/features` with any method other than GET still require a login.
9. Anyone can read the API documentation (the OpenAPI specification and Swagger UI) without logging in. Every other endpoint still requires a login.

## Implementation decisions

- **Configuration:** each flag is a boolean on `AppProperties`, bound from `app.<name>-enabled=${FEATURE_<NAME>_ENABLED:false}` in `application.properties`, as in the CONTRIBUTING example. The flag sits directly under `app`, not in a nested group. The first flag is `app.login-enabled`, from `FEATURE_LOGIN_ENABLED`. `.env.example` lists `FEATURE_LOGIN_ENABLED=false`.
- **Invalid values:** Spring's own boolean conversion decides what's valid (`true`/`false`, and also `yes`/`no`, `on`/`off`, `1`/`0`). Anything else fails the binding, and the backend doesn't start. No custom handling.
- **Module:** a new `featureflag` feature package following the coding standards: a `FeatureFlagController` in the presentation layer, and `IFeatureFlagService` → `FeatureFlagService` in the domain layer. The service maps `AppProperties` to `FeatureFlagsDTO` with one explicit field per flag. There's no data layer.
- **API contract:** `GET /v1/features` → `200` with `FeatureFlagsDTO`, a flat JSON object with one camelCase boolean per flag: `{ "loginEnabled": false }`. Every flag is returned. Flags aren't marked as UI or backend-only. The response has `Cache-Control: no-store`. The endpoint is documented with `@Tag` and `@Operation`. springdoc-openapi isn't a dependency yet, so this issue adds `springdoc-openapi-starter-webmvc-ui`, as the coding standards' API documentation section requires. The path stays `/v1/features`, as decided in ADR-001.
- **Access:** `SecurityConfig` permits `GET /v1/features` without authentication, because the login itself is behind a flag. `SecurityConfig` also permits GET on the OpenAPI specification and Swagger UI, which springdoc adds and which would otherwise need a login that doesn't exist yet. The documentation only describes the endpoints, and the proxy only forwards `/v1/*`. All other requests keep the existing `anyRequest().authenticated()` rule. CORS already covers `/v1/**`.
- **Documentation:** the Add and Remove steps in the Feature flags section of CONTRIBUTING gain "add/remove the field in `FeatureFlagsDTO` and its mapping in `FeatureFlagService`". CONTRIBUTING is a shared document. Kanban4sem/MTOGO-Frontend#21 copies the section.
- **ADRs:** [ADR-001 Feature flags](../adr/001-feature-flags.md). No new ADR and no glossary terms, since a feature flag is a technical concept, not domain language.

## Testing decisions

- **Binding of `app.login-enabled`:** `ApplicationContextRunner` with `withPropertyValues`, with no database and no web server. EP over the flag value: unset → off, `true` → on, `false` → off, `banana` → the context fails to start. The rule set is `login-enabled-binding` under `testcases/config/app-properties/`. This is the Configuration level in the coding standards, which this issue adds (version 3.0). It was chosen so the four values don't need four full Spring contexts.
- **Endpoint access:** `endpoint-access.csv` in `SecurityConfigTest` gains a `method` column, so the table covers method × path. New rows: `GET /v1/features` → `200`, `POST /v1/features` → `401`, and GET on the OpenAPI specification, `/swagger-ui.html` and Swagger UI → `200`. That class is the prior art for endpoint tests with REST Assured and Testcontainers.
- **Endpoint response:** one `@SpringBootTest` endpoint test that checks the body (`loginEnabled` is `false` by default) and the `Cache-Control: no-store` header.
- **Mapping:** one plain JUnit test of `FeatureFlagService`, with `loginEnabled` both on and off in the DTO.

## Out of scope

- Passing the `FEATURE_*` variables to the container in each environment. That belongs to the deployment in #20 (commented there).
- Changing flags at runtime or targeting specific users, which ADR-001 ruled out.
- Using `loginEnabled` in the login code. That's #13 (backend) and Kanban4sem/MTOGO-Frontend#6.
- Loading the flags in the frontend: Kanban4sem/MTOGO-Frontend#21.
- Removing `loginEnabled` once login is released: follow-up issues #31 and Kanban4sem/MTOGO-Frontend#22.

## Further notes

This issue has no feature flag of its own, since it adds no unfinished behaviour. `loginEnabled` is off everywhere until #13 starts using it.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.1 - 2026-11-12 - #30

- **Built:** the `loginEnabled` feature flag, off unless `FEATURE_LOGIN_ENABLED` turns it on when the backend starts, listed in `.env.example`. A `featureflag` feature serves every flag from `GET /v1/features` without a login, with `Cache-Control: no-store`, documented with `@Tag`, `@Operation` and `@Schema`. springdoc-openapi 3.2.0, which targets Spring Boot 4.1.2, is now a dependency. The Feature flags section of CONTRIBUTING has the `FeatureFlagsDTO` step (version 3.0).
- **Deviations:** The binding test loads `application.properties` directly and drops the system environment and system properties, so a local `.env` file or the developer's environment variables can't change the result. `FeatureFlagControllerTest` sets `FEATURE_LOGIN_ENABLED=false` for the same reason. The unset default is covered by the binding test. `loginEnabled` has no `is` prefix, because the record component name decides both the `app.login-enabled` property and the JSON field in the contract.
- **Tests:** `AppPropertiesTest` with `testcases/config/app-properties/login-enabled-binding.csv` (unset, `true`, `false`, `banana`). `SecurityConfigTest` with the `/v1/features` row in `endpoint-access.csv`. `FeatureFlagControllerTest` checks the body and `Cache-Control: no-store`. `FeatureFlagServiceTest` checks the mapping with the flag on and off.
- **Follow-up:** None.

### Spec 1.2 - 2026-11-12 - #30

- **Built:** anyone can read the OpenAPI specification and Swagger UI without a login. Only GET on those paths is permitted. Every other endpoint still requires a login.
- **Deviations:** None.
- **Tests:** two rows in `endpoint-access.csv` in `SecurityConfigTest`, one for the OpenAPI specification and one for Swagger UI.
- **Follow-up:** None.

### Spec 1.3 - 2026-11-12 - #30

- **Built:** no change in behaviour. The flag is `isLoginEnabled` in Java, following the coding standards' boolean naming. `@Name("login-enabled")` keeps the property `app.login-enabled`, and `@JsonProperty("loginEnabled")` keeps the JSON field. The contract is unchanged, and the Add step in the Feature flags section of CONTRIBUTING names both annotations. The README describes the API documentation and its public access. The coding standards (version 3.0) add the Configuration test level and its `expected` values, which `AppPropertiesTest` follows.
- **Deviations:** None. This replaces the 1.1 deviation about the missing `is` prefix.
- **Tests:** `endpoint-access.csv` has a `method` column and covers `POST /v1/features` → `401` and `/swagger-ui.html` → `200`. In `login-enabled-binding.csv`, `expected` is the bound value (`true` or `false`) or the simple name of the exception. `banana` must fail with `ConfigurationPropertiesBindException`, not just any startup failure. `FeatureFlagServiceTest` is one mapping test, and it builds `AppProperties` in one helper. `FeatureFlagControllerTest` checks that the response holds exactly the flags in `FeatureFlagsDTO` (`loginEnabled`), along with its value and `Cache-Control: no-store`.
- **Follow-up:** None.

## Changelog

| Version | Date       | Change                                                                     | Issue |
| ------- | ---------- | -------------------------------------------------------------------------- | ----- |
| 1.0     | 2026-11-12 | First version                                                              | #30   |
| 1.1     | 2026-11-12 | This issue adds springdoc-openapi                                          | #30   |
| 1.2     | 2026-11-12 | The API documentation is public                                            | #30   |
| 1.3     | 2026-11-12 | Endpoint access tests non-GET, configuration tests in the coding standards | #30   |
