package mtogo.feature.order.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import mtogo.feature.order.domain.DashboardService;
import mtogo.feature.order.presentation.dto.response.DashboardDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/dashboard")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin dashboard", description = "Figures about MTOGO's operations")
public class AdminDashboardController
{
    private final DashboardService dashboard;

    public AdminDashboardController(DashboardService dashboard)
    {
        this.dashboard = dashboard;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Show the dashboard's figures for all of MTOGO and every restaurant over the last days")
    @ApiResponse(responseCode = "400", description = "The period is not a whole number from 1 to 365")
    @ApiResponse(responseCode = "404", description = "Ordering is disabled")
    public DashboardDTO get(
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) @Parameter(description = "The period in days, ending now", example = "7") int days)
    {
        return dashboard.getDashboard(days);
    }
}
