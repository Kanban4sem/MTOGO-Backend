package mtogo.feature.commission.presentation.dto.response;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public record CommissionBracketDTO(
        @Schema(description = "In DKK excluding VAT", example = "100.00") BigDecimal lowerLimit,
        @Schema(description = "In percent", example = "5.00") BigDecimal ratePercent)
{
}
