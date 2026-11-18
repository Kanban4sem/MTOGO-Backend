package mtogo.security;

import java.util.function.BooleanSupplier;

// A feature declares a FeatureGate bean for the path its endpoints live under,
// so FeatureGateFilter never changes when a flag is added or removed.
public record FeatureGate(String path, BooleanSupplier isEnabled, String detail)
{
    public boolean isClosedFor(String requestPath)
    {
        return !isEnabled.getAsBoolean() && (requestPath.equals(path) || requestPath.startsWith(path + "/"));
    }
}
