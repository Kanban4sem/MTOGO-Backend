# Domain Model

> [!IMPORTANT]
> This is a draft from before the project started, not a decision. It gives a shared starting point for grilling and specs. When an issue settles part of the domain, the glossary, the spec and the code win, and this diagram is updated to match.

The draft was made by Jonas and Jesper. Daniel and Dino have not approved it yet. It was moved here from `MTOGO/docs/brainstorm/domainmodel.md`. An online copy is on [mermaid.ai](https://mermaid.ai/d/4f7b7a63-16a6-4759-bc76-0d6c9b800673).

```mermaid
classDiagram
direction TB

%% ######################################################
%%                    User Domain
%% ######################################################

class User {
    +Long id
    +String email
    +boolean active
    +String password_hashed
}

class UserProfile {
    +Long id
    +String firstName
    +String lastName
    +String phoneNumber
    +String preferredLanguage
    +Long address
}

class Address {
    +Long id
    +Long postalCodeId
    +boolean isDefault
}

class PostalCode {
    +Long id
    +String city
    +String code
    +String country
}

%% ######################################################
%%                    Business Domain
%% ######################################################

class PaymentService {
    +Long id
    +String companyName
    +String registrationNumber
    +boolean active
}

class Restaurant {
    +Long id
    +String cuisineType
    +Integer estimatedPreparationMinutes
    +boolean acceptsOrders
    +Long address
}

class Menu {
    +Long id
    +String name
    +String description
    +boolean active
}

class MenuItem {
    +Long id
    +String name
    +String description
    +BigDecimal price
    +boolean available
}

%% ######################################################
%%                    Order Domain
%% ######################################################

class Order {
    +Long id
    +BigDecimal totalAmount
    +String orderChannel
    +Long address
    +placeOrder()
    +cancelOrder()
    +updateStatus()
}

class OrderItem {
    +Long id
    +String itemName
    +Integer quantity
    +BigDecimal unitPrice
    +calculateSubtotal()
}

class OrderStatus {
    +Long id
    +String code
    +String nameDa
    +String nameEn
}

class Payment {
    +Long id
    +BigDecimal amount
    +String paymentStatus
    +confirmPayment()
    +failPayment()
}

class Commission {
    +Long id
    +BigDecimal orderAmountExVat
    +BigDecimal commissionAmount
    +BigDecimal serviceFee
    +calculateTotalFee()
}

%% ######################################################
%%                    Employee Domain
%% ######################################################

class Roles {
    +Long id
    +String name
    +String description
}

class DeliveryAgent {
    +Long id
    +String employeeNumber
    +String vehicleType
    +boolean available
}

class Delivery {
    +Long id
    +String deliveryStatus
    +assignAgent()
    +markPickedUp()
    +markDelivered()
}

class Bonus {
    +Long id
    +BigDecimal baseAmount
    +BigDecimal totalAmount
    +String bonusStatus
    +calculateBonus()
}

%% ######################################################
%%                Feedback and Complaints
%% ######################################################

class OrderFeedback {
    +Long id
    +Integer foodRating
    +Integer deliveryRating
    +Integer overallRating
    +String comment
}

class Complaints {

}

%% ######################################################
%%                    Relationships
%% ######################################################

%% Users
User "1" *-- "1" UserProfile : has
User "1" --> "0..*" Address : owns
User "1" --> "0..*" Order : places

%% Menus
Restaurant "1" *-- "0..*" Menu : offers
Menu "1" *-- "0..*" MenuItem : contains

%% Orders
Restaurant "1" --> "0..*" Order : receives
Order "1" *-- "1..*" OrderItem : contains
OrderItem "0..*" --> "1" MenuItem : references
Order "0..*" --> "1" OrderStatus : hasStatus

%% Payments
Order "1" --> "0..1" Payment : hasPayment

%% User roles and delivery profiles
Roles "1" --> "0..*" User : assignedTo
User "1" --> "0..1" DeliveryAgent : deliveryProfile

%% Deliveries
Order "1" --> "0..1" Delivery : hasDelivery
DeliveryAgent "1" --> "0..*" Delivery : performs

%% Bonuses
Delivery "1" --> "0..1" Bonus : earns
DeliveryAgent "1" --> "0..*" Bonus : receives

%% Commissions
Order "1" --> "0..1" Commission : generates

%% Address
Order "1" --> "0..1" Address : hasA
Restaurant "1" --> "0..1" Address : hasA
User "1" --> "0..1" Address : hasA
Address "1" --> "0..1" PostalCode : hasA

%% Feedback
Order "1" --> "0..1" OrderFeedback : receives
User "1" --> "0..*" OrderFeedback : submits

%% Complaints
User "1" --> "0..*" Complaints : maySubmit
PaymentService "1" --> "0..*" Complaints : maySubmit
Order "0..1" --> "0..*" Complaints : mayConcern
```
