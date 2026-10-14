package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.AdminCustomerPageDto;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminCustomerService {
    private final UserDao users;
    public AdminCustomerPageDto list(String query, int page) {
        var result = users.findCustomers(query.strip(), PageRequest.of(page - 1, 20));
        return new AdminCustomerPageDto(result.getContent(), page, 20, result.getTotalElements(), result.getTotalPages());
    }
}
