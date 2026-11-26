# Calculate commission and service fee

**Version:** 1.0 · **Date:** 2026-11-26 · **Issue:** #23 · **Feature flag:** None

## Acceptance criteria

Copied from #23. The issue is the source: change the issue first, then this copy.

- [x] The team has accepted ADR-009 on the commission model and the service fee
- [x] When an order is delivered, its commission (marginal brackets on the order value excluding VAT, rounded once to øre) and service fee are stored with it. Orders that aren't delivered have neither
- [x] Orders delivered before this change get them from the default scale
- [x] A restaurant's own scale applies when it has one. Otherwise, the sales tier with the highest minimum that its sales in the 30 days before the delivery reach applies
- [x] Admins can view and replace the sales tiers, and give, change or remove a restaurant's own scale. An invalid scale gets 400, an unknown restaurant 404, other roles 403 and no login 401
- [x] Changing a scale never changes amounts already stored
- [x] The calculation is tested at every bracket boundary, and the tier choice at every minimum and at the edge of the 30 days

## Behaviour

1. When an order moves to Delivered, its commission and service fee are calculated and stored with it, in the same transaction.
2. An order that isn't delivered (Placed, Accepted, Rejected, Ready or PickedUp) has no commission and no service fee.
3. The order value excluding VAT is the order total divided by 1.25. It is not rounded.
4. The commission is calculated with marginal commission brackets: each part of the order value excluding VAT pays the rate of the bracket it falls in. A bracket runs from its lower limit up to the next bracket's lower limit. The last bracket has no upper limit.
5. With the default brackets, an order total of 125.00 DKK (100.00 excluding VAT) gets 6.00 DKK commission, and 1,250.00 DKK (1,000.00 excluding VAT) gets 46.00 DKK.
6. The commission is rounded once, half up, to 2 decimals, after the brackets are added up. No bracket's part is rounded.
7. The service fee is the commission scale's service fee, whatever the order value.
8. An order with a total of 0.00 DKK gets 0.00 DKK commission and the full service fee.

9. A restaurant with its own commission scale gets that scale, whatever its sales.
10. A restaurant without its own scale gets the sales tier with the highest minimum that its sales reach. Sales exactly at a minimum reach it.
11. A restaurant's sales for the tier are the sum of the order totals of its orders delivered in the 30 days before the delivery. An order delivered exactly 30 days before is counted.
12. The order being delivered doesn't count towards its own sales. Rejected orders and orders that aren't delivered never count.
13. A restaurant whose sales reach no higher tier gets the default scale, which is the sales tier from 0 DKK.
14. A scale is looked up at the moment of delivery. A change to a scale applies to every order delivered after it, including orders placed before it.
15. A change to a scale, a tier or a restaurant's own scale never changes the commission or service fee already stored on an order.

16. Every order that is Delivered when this change is deployed gets its commission and service fee from the default scale (6/5/4/3% from 0/100/500/1,000 DKK and a 5.00 DKK service fee), calculated as in behaviours 3–8.
17. Orders that aren't delivered at deployment get nothing.

18. A commission scale has 1 to 10 commission brackets and a service fee.
19. Each bracket has a lower limit in DKK excluding VAT and a rate in percent, both with at most 2 decimals.
20. The first bracket's lower limit is 0.00. Lower limits strictly ascend.
21. Each rate is from 0.00 to 100.00. A rate is never higher than the rate of the bracket before it.
22. The service fee is from 0.00 to 1,000.00 DKK, with at most 2 decimals.
23. A scale that breaks behaviours 18–22 is refused with 400, and nothing is changed.

24. There are 1 to 5 sales tiers. Each is a commission scale with a minimum sales amount in DKK, with at most 2 decimals.
25. The first tier's minimum is 0.00. It is the default scale. Minimums strictly ascend.
26. Higher tiers don't have to be cheaper than lower ones.
27. A tier list that breaks behaviours 24–25, or that has a tier whose scale breaks behaviours 18–22, is refused with 400, and the tiers are left unchanged.
28. Before any admin changes them, there is one tier: the default scale from behaviour 16.

29. An admin can see every sales tier, sorted by minimum, and every restaurant that has its own scale, sorted by restaurant name.
30. An admin can replace the whole list of sales tiers in one request. Changing the default scale or its service fee is done the same way, by changing the first tier.
31. An admin can give a restaurant its own scale. When the restaurant already has one, it is replaced.
32. An admin can remove a restaurant's own scale. The restaurant then gets the sales tiers again. Removing it when the restaurant has none succeeds and changes nothing.
33. Giving or removing a scale for a restaurant that doesn't exist is refused with 404.
34. A customer, a restaurant manager and a delivery agent can't see or change scales. They get 403.
35. A request without a valid login can't see or change scales. It gets 401.

## Implementation decisions

- **ADR:** [ADR-009](../adr/009-commission-and-service-fee.md) decides the commission model, the service fee, the commission scales and sales tiers, and storing the amounts at delivery.
- **Commission feature:** a new `commission` feature following [ADR-004](../adr/004-backend-structure.md):
  - Domain: a commission scale entity with its commission brackets, which calculates the commission and service fee for an order total (behaviours 3–8). Plain domain logic, without Spring or the database.
  - A commission service that returns the scale for a restaurant given its sales (behaviours 9–13), replaces the tiers, and gives, replaces and removes a restaurant's own scale. Before it saves anything, it checks the rules that compare brackets (behaviours 20–21) and tiers (behaviour 25) with each other, following the coding standards' rule that rules combining several fields belong in the service.
  - Presentation: an admin commission controller with the endpoints below.
- **Dependency direction:** the order feature depends on the commission feature, never the other way. When an order is delivered, the order service works out the restaurant's sales from the order feature's data and asks the commission service for the amounts (`calculate(restaurantId, sales, orderTotal)`), so the scale entity stays inside the commission feature. The commission feature reads no orders.
- **Delivery hook:** the order service's move to Delivered calculates the amounts and sets them on the order before the transaction commits. The order entity sets them only when it is Delivered, and they can't be changed afterwards.
- **Sales query:** one sum query in the order repository over the restaurant's orders whose Delivered status change lies in the 30 days ending at "now", with both ends inclusive. The amounts are calculated before the order moves to Delivered, because Hibernate would otherwise flush a delivered order without amounts before the sales query, which the database refuses. The order isn't delivered yet at that point, so it doesn't count towards its own sales and the query needs no condition that leaves it out. "Now" comes from the order feature's `Clock` bean, like the dashboard. Every status change records its time from the same clock, so the move to Delivered records the time the window ends at, and tests use a fixed clock.
- **Schema:** one versioned Flyway migration:
  - `commission_scales`: id, nullable unique `restaurant_id` referencing `restaurants` (deleted with its restaurant, which the app never does but the test data's restaurant cleanup needs), nullable `minimum_sales` NUMERIC(12,2) ≥ 0, `service_fee` NUMERIC(6,2) from 0 to 1,000. A check that exactly one of `restaurant_id` and `minimum_sales` is set. `minimum_sales` is unique.
  - `commission_brackets`: id, `scale_id` referencing `commission_scales` (deleted with its scale), `lower_limit` NUMERIC(10,2) ≥ 0, `rate_percent` NUMERIC(5,2) from 0 to 100, unique per scale and lower limit.
  - The default tier: minimum 0.00, service fee 5.00, brackets 0.00 → 6.00, 100.00 → 5.00, 500.00 → 4.00 and 1,000.00 → 3.00.
  - `orders` gets nullable `commission` and `service_fee` NUMERIC(10,2) ≥ 0, with a check that both are set exactly when the status is `DELIVERED`.
  - The backfill sets both on every delivered order with the default scale's maths in SQL (behaviour 16). PostgreSQL's `round` on positive numerics is half up.
- **API:** all under `/v1/admin/commission-scales`, each method with `@PreAuthorize("hasRole('ADMIN')")`. A scale has the same shape wherever it appears, in requests and responses:

  ```json
  {
    "serviceFee": 5.0,
    "brackets": [
      { "lowerLimit": 0.0, "ratePercent": 6.0 },
      { "lowerLimit": 100.0, "ratePercent": 5.0 },
      { "lowerLimit": 500.0, "ratePercent": 4.0 },
      { "lowerLimit": 1000.0, "ratePercent": 3.0 }
    ]
  }
  ```

  - `GET /v1/admin/commission-scales` → 200 with `{ "tiers": [{ "minimumSales": 0.00, "scale": {…} }], "restaurants": [{ "restaurantId": 2, "restaurantName": "…", "scale": {…} }] }`.
  - `PUT /v1/admin/commission-scales/tiers` with `{ "tiers": [{ "minimumSales": …, "scale": {…} }] }` → 200 with the saved tiers, in the same shape as the `tiers` field above. 400 for an invalid list.
  - `PUT /v1/admin/commission-scales/restaurants/{id}` with a scale → 200 with the restaurant's scale (`restaurantId`, `restaurantName`, `scale`). 400 for an invalid scale, 404 for an unknown restaurant.
  - `DELETE /v1/admin/commission-scales/restaurants/{id}` → 204, also when the restaurant has no scale of its own. 404 for an unknown restaurant.
  - Every endpoint: 401 without a valid token, 403 for every role except admin. Errors are problem details.
  - Amounts and rates are decimals with 2 places. The request DTOs use Bean Validation for the rules on a single field: required fields, the number of brackets (behaviour 18) and tiers (behaviour 24), each field's range (behaviours 21–22, lower limits and minimums from 0.00) and its digits. The rules that compare brackets or tiers with each other belong to the commission service and give 400 through `BadRequestException`.

- **Feature flag:** none. The calculation changes no existing API or screen, and the admin endpoints are complete when delivered.
- **Glossary:** settled in the grilling: Order value excluding VAT, Commission, Commission bracket, Service fee, Commission scale, Sales tier and Default scale. Revenue and Sales already exist.
- **Shared documents:** the glossary changes. Kanban4sem/MTOGO-Frontend#16 copies them.
- **Domain model:** `Order` gets `commission` and `serviceFee`. The draft `Commission` class is replaced by `CommissionScale` and `CommissionBracket`, with a restaurant's optional own scale.
- **Frontend contract:** Kanban4sem/MTOGO-Frontend#16 builds the commission views against this API.

## Testing decisions

All database tests use `@SharedDatabase`, real collaborators and no mocks.

- **Commission scale, domain, calculation:** BVA on the order total including VAT at every bracket edge excluding VAT and 1 DKK excluding VAT either side of it: 123.75, 125.00 and 126.25 (100 DKK), 623.75, 625.00 and 626.25 (500 DKK), 1,248.75, 1,250.00 and 1,251.25 (1,000 DKK). A step of 0.01 DKK including VAT would change the commission by less than half an øre, so a wrong rate next to the edge would round away. Also 0.00, a large total, and a total whose commission lands exactly on a half øre. Each row expects the commission and the service fee. Rule set `calculate-commission`. Catches the whole-order step, an off-by-one bracket, VAT applied the wrong way, and rounding per bracket.
- **Commission service, save a restaurant's scale:** EP and BVA for behaviours 20–21 and 23:
  - the first lower limit at −0.01, 0.00 and 0.01, and no brackets at all
  - a lower limit equal to, 0.01 above and below the one before it
  - a rate 0.01 above, equal to and 0.01 below the one before it

  Rule set `save-restaurant-scale`, with `expected` `ACCEPT` or `BadRequestException`. Each invalid row also checks that nothing is saved.

- **Order service, deliver:** a decision table over own scale (yes or no) × sales (below, at or above the second tier's minimum, each 0.01 apart). `expected` names the scale whose amounts are stored. Rule set `deliver-scale-choice`.
- **Order service, deliver:** BVA on both edges of the 30-day window with a fixed clock: another order delivered 1 µs outside, exactly on, and 1 µs inside each edge, with `expected` as above. Rule set `deliver-sales-window`.
- **Order service, status changes:** EP over every move the lifecycle allows. Only the move to Delivered stores amounts, and every other status leaves them empty (`expected` `NONE`). Rule set `deliver-amounts`. Prior art: `OrderServiceTest`.
- **Commission service, replace tiers:** EP and BVA for behaviours 25 and 27: the first minimum at 0.00 and 0.01, no tiers at all, a minimum equal to, 0.01 above and below the one before it, and one tier with an invalid scale. Each invalid row also checks the tiers are unchanged. Rule set `replace-tiers`.
- **Scenarios** (service level):
  - a rejected order and an open order don't count towards sales
  - the order being delivered doesn't count towards its own sales
  - changing a tier, replacing a restaurant's own scale and removing it don't change amounts already stored
  - after its own scale is removed, a restaurant gets the tiers again
  - giving a restaurant a scale when it already has one replaces it
- **Admin commission endpoints, HTTP:** a decision table per endpoint over anonymous, customer, restaurant manager, delivery agent and admin, against 2xx, 401 and 403. Rule sets `get-access`, `put-tiers-access`, `put-restaurant-scale-access` and `delete-restaurant-scale-access`. Prior art: `AdminDashboardControllerTest` with `get-access.csv`.
- **Admin commission endpoints, HTTP:** EP and BVA on the request DTOs. Each row names a field and its value:
  - every required field and list element missing or null
  - 0, 1, 10 and 11 brackets, and 0, 1, 5 and 6 tiers
  - the service fee at −0.01, 0.00, 1,000.00 and 1,000.01, a rate at −0.01, 0.00, 100.00 and 100.01, and a lower limit and a minimum at −0.01
  - a lower limit with 8 and 9 digits before the decimal point, a minimum with 10 and 11, and each amount with 3 decimals
  - a tier whose scale breaks a field rule

  Rule sets `put-tiers-body` and `put-restaurant-scale-body`. One invalid scale per domain rule set proves the 400 mapping.

- **Admin commission endpoints, HTTP:** path IDs: an unknown restaurant gives 404 on `PUT` and `DELETE`. `DELETE` on a restaurant without its own scale gives 204. One scenario checks the full JSON shape of `GET` with two tiers and two restaurant scales, sorted by name.
- **Migration:** a test named after the migration checks that the default tier is seeded, that delivered orders get the default scale's amounts (including one at a bracket edge), and that orders that aren't delivered stay empty. Rule set `migrate-backfill`. The check constraint refuses amounts on an order that isn't delivered. Prior art: the existing migration tests in the order feature.

## Out of scope

- Revenue on the admin dashboard (#92, Kanban4sem/MTOGO-Frontend#62).
- Restaurant managers seeing the commission and service fee of their orders (#101, Kanban4sem/MTOGO-Frontend#71).
- The delivery agent bonus, which builds on the revenue (#25).
- A history or audit trail of scale changes. The amounts stored on each order are the record.
- Tiers assigned by a scheduled job. The tier is worked out at each delivery.
- Invoicing restaurants or paying out.
- VAT rates other than 25%, and VAT on the commission or service fee itself.

## Further notes

The service fee of 5.00 DKK is the team's choice, since the case gives no amount. Small orders carry most of the fee: on a 100 DKK order (80 DKK excluding VAT), the restaurant pays 4.80 DKK commission and 5.00 DKK service fee.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-26 - #23

- **Built:** A new `commission` feature. The request DTOs check the rules on a single field with Bean Validation: required fields, the number of brackets and tiers, ranges and digits. A commission scale and its brackets keep amounts and rates with 2 decimals, and a bracket's rate is named `ratePercent` in every layer. A commission scale calculates the commission and service fee for an order total. The commission service gives the amounts for a restaurant from its own scale or the sales tier its sales reach. Before it saves a scale, it checks that the brackets start at 0.00, ascend and never raise the rate, and that the tiers' minimums start at 0.00 and ascend, with one check shared by brackets and tiers. It lets admins view and replace the sales tiers and give, replace or remove a restaurant's own scale through four admin endpoints under `/v1/admin/commission-scales`. A tier and a restaurant's own scale carry their scale as a nested `scale` object, in the same shape as the request body for a restaurant's scale. Every status change records its time from the order feature's clock. When an order is delivered, the order service sums the restaurant's sales in the 30 days before the delivery time, both ends included, asks the commission service for the amounts before the move to Delivered, and stores them on the order, which refuses amounts unless it is delivered and has none yet. Migration V10 creates the scales, seeds the default scale, adds the amounts to orders, backfills delivered orders and makes the database refuse amounts on any order that isn't delivered.
- **Deviations:** None
- **Tests:** `CommissionScaleTest` (`calculate-commission.csv`, 1 DKK excluding VAT either side of each bracket edge), `OrderTest` (`record-commission.csv`), `CommissionServiceTest` (`save-restaurant-scale.csv`, `replace-tiers.csv`, plus replacing a restaurant's existing scale), `OrderServiceTest` with a fixed clock (`deliver-scale-choice.csv`, `deliver-sales-window.csv` on both edges, `deliver-amounts.csv`, plus scenarios for orders that don't count, the order being delivered, stored amounts that never change after a tier change and a restaurant's own scale being replaced and removed, and removing a restaurant's own scale), `AdminCommissionControllerTest` (`get-access.csv`, `put-tiers-access.csv`, `put-restaurant-scale-access.csv`, `delete-restaurant-scale-access.csv`, `put-tiers-body.csv`, `put-restaurant-scale-body.csv`, `put-restaurant-scale-path.csv`, `delete-restaurant-scale-path.csv`, plus the full JSON of `GET`) and `AddCommissionScalesTest` (`migrate-backfill.csv`, plus the seeded default tier and the check constraint), which runs the migration in a schema of its own so the backfill runs on orders that exist before it. `CommissionTestData` is the base for the tests that change scales: it restores the scales the migration seeds before each test, and builds scales and tiers from the short forms the tests use. The order test data gives delivered test orders amounts.
- **Follow-up:** None

## Changelog

| Version | Date       | Change        | Issue |
| ------- | ---------- | ------------- | ----- |
| 1.0     | 2026-11-26 | First version | #23   |
