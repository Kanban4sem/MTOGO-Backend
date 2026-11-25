package mtogo.feature.order.presentation.dto.response;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardDTO(@Schema(description = "The period in days, ending now", example = "7") int days,
        @Schema(description = "The figures for all of MTOGO") DashboardFiguresDTO total,
        @Schema(description = "The figures for every restaurant, sorted by name") List<RestaurantFiguresDTO> restaurants)
{
}
