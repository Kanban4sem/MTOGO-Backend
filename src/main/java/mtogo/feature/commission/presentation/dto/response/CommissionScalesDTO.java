package mtogo.feature.commission.presentation.dto.response;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record CommissionScalesDTO(@Schema(description = "Sorted by minimum sales") List<SalesTierDTO> tiers,
        @Schema(description = "The restaurants with their own scale, sorted by restaurant name") List<RestaurantCommissionScaleDTO> restaurants)
{
}
