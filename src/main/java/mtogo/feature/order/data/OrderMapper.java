package mtogo.feature.order.data;

import mtogo.feature.address.domain.Address;
import mtogo.feature.address.domain.PostalCode;
import mtogo.feature.order.domain.Order;
import mtogo.feature.order.domain.OrderLine;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.response.DeliveryAddressDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderLineDTO;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper
{
    public Address toEntity(PlaceOrderDTO.DeliveryAddress request)
    {
        PostalCode postalCode = new PostalCode();
        postalCode.setCode(request.postalCode());
        postalCode.setCity(request.city().strip());
        Address address = new Address();
        address.setStreet(request.street().strip());
        address.setPostalCode(postalCode);
        return address;
    }

    public OrderDTO toDTO(Order order)
    {
        Address address = order.getDeliveryAddress();
        PostalCode postalCode = address.getPostalCode();
        return new OrderDTO(order.getId(), order.getRestaurant().getId(), order.getStatus(), order.getChannel(),
                order.getPlacedAt(), order.getTotal(), new DeliveryAddressDTO(address.getStreet(), postalCode.getCode(),
                        postalCode.getCity(), postalCode.getCountry()),
                order.getLines().stream().map(this::toDTO).toList());
    }

    private OrderLineDTO toDTO(OrderLine line)
    {
        return new OrderLineDTO(line.getMenuItem().getId(), line.getName(), line.getUnitPrice(), line.getQuantity(),
                line.getSubtotal());
    }
}
