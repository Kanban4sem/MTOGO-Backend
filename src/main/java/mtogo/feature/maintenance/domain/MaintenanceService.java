package mtogo.feature.maintenance.domain;

import java.util.List;
import java.util.Optional;
import mtogo.feature.maintenance.data.MaintenanceRepository;
import mtogo.feature.maintenance.presentation.dto.response.MaintenanceStatusDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService
{
    private final MaintenanceRepository maintenance;

    public MaintenanceService(MaintenanceRepository maintenance)
    {
        this.maintenance = maintenance;
    }

    public List<MaintenanceStatusDTO> getAll()
    {
        return maintenance.findAll().stream().map(status -> new MaintenanceStatusDTO(status.getFeature(),
                status.isPaused(), status.isPaused() ? status.getFeature().getPauseMessage() : null)).toList();
    }

    public Optional<String> getPauseMessage(MaintainableFeature feature)
    {
        return maintenance.findById(feature).filter(FeatureMaintenance::isPaused)
                .map(status -> feature.getPauseMessage());
    }

    @Transactional
    public void setPause(MaintainableFeature feature, boolean isPaused)
    {
        maintenance.setPause(feature, isPaused);
    }
}
