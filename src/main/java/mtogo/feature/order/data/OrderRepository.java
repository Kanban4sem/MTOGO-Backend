package mtogo.feature.order.data;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import mtogo.feature.order.domain.Order;
import mtogo.feature.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long>
{
    @EntityGraph(attributePaths = "restaurant")
    List<Order> findAllByCustomerIdOrderByIdDesc(Long customerId);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    List<Order> findAllByRestaurantIdOrderByIdDesc(Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    @EntityGraph(attributePaths = {"restaurant", "deliveryAddress", "deliveryAddress.postalCode"})
    List<Order> findAllByDeliveryAgentIdOrStatusAndDeliveryAgentIsNullOrderByIdDesc(Long deliveryAgentId,
            OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
