# ADR-011 Delivery bonus

## Status

Proposed · [#25](https://github.com/Kanban4sem/MTOGO-Backend/issues/25)

## Context

The project brief asks MTOGO to reward employees for deliveries while keeping bonuses within revenue. Order totals include VAT, customers pay no delivery charge, and MTOGO earns commission and a service fee from restaurants. ADR-009 stores the commission at delivery. Order feedback supplies a delivery rating from 1 to 6. The order lifecycle records delivery times. It has no record of hours worked.

## Decision

- Calculate a delivery bonus for each order delivered by an assigned agent. Start with 0.75% of the order value excluding VAT. Add 0.25 DKK for delivery before 08:00 or from 22:00 local time. Add 0.15 DKK for a delivery rating of 5 or 0.30 DKK for 6.
- Limit each delivery bonus to 25% of its stored commission. Round this cap down to whole øre. Round the reward before the cap half up to whole øre. Sum the capped amounts for each agent and Copenhagen calendar month.
- Calculate the bonus from existing records on request. Feedback submitted later updates the month in which the order was delivered. The agent can read their own calculated bonus.

## Alternatives

- A shared company-wide budget would allow larger rewards where commissions are low. It would require allocating that budget across agents and recalculating one agent's bonus when another agent delivers.
- A cap including the service fee would make more revenue available for bonuses. Keeping the service fee outside the cap leaves it available for operating costs.
- Recording a fixed bonus at delivery would preserve historical amounts. Delivery ratings arrive after delivery, so that approach would also need an adjustment process.

## Consequences

- Across all agents, a period's bonuses consume at most 25% of the stored commission from eligible deliveries. The cap leaves at least 75% of that commission and all service fees before other costs. Actual profitability also depends on those costs.
- The size and timing of a delivered order and its delivery rating can all increase the bonus up to the cap. A zero-commission order earns zero.
- The current rules apply to historical deliveries with an assigned agent and a recorded delivery time. Previous months can change when feedback arrives. Payroll approval, settlement and versioning a future bonus policy need their own decisions.
