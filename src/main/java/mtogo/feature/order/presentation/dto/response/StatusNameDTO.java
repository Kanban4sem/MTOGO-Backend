package mtogo.feature.order.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The order status's name in each language the frontend can show")
public record StatusNameDTO(
        @Schema(description = "Status name in Danish", example = "Klar til afhentning") String danish,
        @Schema(description = "Status name in English", example = "Ready for pickup") String english)
{
}
