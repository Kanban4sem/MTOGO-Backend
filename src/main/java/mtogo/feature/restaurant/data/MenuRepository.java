package mtogo.feature.restaurant.data;

import java.util.List;
import mtogo.feature.restaurant.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long>
{
    List<Menu> findByRestaurantIdAndIsActiveTrueOrderByIdAsc(Long restaurantId);
}
