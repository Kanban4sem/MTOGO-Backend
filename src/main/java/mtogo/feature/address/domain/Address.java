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
    private static final String DELIVERY_COUNTRY = "Denmark";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Registration can store a postal area without a street.
    @Column(length = 100)
    private String street;
    @ManyToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "postal_code_id", nullable = false)
    private PostalCode postalCode;
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    /**
     * Changes the delivery address. MTOGO delivers in Denmark only, so the country
     * is always Denmark.
     */
    public void changeDeliveryAddress(String street, String code, String city)
    {
        this.street = street;
        if (postalCode == null)
        {
            postalCode = new PostalCode();
        }
        postalCode.setCode(code);
        postalCode.setCity(city);
        postalCode.setCountry(DELIVERY_COUNTRY);
    }
}
