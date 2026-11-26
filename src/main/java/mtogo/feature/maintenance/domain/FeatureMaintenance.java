package mtogo.feature.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feature_maintenance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeatureMaintenance
{
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private MaintainableFeature feature;

    @Column(name = "is_paused", nullable = false)
    private boolean isPaused;
}
