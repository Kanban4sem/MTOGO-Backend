# Place and pay for an order

**Version:** 5.1 · **Date:** 2026-11-25 · **Issue:** #15 · **Feature flag:** None

## Acceptance criteria

Copied from #15, #66, #46 and #44. The issues are the source: change the issue first, then this copy.

From #15:

- [x] A customer can order menu items to a delivery address
- [x] The order total is calculated from the menu prices
- [x] An order is placed only when the mocked payment is confirmed
- [x] The order records its channel and when it was placed

From #66:

- [x] Every order status has a Danish and an English name
- [x] Placing an order, the customer's order list and detail, the restaurant's orders and status change, and the delivery agent's orders and updates all return the status's Danish and English name as `statusName.danish` and `statusName.english`
- [x] The customer's order list and order detail include the order's delivery address
- [x] The delivery agent's orders include the order lines and the order total
- [x] The delivery agent's orders no longer include `deliveryCity`, and still return `assignedToMe`, backed by the Java field `isAssignedToMe`
- [x] Endpoint tests cover the status names and the new fields on every order response

From #46:

- [x] Placing an order returns a `Location` header with the order's URL
- [x] A GET on that URL returns the placed order

From #44:

- [x] A customer can place an order without `FEATURE_ORDERING_ENABLED` set
- [x] `GET /v1/features` no longer returns `orderingEnabled`
- [x] No code or configuration refers to the flag

## Behaviour

1. A logged-in customer can place an order with order lines, a payment token and either a newly entered delivery address or their complete default delivery address.
2. An order has menu items from exactly one restaurant. An order with menu items from more than one restaurant is refused.
3. An order has 1–20 order lines. Each order line has one menu item and a quantity of 1–20. A menu item appears on at most one order line in the same order.
4. A customer can only order available menu items on an active menu of a restaurant that accepts orders. An unknown menu item is refused, and so is an unavailable item, an item on an inactive menu or an item from a restaurant that does not accept orders.
5. Each order line copies the menu item's name and unit price when the order is placed. A later change to the menu item does not change the order.
6. The order total is the sum of unit price times quantity over the order lines, in DKK. The customer never sends prices.
7. A delivery address has a street, a four-digit postal code, a city and the country Denmark. A delivery address without a street, postal code or city is refused, and so is a postal code that is not exactly four digits.
8. The order is placed only when the mocked payment of the order total is confirmed. A declined payment creates no order, and the customer is told the payment was declined.
9. The mocked payment declines the payment token `decline` and confirms any other payment token that is not blank.
10. A placed order has the order status Placed and records when it was placed.
11. A placed order records its channel. Orders placed through the API have the channel website.
12. A placed order belongs to the customer who placed it and to the restaurant of its menu items.
13. An order moves only along the lifecycle in the [MVP](../minimum-viable-product.md#order-status-lifecycle): Placed to Accepted or Rejected, Accepted to Ready, Ready to PickedUp and PickedUp to Delivered. Any other move is refused. Each move records when the new order status was set.
14. Only customers can place orders. Anonymous users, restaurants and delivery agents cannot.
15. A menu item that has been ordered cannot be deleted. It is taken off the menu by making it unavailable.
16. Ordering is always available. No feature flag hides the order endpoints.
17. A customer can save a newly entered delivery address as their profile default after successful payment. A one-time address or a declined payment leaves the profile as it was.
18. A customer can reuse their complete default without entering any address fields. A profile without a street, including a registration or seeded address with only a postal area, requires the customer to enter a delivery address.
19. Each order keeps its own delivery address when the customer changes the profile default.
20. A delivery agent sees the complete delivery address for every ready, unassigned order and every order assigned to them. Taking an order removes it from other agents' lists.
21. Every order status has a Danish and an English status name: Placed is "Bestilt" and "Placed", Accepted is "Accepteret" and "Accepted", Rejected is "Afvist" and "Rejected", Ready is "Klar til afhentning" and "Ready for pickup", PickedUp is "Afhentet" and "Picked up", and Delivered is "Leveret" and "Delivered".
22. Every order response returns the order status together with both its status names: placing an order, the customer's order list and order detail, the restaurant user's orders and status change, and the delivery agent's orders and the response to taking, picking up or delivering an order.
23. A customer sees the delivery address recorded on each of their orders in their order list and order detail, not their current profile default.
24. A delivery agent sees the order lines and the order total of every order they can see: every ready, unassigned order and every order assigned to them.
25. A delivery agent's order gives the city only as part of the delivery address, and still tells the agent whether the order is assigned to them.
26. When a customer places an order, the response gives them the order's URL in the `Location` header. The customer can read the order by sending an authenticated GET to that URL.

## Implementation decisions

- **Feature:** a new order feature following [ADR-004](../adr/004-backend-structure.md). It owns Order, OrderLine, the status history (OrderStatusChange), the OrderStatus and OrderChannel enums, the order service, controller, mapper and DTOs. Delivery addresses reuse the address feature's Address and PostalCode. The order service reads menu items through the restaurant feature's repositories.
- **Payment:** an IPaymentProvider interface in the order feature with one implementation, MockPaymentProvider. This is the concrete need ADR-004 asks for before adding an interface: a real provider replaces the implementation without changing the order flow, as the MVP decides. `charge(total, paymentToken)` returns confirmed with a made-up reference, or declined. The reference and the payment are not stored; an order exists only for a confirmed payment.
- **Flow:** one request validates the input, loads the menu items, checks unknown and repeated menu items, then that all items come from one restaurant, then that every item is orderable, calculates the order total, charges the IPaymentProvider and saves the order in one transaction. A declined payment throws before anything is saved.
- **Order status:** OrderStatus is an enum with English codes PLACED, ACCEPTED, REJECTED, READY, PICKED_UP and DELIVERED. The database and the place-order response use these codes. Order has the current status and a `moveTo(status)` method that applies the lifecycle and appends an OrderStatusChange with the time it was set. Placing an order records the PLACED entry, which is the time the order was placed. No endpoint moves an order in this issue; #16 and #17 call `moveTo`.
- **Channel:** OrderChannel is an enum with WEBSITE, APP and TELEPHONE. The backend sets WEBSITE; the client does not send it.
- **API:** `POST /v1/orders` with PlaceOrderDTO: `lines` (array of `menuItemId`, `quantity`), `deliveryAddress` (`street`, `postalCode`, `city`) and `paymentToken`. Bean Validation: 1–20 lines, a positive menu item ID, quantity 1–20, street and city not blank and at most 100 characters, postal code matching exactly four digits, payment token not blank. It returns `201` with OrderDTO: `id`, `restaurantId`, `status`, `channel`, `placedAt`, `total`, `deliveryAddress` (`street`, `postalCode`, `city`, `country`) and `lines` (`menuItemId`, `name`, `unitPrice`, `quantity`, `subtotal`). The `Location` header contains the relative URL `/v1/customer/orders/{id}` for the order.
- **Address selection:** `deliveryAddress` is required for manual entry. Send `useDefaultAddress: true` and omit `deliveryAddress` to reuse the saved address. Send `saveAsDefault: true` with a newly entered address to save it. Both flags default to false. Invalid combinations and a missing complete default return `400`.
- **Errors:** ProblemDetail through the existing GlobalExceptionHandler. `400` for invalid input, unknown or repeated menu items and menu items from more than one restaurant. `401` without a valid token. `402` for a declined payment, from a new PaymentRequiredException with the detail "The payment was declined". `403` for users who are not customers. `409` for an unavailable item, an inactive menu or a restaurant that does not accept orders.
- **Access:** SecurityConfig restricts `POST /v1/orders` to CUSTOMER users, and the controller declares the same with `@PreAuthorize`. The order's customer is the authenticated user, never a request field.
- **Schema:** one Flyway migration adds a nullable `street` column to addresses (existing profile addresses have none) and creates orders, order lines and order status changes. Orders reference the customer, the restaurant and their own delivery address row, and store the order total as NUMERIC(10, 2), the channel and the current status. Order lines reference the order and the menu item with a restricting foreign key, and copy the name and the unit price as NUMERIC(10, 2), with a check that quantity is 1–20 and the unit price is not negative. Order status changes reference the order and store the status and when it was set. The delivery address stores the country as `Denmark`.
- **Flag:** none. #15 released ordering behind a temporary `orderingEnabled` flag, and #44 removed it as described in the [coding standards](../coding-standards.md#feature-flags). `GET /v1/features` no longer reports it, and the order endpoints have no FeatureGate. Placing an order requires a customer token, and authentication has no flag since #31.
- **Default address:** checkout stores the default in the existing profile address and stores the delivery address separately on each order. Saving the default and placing the order share one transaction. #61 adds no feature flag.
- **Delivery agent list:** `GET /v1/delivery-agent/orders` returns the order's `deliveryAddress` with street, postal code, city and country for every item in the existing agent-scoped list.
- **Status names:** OrderStatus holds a Danish and an English status name next to each code. Every order response DTO (OrderDTO, CustomerOrderSummaryDTO, CustomerOrderDTO, RestaurantOrderDTO and DeliveryAgentOrderDTO) returns `status` (the code) and `statusName` as a StatusNameDTO object `{ "danish": "Bestilt", "english": "Placed" }`. It replaces the Danish `statusName` string from #18. The keys are words, not language codes, so a later language is one more key. This covers `POST /v1/orders`, `GET /v1/customer/orders` and `/{id}`, `GET` and `PATCH /v1/restaurant/orders`, and `GET` and `PATCH /v1/delivery-agent/orders`.
- **Customer order reads:** CustomerOrderSummaryDTO and CustomerOrderDTO add `deliveryAddress` (`street`, `postalCode`, `city`, `country`), taken from the order's own address.
- **Delivery agent order:** DeliveryAgentOrderDTO adds `lines` (the existing OrderLineDTO) and `total`, and drops `deliveryCity`, which repeated `deliveryAddress.city`. Its boolean is the Java field `isAssignedToMe`, mapped to the unchanged JSON name `assignedToMe` with `@JsonProperty`, like the other response booleans. The PATCH response uses the same DTO.
- **Mapper:** OrderMapper builds DeliveryAddressDTO, StatusNameDTO and the order lines in one place each, and every order DTO uses them.
- **Contract:** removing `deliveryCity` and turning `statusName` into an object break the contract, so #66 ships together with Kanban4sem/MTOGO-Frontend#50. Swagger describes StatusNameDTO and the new fields with `@Schema`.
- **Flag for #66:** none of its own. The changed endpoints were hidden behind `orderingEnabled` until #44 removed it.
- **Flag for #46:** none.
- **ADRs:** [ADR-001](../adr/001-feature-flags.md), [ADR-004](../adr/004-backend-structure.md) and [ADR-005](../adr/005-account-authentication.md) apply. No new ADR: the mocked payment behind an interface is decided in the MVP.

## Testing decisions

- **OrderTest** (plain JUnit): `moveTo` as a decision table over the current and the requested order status; `move-to-transitions`. The six allowed moves succeed and record a status change; the rest are refused.
- **MockPaymentProviderTest** (plain JUnit): `charge` with EP over the payment token, `decline`, blank and any other; `charge-token`.
- **OrderServiceTest** (`@SpringBootTest`, `@SharedDatabase`): `place` with EP over the menu items: unknown, repeated, unavailable, inactive menu, restaurant not accepting orders, items from two restaurants and a valid order; `place-items`. `place` with EP over the payment outcome, where a declined payment leaves no order, order line or address; `place-payment`. Ordinary `@Test` methods cover the copied name and unit price, the order total over several order lines, the channel WEBSITE, the Placed entry and its time, the owning customer and restaurant, and that a later menu price change leaves the order unchanged.
- **OrderControllerTest** (REST Assured, `@SharedDatabase`): EP and BVA on PlaceOrderDTO, with 0/1/2/19/20/21 lines and quantities, a menu item ID of 0 and -1, street and city blank, 100 and 101 characters, postal code `123`, `1234`, `12345` and `12a4`, and a blank payment token; `place-fields`. Access as a decision table, with anonymous 401, restaurant and delivery agent 403, customer 201; `place-access`. One case each proves the 400, 402 and 409 error mapping. Copy the setup from UserControllerTest and RestaurantControllerTest.
- **OrderSchemaTest** (`@DataJpaTest`, `@SharedDatabase`): deleting a menu item that an order line references is refused. Copy RestaurantSchemaTest.
- **OrderServiceTest** covers the checkout address decision table in `place-delivery-address`. **OrderControllerTest** verifies that a seeded customer can save, read, reuse and replace a default while earlier orders retain their addresses.
- **FeatureGateTest** (plain JUnit): `isClosedFor` as a decision table over the flag and the request path; `is-closed-for`.
- **FeatureFlagControllerTest** and **FeatureFlagServiceTest** (#44): `GET /v1/features` no longer reports `orderingEnabled`. Since #31 it reports no flag. The order controller tests leave `FEATURE_ORDERING_ENABLED` unset, so their happy paths show that a customer can order without it.
- **CustomerOrderControllerTest** (#66): `get-current-status` has the columns `status`, `danishName`, `englishName`, `expected` (the HTTP status code) and `reason`, so EP over the six order statuses checks both status names on the order list and the order detail. The get-all and get-by-id happy paths change the profile default after placing and check that `deliveryAddress` is still the order's own address.
- **OrderControllerTest** (#66): the place happy path checks `statusName.danish` and `statusName.english` for Placed.
- **OrderControllerTest** (#46) checks the `Location` URL against the ID returned by `POST /v1/orders` and follows it with the same customer token to verify the order can be read.
- **RestaurantOrderControllerTest** (#66): the get-all happy path checks both status names, and every successful row of `update-status-transition` checks both names of the new status on the PATCH response.
- **DeliveryAgentOrderControllerTest** (#66): the delivery happy path checks both status names at Ready, PickedUp and Delivered across the list and the PATCH responses, `lines` and `total`, `assignedToMe`, and that `deliveryCity` is absent. The status names are a mapping, so they need no further CSV files and no domain test.
- CSV files live under `testcases/order/<class-under-test>/`, `testcases/featureflag/feature-gate/` and `testcases/config/app-properties/`.

## Out of scope

- Frontend views, the basket and the preview total: Kanban4sem/MTOGO-Frontend#8. The contract and the glossary terms are in a comment on that issue.
- Moving an order after Placed: #16 and #17.
- Storing payments, refunds and pay on delivery.
- Validating postal codes against the real Danish list: #45.
- Delivery fees, service fees and commission: #23.
- The frontend side of #66: Kanban4sem/MTOGO-Frontend#50.

## Further notes

Issue #18 exposed a Danish `statusName` alongside the status code on customer order reads. #66 turned it into a Danish and English status name on every order response. The domain model was updated for orders during the grilling.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-17 - #15

- **Built:** The order feature. A customer places an order with `POST /v1/orders`, which SecurityConfig restricts to CUSTOMER users. The order service refuses repeated and unknown menu items, menu items from more than one restaurant, and menu items that aren't orderable. OrderLine copies each menu item's name and unit price in `OrderLine.of`, and Order sums the order total. It charges the total through IPaymentProvider, and only a confirmed payment saves the order, with channel WEBSITE and a Placed status change. The order service gives the delivery address the country Denmark and leaves it non-default; the mapper only converts. MenuItem answers its restaurant and whether it is on an active menu. Order owns the lifecycle in `moveTo`, OrderStatus holds the allowed moves, and the time comes from the status change. MockPaymentProvider declines `decline` and blank tokens. The new PaymentRequiredException gives `402`. Migration V4 adds `addresses.street` and the orders, order lines and order status changes tables, with a restricting foreign key from order lines to menu items. The `orderingEnabled` flag is in AppProperties, `application.properties`, `.env.example`, `deploy/backend.env.example`, FeatureFlagsDTO and the README. With the flag off, FeatureGateFilter answers `404` for the order endpoint before authentication and validation, so the endpoint does not exist for anyone. Each feature declares a FeatureGate bean for its path (OrderGateConfig, and UserGateConfig for the account endpoints, which moved out of JwtAuthenticationFilter), so the filters never change for a new flag. The order service doesn't check the flag itself. The coding standards list PaymentRequiredException and the FeatureGate step (6.1).
- **Deviations:** Three names in this spec broke the coding standards. The developer agreed to correct them in place at 1.0, since the spec isn't on `main`: IPaymentProvider, PaymentRequiredException and OrderTest. The flag-off cases are a nested test class rather than rows in `place-access.csv`, because the flag is read at startup and needs its own application context. An unset flag is tested in AppPropertiesTest, which binds it to off in isolation from a local `.env`; the endpoint tests pin the flag so a developer's `.env` can't change them. A delivery address is saved with `is_default` false, because the default flag belongs to profile addresses. The glossary calls the channel "Order channel", matching OrderChannel.
- **Tests:** FeatureGateTest (`is-closed-for`), OrderTest (`move-to-transitions`), MockPaymentProviderTest (`charge-token`), OrderServiceTest (`place-items`, `place-payment`, plus the copied name and unit price, the order total, a later price change, channel, Placed time, owners and delivery address). OrderControllerTest (`place-fields`, including a menu item ID of 0 and -1, `place-access`, the 400, 402 and 409 mapping, the response contract, and the flag off for every role and an invalid body in `WhenOrderingIsDisabled`, `place-disabled`). OrderSchemaTest (an ordered menu item can't be deleted, an unordered one can). AppPropertiesTest (`bind-ordering-enabled`), FeatureFlagServiceTest and FeatureFlagControllerTest.
- **Follow-up:** None.

### Spec 1.1 - 2026-11-18 - #48

- **Built:** nothing new. The tests now get their database from the shared PostgreSQL test container through `@SharedDatabase`, one database per test class, as described under Test database in the coding standards.
- **Deviations:** None.
- **Tests:** the test classes above moved to `@SharedDatabase`. The nested `WhenOrderingIsDisabled` class uses OrderControllerTest's database, so its check that no order was saved looks at the database the endpoint writes to. Earlier notes that mention a container per test class or isolated Testcontainers describe the setup at that time and are historical.
- **Follow-up:** None.

### Spec 1.2 - 2026-11-21 - #18

- **Built:** Customer order reads use the existing order status codes and add a Danish display name. The order placement contract still returns the status code.
- **Deviations:** The earlier note anticipated frontend translations. #18 supplies the Danish names in the read API.
- **Tests:** `CustomerOrderControllerTest` covers customer reads and the Danish name for each status.
- **Follow-up:** Kanban4sem/MTOGO-Frontend#11 uses the read API. #46 adds the `Location` header to order placement.

### Spec 2.0 - 2026-11-22 - #61

- **Built:** checkout accepts a one-time address or the authenticated customer's saved default and can save an entered address after successful payment. Profile and order addresses use the existing schema. Delivery agents see the complete delivery address on ready, unassigned orders and on their own orders.
- **Deviations:** None.
- **Tests:** `OrderServiceTest` (`place-delivery-address`) and the checkout flow in `OrderControllerTest`. `DeliveryAgentOrderControllerTest` checks the full address before assignment and that another agent loses access after assignment. Test execution is left to the developer.
- **Follow-up:** frontend checkout integration.

### Spec 3.0 - 2026-11-24 - #66

- **Built:** OrderStatus holds a Danish and an English status name for each status. OrderMapper builds StatusNameDTO, DeliveryAddressDTO and the order lines in one private method each, and every order DTO uses them. OrderDTO, CustomerOrderSummaryDTO, CustomerOrderDTO, RestaurantOrderDTO and DeliveryAgentOrderDTO return `statusName` as `{ "danish", "english" }`. The customer's order list and detail return the order's own `deliveryAddress`. DeliveryAgentOrderDTO adds `total` and `lines`, drops `deliveryCity`, and keeps the JSON name `assignedToMe` for the Java field `isAssignedToMe` with `@JsonProperty`. `statusName` on every order DTO, `danish`, `english` and the new fields carry `@Schema` descriptions. The README and `customerOrders.http` describe the new fields.
- **Deviations:** None.
- **Tests:** CustomerOrderControllerTest (`get-current-status` with both names and `expected` 200 for all six statuses on the list and the detail; the get-all and get-by-id happy paths save the delivery address as the profile default, change the default after placing and check that `deliveryAddress` is still the order's own). OrderControllerTest (the place happy path checks both names for Placed). RestaurantOrderControllerTest (the get-all happy path checks both names, and every 200 row of `update-status-transition` checks both names of the new status on the PATCH response). DeliveryAgentOrderControllerTest (the delivery happy path checks both names at Ready, PickedUp and Delivered on the list and on the TAKE, PICK_UP and DELIVER responses, `lines` and `total` before and after taking, and `assignedToMe` false on the list and true on the TAKE response. `deliveryCity` and `isAssignedToMe` are checked absent on the Ready list and the TAKE response).
- **Follow-up:** None. The frontend side is Kanban4sem/MTOGO-Frontend#50.

### Spec 4.0 - 2026-11-25 - #46

- **Built:** `POST /v1/orders` includes the customer order URL in the `Location` header of its `201` response. OpenAPI and the README document the header.
- **Deviations:** None.
- **Tests:** The place happy path in OrderControllerTest verifies the relative URL and retrieves the order at that URL with the same customer token. Test execution is left to the developer.
- **Follow-up:** None.

### Spec 5.0 - 2026-11-25 - #44

- **Built:** The `orderingEnabled` flag is gone from AppProperties, `application.properties`, `.env.example`, `deploy/backend.env.example`, FeatureFlagsDTO, FeatureFlagService, `docs/local-development.md` and the `.http` files. OrderGateConfig is deleted, so the order endpoints and the admin dashboard from #26 have no FeatureGate (spec 26, version 2.0). The OpenAPI `404` responses no longer mention disabled ordering, and the order, restaurant, delivery agent and notification lists no longer document a `404`. FeatureGate, FeatureGateFilter and UserGateConfig stay for `authEnabled`. The coding standards name UserGateConfig as the FeatureGate example (9.2). Notes for earlier versions that mention the flag are historical.
- **Deviations:** None.
- **Tests:** The `WhenOrderingIsDisabled` classes in OrderControllerTest, CustomerOrderControllerTest, RestaurantOrderControllerTest and DeliveryAgentOrderControllerTest, their `*-disabled.csv` files and `bind-ordering-enabled` in AppPropertiesTest are removed. The order tests no longer set `FEATURE_ORDERING_ENABLED`. FeatureFlagServiceTest and FeatureFlagControllerTest check that `authEnabled` is the only flag. FeatureGateTest uses `/v1/users`, the path UserGateConfig gates, as its sample. The five order endpoint test classes share their startup properties through `@AuthenticatedEndpointTest` in `mtogo.testsupport`.
- **Follow-up:** `FEATURE_ORDERING_ENABLED` is removed from each environment's `.env` together with `FEATURE_AUTH_ENABLED` in #31. The frontend side is Kanban4sem/MTOGO-Frontend#34.

### Spec 5.1 - 2026-11-25 - #31

- **Built:** no change to ordering. #31 removed `authEnabled`, so placing an order needs a customer token but no flag, and `GET /v1/features` reports no flag. UserGateConfig is deleted, so the coding standards point to it in git history as the FeatureGate example (9.3).
- **Deviations:** None.
- **Tests:** FeatureFlagServiceTest and FeatureFlagControllerTest check that no flag is reported. The order endpoint tests no longer set `FEATURE_AUTH_ENABLED` through `@AuthenticatedEndpointTest`. The JWT signing key comes from the test `application.properties`. FeatureGateTest keeps `/v1/users` as sample data, though no gate is declared for it.
- **Follow-up:** None. Removing both flags from each environment's `.env` is tracked in spec 13's note for #31.

## Changelog

| Version | Date       | Change                                                          | Issue |
| ------- | ---------- | --------------------------------------------------------------- | ----- |
| 1.0     | 2026-11-16 | First version                                                   | #15   |
| 1.1     | 2026-11-18 | Tests use the shared test container, older notes are historical | #48   |
| 1.2     | 2026-11-21 | Clarify where Danish status names are returned                  | #18   |
| 2.0     | 2026-11-22 | Save, reuse and display delivery addresses                      | #61   |
| 3.0     | 2026-11-24 | Status names in Danish and English, complete order responses    | #66   |
| 4.0     | 2026-11-25 | Return the order URL in the `Location` header                   | #46   |
| 5.0     | 2026-11-25 | Remove the ordering feature flag                                | #44   |
| 5.1     | 2026-11-25 | Authentication has no flag                                      | #31   |
