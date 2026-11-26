package mtogo.feature.commission.domain;

import java.math.BigDecimal;

/**
 * What a restaurant pays MTOGO for one delivered order, in DKK with 2 decimals.
 */
public record CommissionAmounts(BigDecimal commission, BigDecimal serviceFee)
{
}
