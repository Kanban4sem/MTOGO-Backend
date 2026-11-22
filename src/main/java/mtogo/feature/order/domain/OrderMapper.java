package mtogo.feature.order.domain;

import mtogo.feature.address.domain.Address;
import mtogo.feature.address.domain.PostalCode;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderSummaryDTO;
import mtogo.feature.order.presentation.dto.response.DeliveryAddressDTO;
import mtogo.feature.order.presentation.dto.response.DeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderLineDTO;
import mtogo.feature.order.presentation.dto.response.RestaurantOrderDTO;
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

    public CustomerOrderSummaryDTO toCustomerOrderSummaryDTO(Order order)
    {
        OrderStatus status = order.getStatus();
        return new CustomerOrderSummaryDTO(order.getId(), order.getRestaurant().getId(), status, status.getDanishName(),
                order.getTotal());
    }

    public CustomerOrderDTO toCustomerOrderDTO(Order order)
    {
        OrderStatus status = order.getStatus();
        return new CustomerOrderDTO(order.getId(), order.getRestaurant().getId(), status, status.getDanishName(),
                order.getPlacedAt(), order.getTotal(), order.getLines().stream().map(this::toDTO).toList());
    }

    public RestaurantOrderDTO toRestaurantOrderDTO(Order order)
    {
        OrderStatus status = order.getStatus();
        return new RestaurantOrderDTO(order.getId(), status, status.getDanishName(), order.getPlacedAt(),
                order.getTotal(), order.getLines().stream().map(this::toDTO).toList());
    }

    public DeliveryAgentOrderDTO toDeliveryAgentOrderDTO(Order order, Long agentId)
    {
        Address address = order.getDeliveryAddress();
        PostalCode postalCode = address.getPostalCode();
        boolean assignedToMe = order.isAssignedTo(agentId);
        DeliveryAddressDTO deliveryAddress = new DeliveryAddressDTO(address.getStreet(), postalCode.getCode(),
                postalCode.getCity(), postalCode.getCountry());
        OrderStatus status = order.getStatus();
        return new DeliveryAgentOrderDTO(order.getId(), order.getRestaurant().getId(), order.getRestaurant().getName(),
                status, status.getDanishName(), assignedToMe, postalCode.getCity(), deliveryAddress);
    }

    private OrderLineDTO toDTO(OrderLine line)
    {
        return new OrderLineDTO(line.getMenuItem().getId(), line.getName(), line.getUnitPrice(), line.getQuantity(),
                line.getSubtotal());
    }
}
