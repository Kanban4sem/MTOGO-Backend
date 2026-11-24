package mtogo.feature.order.domain;

/**
 * What a delivery agent does to an order: take a ready order no one has taken,
 * pick up their order at the restaurant, or deliver it.
 */
public enum DeliveryAction
{
    TAKE, PICK_UP, DELIVER
}
