package mtogo.feature.commission.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtogo.feature.restaurant.domain.Restaurant;

/**
 * Commission brackets and a service fee. A scale is either a sales tier, with a
 * minimum sales amount, or a restaurant's own scale, never both.
 */
@Entity
@Table(name = "commission_scales")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionScale
{
    private static final BigDecimal VAT_FACTOR = new BigDecimal("1.25");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", unique = true)
    private Restaurant restaurant;
    @Column(name = "minimum_sales", unique = true, precision = 12, scale = 2)
    private BigDecimal minimumSales;
    @Column(name = "service_fee", nullable = false, precision = 6, scale = 2)
    private BigDecimal serviceFee;
    @OneToMany(mappedBy = "scale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lowerLimit")
    private List<CommissionBracket> brackets = new ArrayList<>();

    private CommissionScale(BigDecimal serviceFee, List<CommissionBracket> brackets)
    {
        this.serviceFee = twoDecimals(serviceFee);
        brackets.forEach(bracket -> {
            bracket.setScale(this);
            this.brackets.add(bracket);
        });
    }

    public static CommissionScale salesTier(BigDecimal minimumSales, BigDecimal serviceFee,
            List<CommissionBracket> brackets)
    {
        CommissionScale scale = new CommissionScale(serviceFee, brackets);
        scale.minimumSales = twoDecimals(minimumSales);
        return scale;
    }

    public static CommissionScale ownScale(Restaurant restaurant, BigDecimal serviceFee,
            List<CommissionBracket> brackets)
    {
        CommissionScale scale = new CommissionScale(serviceFee, brackets);
        scale.restaurant = restaurant;
        return scale;
    }

    /**
     * Calculates the commission with marginal brackets on the order value excluding
     * VAT, rounded once to øre after the brackets are added up, and adds the
     * service fee.
     */
    public CommissionAmounts calculate(BigDecimal orderTotal)
    {
        // Dividing by 1.25 always terminates, so the value stays exact.
        BigDecimal value = orderTotal.divide(VAT_FACTOR);
        BigDecimal commission = BigDecimal.ZERO;
        for (int i = 0; i < brackets.size(); i++)
        {
            BigDecimal lowerLimit = brackets.get(i).getLowerLimit();
            if (value.compareTo(lowerLimit) <= 0)
            {
                break;
            }
            BigDecimal upperLimit = i + 1 < brackets.size() ? brackets.get(i + 1).getLowerLimit() : null;
            BigDecimal top = upperLimit == null ? value : value.min(upperLimit);
            commission = commission
                    .add(top.subtract(lowerLimit).multiply(brackets.get(i).getRatePercent()).movePointLeft(2));
        }
        return new CommissionAmounts(commission.setScale(2, RoundingMode.HALF_UP), serviceFee);
    }

    // Amounts and rates keep 2 decimals, as in the database. A scale built from a
    // request still has the scale the client sent, such as 5.0. The request allows
    // at most 2 decimals, so no rounding happens.
    static BigDecimal twoDecimals(BigDecimal value)
    {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
