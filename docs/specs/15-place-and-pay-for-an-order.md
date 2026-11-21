# Place and pay for an order

**Version:** 1.2 · **Date:** 2026-11-21 · **Issue:** #15 · **Feature flag:** `orderingEnabled`, off by default

## Acceptance criteria

Copied from #15. The issue is the source: change the issue first, then this copy.

- [x] A customer can order menu items to a delivery address
- [x] The order total is calculated from the menu prices
- [x] An order is placed only when the mocked payment is confirmed
- [x] The order records its channel and when it was placed

## Behaviour

1. A logged-in customer can place an order with order lines, a delivery address and a payment token.
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
16. When `orderingEnabled` is off, orders cannot be placed and the endpoint does not exist.

## Implementation decisions

- **Feature:** a new order feature following [ADR-004](../adr/004-backend-structure.md). It owns Order, OrderLine, the status history (OrderStatusChange), the OrderStatus and OrderChannel enums, the order service, controller, mapper and DTOs. Delivery addresses reuse the address feature's Address and PostalCode. The order service reads menu items through the restaurant feature's repositories.
- **Payment:** an IPaymentProvider interface in the order feature with one implementation, MockPaymentProvider. This is the concrete need ADR-004 asks for before adding an interface: a real provider replaces the implementation without changing the order flow, as the MVP decides. `charge(total, paymentToken)` returns confirmed with a made-up reference, or declined. The reference and the payment are not stored; an order exists only for a confirmed payment.
- **Flow:** one request validates the input, loads the menu items, checks unknown and repeated menu items, then that all items come from one restaurant, then that every item is orderable, calculates the order total, charges the IPaymentProvider and saves the order in one transaction. A declined payment throws before anything is saved.
- **Order status:** OrderStatus is an enum with English codes PLACED, ACCEPTED, REJECTED, READY, PICKED_UP and DELIVERED. The database and the place-order response use these codes. Order has the current status and a `moveTo(status)` method that applies the lifecycle and appends an OrderStatusChange with the time it was set. Placing an order records the PLACED entry, which is the time the order was placed. No endpoint moves an order in this issue; #16 and #17 call `moveTo`.
- **Channel:** OrderChannel is an enum with WEBSITE, APP and TELEPHONE. The backend sets WEBSITE; the client does not send it.
- **API:** `POST /v1/orders` with PlaceOrderDTO: `lines` (array of `menuItemId`, `quantity`), `deliveryAddress` (`street`, `postalCode`, `city`) and `paymentToken`. Bean Validation: 1–20 lines, a positive menu item ID, quantity 1–20, street and city not blank and at most 100 characters, postal code matching exactly four digits, payment token not blank. It returns `201` with OrderDTO: `id`, `restaurantId`, `status`, `channel`, `placedAt`, `total`, `deliveryAddress` (`street`, `postalCode`, `city`, `country`) and `lines` (`menuItemId`, `name`, `unitPrice`, `quantity`, `subtotal`). No `Location` header until #46.
- **Errors:** ProblemDetail through the existing GlobalExceptionHandler. `400` for invalid input, unknown or repeated menu items and menu items from more than one restaurant. `401` without a valid token. `402` for a declined payment, from a new PaymentRequiredException with the detail "The payment was declined". `403` for users who are not customers. `404` when `orderingEnabled` is off. `409` for an unavailable item, an inactive menu or a restaurant that does not accept orders.
- **Access:** SecurityConfig restricts `POST /v1/orders` to CUSTOMER users, and the controller declares the same with `@PreAuthorize`. The order's customer is the authenticated user, never a request field.
- **Schema:** one Flyway migration adds a nullable `street` column to addresses (existing profile addresses have none) and creates orders, order lines and order status changes. Orders reference the customer, the restaurant and their own delivery address row, and store the order total as NUMERIC(10, 2), the channel and the current status. Order lines reference the order and the menu item with a restricting foreign key, and copy the name and the unit price as NUMERIC(10, 2), with a check that quantity is 1–20 and the unit price is not negative. Order status changes reference the order and store the status and when it was set. The delivery address stores the country as `Denmark`.
- **Flag:** a new `orderingEnabled` flag (`FEATURE_ORDERING_ENABLED`, property `app.ordering-enabled`), off by default, in AppProperties, `application.properties`, `.env.example` and FeatureFlagsDTO, as described in the [coding standards](../coding-standards.md#feature-flags). `GET /v1/features` reports it. Placing an order also needs `authEnabled`, since it requires a customer token. The order feature declares a FeatureGate for `/v1/orders`, so FeatureGateFilter answers `404` before authentication while the flag is off. #44 removes the flag.
- **ADRs:** [ADR-001](../adr/001-feature-flags.md), [ADR-004](../adr/004-backend-structure.md) and [ADR-005](../adr/005-account-authentication.md) apply. No new ADR: the mocked payment behind an interface is decided in the MVP.

## Testing decisions

- **OrderTest** (plain JUnit): `moveTo` as a decision table over the current and the requested order status; `move-to-transitions`. The six allowed moves succeed and record a status change; the rest are refused.
- **MockPaymentProviderTest** (plain JUnit): `charge` with EP over the payment token, `decline`, blank and any other; `charge-token`.
- **OrderServiceTest** (`@SpringBootTest`, `@SharedDatabase`): `place` with EP over the menu items: unknown, repeated, unavailable, inactive menu, restaurant not accepting orders, items from two restaurants and a valid order; `place-items`. `place` with EP over the payment outcome, where a declined payment leaves no order, order line or address; `place-payment`. Ordinary `@Test` methods cover the copied name and unit price, the order total over several order lines, the channel WEBSITE, the Placed entry and its time, the owning customer and restaurant, and that a later menu price change leaves the order unchanged.
- **OrderControllerTest** (REST Assured, `@SharedDatabase`): EP and BVA on PlaceOrderDTO, with 0/1/2/19/20/21 lines and quantities, a menu item ID of 0 and -1, street and city blank, 100 and 101 characters, postal code `123`, `1234`, `12345` and `12a4`, and a blank payment token; `place-fields`. Access as a decision table, with anonymous 401, restaurant and delivery agent 403, customer 201 and the flag off 404; `place-access`. One case each proves the 400, 402 and 409 error mapping. Copy the setup from UserControllerTest and RestaurantControllerTest.
- **OrderSchemaTest** (`@DataJpaTest`, `@SharedDatabase`): deleting a menu item that an order line references is refused. Copy RestaurantSchemaTest.
- **AppPropertiesTest**: EP over `app.ordering-enabled`, unset, true, false and invalid; `bind-ordering-enabled`.
- **FeatureGateTest** (plain JUnit): `isClosedFor` as a decision table over the flag and the request path; `is-closed-for`.
- **FeatureFlagControllerTest** and **FeatureFlagServiceTest**: update them so `GET /v1/features` reports `orderingEnabled`.
- CSV files live under `testcases/order/<class-under-test>/`, `testcases/featureflag/feature-gate/` and `testcases/config/app-properties/`.

## Out of scope

- Frontend views, the basket and the preview total: Kanban4sem/MTOGO-Frontend#8. The contract and the glossary terms are in a comment on that issue.
- Reading orders and the `Location` header: #18 and #46.
- Moving an order after Placed: #16 and #17.
- Storing payments, refunds and pay on delivery.
- Validating postal codes against the real Danish list: #45.
- Delivery fees, service fees and commission: #23.
- Removing the flag: #44.

## Further notes

Issue #18 exposes a Danish `statusName` alongside the status code on customer order reads. The domain model was updated for orders during the grilling.

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

## Changelog

| Version | Date       | Change                                                          | Issue |
| ------- | ---------- | --------------------------------------------------------------- | ----- |
| 1.0     | 2026-11-16 | First version                                                   | #15   |
| 1.1     | 2026-11-18 | Tests use the shared test container, older notes are historical | #48   |
| 1.2     | 2026-11-21 | Clarify where Danish status names are returned                  | #18   |
