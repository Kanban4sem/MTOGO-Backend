package mtogo.feature.order.data;

import java.util.Set;
import mtogo.feature.order.domain.OrderFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderFeedbackRepository extends JpaRepository<OrderFeedback, Long>
{
    @Query("select f.orderId from OrderFeedback f where f.order.customer.id = :customerId")
    Set<Long> findOrderIdsByCustomerId(@Param("customerId") Long customerId);
}
