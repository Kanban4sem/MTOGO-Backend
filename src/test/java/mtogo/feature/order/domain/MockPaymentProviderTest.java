package mtogo.feature.order.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class MockPaymentProviderTest
{
    private final MockPaymentProvider payments = new MockPaymentProvider();

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/mock-payment-provider/charge-token.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Charge - declines the decline token and blank tokens and confirms any other token")
    void chargeDependsOnToken(String paymentToken, boolean expected, String reason)
    {
        PaymentResult result = payments.charge(new BigDecimal("179.90"), paymentToken);

        assertThat(result.isConfirmed(), is(expected));
        assertThat(result.reference(), expected ? not(blankOrNullString()) : is(nullValue()));
    }
}
