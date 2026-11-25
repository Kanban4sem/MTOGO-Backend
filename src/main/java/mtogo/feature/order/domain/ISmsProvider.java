package mtogo.feature.order.domain;

public interface ISmsProvider
{
    void send(String phoneNumber, String message);
}
