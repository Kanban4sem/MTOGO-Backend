package mtogo.feature.maintenance.data;

import mtogo.feature.maintenance.domain.FeatureMaintenance;
import mtogo.feature.maintenance.domain.MaintainableFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRepository extends JpaRepository<FeatureMaintenance, MaintainableFeature>
{
    @Modifying
    @Query("update FeatureMaintenance m set m.isPaused = :paused where m.feature = :feature")
    void setPause(@Param("feature") MaintainableFeature feature, @Param("paused") boolean isPaused);
}
