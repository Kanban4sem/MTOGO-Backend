package mtogo.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class FeatureGateTest
{
    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/security/feature-gate/is-closed-for.csv", numLinesToSkip = 1)
    @DisplayName("Is closed for - closes the gated path and the paths below it while the flag is off")
    void isClosedForDependsOnFlagAndPath(boolean isEnabled, String requestPath, boolean expected, String reason)
    {
        FeatureGate gate = new FeatureGate("/v1/users", () -> isEnabled, "Authentication is not available");

        assertThat(gate.isClosedFor(requestPath), is(expected));
    }
}
