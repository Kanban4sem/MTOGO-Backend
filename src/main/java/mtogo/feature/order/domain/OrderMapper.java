package mtogo.feature.order.domain;

import java.util.List;
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
import mtogo.feature.order.presentation.dto.response.StatusNameDTO;
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
        OrderStatus status = order.getStatus();
        return new OrderDTO(order.getId(), order.getRestaurant().getId(), status, toStatusNameDTO(status),
                order.getChannel(), order.getPlacedAt(), order.getTotal(), toDeliveryAddressDTO(order),
                toLineDTOs(order));
    }

    public CustomerOrderSummaryDTO toCustomerOrderSummaryDTO(Order order)
    {
        OrderStatus status = order.getStatus();
        return new CustomerOrderSummaryDTO(order.getId(), order.getRestaurant().getId(), status,
                toStatusNameDTO(status), order.getTotal(), toDeliveryAddressDTO(order));
    }

    public CustomerOrderDTO toCustomerOrderDTO(Order order)
    {
        OrderStatus status = order.getStatus();
        return new CustomerOrderDTO(order.getId(), order.getRestaurant().getId(), status, toStatusNameDTO(status),
                order.getPlacedAt(), order.getTotal(), toDeliveryAddressDTO(order), toLineDTOs(order));
    }

    public RestaurantOrderDTO toRestaurantOrderDTO(Order order)
    {
        OrderStatus status = order.getStatus();
        return new RestaurantOrderDTO(order.getId(), status, toStatusNameDTO(status), order.getPlacedAt(),
                order.getTotal(), toLineDTOs(order));
    }

    public DeliveryAgentOrderDTO toDeliveryAgentOrderDTO(Order order, Long agentId)
    {
        OrderStatus status = order.getStatus();
        return new DeliveryAgentOrderDTO(order.getId(), order.getRestaurant().getId(), order.getRestaurant().getName(),
                status, toStatusNameDTO(status), order.isAssignedTo(agentId), toDeliveryAddressDTO(order),
                order.getTotal(), toLineDTOs(order));
    }

    private StatusNameDTO toStatusNameDTO(OrderStatus status)
    {
        return new StatusNameDTO(status.getDanishName(), status.getEnglishName());
    }

    private DeliveryAddressDTO toDeliveryAddressDTO(Order order)
    {
        Address address = order.getDeliveryAddress();
        PostalCode postalCode = address.getPostalCode();
        return new DeliveryAddressDTO(address.getStreet(), postalCode.getCode(), postalCode.getCity(),
                postalCode.getCountry());
    }

    private List<OrderLineDTO> toLineDTOs(Order order)
    {
        return order.getLines().stream().map(this::toDTO).toList();
    }

    private OrderLineDTO toDTO(OrderLine line)
    {
        return new OrderLineDTO(line.getMenuItem().getId(), line.getName(), line.getUnitPrice(), line.getQuantity(),
                line.getSubtotal());
    }
}
