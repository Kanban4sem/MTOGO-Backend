package mtogo.feature.restaurant.data;

import java.util.List;
import java.util.Optional;
import mtogo.feature.restaurant.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long>
{
    List<Restaurant> findByIsAcceptingOrdersTrueOrderByIdAsc();

    Optional<Restaurant> findByIdAndIsAcceptingOrdersTrue(Long id);

    List<Restaurant> findAllByName(String name);
}
