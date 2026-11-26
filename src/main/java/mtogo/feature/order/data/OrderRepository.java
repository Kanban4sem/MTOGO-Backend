package mtogo.feature.order.data;

import java.time.Instant;
import java.util.Collection;
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

    long countByStatusAndDeliveryAgentIsNull(OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /**
     * Finds the orders in one of the given statuses, and the orders with a status
     * change between {@code from} and {@code until}, both included, with their
     * status changes.
     */
    @EntityGraph(attributePaths = "statusChanges")
    @Query("""
            select o from Order o
            where o.status in :statuses
               or exists (select c from OrderStatusChange c
                          where c.order = o and c.changedAt between :from and :until)
            """)
    List<Order> findAllByStatusInOrChangedBetween(@Param("statuses") Collection<OrderStatus> statuses,
            @Param("from") Instant from, @Param("until") Instant until);
}
