# Show restaurants and their menus

**Version:** 1.1 · **Date:** 2026-11-18 · **Issue:** #14 · **Feature flag:** None, confirmed by the developer

## Acceptance criteria

- [x] A customer can see the restaurants that accept orders.
- [x] A customer can see a restaurant's menu items with prices.

## Behaviour

1. Anyone can list restaurants that accept orders without logging in.
2. Anyone can read the active menus of a restaurant accepting orders, including available items, descriptions, prices and image URLs.
3. Restaurants not accepting orders are omitted from the list and return 404 for menu requests, as do unknown restaurants.
4. Restaurants without active menus return an empty list. Active menus without available items return an empty items list.
5. A new database contains two restaurants accepting orders, each with one active menu and one available item.

## Implementation decisions

- Follow the existing feature layering and Spring Data JPA architecture. Restaurant, Menu and MenuItem belong to the restaurant feature. Each menu references one restaurant and each item references one menu.
- GET /v1/restaurants returns an array of RestaurantDTO with id, name and acceptsOrders. Only restaurants accepting orders are returned.
- GET /v1/restaurants/{restaurantId}/menus returns an array of MenuDTO with id, restaurantId, name, description and items. Each MenuItemDTO contains id, name, description, price, imageUrl and available. Results are ordered by ID.
- Both endpoints are public GET endpoints; other methods retain the existing security defaults. Missing or closed restaurants return the existing ProblemDetail 404 response.
- Prices use BigDecimal and NUMERIC(10, 2), in DKK. Database constraints reject negative prices.
- Flyway creates the tables and foreign keys, then seeds MTOGO Kebab with a kebab meal at 89.00 DKK and MTOGO Bakery with a croissant at 25.00 DKK. Names and prices are initial sample data.
- Image URLs reference the two existing frontend public images. Resolve them against the frontend origin, since the backend does not host the files.
- The developer explicitly chose no feature flag, so no removal issue is needed.

## Testing decisions

- The developer will run tests. No tests are run in this implementation session, as requested.
- Relevant checks: accepting versus closed restaurants; known versus unknown restaurant IDs; active versus inactive menus; available versus unavailable items; two seeded restaurants with one item each; decimal prices and image paths; anonymous GET access.

## Out of scope

- Frontend changes, tracked by MTOGO-Frontend#7.
- Restaurant management, ordering, account ownership and additional restaurant address or preparation fields.

## Further notes

The domain-model draft provides the relationship structure. Only fields needed for this slice are implemented.

## Implementation notes

### Spec 1.0 - 2026-11-16 - #38

- **Built:** Three entities, repositories, response mapping, public read endpoints and schema/seed migrations.
- **Deviations:** None.
- **Tests:** RestaurantServiceTest covers accepting/closed/unknown restaurants, empty results, active menus, available items, isolation between restaurants and menus, ID ordering and decimal-preserving response mapping. CSV sets: get-all-acceptance, get-menus-restaurant and get-menus-visibility. RestaurantControllerTest checks anonymous JSON access and ProblemDetail mapping with get-menus-path. RestaurantSchemaTest checks Flyway's initial catalogue and the nonnegative price boundary through save-price. All database tests use isolated PostgreSQL Testcontainers; no inherited CRUD or generated accessor tests were added.
- **Validation:** On 2026-11-16, the full backend suite passed in the dev container: 161 tests, no failures, errors or skipped tests. The fix-all and trunk-worthy checks passed, including clean verify and formatting. JaCoCo was inspected; the SonarQube Cloud quality gate has not been run in this session.
- **Follow-up:** Frontend integration remains MTOGO-Frontend#7.

### Spec 1.1 - 2026-11-18 - #48

- **Built:** nothing new. The tests now get their database from the shared PostgreSQL test container through `@SharedDatabase`, one database per test class, as described under Test database in the coding standards.
- **Deviations:** None.
- **Tests:** the test classes above moved to `@SharedDatabase`. Earlier notes that mention a container per test class or isolated Testcontainers describe the setup at that time and are historical.
- **Follow-up:** None.

## Changelog

| Version | Date       | Change                                                          | Issue |
| ------- | ---------- | --------------------------------------------------------------- | ----- |
| 1.0     | 2026-11-15 | First version                                                   | #14   |
| 1.1     | 2026-11-18 | Tests use the shared test container, older notes are historical | #48   |
