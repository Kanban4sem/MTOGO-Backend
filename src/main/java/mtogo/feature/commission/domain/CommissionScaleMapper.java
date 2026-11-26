package mtogo.feature.commission.domain;

import java.util.List;
import mtogo.feature.commission.presentation.dto.request.SaveCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.CommissionBracketDTO;
import mtogo.feature.commission.presentation.dto.response.CommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.RestaurantCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.SalesTierDTO;
import org.springframework.stereotype.Component;

@Component
public class CommissionScaleMapper
{
    public List<CommissionBracket> toBrackets(SaveCommissionScaleDTO scale)
    {
        return scale.brackets().stream()
                .map(bracket -> new CommissionBracket(bracket.lowerLimit(), bracket.ratePercent())).toList();
    }

    public SalesTierDTO toSalesTierDTO(CommissionScale scale)
    {
        return new SalesTierDTO(scale.getMinimumSales(), toCommissionScaleDTO(scale));
    }

    public RestaurantCommissionScaleDTO toRestaurantCommissionScaleDTO(CommissionScale scale)
    {
        return new RestaurantCommissionScaleDTO(scale.getRestaurant().getId(), scale.getRestaurant().getName(),
                toCommissionScaleDTO(scale));
    }

    private static CommissionScaleDTO toCommissionScaleDTO(CommissionScale scale)
    {
        return new CommissionScaleDTO(scale.getServiceFee(), scale.getBrackets().stream()
                .map(bracket -> new CommissionBracketDTO(bracket.getLowerLimit(), bracket.getRatePercent())).toList());
    }
}
