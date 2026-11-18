package mtogo.feature.order.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import mtogo.feature.address.domain.Address;
import mtogo.exceptions.*;
import mtogo.feature.order.data.OrderRepository;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import mtogo.feature.restaurant.data.MenuItemRepository;
import mtogo.feature.restaurant.domain.MenuItem;
import mtogo.feature.restaurant.domain.Restaurant;
import mtogo.feature.user.data.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService
{
    // MTOGO delivers only in Denmark.
    static final String DELIVERY_COUNTRY = "Denmark";

    private final OrderRepository orders;
    private final MenuItemRepository menuItems;
    private final UserRepository users;
    private final OrderMapper mapper;
    private final IPaymentProvider payments;

    public OrderService(OrderRepository orders, MenuItemRepository menuItems, UserRepository users, OrderMapper mapper,
            IPaymentProvider payments)
    {
        this.orders = orders;
        this.menuItems = menuItems;
        this.users = users;
        this.mapper = mapper;
        this.payments = payments;
    }

    /**
     * Places an order for the customer, charging the order total first. Nothing is
     * saved unless the payment is confirmed.
     */
    @Transactional
    public OrderDTO place(Long customerId, PlaceOrderDTO request)
    {
        Map<Long, MenuItem> items = requireMenuItems(request.lines());
        Restaurant restaurant = requireOneRestaurant(items.values());
        items.values().forEach(OrderService::requireOrderable);

        Order order = new Order();
        order.setCustomer(users.getReferenceById(customerId));
        order.setRestaurant(restaurant);
        order.setDeliveryAddress(deliveryAddress(request.deliveryAddress()));
        order.setChannel(OrderChannel.WEBSITE);
        request.lines().forEach(line -> order.addLine(OrderLine.of(items.get(line.menuItemId()), line.quantity())));

        if (!payments.charge(order.getTotal(), request.paymentToken()).isConfirmed())
        {
            throw new PaymentRequiredException("The payment was declined");
        }
        order.moveTo(OrderStatus.PLACED);
        return mapper.toDTO(orders.save(order));
    }

    private Address deliveryAddress(PlaceOrderDTO.DeliveryAddress request)
    {
        Address address = mapper.toEntity(request);
        address.getPostalCode().setCountry(DELIVERY_COUNTRY);
        // Default is a profile address setting; a delivery address belongs to one
        // order.
        address.setDefault(false);
        return address;
    }

    private Map<Long, MenuItem> requireMenuItems(List<PlaceOrderDTO.OrderLine> lines)
    {
        List<Long> ids = lines.stream().map(PlaceOrderDTO.OrderLine::menuItemId).toList();
        if (new HashSet<>(ids).size() != ids.size())
        {
            throw new BadRequestException("A menu item can appear on only one order line");
        }
        Map<Long, MenuItem> items = menuItems.findAllById(ids).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        ids.stream().filter(id -> !items.containsKey(id)).findFirst().ifPresent(id -> {
            throw new BadRequestException("Menu item not found with id: " + id);
        });
        return items;
    }

    private static Restaurant requireOneRestaurant(Collection<MenuItem> items)
    {
        Map<Long, Restaurant> restaurants = items.stream().map(MenuItem::getRestaurant)
                .collect(Collectors.toMap(Restaurant::getId, Function.identity(), (first, same) -> first));
        if (restaurants.size() > 1)
        {
            throw new BadRequestException("An order can only contain menu items from one restaurant");
        }
        return restaurants.values().iterator().next();
    }

    private static void requireOrderable(MenuItem item)
    {
        if (!item.getRestaurant().isAcceptsOrders())
        {
            throw new ConflictException("The restaurant does not accept orders");
        }
        if (!item.isOnActiveMenu())
        {
            throw new ConflictException("Menu item is not on an active menu: " + item.getName());
        }
        if (!item.isAvailable())
        {
            throw new ConflictException("Menu item is not available: " + item.getName());
        }
    }
}
