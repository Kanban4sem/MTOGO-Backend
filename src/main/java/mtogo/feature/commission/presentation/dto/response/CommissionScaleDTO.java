package mtogo.feature.commission.presentation.dto.response;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record CommissionScaleDTO(
        @Schema(description = "DKK per delivered order", example = "5.00") BigDecimal serviceFee,
        @Schema(description = "Sorted by lower limit") List<CommissionBracketDTO> brackets)
{
}
