package mtogo.feature.user.presentation;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtogo.feature.address.presentation.dto.request.SaveDeliveryAddressDTO;
import mtogo.feature.address.presentation.dto.response.SavedDeliveryAddressDTO;
import mtogo.feature.user.domain.CustomerDeliveryAddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users/me/delivery-addresses")
@Tag(name = "Customer delivery addresses", description = "Saved delivery addresses for the current customer")
@SecurityRequirement(name = "bearerAuth")
public class CustomerDeliveryAddressController
{
    private final CustomerDeliveryAddressService addresses;

    public CustomerDeliveryAddressController(CustomerDeliveryAddressService addresses)
    {
        this.addresses = addresses;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List the current customer's saved delivery addresses")
    public List<SavedDeliveryAddressDTO> getAll(Principal principal)
    {
        return addresses.getAll(Long.valueOf(principal.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get one of the current customer's saved delivery addresses")
    @ApiResponse(responseCode = "404", description = "Saved delivery address not found")
    public SavedDeliveryAddressDTO get(@PathVariable Long id, Principal principal)
    {
        return addresses.get(Long.valueOf(principal.getName()), id);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Save a new delivery address for the current customer")
    @ApiResponse(responseCode = "201", description = "Delivery address saved")
    @ApiResponse(responseCode = "400", description = "Invalid delivery address")
    public ResponseEntity<SavedDeliveryAddressDTO> create(@Valid @RequestBody SaveDeliveryAddressDTO request,
            Principal principal)
    {
        SavedDeliveryAddressDTO address = addresses.create(Long.valueOf(principal.getName()), request);
        return ResponseEntity.created(URI.create("/v1/users/me/delivery-addresses/" + address.id())).body(address);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Change one of the current customer's saved delivery addresses")
    @ApiResponse(responseCode = "400", description = "Invalid delivery address")
    @ApiResponse(responseCode = "404", description = "Saved delivery address not found")
    public SavedDeliveryAddressDTO update(@PathVariable Long id, @Valid @RequestBody SaveDeliveryAddressDTO request,
            Principal principal)
    {
        return addresses.update(Long.valueOf(principal.getName()), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Remove one of the current customer's saved delivery addresses")
    @ApiResponse(responseCode = "204", description = "Saved delivery address removed")
    @ApiResponse(responseCode = "404", description = "Saved delivery address not found")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal)
    {
        addresses.delete(Long.valueOf(principal.getName()), id);
        return ResponseEntity.noContent().build();
    }
}
