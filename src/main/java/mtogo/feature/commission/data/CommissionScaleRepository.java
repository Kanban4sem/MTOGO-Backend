package mtogo.feature.commission.data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import mtogo.feature.commission.domain.CommissionScale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionScaleRepository extends JpaRepository<CommissionScale, Long>
{
    @EntityGraph(attributePaths = "brackets")
    List<CommissionScale> findAllByMinimumSalesIsNotNullOrderByMinimumSalesAsc();

    @EntityGraph(attributePaths = {"restaurant", "brackets"})
    List<CommissionScale> findAllByRestaurantIsNotNullOrderByRestaurantNameAscIdAsc();

    Optional<CommissionScale> findByRestaurantId(Long restaurantId);

    Optional<CommissionScale> findFirstByMinimumSalesLessThanEqualOrderByMinimumSalesDesc(BigDecimal sales);
}
