package mtogo.feature.order.domain;

public enum OrderStatus
{
    PLACED("Bestilt", "Placed"), ACCEPTED("Accepteret", "Accepted"), REJECTED("Afvist", "Rejected"), READY(
            "Klar til afhentning",
            "Ready for pickup"), PICKED_UP("Afhentet", "Picked up"), DELIVERED("Leveret", "Delivered");

    private final String danishName;
    private final String englishName;

    OrderStatus(String danishName, String englishName)
    {
        this.danishName = danishName;
        this.englishName = englishName;
    }

    public String getDanishName()
    {
        return danishName;
    }

    public String getEnglishName()
    {
        return englishName;
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
