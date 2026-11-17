package mtogo.feature.order.domain;

// The lifecycle from the MVP. The frontend translates the codes.
public enum OrderStatus
{
    PLACED, ACCEPTED, REJECTED, READY, PICKED_UP, DELIVERED;

    public boolean canMoveTo(OrderStatus next)
    {
        return switch (this)
        {
            case PLACED -> next == ACCEPTED || next == REJECTED;
            case ACCEPTED -> next == READY;
            case READY -> next == PICKED_UP;
            case PICKED_UP -> next == DELIVERED;
            case REJECTED, DELIVERED -> false;
        };
    }
}
