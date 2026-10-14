package se327.bookstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "order_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(nullable = false)
    private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private BookFormat format;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "line_total", nullable = false, precision = 20, scale = 2)
    private BigDecimal lineTotal;
}
