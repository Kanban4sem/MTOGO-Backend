package mtogo.feature.commission;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import mtogo.feature.commission.presentation.dto.request.ReplaceSalesTiersDTO;
import mtogo.feature.commission.presentation.dto.request.SaveCommissionScaleDTO;
import mtogo.feature.order.OrderTestData;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

// The base for tests that change the commission scales, which puts back the
// scales the migration seeds before each test, so every test starts with the
// default scale. Also builds commission scales from the short forms the tests
// and their CSV files use.
public abstract class CommissionTestData extends OrderTestData
{
    // The default scale from ADR-009.
    public static final String DEFAULT_BRACKETS = "0.00:6.00;100.00:5.00;500.00:4.00;1000.00:3.00";

    // Every test database is migrated the same way, so the scales read before the
    // first change serve every test, and the seed stays in the migration alone.
    private static List<Map<String, Object>> seededScales;
    private static List<Map<String, Object>> seededBrackets;

    @BeforeEach
    void restoreSeededScales()
    {
        if (seededScales == null)
        {
            seededScales = jdbc.queryForList("SELECT * FROM commission_scales");
            seededBrackets = jdbc.queryForList("SELECT * FROM commission_brackets");
        }
        jdbc.update("DELETE FROM commission_scales");
        SimpleJdbcInsert scales = new SimpleJdbcInsert(jdbc).withTableName("commission_scales");
        seededScales.forEach(scales::execute);
        SimpleJdbcInsert brackets = new SimpleJdbcInsert(jdbc).withTableName("commission_brackets");
        seededBrackets.forEach(brackets::execute);
    }

    // Reads brackets written as "lowerLimit:ratePercent", separated by semicolons.
    public static SaveCommissionScaleDTO scale(String serviceFee, String brackets)
    {
        return new SaveCommissionScaleDTO(new BigDecimal(serviceFee), split(brackets).stream().map(bracket -> {
            String[] parts = bracket.split(":");
            return new SaveCommissionScaleDTO.Bracket(new BigDecimal(parts[0]), new BigDecimal(parts[1]));
        }).toList());
    }

    public static ReplaceSalesTiersDTO.SalesTier tier(String minimumSales, SaveCommissionScaleDTO scale)
    {
        return new ReplaceSalesTiersDTO.SalesTier(new BigDecimal(minimumSales), scale);
    }

    // Reads decimals separated by semicolons. An empty string is an empty list.
    public static List<BigDecimal> decimals(String decimals)
    {
        return split(decimals).stream().map(BigDecimal::new).toList();
    }

    private static List<String> split(String list)
    {
        return Arrays.stream(list.split(";")).filter(item -> !item.isEmpty()).toList();
    }
}
