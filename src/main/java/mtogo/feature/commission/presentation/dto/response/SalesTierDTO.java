package mtogo.feature.commission.presentation.dto.response;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public record SalesTierDTO(
        @Schema(description = "DKK of sales in the last 30 days a restaurant must reach. The tier from 0.00 is the default scale.", example = "0.00") BigDecimal minimumSales,
        CommissionScaleDTO scale)
{
}
