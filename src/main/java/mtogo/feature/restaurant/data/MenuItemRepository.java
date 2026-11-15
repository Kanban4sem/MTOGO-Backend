package mtogo.feature.restaurant.data;

import java.util.List;
import mtogo.feature.restaurant.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long>
{
    List<MenuItem> findByMenuIdAndIsAvailableTrueOrderByIdAsc(Long menuId);
}
