package se327.bookstore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.OrderDao;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.AdminOrderDetailDto;
import se327.bookstore.dto.AdminOrderPageDto;
import se327.bookstore.dto.AdminOrderStatusRequest;
import se327.bookstore.entity.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTests {

    @Mock
    private OrderDao orders;

    @Mock
    private ProductDao products;

    @InjectMocks
    private AdminOrderService service;

    private User customer;
    private Product product;
    private PurchaseOrder order;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(10L)
                .username("Alice")
                .email("alice@example.com")
                .phone("0812345678")
                .role(Role.CUSTOMER)
                .build();

        product = Product.builder()
                .id(100L)
                .name("Clean Architecture")
                .price(new BigDecimal("35.00"))
                .stockQuantity(10)
                .version(1L)
                .build();

        order = PurchaseOrder.builder()
                .id(50L)
                .user(customer)
                .checkoutKey(UUID.randomUUID())
                .requestHash("hash-value")
                .status(OrderStatus.PENDING)
                .paymentType(PaymentType.CASH_ON_DELIVERY)
                .paymentDisplay("Cash on delivery")
                .recipientName("Alice")
                .shippingAddress("123 Street")
                .total(new BigDecimal("70.00"))
                .version(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .order(order)
                .productId(product.getId())
                .title(product.getName())
                .format(BookFormat.PAPERBACK)
                .quantity(2)
                .unitPrice(new BigDecimal("35.00"))
                .lineTotal(new BigDecimal("70.00"))
                .build();
        order.getItems().add(item);
    }

    @Test
    void listAdminOrdersWithQueryAndStatusFilter() {
        Page<PurchaseOrder> page = new PageImpl<>(List.of(order), Pageable.unpaged(), 1);
        when(orders.findAdminOrders(eq("Alice"), eq(OrderStatus.PENDING), any())).thenReturn(page);

        AdminOrderPageDto result = service.list(" Alice ", OrderStatus.PENDING, 1);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().customerEmail()).isEqualTo("alice@example.com");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getOrderDetailSuccessfully() {
        when(orders.findById(50L)).thenReturn(Optional.of(order));

        AdminOrderDetailDto detail = service.get(50L);

        assertThat(detail.order().id()).isEqualTo(50L);
        assertThat(detail.customerEmail()).isEqualTo("alice@example.com");
        assertThat(detail.allowedStatuses()).contains(OrderStatus.PROCESSING, OrderStatus.COMPLETED, OrderStatus.CANCELLED);
    }

    @Test
    void getOrderDetailThrowsNotFoundWhenMissing() {
        when(orders.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateStatusFromPendingToProcessing() {
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.PROCESSING, 1L);
        AdminOrderDetailDto updated = service.updateStatus(50L, req);

        assertThat(updated.order().status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orders).save(order);
        verifyNoInteractions(products);
    }

    @Test
    void updateStatusFromProcessingToCompleted() {
        order.setStatus(OrderStatus.PROCESSING);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.COMPLETED, 1L);
        AdminOrderDetailDto updated = service.updateStatus(50L, req);

        assertThat(updated.order().status()).isEqualTo(OrderStatus.COMPLETED);
        verifyNoInteractions(products);
    }

    @Test
    void updateStatusFromCompletedToCancelledRestoresStock() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(products.findByIdForUpdate(100L)).thenReturn(Optional.of(product));
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.CANCELLED, 1L);
        AdminOrderDetailDto updated = service.updateStatus(50L, req);

        assertThat(updated.order().status()).isEqualTo(OrderStatus.CANCELLED);
        // Initially 10 stock, purchased 2 items -> restored stock must be 12
        assertThat(product.getStockQuantity()).isEqualTo(12);
        verify(orders).save(order);
    }

    @Test
    void updateStatusFromProcessingToCancelledRestoresStock() {
        order.setStatus(OrderStatus.PROCESSING);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(products.findByIdForUpdate(100L)).thenReturn(Optional.of(product));
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.CANCELLED, 1L);
        AdminOrderDetailDto updated = service.updateStatus(50L, req);

        assertThat(updated.order().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(12);
    }

    @Test
    void updateStatusFromPendingToCancelledRestoresStock() {
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(products.findByIdForUpdate(100L)).thenReturn(Optional.of(product));
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.CANCELLED, 1L);
        AdminOrderDetailDto updated = service.updateStatus(50L, req);

        assertThat(updated.order().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(12);
    }

    @Test
    void updateStatusIdempotentWhenAlreadyInRequestedStatus() {
        order.setStatus(OrderStatus.PROCESSING);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.PROCESSING, 1L);
        AdminOrderDetailDto result = service.updateStatus(50L, req);

        assertThat(result.order().status()).isEqualTo(OrderStatus.PROCESSING);
        verify(orders, never()).save(any());
        verifyNoInteractions(products);
    }

    @Test
    void updateStatusRejectsVersionMismatchConflict() {
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.PROCESSING, 999L);

        assertThatThrownBy(() -> service.updateStatus(50L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateStatusRejectsDisallowedTransitionFromCancelled() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.PROCESSING, 1L);

        assertThatThrownBy(() -> service.updateStatus(50L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateStatusRejectsDisallowedTransitionFromCompletedToProcessing() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.PROCESSING, 1L);

        assertThatThrownBy(() -> service.updateStatus(50L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateStatusThrowsConflictWhenRestoringProductIsMissing() {
        order.setStatus(OrderStatus.PENDING);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(products.findByIdForUpdate(100L)).thenReturn(Optional.empty());

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.CANCELLED, 1L);

        assertThatThrownBy(() -> service.updateStatus(50L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateStatusThrowsNotFoundWhenOrderMissing() {
        when(orders.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        AdminOrderStatusRequest req = new AdminOrderStatusRequest(OrderStatus.PROCESSING, 1L);

        assertThatThrownBy(() -> service.updateStatus(999L, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
