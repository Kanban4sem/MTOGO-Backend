# Glossary

The domain language of MTOGO. Code, issues, specs and documentation call each concept by the name recorded here, as described in [CONTRIBUTING](../CONTRIBUTING.md#language). Terms are added while an issue is grilled.

## Language

### Ordering

**Order**:
A customer's paid request for menu items from one restaurant, delivered to one delivery address.
_Avoid_: Purchase, transaction, basket

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

**Order channel**:
The way an order reached MTOGO: website, app or telephone.
_Avoid_: Source, platform, origin
