package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.OrderDao;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.OrderDto;
import se327.bookstore.dto.OrderSummaryDto;
import se327.bookstore.exception.UserNotFoundException;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class OrderService {
    private final OrderDao orderDao;
    private final UserDao userDao;
    private final CheckoutService checkoutService;

    public List<OrderSummaryDto> getOrders(String email) {
        var user = userDao.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found."));
        return orderDao.findByUserId(user.getId()).stream().map(order -> new OrderSummaryDto(order.getId(), order.getStatus(),
                order.getPaymentDisplay(), order.getTotal(), order.getCreatedAt())).toList();
    }

    public OrderDto getOrder(String email, Long id) {
        return checkoutService.confirmation(email, id);
    }
}
