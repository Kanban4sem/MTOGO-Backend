# Glossary

The domain language of MTOGO. Code, issues, specs and documentation call each concept by the name recorded here, as described in [CONTRIBUTING](../CONTRIBUTING.md#language). Terms are added while an issue is grilled.

## Language

### Accounts

**Customer**:
A user with the customer role, who orders food from restaurants.
_Avoid_: Client, buyer, consumer

**Restaurant manager**:
A user with the restaurant role, who manages exactly one restaurant and handles its orders.
_Avoid_: Restaurant user, restaurant staff, restaurant owner, restaurant account

**Delivery agent**:
A user with the delivery-agent role, who collects ready orders and delivers them.
_Avoid_: Courier, driver, rider

**Admin**:
A user with the admin role, who looks after MTOGO as a whole, such as following operations on the dashboard.
_Avoid_: Sys admin, superuser, management

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
The Danish or English name of an order status that customers, restaurant managers, delivery agents and admins read, such as "Klar til afhentning" or "Ready for pickup".
_Avoid_: Label, display name, status text

**Order notification**:
A message for the customer when one of their orders changes status after placement. The customer can mark an in-app notification as read.

**Delivery action**:
What a delivery agent does to an order: take a ready order no one has taken, pick up their order at the restaurant, or deliver it.
_Avoid_: Claim, accept, collect

**Order channel**:
The way an order reached MTOGO: website, app or telephone.
_Avoid_: Source, platform, origin

**Open order**:
An order whose status is not final: Placed, Accepted, Ready or PickedUp. Rejected and Delivered are final.
_Avoid_: Active order, pending order, current order

**Delivery time**:
The time from an order being placed to it being delivered. Only a delivered order has one.
_Avoid_: Processing time, lead time, turnaround

### Operations

**Dashboard**:
The page that shows figures about MTOGO's operations, calculated from the orders.
_Avoid_: Overview, report, statistics page

**Figure**:
One number on the dashboard, such as the number of open orders.
_Avoid_: Metric, KPI, statistic, key figure

**Orders per 24 hours**:
The average number of orders placed per 24 hours over a recent period. Rejected orders count, because they were placed.
_Avoid_: Daily orders, order rate, orders per day

**Sales**:
The sum of order totals of the orders delivered in a period, in DKK.
_Avoid_: Revenue, turnover, income

**Revenue**:
What MTOGO earns from an order: its commission and service fee.
_Avoid_: Sales, profit, income

**Acceptance time**:
The time from an order being placed to the restaurant accepting it. A rejected order has none.
_Avoid_: Response time

**Preparation time**:
The time from an order being accepted to it being ready for pickup.
_Avoid_: Cooking time

**Pickup time**:
The time from an order being ready to a delivery agent picking it up.
_Avoid_: Waiting time

**Transit time**:
The time from an order being picked up to it being delivered.
_Avoid_: Delivery time, driving time
