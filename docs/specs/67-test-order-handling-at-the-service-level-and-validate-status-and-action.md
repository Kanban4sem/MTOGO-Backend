# Test order handling at the service level and validate status and action

**Version:** 1.1 · **Date:** 2026-11-25 · **Issue:** #67 · **Feature flag:** None

## Acceptance criteria

Copied from #67. The issue is the source: change the issue first, then this copy.

- [ ] `OrderServiceTest` has a parameterized rule set, with CSV data, for each of customer read, restaurant status change, delivery action and the delivery agent's order list
- [ ] `DeliveryAgentOrderControllerTest` has no `@Test` that checks more than one decision outcome
- [ ] A restaurant status that is unknown, numeric, missing or not settable by a restaurant, and a delivery action that is unknown, numeric or missing, are rejected by request validation with 400, and `OrderService` has no switch on strings
- [ ] Every remaining branch in `OrderService` is reachable and tested

## Behaviour

No behaviour changes for valid requests. This list records the existing rules that the new tests pin down.

1. A customer can read their own order.
2. A customer cannot read another customer's order: it answers 404, the same as an order that does not exist.
3. A restaurant user can accept or reject a Placed order of their restaurant.
4. A restaurant user can mark an Accepted order of their restaurant as Ready.
5. A restaurant user cannot make any other move between order statuses: it answers 409, and no status change is recorded.
6. A restaurant user cannot change the status of another restaurant's order, or of an order that does not exist: it answers 404.
7. A restaurant user can only request Accepted, Rejected or Ready. Any other order status, an unknown value, a number or a missing status answers 400 before the order is read.
8. A delivery agent can take a Ready order that no delivery agent has taken.
9. A delivery agent cannot take an order that is not Ready, or that is already taken, by themselves or by another delivery agent: it answers 409.
10. A delivery agent can pick up a Ready order they have taken, and deliver a PickedUp order they have taken.
11. A delivery agent cannot pick up or deliver an order they have not taken, whether no one or another delivery agent has taken it: it answers 404.
12. A delivery agent cannot pick up or deliver their own order when its order status does not allow the move: it answers 409.
13. A delivery action on an order that does not exist answers 404.
14. A delivery action that is unknown, a number or missing answers 400.
15. A delivery agent's list shows the Ready orders that no delivery agent has taken, and every order the delivery agent has taken, whatever its order status.
16. A delivery agent's list does not show orders that are not yet Ready or were rejected, or orders another delivery agent has taken.

## Implementation decisions

- This issue adds no flag-disabled tests. #44 has since removed the ordering feature flag and its existing flag-disabled tests.
- **Restaurant status request:** the `status` field of the restaurant's status request is typed as **Order status**. A Bean Validation rule on the request allows only the statuses a restaurant user may set (Accepted, Rejected, Ready), backed by a method on the order status that says whether a restaurant sets it. The OpenAPI schema keeps listing only those three values. The service also refuses any other status with a 400 before it reads the order, so the rule holds without the request, and then moves the order to the requested status with no switch.
- **Delivery action request:** a new enum `DeliveryAction` (TAKE, PICK_UP, DELIVER) in the order domain types the `action` field, which stays required. The service switches on the enum exhaustively, with no `default` and no `null` case.
- **Enum numbers:** Jackson is configured for the whole application to refuse numbers for enum fields, so `{"status": 1}` keeps answering 400 instead of being read as the second constant. No other request DTO uses an enum yet.
- **Malformed values:** an unknown or numeric enum value fails while the request body is read and answers 400 with Spring's generic problem detail. The previous details ("A restaurant can only set an order to …", "Invalid delivery action") go away. The frontend sends only valid values, so users never see this detail.
- **Customer lookup:** an authenticated customer always exists, so placing an order reads the customer without the "Customer not found" 404. A broken invariant would surface as a 500.
- **Restaurant lookup:** a restaurant user always has a restaurant (enforced when the role is assigned), and only restaurant users reach the restaurant endpoints. The service reads the user's restaurant directly, without the "No restaurant is assigned to this account" 404. A broken invariant would surface as a 500. The `GET /v1/restaurant/orders` 404 description drops "or the account has no restaurant".
- **API contract:** paths, status codes and accepted values are unchanged, so the frontend needs no change.

## Testing decisions

Service seam, `OrderServiceTest`, with one `@ParameterizedTest` per rule set. Prior art: the `place-*` rule sets in the same class.

- **Customer read**, `get-customer-order-access`. Decision table on whose order it is: own → `ACCEPT`, another customer's → `NotFoundException`, unknown → `NotFoundException`.
- **Restaurant status change**, `update-restaurant-order-status-transition`. Decision table with the columns `order` (OWN, OTHER, UNKNOWN), `currentStatus` and `requestedStatus`. It holds the full matrix of the 6 current statuses × Accepted, Rejected and Ready for an own order (3 `ACCEPT`, 15 `ConflictException`), plus another restaurant's Placed order and an unknown order → `NotFoundException`, plus Placed requested on a Placed order, PickedUp on a Ready order and Delivered on a PickedUp order → `BadRequestException`. `ACCEPT` rows check the new status and the added status change. 23 rows.
- **Delivery action**, `update-delivery-agent-order-action`. Decision table with the columns `action`, `status` and `assignedTo` (NONE, ME, OTHER), leaving out impossible combinations: a taken order is always Ready or later. `ACCEPT` rows check the status and the assigned delivery agent. 17 rows:
  - TAKE: unassigned Placed, Accepted or Rejected → `ConflictException`; unassigned Ready → `ACCEPT`; Ready taken by me or by another agent → `ConflictException`; unknown order → `NotFoundException`.
  - PICK_UP: Ready, unassigned or taken by another agent → `NotFoundException`; Ready taken by me → `ACCEPT`; PickedUp or Delivered taken by me → `ConflictException`.
  - DELIVER: Ready, unassigned, or PickedUp taken by another agent → `NotFoundException`; Ready or Delivered taken by me → `ConflictException`; PickedUp taken by me → `ACCEPT`.
- **Delivery agent's order list**, `get-delivery-agent-orders-visibility`. Decision table with the columns `status` and `assignedTo`. `expected` is `LISTED` or `NOT_LISTED`. A list query throws nothing, so `ACCEPT` or an exception name from the coding standards does not fit, and the values name the outcome instead. Unassigned Placed, Accepted, Rejected and Ready (only Ready is listed); taken by me Ready, PickedUp and Delivered (all listed); taken by another agent Ready and PickedUp (not listed). 9 rows.

- **Placing an order**, `place-delivery-address`, gains a row: a profile address that is not the default → `BadRequestException`. With it, every branch in `OrderService` is tested.

Endpoint seam, `RestaurantOrderControllerTest`:

- `update-status-fields` replaces `update-status-transition`. EP over the Bean Validation rule on `status`: Accepted → 200; Placed, PickedUp and Delivered → 400; unknown, lowercase `accepted`, numeric and missing → 400. One 409 row (Placed → Ready) proves the error mapping for the service rule set.
- `update-status-access` is unchanged.

Endpoint seam, `DeliveryAgentOrderControllerTest`:

- `access` keeps its rows. TAKE on a Placed order → 409 is the one invalid case for the delivery action rule set. It adds 400 rows for a numeric action and lowercase `take`.
- `deliverReadyOrder` becomes one happy-path `@Test`: one delivery agent takes, picks up and delivers a Ready order. It checks the 200 responses, the mapped fields and the recorded status changes, with no 404 or 409 checks and no second agent.
- `takeConcurrently` is unchanged.

Endpoint seam, `CustomerOrderControllerTest`: `get-by-id-access` is unchanged. It is the access decision table plus EP on the path ID.

## Out of scope

- Changing the outcome of picking up or delivering an order no one has taken (404 today, arguably 409). The grilling kept today's behaviour, and no follow-up issue was created.
- Ownership of the customer's order list: `CustomerOrderControllerTest` already covers it.
- New feature-flag tests: #44 removes the ordering flag.

## Further notes

The glossary gains **Delivery action**. The glossary is shared with MTOGO-Frontend, so the linked issue Kanban4sem/MTOGO-Frontend#59 copies the term and records the API change for the frontend.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-24 - #67

- **Built:** The restaurant's status request types `status` as OrderStatus. An `@AssertTrue` rule on UpdateRestaurantOrderStatusDTO lets through only statuses for which `OrderStatus.isSetByRestaurant()` is true (Accepted, Rejected, Ready), and the order service refuses any other status with `BadRequestException` before it reads the order, then moves the order to the requested status with no switch. The new DeliveryAction enum (TAKE, PICK_UP, DELIVER) in the order domain types the delivery agent's `action`, and the order service switches on it with no `default` or `null` case. `spring.jackson.datatype.enum.fail-on-numbers-for-enums=true` makes an enum field refuse numbers across the application. Placing an order reads the customer without the "Customer not found" 404. The order service reads a restaurant user's restaurant directly, without the "No restaurant is assigned to this account" 404, and the `GET /v1/restaurant/orders` 404 description no longer mentions it. The OpenAPI schema still lists only ACCEPTED, REJECTED and READY for `status`, and TAKE, PICK_UP and DELIVER for `action`, both required.
- **Deviations:** None. The existing flag-disabled tests and the "or ordering is disabled" 404 descriptions stay until #44 removes the flag. In `update-delivery-agent-order-action`, an order that does not exist is the row with status `NULL`.
- **Tests:** OrderServiceTest: `get-customer-order-access`, `update-restaurant-order-status-transition` (23 rows), `update-delivery-agent-order-action` (17 rows) and `get-delivery-agent-orders-visibility` (9 rows, `LISTED` or `NOT_LISTED`). `place-delivery-address` gains a row for a profile address that is not the default. RestaurantOrderControllerTest: `update-status-fields` replaces `update-status-transition`, with numeric `1` (the position of ACCEPTED) and one 409 row. DeliveryAgentOrderControllerTest: `access` adds lowercase `take` and numeric `0` (the position of TAKE) → 400, and `deliverReadyOrder` is one happy path for a single agent. OrderTestData now holds the helpers for restaurant users, delivery agents, order status, assigned agent and status history.
- **Follow-up:** Kanban4sem/MTOGO-Frontend#59 copies the glossary term and records the API change.

## Changelog

| Version | Date       | Change                               | Issue |
| ------- | ---------- | ------------------------------------ | ----- |
| 1.0     | 2026-11-24 | First version                        | #67   |
| 1.1     | 2026-11-25 | The ordering feature flag is removed | #44   |
