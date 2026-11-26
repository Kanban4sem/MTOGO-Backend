# ADR-009: Commission and Service Fee

## Status

Accepted — [#23](https://github.com/Kanban4sem/MTOGO-Backend/issues/23)

## Context

Restaurants pay MTOGO a service fee and a commission on each order's value excluding VAT. The case says the commission rate falls from 6% for orders under 101 DKK to 3% for orders over 1,000 DKK, and gives an example: the first 100 DKK at 6%, up to 500 DKK at 5%, up to 1,000 DKK at 4%, anything above at 3%. The business hasn't agreed how the sliding scale works, and the case gives no amount or method for the service fee. Customers pay no delivery charge, so restaurants are MTOGO's only source of revenue, and the bonus scheme (#25) must stay within that revenue. Admins also want to give restaurants with high sales better terms.

## Decision

- **Marginal brackets.** The commission is calculated like income tax: each part of the order value excluding VAT pays the rate of the bracket it falls in. The default brackets are the case's example: 6% from 0 DKK, 5% from 100 DKK, 4% from 500 DKK and 3% from 1,000 DKK.
- **Value excluding VAT.** Menu prices and the order total include 25% VAT, so the value excluding VAT is the order total divided by 1.25. It is kept unrounded, and the commission is rounded once to øre, half up.
- **Service fee.** A fixed amount per delivered order, whatever its value. The default is 5.00 DKK. The case gives no figure, so this is our choice.
- **Commission scale.** The brackets and the service fee form a commission scale. A valid scale starts its first bracket at 0, has 1 to 10 brackets with strictly ascending lower limits and rates from 0 to 100% that never rise from one bracket to the next, and has a service fee from 0 to 1,000 DKK.
- **Sales tiers.** Each sales tier is a commission scale with a minimum sales amount. A restaurant gets the tier with the highest minimum that its sales in the 30 days before the delivery reach, not counting the order being delivered. The tier from 0 DKK is the default scale and can't be removed. There are 1 to 5 tiers.
- **A restaurant's own scale.** An admin can give a restaurant its own scale, which applies instead of the tiers until the admin removes it.
- **Stored at delivery.** The commission and service fee are calculated when an order moves to Delivered and stored on the order. Later changes to a scale never change stored amounts. Orders that are never delivered have neither. Orders delivered before this decision are given amounts from the default scale.

## Alternatives

- **Whole-order step:** The whole order pays the rate of the bracket it falls in. Simple, but an order of 1,001 DKK pays 30.03 DKK while one of 1,000 DKK pays 40.00 DKK, so restaurants gain from padding or splitting orders.
- **A smooth curve:** The rate falls continuously with the order value. No steps, but harder for restaurants to check and for admins to edit, and boundary testing has no clear edges.
- **A service fee as a percentage:** Overlaps with the commission and makes the fee depend on the order value, which the commission already does.
- **One service fee for all of MTOGO:** One endpoint and one value, but admins couldn't lower the fee as part of a restaurant's better terms.
- **A discount factor or flat rate for big restaurants:** Fewer inputs, but a second shape with its own rules next to the scale.
- **Tiers assigned by a scheduled job:** Stores each restaurant's tier, but needs a schedule and goes stale between runs. Working the tier out at delivery costs one sum query per delivery.
- **Calculating the amounts when they are read:** Always uses the current scale, but rewrites history whenever a scale changes.

## Consequences

- Small orders carry most of the fee: on a 100 DKK order (80 DKK excluding VAT), the restaurant pays 4.80 DKK commission and 5.00 DKK service fee.
- The total commission rises with every krone of the order value, so the scale has no point where a larger order pays less.
- Each delivery runs one query for the restaurant's sales in the last 30 days.
- A scale or tier change applies to every order delivered after it, including orders placed before it.
- Scales keep no history. The amounts stored on each order are the record of what a restaurant paid.
- The backfill repeats the default calculation in SQL once, in the migration.
