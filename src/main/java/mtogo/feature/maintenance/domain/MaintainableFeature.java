package mtogo.feature.maintenance.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MaintainableFeature
{
    AUTHENTICATION("Login and registration are temporarily unavailable."), ORDERING(
            "Placing orders is temporarily unavailable.");

    private final String pauseMessage;
}
