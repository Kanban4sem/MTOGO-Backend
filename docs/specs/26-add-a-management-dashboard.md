# Add a management dashboard

**Version:** 2.0 · **Date:** 2026-11-25 · **Issue:** #26 · **Feature flag:** None

## Acceptance criteria

Copied from #26. The issue is the source: change the issue first, then this copy.

- [ ] Only admins can open the dashboard. Other roles get 403, and requests without a login get 401
- [ ] With `SEED_ADMIN_PASSWORD` set, `admin@mtogo.example` is seeded, can log in and gets the ADMIN role
- [ ] The dashboard shows each figure for all of MTOGO and for every restaurant, with whether that restaurant accepts orders
- [ ] It shows the number of open orders, in total and per status (Placed, Accepted, Ready, PickedUp)
- [ ] It shows the orders per 24 hours: orders placed in the period divided by its days
- [ ] It shows the number of orders rejected in the period
- [ ] It shows the sales of orders delivered in the period
- [ ] It shows the average delivery, acceptance, preparation, pickup and transit time in whole minutes, or no value when there is none
- [ ] The period is 1–365 days, 7 by default. Any other value gives 400
- [ ] ~~With ordering disabled, the dashboard answers 404~~ Removed by #44, which removes the ordering flag

## Behaviour

1. An admin is a user with the admin role. An admin has no restaurant.
2. On every startup, the backend creates the seeded admin when `SEED_ADMIN_PASSWORD` is set, the same way as the other seeded users in [#52](52-seed-restaurant-and-delivery-agent-users.md):

   | Email                 | Role  | Restaurant | Name        | Phone         | Seed password         |
   | --------------------- | ----- | ---------- | ----------- | ------------- | --------------------- |
   | `admin@mtogo.example` | ADMIN | None       | MTOGO Admin | `+4500000005` | `SEED_ADMIN_PASSWORD` |

3. The seeded admin is active, prefers Danish and has no profile address.
4. The seeded admin can log in with their email and the admin seed password, and gets the admin role.
5. When `SEED_ADMIN_PASSWORD` is unset or blank, no admin is created, and the backend logs that it skipped the admin.
6. When `SEED_ADMIN_PASSWORD` breaks the password rules, the backend does not start, and no seeded user of any role is created.
7. An existing user with the admin's email is left unchanged.
8. Prod sets no admin seed password, so prod has no admin with a password known from the repository. A prod deployment refuses to run while prod sets one.
9. A customer who registers still gets the customer role.

10. An admin can open the dashboard.
11. A customer, a restaurant manager and a delivery agent cannot open the dashboard. They get 403.
12. A request without a valid login cannot open the dashboard. It gets 401.
13. The dashboard does not depend on any feature flag.

14. The admin picks the period as a number of days from 1 to 365. Without a choice, the period is 7 days.
15. A period of 0, of more than 365, or that is not a whole number, is refused with 400.
16. The period is the time from now back exactly that many times 24 hours. An event exactly on that edge is in the period.

17. The dashboard shows the figures once for all of MTOGO and once for every restaurant.
18. Every restaurant is listed, also one that doesn't accept orders and one with no orders, sorted by name. Each row shows whether the restaurant accepts orders.
19. **Open orders** is the number of open orders right now (Placed, Accepted, Ready or PickedUp), whatever the period.
20. **Open orders per status** is the number of open orders right now in each of Placed, Accepted, Ready and PickedUp. All four statuses are always shown, also with 0.
21. **Orders per 24 hours** is the number of orders placed in the period divided by its days, rounded half up to 1 decimal. Rejected orders count.
22. **Rejected orders** is the number of orders rejected in the period.
23. **Sales** is the sum of the order totals of the orders delivered in the period, in DKK with 2 decimals. A rejected order never counts.
24. **Average delivery time** is the average delivery time of the orders delivered in the period.
25. **Average acceptance time** is the average acceptance time of the orders accepted in the period. A rejected order has no acceptance time.
26. **Average preparation time** is the average preparation time of the orders that became ready in the period.
27. **Average pickup time** is the average pickup time of the orders picked up in the period.
28. **Average transit time** is the average transit time of the orders delivered in the period.
29. A time is counted in the period its step ended in, even when the order was placed before the period.
30. Each average time is shown in whole minutes, rounded half up. When no order counts towards it, it has no value.
31. With no orders, the counts are 0, orders per 24 hours is 0.0, sales is 0.00 and every average time has no value.
32. For all of MTOGO, the counts and sales equal the sums over the restaurant rows. The averages are taken over all of MTOGO's orders, not over the restaurant rows' averages.
33. The step averages cover different orders, so they don't have to add up to the average delivery time.

## Implementation decisions

- **Role:** the role feature gets `ADMIN`. A versioned Flyway migration inserts the `ADMIN` row into `roles` and changes nothing else. The existing rule that only a restaurant manager has a restaurant already covers the admin.
- **Seeding:** the seeded users in the user feature's seed service get the admin from behaviour 2. `app.seed.admin-password`, bound from `SEED_ADMIN_PASSWORD` and defaulting to empty, is added to the seed properties next to the other roles' passwords. It is added to `.env.example` and the droplet's `backend.env.example` with the note to leave it empty on prod. `deploy.sh`'s prod guard already matches every `SEED_*_PASSWORD`, so it needs no change. Follows [ADR-006](../adr/006-seeded-user-credentials.md).
- **Feature placement:** the dashboard lives in the order feature, because every figure is an order figure. An admin dashboard controller in its presentation layer and a dashboard service in its domain layer, following the per-role controllers for restaurant managers and delivery agents. The service reads the orders, their status changes and the restaurants through the order and restaurant features' repositories, and returns DTOs, following [ADR-004](../adr/004-backend-structure.md).
- **Calculation:** the database only selects the orders the dashboard needs: the open ones and those with a status change in the period, with their status changes. The dashboard service decides the figures from the orders' current status and status changes. Each step's time is the difference between the two status changes that start and end it. The service adds every order to its restaurant's row and to the total, so the total's averages are taken over all orders (behaviour 32). Rounding to minutes happens after averaging.
- **Time:** a `Clock` bean in the order feature (system clock, UTC) gives the dashboard service "now". Tests replace it with a fixed clock. How orders record their status times doesn't change.
- **API:** `GET /v1/admin/dashboard?days=<1–365>`, with `days` optional and 7 by default.

  ```json
  {
    "days": 7,
    "total": {
      "openOrders": 12,
      "openOrdersByStatus": {
        "PLACED": 2,
        "ACCEPTED": 5,
        "READY": 1,
        "PICKED_UP": 4
      },
      "ordersPer24Hours": 34.6,
      "rejectedOrders": 3,
      "sales": 18432.5,
      "averageDeliveryTimeMinutes": 45,
      "averageStepMinutes": {
        "acceptance": 4,
        "preparation": 18,
        "pickup": 6,
        "transit": 15
      }
    },
    "restaurants": [
      {
        "id": 2,
        "name": "MTOGO Bakery",
        "acceptsOrders": true,
        "openOrders": 3,
        "...": "the same figures as total"
      }
    ]
  }
  ```

  `sales` is a decimal with 2 places. Each minutes field is a whole number or `null`. Status codes: 200; 400 for an invalid `days` as a problem detail; 401 without a valid token; 403 for every role except admin.

- **Access:** the endpoint requires the admin role through method security, and `SecurityConfig` restricts `/v1/admin/**` to the admin role, like the other role scopes.
- **Feature flag:** none. The dashboard followed the ordering flag until #44 removed it. Seeding the admin doesn't depend on any flag (ADR-006).
- **Glossary:** settled in the grilling: Admin, Restaurant manager (renamed from restaurant user), Open order, Delivery time, Dashboard, Figure, Orders per 24 hours, Sales, Revenue, Acceptance time, Preparation time, Pickup time and Transit time.
- **Shared documents:** the minimum viable product's account decision says every user has exactly one role: customer, restaurant, delivery agent or admin. Its _Not in the MVP_ item 4 says an admin opens the dashboard. Kanban4sem/MTOGO-Frontend#16 copies the glossary and minimum viable product changes.
- **Domain model:** Role lists `ADMIN`, and the class note says admins have no restaurant.
- **Documentation:** the _Seeded users_ table in `docs/local-development.md` gets the admin row and the variable, and the README's feature list gets the dashboard.
- **Frontend contract:** Kanban4sem/MTOGO-Frontend#16 builds the views against this API. It offers the periods day (1), week (7), month (30), quarter (90), half year (182) and year (365), with week by default, and shows "–" for a figure with no value.

## Testing decisions

All database tests use `@SharedDatabase`, real collaborators and no mocks.

- **Dashboard service, figures method:** EP over all six order statuses for open orders and open orders per status. Rule set `count-open-orders`. Catches a final status counted as open, or an open status missing from the per-status counts. Prior art: `OrderServiceTest`.
- **Dashboard service, figures method:** BVA at the period's edge with a fixed clock: 1 µs before the edge, on the edge, and 1 µs inside, at 1 and 365 days. Covers orders per 24 hours, rejected orders, sales, delivery time and each step time, each measured at its own status time. Rule set `dashboard-window`. Catches an off-by-one period and a figure measured at the wrong status time.
- **Dashboard service, figures method:** half-up rounding of orders per 24 hours to 1 decimal and of each average to whole minutes, below, on and above the half. Rule set `average-rounding`.
- **Dashboard service, scenarios:**
  - no orders at all, matching behaviour 31
  - a restaurant with no orders
  - a rejected order counted in orders per 24 hours and as rejected, but in no time and not in sales
  - open orders left out of every time and of sales
  - the total's counts and sales equal the sums of the rows, and its averages are weighted over all orders
  - restaurants sorted by name, with one that doesn't accept orders
  - a step that ended in the period for an order placed before it
- **Admin dashboard endpoint, HTTP:** a decision table over anonymous, customer, restaurant manager, delivery agent and admin, against 200, 401 and 403. Rule set `dashboard-access`. Prior art: `DeliveryAgentOrderControllerTest` with `access.csv`.
- **Admin dashboard endpoint, HTTP:** BVA on `days` at 0, 1, 365 and 366, plus a non-numeric value and a missing value, which means 7. Rule set `dashboard-days`.
- **Admin dashboard endpoint, HTTP:** one scenario checks the full JSON shape of a response with orders, including `null` minutes and all four statuses at 0.
- **Seeding:** the existing rule sets get an admin column or rows, so behaviours 2–8 are covered the same way as the other roles:
  - `seed-roles` and `seed-lengths` in `UserSeedServiceTest`
  - `seeded-login` in `UserSeedInitializerTest`, where the admin logs in and `GET /v1/users/me` reports ADMIN
  - `bind-seed-passwords` in `AppPropertiesTest`
  - `restaurant-link` in `UserTest`, where an admin is accepted without a restaurant and refused with one
  - the persisted-state scenario in `UserSeedServiceTest`, which also checks the admin

  Behaviour 8 is shown by `seed-roles` with the variable empty, since `deploy.sh`'s guard already covers every seed password.

- **Schema:** the migration's `ADMIN` role row is covered by the seeding and login tests. It needs no separate schema test.

## Out of scope

- Renaming restaurant user to restaurant manager in the code (#90, Kanban4sem/MTOGO-Frontend#60). This spec uses the new glossary term.
- Orders per channel on the dashboard (#91, Kanban4sem/MTOGO-Frontend#61).
- MTOGO's revenue on the dashboard, after #23 calculates the commission (#92, Kanban4sem/MTOGO-Frontend#62).
- A list of orders for admins to browse and filter (#93, Kanban4sem/MTOGO-Frontend#63).
- Restaurant managers editing their menu (#94, Kanban4sem/MTOGO-Frontend#64), applying to add a restaurant (#95, Kanban4sem/MTOGO-Frontend#65) and managing their restaurant (#96, Kanban4sem/MTOGO-Frontend#66).
- Admins deactivating restaurants and seeing their managers' contact details (#97, Kanban4sem/MTOGO-Frontend#67), and reactivation requests (#98, Kanban4sem/MTOGO-Frontend#68).
- All-time figures, calendar-day periods and time zones. The period always ends now.
- Live updates. The dashboard is calculated when it is requested.
- Restaurant chains, and a restaurant manager with more than one restaurant.

## Further notes

Admins keep MTOGO in order but don't run restaurants. Restaurants own their details, menu and whether they accept orders, and admins approve and deactivate them (#95–#98). This issue only gives admins read access to figures.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-25 - #26

- **Built:** `V9__add_admin_role.sql` inserts the `ADMIN` role, and `RoleName` gets `ADMIN`. `UserSeedService` seeds `admin@mtogo.example` (MTOGO Admin, `+4500000005`, Danish, no address, no restaurant) from `app.seed.admin-password`, which `AppProperties.Seed` binds from `SEED_ADMIN_PASSWORD`. The variable is in `.env.example`, `deploy/backend.env.example` and the _Seeded users_ table in `docs/local-development.md`, and the README lists the dashboard among the backend's features, and the test configuration sets it empty. `GET /v1/admin/dashboard?days=` is served by `AdminDashboardController` in the order feature, with `@Min(1) @Max(365)` on `days` (default 7) and `@PreAuthorize("hasRole('ADMIN')")`. `SecurityConfig` restricts `/v1/admin/**` to `ADMIN`, and `OrderGateConfig` adds a gate on `/v1/admin/dashboard` only. `DashboardService.getDashboard(days)` takes "now" from the new `Clock` bean in the order feature's `ClockConfig` (system clock, UTC), truncated to microseconds. `OrderRepository.findAllByStatusInOrChangedBetween` loads the open orders and the orders with a status change between `from` and `until`, with their status changes, and `RestaurantRepository` lists every restaurant sorted by name and id. `OrderStatus.isOpen()` decides which statuses are open, `Order.getChangedAt(status)` gives when an order first reached a status, and a `Step` enum in the service names each time's starting and ending status. The service counts a figure when its ending status change lies in the period, both edges included, and adds every order to its restaurant's row and to the total, so the total's averages are over all orders. Orders per 24 hours is rounded half up to 1 decimal, and each average to whole minutes half up after averaging. `RestaurantFiguresDTO` unwraps its figures into the row with `@JsonUnwrapped`, so a row has the same fields as `total` plus `id`, `name` and `acceptsOrders`. `docs/domain-model.md` lists `ADMIN` and notes that admins have no restaurant. The minimum viable product, version 2.0, lists the admin role and says an admin opens the dashboard.
- **Deviations:** The rule-set files start with the method or endpoint action, as the coding standards require, so the spec's `count-open-orders`, `dashboard-window`, `average-rounding`, `dashboard-access` and `dashboard-days` are `get-dashboard-open-orders`, `get-dashboard-window`, `get-dashboard-rounding`, `get-access` and `get-days`. `AdminDashboardControllerTest` starts its disabled and unset backends itself instead of copying `DeliveryAgentOrderControllerTest`'s `@Nested` class, so the whole access decision table, the unset flag included, stays one `@ParameterizedTest` over one CSV file. The service tests' `expected` values are figures rather than `ACCEPT`, which the coding standards don't yet allow; a change to the rule is proposed in a separate issue.
- **Tests:** `DashboardServiceTest`, with a fixed clock, with `order/dashboard-service/get-dashboard-open-orders.csv`, `get-dashboard-window.csv` (each figure at 1 µs before, on and 1 µs inside the edge of 1 and 365 days) and `get-dashboard-rounding.csv`, plus the scenarios from the spec: no orders, a restaurant with no orders, a rejected order, open orders, the total against the rows, sorting with a restaurant that doesn't accept orders, and a step of an order placed before the period. `OrderTestData.orderAt` inserts an order with a status change at each given time. `AdminDashboardControllerTest`, with a fixed clock, with `order/admin-dashboard-controller/get-access.csv`, which starts one extra backend each for ordering disabled and unset, `get-days.csv`, and a full-JSON scenario that checks `null` minutes, all four statuses at 0 and the decimal places of `sales` and `ordersPer24Hours`. Seeding: admin rows or columns in `seed-roles.csv`, `seed-lengths.csv`, `seeded-login.csv`, `bind-seed-passwords.csv` and `restaurant-link.csv`, and the admin in `UserSeedServiceTest`'s persisted-state, existing-user and second-run scenarios and in `UserSeedInitializerTest`. Moving the period's edge by 1 µs failed the 16 on-edge rows, also after the calculation moved from SQL to the service, and moving the gate's path failed the 10 disabled and unset rows.
- **Follow-up:** None

### Spec 2.0 - 2026-11-25 - #44

- **Built:** The dashboard no longer follows the ordering flag, which #44 removed. Its gate in OrderGateConfig is gone with that class, so `GET /v1/admin/dashboard` never answers 404, and its OpenAPI description no longer lists one.
- **Deviations:** #26 is closed, so its criterion about disabled ordering is struck through here rather than changed on the issue first.
- **Tests:** `get-access.csv` keeps the five enabled rows without the `ordering` column, and `AdminDashboardControllerTest` no longer starts extra backends for ordering disabled and unset. It and `DashboardServiceTest` no longer set `FEATURE_ORDERING_ENABLED`, and `AdminDashboardControllerTest` uses `@AuthenticatedEndpointTest`.
- **Follow-up:** None

## Changelog

| Version | Date       | Change                                            | Issue |
| ------- | ---------- | ------------------------------------------------- | ----- |
| 1.0     | 2026-11-25 | First version                                     | #26   |
| 2.0     | 2026-11-25 | The dashboard no longer follows the ordering flag | #44   |
