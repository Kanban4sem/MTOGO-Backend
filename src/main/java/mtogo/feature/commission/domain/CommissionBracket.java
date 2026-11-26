package mtogo.feature.commission.domain;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "commission_brackets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionBracket
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_id", nullable = false)
    private CommissionScale scale;
    // In DKK excluding VAT. The bracket runs up to the next bracket's lower limit.
    @Column(name = "lower_limit", nullable = false, precision = 10, scale = 2)
    private BigDecimal lowerLimit;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    public CommissionBracket(BigDecimal lowerLimit, BigDecimal ratePercent)
    {
        this.lowerLimit = CommissionScale.twoDecimals(lowerLimit);
        this.ratePercent = CommissionScale.twoDecimals(ratePercent);
    }

    void setScale(CommissionScale scale)
    {
        this.scale = scale;
    }
}
