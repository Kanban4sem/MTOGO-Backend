package mtogo.feature.order.presentation.dto.request;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderFeedbackDTO(@Size(max = 100) String title, @Size(max = 1000) String description,
        @Schema(type = "integer", example = "5") @NotNull @Digits(integer = 1, fraction = 0) @Min(1) @Max(6) BigDecimal foodRating,
        @Schema(type = "integer", example = "5") @NotNull @Digits(integer = 1, fraction = 0) @Min(1) @Max(6) BigDecimal overallRating,
        @Schema(type = "integer", description = "Rating of the delivery agent", example = "6") @NotNull @Digits(integer = 1, fraction = 0) @Min(1) @Max(6) BigDecimal deliveryRating)
{
}
