package mtogo.feature.commission.presentation.dto.request;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReplaceSalesTiersDTO(
        @NotNull @Size(min = 1, max = 5) @Schema(description = "The first, the default scale, has a minimum of 0.00, and minimums ascend.") List<@NotNull @Valid SalesTier> tiers)
{
    public record SalesTier(
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) @Schema(description = "DKK of sales in the last 30 days a restaurant must reach", example = "50000.00") BigDecimal minimumSales,
            @NotNull @Valid SaveCommissionScaleDTO scale)
    {
    }
}
