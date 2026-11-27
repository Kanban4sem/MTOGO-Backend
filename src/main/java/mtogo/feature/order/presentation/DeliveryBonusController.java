package mtogo.feature.order.presentation;

import java.security.Principal;
import java.time.YearMonth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import mtogo.feature.order.domain.DeliveryBonusService;
import mtogo.feature.order.presentation.dto.response.DeliveryBonusDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/delivery-agent/bonuses")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Delivery bonuses", description = "Monthly bonuses for delivery agents")
public class DeliveryBonusController
{
    private final DeliveryBonusService bonuses;

    public DeliveryBonusController(DeliveryBonusService bonuses)
    {
        this.bonuses = bonuses;
    }

    @GetMapping
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Show the authenticated delivery agent's bonus for a Copenhagen calendar month")
    @ApiResponse(responseCode = "400", description = "The month is malformed or in the future")
    public DeliveryBonusDTO get(Principal principal,
            @RequestParam(required = false) @Pattern(regexp = "(?!0000)[0-9]{4}-(0[1-9]|1[0-2])") @Parameter(description = "Calendar month in Copenhagen, defaulting to the current month", example = "2026-11") String month)
    {
        return bonuses.getBonus(Long.valueOf(principal.getName()), month == null ? null : YearMonth.parse(month));
    }
}
