package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.OrderDao;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.*;
import se327.bookstore.entity.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional
public class AdminOrderService {
    private final OrderDao orders;
    private final ProductDao products;

    @Transactional(readOnly = true)
    public AdminOrderPageDto list(String query, OrderStatus status, int page) {
        var result = orders.findAdminOrders(query.strip(), status, PageRequest.of(page - 1, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new AdminOrderPageDto(result.stream().map(o -> new AdminOrderSummaryDto(o.getId(), o.getUser().getId(), o.getUser().getUsername(),
                o.getUser().getEmail(), o.getStatus(), o.getPaymentDisplay(), o.getTotal(), o.getCreatedAt())).toList(), page, 20, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public AdminOrderDetailDto get(Long id) { return toDto(orders.findById(id).orElseThrow(this::notFound)); }

    public AdminOrderDetailDto updateStatus(Long id, AdminOrderStatusRequest request) {
        PurchaseOrder order = orders.findByIdForUpdate(id).orElseThrow(this::notFound);
        // Retrying a state already applied is harmless, including cancellation with an older version.
        if (order.getStatus() == request.status()) return toDto(order);
        if (order.getVersion() != request.version()) throw conflict("This order changed. Reload before updating its status.");
        if (!allowed(order.getStatus()).contains(request.status())) throw conflict("That status transition is not allowed.");
        if (request.status() == OrderStatus.CANCELLED) restoreStock(order);
        order.setStatus(request.status());
        return toDto(orders.save(order));
    }
    private void restoreStock(PurchaseOrder order) {
        Map<Long, Long> quantities = order.getItems().stream().collect(Collectors.groupingBy(OrderItem::getProductId, TreeMap::new,
                Collectors.summingLong(OrderItem::getQuantity)));
        for (var entry : quantities.entrySet()) {
            Product product = products.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> conflict("A purchased product is missing. Cancellation was not applied."));
            long restored = product.getStockQuantity() + entry.getValue();
            if (restored > Integer.MAX_VALUE) throw conflict("Restoring stock would exceed its limit. Adjust inventory before cancelling.");
            product.setStockQuantity((int) restored);
        }
    }
    private List<OrderStatus> allowed(OrderStatus status) {
        return switch (status) {
            case PENDING -> List.of(OrderStatus.PROCESSING, OrderStatus.COMPLETED, OrderStatus.CANCELLED);
            case PROCESSING -> List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
            case COMPLETED -> List.of(OrderStatus.CANCELLED);
            case CANCELLED -> List.of();
        };
    }
    private AdminOrderDetailDto toDto(PurchaseOrder order) {
        var customer = order.getUser();
        return new AdminOrderDetailDto(OrderDto.fromEntity(order), customer.getId(), customer.getUsername(), customer.getEmail(), customer.getPhone(),
                order.getVersion(), order.getUpdatedAt(), allowed(order.getStatus()));
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
