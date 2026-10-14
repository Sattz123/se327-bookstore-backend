package se327.bookstore.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "payment_methods")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank @Size(max = 50) @Column(nullable = false, length = 50)
    private String provider;

    @NotBlank @Size(max = 100) @Column(name = "cardholder_name", nullable = false, length = 100)
    private String cardholderName;

    @NotNull @Pattern(regexp = "[0-9]{4}") @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    @Min(1) @Max(12) @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Min(2000) @Max(9999) @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
