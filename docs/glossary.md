# Glossary

The domain language of MTOGO. Code, issues, specs and documentation call each concept by the name recorded here, as described in [CONTRIBUTING](../CONTRIBUTING.md#language). Terms are added while an issue is grilled.

## Language

### Accounts

**Customer**:
A user with the customer role, who orders food from restaurants.
_Avoid_: Client, buyer, consumer

**Restaurant user**:
A user with the restaurant role, who handles orders for exactly one restaurant.
_Avoid_: Restaurant staff, restaurant owner, restaurant account

**Delivery agent**:
A user with the delivery-agent role, who collects ready orders and delivers them.
_Avoid_: Courier, driver, rider

### Ordering

**Order**:
A customer's paid request for menu items from one restaurant, delivered to one delivery address.
_Avoid_: Purchase, transaction, basket

**Basket**:
The menu items and quantities a customer has picked from one restaurant before placing an order.
_Avoid_: Cart, draft order

**Basket total**:
The sum of menu price times quantity over a basket, shown as a preview before paying. The order total is calculated when the order is placed.
_Avoid_: Order total, cart total

**Order line**:
One menu item on an order with its quantity, and the item's name and unit price as they were when the order was placed.
_Avoid_: Order item, cart item

**Order total**:
The sum of unit price times quantity over an order's lines, in DKK.
_Avoid_: Amount, sum, price

**Delivery address**:
The street, postal code, city and country an order is delivered to.
_Avoid_: Shipping address, location

**Order status**:
The point an order has reached in its lifecycle: Placed, Accepted, Rejected, Ready, PickedUp or Delivered.
_Avoid_: State, stage

**Status name**:
The Danish or English name of an order status that customers, restaurant users and delivery agents read, such as "Klar til afhentning" or "Ready for pickup".
_Avoid_: Label, display name, status text

**Order channel**:
The way an order reached MTOGO: website, app or telephone.
_Avoid_: Source, platform, origin
