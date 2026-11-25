package mtogo.feature.order.domain;

import java.util.List;
import mtogo.exceptions.NotFoundException;
import mtogo.feature.order.data.OrderStatusChangeRepository;
import mtogo.feature.order.presentation.dto.response.OrderNotificationDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderNotificationService
{
    private final OrderStatusChangeRepository statusChanges;
    private final OrderMapper mapper;

    public OrderNotificationService(OrderStatusChangeRepository statusChanges, OrderMapper mapper)
    {
        this.statusChanges = statusChanges;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<OrderNotificationDTO> getAll(Long customerId)
    {
        return statusChanges
                .findAllByOrderCustomerIdAndStatusNotOrderByChangedAtDescIdDesc(customerId, OrderStatus.PLACED).stream()
                .map(mapper::toOrderNotificationDTO).toList();
    }

    @Transactional
    public void markRead(Long customerId, Long id)
    {
        OrderStatusChange change = statusChanges
                .findByIdAndOrderCustomerIdAndStatusNot(id, customerId, OrderStatus.PLACED)
                .orElseThrow(() -> new NotFoundException("Order notification not found with id: " + id));
        change.markRead();
    }
}
