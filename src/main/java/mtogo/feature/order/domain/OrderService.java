package mtogo.feature.order.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import mtogo.feature.address.domain.Address;
import mtogo.feature.address.domain.AddressMapper;
import mtogo.feature.address.domain.PostalCode;
import mtogo.feature.address.presentation.dto.response.SavedDeliveryAddressDTO;
import mtogo.exceptions.*;
import mtogo.feature.order.data.OrderRepository;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateDeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateRestaurantOrderStatusDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderSummaryDTO;
import mtogo.feature.order.presentation.dto.response.DeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import mtogo.feature.order.presentation.dto.response.RestaurantOrderDTO;
import mtogo.feature.restaurant.data.MenuItemRepository;
import mtogo.feature.restaurant.domain.MenuItem;
import mtogo.feature.restaurant.domain.Restaurant;
import mtogo.feature.user.data.UserRepository;
import mtogo.feature.user.domain.CustomerDeliveryAddressService;
import mtogo.feature.user.domain.User;
import mtogo.feature.user.domain.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderService
{
    private final OrderRepository orders;
    private final MenuItemRepository menuItems;
    private final UserRepository users;
    private final OrderMapper mapper;
    private final IPaymentProvider payments;
    private final AddressMapper addresses;
    private final CustomerDeliveryAddressService savedAddresses;

    public OrderService(OrderRepository orders, MenuItemRepository menuItems, UserRepository users, OrderMapper mapper,
            IPaymentProvider payments, AddressMapper addresses, CustomerDeliveryAddressService savedAddresses)
    {
        this.orders = orders;
        this.menuItems = menuItems;
        this.users = users;
        this.mapper = mapper;
        this.payments = payments;
        this.addresses = addresses;
        this.savedAddresses = savedAddresses;
    }

    /**
     * Places an order for the customer, charging the order total first. Nothing is
     * saved unless the payment is confirmed.
     */
    @Transactional
    public OrderDTO place(Long customerId, PlaceOrderDTO request)
    {
        validateDeliveryAddressChoice(request);
        Map<Long, MenuItem> items = requireMenuItems(request.lines());
        Restaurant restaurant = requireOneRestaurant(items.values());
        items.values().forEach(OrderService::requireOrderable);

        // Reusing an address must see the street and postal area from the same update.
        // An authenticated customer always exists.
        User customer = request.useDefaultAddress() || request.saveAsDefault() || request.deliveryAddressId() != null
                ? users.findByIdForUpdate(customerId).orElseThrow()
                : users.getReferenceById(customerId);
        Address address = deliveryAddress(customer, request);

        Order order = new Order();
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setDeliveryAddress(address);
        order.setChannel(OrderChannel.WEBSITE);
        request.lines().forEach(line -> order.addLine(OrderLine.of(items.get(line.menuItemId()), line.quantity())));

        if (!payments.charge(order.getTotal(), request.paymentToken()).isConfirmed())
        {
            throw new PaymentRequiredException("The payment was declined");
        }
        if (request.saveAsDefault())
        {
            saveDefaultAddress(customer.getProfile(), address);
        }
        order.moveTo(OrderStatus.PLACED);
        return mapper.toDTO(orders.save(order));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderSummaryDTO> getCustomerOrders(Long customerId)
    {
        return orders.findAllByCustomerIdOrderByIdDesc(customerId).stream().map(mapper::toCustomerOrderSummaryDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerOrderDTO getCustomerOrder(Long customerId, Long orderId)
    {
        return orders.findByIdAndCustomerId(orderId, customerId).map(mapper::toCustomerOrderDTO)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<RestaurantOrderDTO> getRestaurantOrders(Long userId)
    {
        return orders.findAllByRestaurantIdOrderByIdDesc(getRestaurantId(userId)).stream()
                .map(mapper::toRestaurantOrderDTO).toList();
    }

    @Transactional
    public RestaurantOrderDTO updateRestaurantOrderStatus(Long userId, Long orderId,
            UpdateRestaurantOrderStatusDTO request)
    {
        if (!request.status().isSetByRestaurant())
        {
            throw new BadRequestException("A restaurant cannot set an order to " + request.status());
        }
        Order order = orders.findByIdAndRestaurantId(orderId, getRestaurantId(userId))
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        order.moveTo(request.status());
        return mapper.toRestaurantOrderDTO(order);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAgentOrderDTO> getDeliveryAgentOrders(Long agentId)
    {
        return orders.findAllByDeliveryAgentIdOrStatusAndDeliveryAgentIsNullOrderByIdDesc(agentId, OrderStatus.READY)
                .stream().map(order -> mapper.toDeliveryAgentOrderDTO(order, agentId)).toList();
    }

    @Transactional
    public DeliveryAgentOrderDTO updateDeliveryAgentOrder(Long agentId, Long orderId,
            UpdateDeliveryAgentOrderDTO request)
    {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        switch (request.action())
        {
            case TAKE -> order.assignDeliveryAgent(users.getReferenceById(agentId));
            case PICK_UP -> moveDeliveryOrder(order, agentId, OrderStatus.PICKED_UP);
            case DELIVER -> moveDeliveryOrder(order, agentId, OrderStatus.DELIVERED);
        }
        return mapper.toDeliveryAgentOrderDTO(order, agentId);
    }

    private static void moveDeliveryOrder(Order order, Long agentId, OrderStatus status)
    {
        if (!order.isAssignedTo(agentId))
        {
            throw new NotFoundException("Order not found with id: " + order.getId());
        }
        order.moveTo(status);
    }

    // Every restaurant user has a restaurant: the role cannot be assigned without
    // one.
    private Long getRestaurantId(Long userId)
    {
        return users.getReferenceById(userId).getRestaurant().getId();
    }

    private static void validateDeliveryAddressChoice(PlaceOrderDTO request)
    {
        boolean hasNewAddress = request.deliveryAddress() != null;
        int addressChoices = (hasNewAddress ? 1 : 0) + (request.useDefaultAddress() ? 1 : 0)
                + (request.deliveryAddressId() != null ? 1 : 0);
        if (addressChoices != 1)
        {
            throw new BadRequestException("Enter a delivery address or select one saved delivery address");
        }
        if (request.saveAsDefault() && !hasNewAddress)
        {
            throw new BadRequestException("Only an entered delivery address can be saved as the default");
        }
    }

    private Address copyDefaultAddress(User customer)
    {
        Address address = customer.getProfile().getAddress();
        if (address == null || !address.isDefault() || !StringUtils.hasText(address.getStreet()))
        {
            throw new BadRequestException("Your profile has no complete default delivery address");
        }
        PostalCode postalCode = address.getPostalCode();
        return addresses.toDeliveryAddress(address.getStreet(), postalCode.getCode(), postalCode.getCity());
    }

    private static void saveDefaultAddress(UserProfile profile, Address deliveryAddress)
    {
        Address profileAddress = profile.getAddress();
        if (profileAddress == null)
        {
            profileAddress = new Address();
            profileAddress.setPostalCode(new PostalCode());
            profile.setAddress(profileAddress);
        }
        profileAddress.setStreet(deliveryAddress.getStreet());
        profileAddress.setDefault(true);
        PostalCode postalCode = profileAddress.getPostalCode();
        PostalCode deliveryPostalCode = deliveryAddress.getPostalCode();
        postalCode.setCode(deliveryPostalCode.getCode());
        postalCode.setCity(deliveryPostalCode.getCity());
        postalCode.setCountry(deliveryPostalCode.getCountry());
    }

    private Address deliveryAddress(User customer, PlaceOrderDTO request)
    {
        if (request.deliveryAddressId() != null)
        {
            SavedDeliveryAddressDTO saved = savedAddresses.get(customer.getId(), request.deliveryAddressId());
            return addresses.toDeliveryAddress(saved.street(), saved.postalCode(), saved.city());
        }
        if (request.useDefaultAddress())
        {
            return copyDefaultAddress(customer);
        }
        PlaceOrderDTO.DeliveryAddress address = request.deliveryAddress();
        return addresses.toDeliveryAddress(address.street(), address.postalCode(), address.city());
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
        if (!item.getRestaurant().isAcceptingOrders())
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
