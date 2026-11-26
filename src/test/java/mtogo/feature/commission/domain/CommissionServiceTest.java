package mtogo.feature.commission.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import mtogo.exceptions.ApiException;
import mtogo.feature.commission.presentation.dto.request.ReplaceSalesTiersDTO;
import mtogo.feature.commission.presentation.dto.request.SaveCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.CommissionBracketDTO;
import mtogo.feature.commission.presentation.dto.response.CommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.RestaurantCommissionScaleDTO;
import mtogo.feature.commission.presentation.dto.response.SalesTierDTO;
import mtogo.feature.commission.CommissionTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CommissionServiceTest extends CommissionTestData
{
    @Autowired
    CommissionService commissions;

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/commission/commission-service/replace-tiers.csv", numLinesToSkip = 1)
    @DisplayName("Replace tiers - valid tiers from a minimum of 0.00, ascending, or the tiers are left unchanged")
    void replaceTiersChecksTheList(String minimums, boolean hasInvalidScale, String expected, String reason)
    {
        List<BigDecimal> minimumSales = decimals(minimums);
        // With an invalid scale, the last tier by position gets it.
        List<ReplaceSalesTiersDTO.SalesTier> tiers = IntStream.range(0, minimumSales.size()).mapToObj(i -> {
            boolean isInvalid = hasInvalidScale && i == minimumSales.size() - 1;
            return new ReplaceSalesTiersDTO.SalesTier(minimumSales.get(i),
                    scale("5.00", isInvalid ? "0.01:6.00" : "0.00:6.00"));
        }).toList();
        ReplaceSalesTiersDTO request = new ReplaceSalesTiersDTO(tiers);
        List<SalesTierDTO> tiersBefore = commissions.getAll().tiers();

        if (expected.equals("ACCEPT"))
        {
            List<SalesTierDTO> result = commissions.replaceTiers(request);
            assertThat(result.stream().map(SalesTierDTO::minimumSales).toList(), is(minimumSales));
            assertThat(commissions.getAll().tiers(), is(result));
        } else
        {
            ApiException exception = assertThrows(ApiException.class, () -> commissions.replaceTiers(request));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(commissions.getAll().tiers(), is(tiersBefore));
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/commission/commission-service/save-restaurant-scale.csv", numLinesToSkip = 1)
    @DisplayName("Save restaurant scale - brackets start at 0, their lower limits ascend and their rates never rise, or nothing is saved")
    void saveRestaurantScaleChecksTheBrackets(String brackets, String expected, String reason)
    {
        long restaurantId = restaurant("Bakery", true);
        SaveCommissionScaleDTO request = scale("5.00", brackets);

        if (expected.equals("ACCEPT"))
        {
            RestaurantCommissionScaleDTO result = commissions.saveRestaurantScale(restaurantId, request);
            assertThat(result.scale().brackets(), hasSize(request.brackets().size()));
            assertThat(commissions.getAll().restaurants(), contains(result));
        } else
        {
            ApiException exception = assertThrows(ApiException.class,
                    () -> commissions.saveRestaurantScale(restaurantId, request));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(commissions.getAll().restaurants(), is(empty()));
        }
    }

    @Test
    @DisplayName("Save restaurant scale - giving a restaurant a scale when it already has one replaces it")
    void saveRestaurantScaleReplacesTheExistingScale()
    {
        long restaurantId = restaurant("Bakery", true);
        commissions.saveRestaurantScale(restaurantId, scale("2.00", "0.00:1.00"));

        RestaurantCommissionScaleDTO result = commissions.saveRestaurantScale(restaurantId,
                scale("3.00", "0.00:4.00;200.00:2.00"));

        RestaurantCommissionScaleDTO expected = new RestaurantCommissionScaleDTO(restaurantId, "Bakery",
                new CommissionScaleDTO(new BigDecimal("3.00"),
                        List.of(new CommissionBracketDTO(new BigDecimal("0.00"), new BigDecimal("4.00")),
                                new CommissionBracketDTO(new BigDecimal("200.00"), new BigDecimal("2.00")))));
        assertThat(result, is(expected));
        assertThat(commissions.getAll().restaurants(), contains(expected));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_brackets", Long.class), is(6L));
    }
}
