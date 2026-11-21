package mtogo.feature.order.domain;

public enum OrderStatus
{
    PLACED("Bestilt"), ACCEPTED("Accepteret"), REJECTED("Afvist"), READY("Klar til afhentning"), PICKED_UP(
            "Afhentet"), DELIVERED("Leveret");

    private final String danishName;

    OrderStatus(String danishName)
    {
        this.danishName = danishName;
    }

    public String getDanishName()
    {
        return danishName;
    }

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
