package se327.bookstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "orders")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable = false)
    private long version;
    @org.hibernate.annotations.UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "checkout_key", nullable = false)
    private UUID checkoutKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OrderStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;
    @Column(name = "payment_display", nullable = false, length = 100)
    private String paymentDisplay;
    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;
    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;
    @Column(name = "shipping_address", nullable = false, columnDefinition = "text")
    private String shippingAddress;
    @Column(nullable = false, precision = 24, scale = 2)
    private BigDecimal total;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Builder.Default @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();
}
