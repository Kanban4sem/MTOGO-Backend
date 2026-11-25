package mtogo.feature.order.data;

import java.util.List;
import java.util.Optional;
import mtogo.feature.order.domain.OrderStatus;
import mtogo.feature.order.domain.OrderStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusChangeRepository extends JpaRepository<OrderStatusChange, Long>
{
    List<OrderStatusChange> findAllByOrderCustomerIdAndStatusNotOrderByChangedAtDescIdDesc(Long customerId,
            OrderStatus status);

    Optional<OrderStatusChange> findByIdAndOrderCustomerIdAndStatusNot(Long id, Long customerId, OrderStatus status);
}
