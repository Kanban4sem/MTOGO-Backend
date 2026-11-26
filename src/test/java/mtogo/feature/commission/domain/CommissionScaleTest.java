package mtogo.feature.commission.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import mtogo.feature.commission.CommissionTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class CommissionScaleTest
{
    enum Scale
    {
        DEFAULT("5.00", CommissionTestData.DEFAULT_BRACKETS),
        // Rates so small that a bracket's part can land exactly on half an øre.
        TINY_RATES("0.00", "0.00:0.01;50.00:0.01");

        final String serviceFee;
        final String brackets;

        Scale(String serviceFee, String brackets)
        {
            this.serviceFee = serviceFee;
            this.brackets = brackets;
        }
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/commission/commission-scale/calculate-commission.csv", numLinesToSkip = 1)
    @DisplayName("Calculate - adds up marginal brackets on the value excluding VAT, rounds once and adds the service fee")
    void calculateUsesMarginalBrackets(Scale scale, String orderTotal, String expectedCommission,
            String expectedServiceFee, String reason)
    {
        CommissionScale commissionScale = scale(scale.serviceFee, scale.brackets);

        CommissionAmounts result = commissionScale.calculate(new BigDecimal(orderTotal));

        assertThat(result,
                is(new CommissionAmounts(new BigDecimal(expectedCommission), new BigDecimal(expectedServiceFee))));
    }

    private static CommissionScale scale(String serviceFee, String brackets)
    {
        return CommissionScale.salesTier(BigDecimal.ZERO, new BigDecimal(serviceFee),
                new CommissionScaleMapper().toBrackets(CommissionTestData.scale(serviceFee, brackets)));
    }
}
