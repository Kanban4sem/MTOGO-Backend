# Calculate bonuses for delivery agents

**Version:** 1.0 · **Date:** 2026-11-27 · **Issue:** #25 · **Feature flag:** None

## Acceptance criteria

Copied from #25. The issue is the source.

- [ ] The team has accepted the bonus ADR
- [x] An agent's bonus for a period is calculated from their deliveries, hours and ratings
- [x] The total bonus never exceeds the cap from the ADR

## Behaviour

1. A bonus period is a calendar month in Europe/Copenhagen. It starts at local midnight on the first day of the month inclusive and ends at local midnight on the first day of the next month exclusive.
2. An order counts once if its recorded move to Delivered is in the period and it is assigned to the requesting agent. Orders without an assigned agent or recorded delivery time earn no bonus.
3. Each eligible order starts with a reward of 0.75% of its order value excluding VAT. The order value excluding VAT is the order total divided by 1.25.
4. A delivery before 08:00 or at or after 22:00 Copenhagen time adds 0.25 DKK. The recorded delivery time determines the supplement.
5. A delivery rating of 5 adds 0.15 DKK. A rating of 6 adds 0.30 DKK. Ratings from 1 to 4 and absent feedback add zero. Food and overall ratings have no effect.
6. The reward is rounded once to whole øre, half up. The cap is 25% of the stored commission on that order, rounded down to whole øre. The delivery bonus is the smaller amount.
7. The monthly bonus is the sum of the capped amounts. With no eligible deliveries it is 0.00 DKK. An eligible zero-commission order still counts as a delivery.
8. Feedback submitted after the end of a month affects the bonus for the delivery month.
9. A delivery agent can read their own calculation for the current or a past month. An omitted month selects the current month in Copenhagen. A malformed or future month returns 400. An unauthenticated request returns 401 and other roles get 403.

## Implementation decisions

- [ADR-011](../adr/011-delivery-bonus.md) defines the formula, the cap and when the bonus is calculated. The order feature reads order totals, stored commissions, delivery times and optional delivery ratings in one query. The service calculates the bonus without writes.
- `GET /v1/delivery-agent/bonuses?month=2026-11` returns `month` as `YYYY-MM`, `currency` as `DKK`, `deliveredOrders` as an integer and `bonusAmount` as a decimal in DKK with two places. The token identifies the agent.
- The glossary defines Delivery bonus, Bonus period and Bonus cap. A linked frontend issue can carry the dashboard work and the shared glossary update.
- Proposed domain model update for review. Replace the draft stored Bonus with a calculated delivery bonus derived from the order's delivery agent, delivery time, stored commission and optional delivery rating.

## Testing decisions

- HTTP tests with PostgreSQL cover calculation and ownership through the public endpoint. `calculate-bonus` uses boundary values at 08:00 and 22:00, rating partitions, zero commission, the binding cap and separate rounding for the reward and the cap.
- `get-access` covers all roles and unauthenticated requests. `get-month` covers explicit current and past months, future months, malformed months and zero totals. A scenario covers exact period boundaries, Copenhagen's current month, open orders and deliveries assigned to someone else. Another scenario verifies that late feedback updates the delivery month.

## Out of scope

- Frontend dashboard, payroll approval, payouts, history of bonus rules, and recording hours worked.

## Further notes

A 125.00 DKK order with 6.00 DKK commission earns 0.75 DKK in daytime without a rating, or 1.30 DKK at 22:00 with a delivery rating of 6. If the stored commission is 3.00 DKK, either delivery earns at most 0.75 DKK.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-27 - #25

- **Built:** The delivery agent can read a monthly delivery count and bonus from existing order and feedback records. The calculation uses Copenhagen time and caps each delivery before summing.
- **Deviations:** None.
- **Tests:** `DeliveryBonusControllerTest` with `get-access`, `get-month` and `calculate-bonus`, plus scenarios for month boundaries, ownership and late feedback.
- **Follow-up:** Proposed frontend issue for the dashboard and glossary sync.

## Changelog

| Version | Date       | Change        | Issue |
| ------- | ---------- | ------------- | ----- |
| 1.0     | 2026-11-27 | First version | #25   |
