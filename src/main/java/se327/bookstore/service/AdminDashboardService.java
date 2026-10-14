package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.OrderDao;
import se327.bookstore.dto.AdminDashboardDto;
import se327.bookstore.dto.AdminOrderSummaryDto;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final OrderDao orders;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDashboardDto get(int page) {
        var totals = orders.dashboardTotals();
        var recent = orders.findAdminOrders("", null,
                PageRequest.of(page - 1, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        var summaries = recent.stream().map(o -> new AdminOrderSummaryDto(o.getId(), o.getUser().getId(),
                o.getUser().getUsername(), o.getUser().getEmail(), o.getStatus(), o.getPaymentDisplay(),
                o.getTotal(), o.getCreatedAt())).toList();
        return new AdminDashboardDto(totals.getTotalProducts(), totals.getTotalOrders(), totals.getTotalCustomers(),
                summaries, page, 10, recent.getTotalPages());
    }
}
