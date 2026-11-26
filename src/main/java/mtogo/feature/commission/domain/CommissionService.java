package mtogo.feature.commission.domain;

import java.math.BigDecimal;
import java.util.List;
import mtogo.exceptions.BadRequestException;
import mtogo.exceptions.NotFoundException;
import mtogo.feature.commission.data.CommissionScaleRepository;
import mtogo.feature.commission.presentation.dto.request.ReplaceSalesTiersDTO;
import mtogo.feature.commission.presentation.dto.request.SaveCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.CommissionScalesDTO;
import mtogo.feature.commission.presentation.dto.response.RestaurantCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.SalesTierDTO;
import mtogo.feature.restaurant.data.RestaurantRepository;
import mtogo.feature.restaurant.domain.Restaurant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionService
{
    private final CommissionScaleRepository scales;
    private final RestaurantRepository restaurants;
    private final CommissionScaleMapper mapper;

    public CommissionService(CommissionScaleRepository scales, RestaurantRepository restaurants,
            CommissionScaleMapper mapper)
    {
        this.scales = scales;
        this.restaurants = restaurants;
        this.mapper = mapper;
    }

    /**
     * Calculates what the restaurant pays for a delivered order with the scale that
     * applies now: its own scale, or else the sales tier with the highest minimum
     * that its sales reach. The caller works out the sales, since this feature
     * reads no orders.
     */
    @Transactional
    public CommissionAmounts calculate(Long restaurantId, BigDecimal sales, BigDecimal orderTotal)
    {
        // The default scale has a minimum of 0.00 and can't be removed, so a tier
        // is always found.
        CommissionScale scale = scales.findByRestaurantId(restaurantId)
                .or(() -> scales.findFirstByMinimumSalesLessThanEqualOrderByMinimumSalesDesc(sales)).orElseThrow();
        return scale.calculate(orderTotal);
    }

    @Transactional(readOnly = true)
    public CommissionScalesDTO getAll()
    {
        return new CommissionScalesDTO(
                scales.findAllByMinimumSalesIsNotNullOrderByMinimumSalesAsc().stream().map(mapper::toSalesTierDTO)
                        .toList(),
                scales.findAllByRestaurantIsNotNullOrderByRestaurantNameAscIdAsc().stream()
                        .map(mapper::toRestaurantCommissionScaleDTO).toList());
    }

    /**
     * Replaces every sales tier. An invalid list changes nothing.
     *
     * @throws BadRequestException
     *             when the list or one of its scales breaks the rules
     */
    @Transactional
    public List<SalesTierDTO> replaceTiers(ReplaceSalesTiersDTO request)
    {
        requireAscendingFromZero(request.tiers().stream().map(ReplaceSalesTiersDTO.SalesTier::minimumSales).toList(),
                "The first sales tier, the default scale, must have a minimum of 0.00 DKK",
                "The minimums of the sales tiers must strictly ascend");
        request.tiers().forEach(tier -> requireValidBrackets(tier.scale()));
        List<CommissionScale> tiers = request.tiers().stream().map(tier -> CommissionScale
                .salesTier(tier.minimumSales(), tier.scale().serviceFee(), mapper.toBrackets(tier.scale()))).toList();
        replace(scales.findAllByMinimumSalesIsNotNullOrderByMinimumSalesAsc(), tiers);
        return tiers.stream().map(mapper::toSalesTierDTO).toList();
    }

    /**
     * Gives the restaurant its own scale, replacing the one it has.
     *
     * @throws NotFoundException
     *             when the restaurant doesn't exist
     * @throws BadRequestException
     *             when the scale breaks the rules
     */
    @Transactional
    public RestaurantCommissionScaleDTO saveRestaurantScale(Long restaurantId, SaveCommissionScaleDTO request)
    {
        Restaurant restaurant = requireRestaurant(restaurantId);
        requireValidBrackets(request);
        CommissionScale scale = CommissionScale.ownScale(restaurant, request.serviceFee(), mapper.toBrackets(request));
        replace(scales.findByRestaurantId(restaurantId).stream().toList(), List.of(scale));
        return mapper.toRestaurantCommissionScaleDTO(scale);
    }

    /**
     * Removes the restaurant's own scale, so the sales tiers apply again. Removing
     * a scale the restaurant doesn't have changes nothing.
     *
     * @throws NotFoundException
     *             when the restaurant doesn't exist
     */
    @Transactional
    public void removeRestaurantScale(Long restaurantId)
    {
        requireRestaurant(restaurantId);
        scales.findByRestaurantId(restaurantId).ifPresent(scales::delete);
    }

    private void replace(List<CommissionScale> existing, List<CommissionScale> replacements)
    {
        scales.deleteAll(existing);
        // Hibernate inserts before it deletes, which would break the unique minimum
        // and the unique restaurant.
        scales.flush();
        scales.saveAll(replacements);
    }

    private Restaurant requireRestaurant(Long restaurantId)
    {
        return restaurants.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found with id: " + restaurantId));
    }

    // The rules on a single field, such as the ranges and the number of brackets,
    // are checked on the request. These compare brackets with each other.
    private static void requireValidBrackets(SaveCommissionScaleDTO scale)
    {
        List<SaveCommissionScaleDTO.Bracket> brackets = scale.brackets();
        requireAscendingFromZero(brackets.stream().map(SaveCommissionScaleDTO.Bracket::lowerLimit).toList(),
                "The first commission bracket must start at 0.00 DKK",
                "The lower limits of the commission brackets must strictly ascend");
        for (int i = 1; i < brackets.size(); i++)
        {
            if (brackets.get(i).ratePercent().compareTo(brackets.get(i - 1).ratePercent()) > 0)
            {
                throw new BadRequestException(
                        "A commission rate cannot be higher than the rate of the bracket before it");
            }
        }
    }

    // The minimums of the sales tiers and the lower limits of the commission
    // brackets both start at 0.00 and strictly ascend.
    private static void requireAscendingFromZero(List<BigDecimal> values, String notFromZero, String notAscending)
    {
        if (values.isEmpty() || values.getFirst().signum() != 0)
        {
            throw new BadRequestException(notFromZero);
        }
        for (int i = 1; i < values.size(); i++)
        {
            if (values.get(i).compareTo(values.get(i - 1)) <= 0)
            {
                throw new BadRequestException(notAscending);
            }
        }
    }
}
