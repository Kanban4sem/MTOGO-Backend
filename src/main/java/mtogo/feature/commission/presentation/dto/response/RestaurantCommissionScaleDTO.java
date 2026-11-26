package mtogo.feature.commission.presentation.dto.response;

public record RestaurantCommissionScaleDTO(Long restaurantId, String restaurantName, CommissionScaleDTO scale)
{
}
