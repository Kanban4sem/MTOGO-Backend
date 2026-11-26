package mtogo.feature.commission.presentation;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtogo.feature.commission.domain.CommissionService;
import mtogo.feature.commission.presentation.dto.request.ReplaceSalesTiersDTO;
import mtogo.feature.commission.presentation.dto.request.SaveCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.CommissionScalesDTO;
import mtogo.feature.commission.presentation.dto.response.RestaurantCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.SalesTierDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/commission-scales")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin commission", description = "The commission scales restaurants pay MTOGO")
public class AdminCommissionController
{
    private final CommissionService commissions;

    public AdminCommissionController(CommissionService commissions)
    {
        this.commissions = commissions;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Show every sales tier and every restaurant's own commission scale")
    public CommissionScalesDTO getAll()
    {
        return commissions.getAll();
    }

    @PutMapping("/tiers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace every sales tier. The first tier is the default scale")
    @ApiResponse(responseCode = "400", description = "The tiers or one of their scales break the rules")
    public List<SalesTierDTO> replaceTiers(@Valid @RequestBody ReplaceSalesTiersDTO request)
    {
        return commissions.replaceTiers(request);
    }

    @PutMapping("/restaurants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Give a restaurant its own commission scale, replacing the one it has")
    @ApiResponse(responseCode = "400", description = "The scale breaks the rules")
    @ApiResponse(responseCode = "404", description = "The restaurant doesn't exist")
    public RestaurantCommissionScaleDTO saveRestaurantScale(@PathVariable Long id,
            @Valid @RequestBody SaveCommissionScaleDTO request)
    {
        return commissions.saveRestaurantScale(id, request);
    }

    @DeleteMapping("/restaurants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a restaurant's own commission scale, so the sales tiers apply again")
    @ApiResponse(responseCode = "404", description = "The restaurant doesn't exist")
    public void removeRestaurantScale(@PathVariable Long id)
    {
        commissions.removeRestaurantScale(id);
    }
}
