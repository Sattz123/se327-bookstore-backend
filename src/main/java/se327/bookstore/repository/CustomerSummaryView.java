package se327.bookstore.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface CustomerSummaryView {
    Long getId();
    String getUsername();
    String getEmail();
    String getPhone();
    Instant getCreatedAt();
    long getOrderCount();
    BigDecimal getTotalSpent();
}
