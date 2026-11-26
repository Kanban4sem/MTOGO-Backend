package mtogo.feature.commission.presentation.dto.request;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// The rules that compare brackets belong to the commission service, which answers
// 400 when they are broken.
public record SaveCommissionScaleDTO(
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2) @Schema(description = "DKK per delivered order", example = "5.00") BigDecimal serviceFee,
        @NotNull @Size(min = 1, max = 10) @Schema(description = "The first starts at 0.00, lower limits ascend and rates never rise.") List<@NotNull @Valid Bracket> brackets)
{
    public record Bracket(
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) @Schema(description = "In DKK excluding VAT", example = "100.00") BigDecimal lowerLimit,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) @Schema(description = "In percent", example = "5.00") BigDecimal ratePercent)
    {
    }
}
