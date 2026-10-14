package se327.bookstore.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import se327.bookstore.dao.OrderDao;
import se327.bookstore.dto.AdminDashboardDto;
import se327.bookstore.entity.OrderStatus;
import se327.bookstore.entity.PurchaseOrder;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.repository.DashboardTotalsView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTests {

    @Mock
    private OrderDao orders;

    @InjectMocks
    private AdminDashboardService service;

    @Test
    void get_returnsDashboardTotalsAndRecentOrders() {
        DashboardTotalsView totals = new DashboardTotalsView() {
            @Override public long getTotalProducts() { return 42; }
            @Override public long getTotalOrders() { return 15; }
            @Override public long getTotalCustomers() { return 8; }
        };
        User user = User.builder().id(1L).username("testuser").email("user@example.com").role(Role.CUSTOMER).build();
        PurchaseOrder order = PurchaseOrder.builder()
                .id(101L)
                .user(user)
                .status(OrderStatus.COMPLETED)
                .paymentDisplay("Cash on delivery")
                .total(new BigDecimal("50.00"))
                .createdAt(Instant.now())
                .build();
        Page<PurchaseOrder> page = new PageImpl<>(List.of(order));

        when(orders.dashboardTotals()).thenReturn(totals);
        when(orders.findAdminOrders(eq(""), isNull(), any(Pageable.class))).thenReturn(page);

        AdminDashboardDto dto = service.get(1);

        assertThat(dto.totalProducts()).isEqualTo(42);
        assertThat(dto.totalOrders()).isEqualTo(15);
        assertThat(dto.totalCustomers()).isEqualTo(8);
        assertThat(dto.recentOrders()).hasSize(1);
        assertThat(dto.recentOrders().getFirst().id()).isEqualTo(101L);
        assertThat(dto.recentOrders().getFirst().customerName()).isEqualTo("testuser");
    }
}
