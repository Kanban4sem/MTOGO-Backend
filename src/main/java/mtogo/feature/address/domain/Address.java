package mtogo.feature.address.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Profile addresses have no street; delivery addresses always have one.
    @Column(length = 100)
    private String street;
    @ManyToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "postal_code_id", nullable = false)
    private PostalCode postalCode;
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
