package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.PaymentMethodDao;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.PaymentMethodDto;
import se327.bookstore.dto.PaymentMethodRequest;
import se327.bookstore.entity.PaymentMethod;
import se327.bookstore.entity.User;
import se327.bookstore.exception.UserNotFoundException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class PaymentMethodServiceImpl implements PaymentMethodService {
    private final PaymentMethodDao paymentMethodDao;
    private final UserDao userDao;

    @Override @Transactional(readOnly = true)
    public List<PaymentMethodDto> getPaymentMethods(String email) {
        User user = userDao.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found."));
        return paymentMethodDao.findByUserId(user.getId()).stream().map(this::toDto).toList();
    }

    @Override
    public PaymentMethodDto addPaymentMethod(String email, PaymentMethodRequest request) {
        if (YearMonth.of(request.expiryYear(), request.expiryMonth()).isBefore(YearMonth.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must not be in the past.");
        }
        User user = lockUser(email);
        if (request.isDefault()) paymentMethodDao.clearDefault(user.getId());
        PaymentMethod method = PaymentMethod.builder().user(user).provider(request.provider().strip())
                .cardholderName(request.cardholderName().strip()).lastFour(request.lastFour())
                .expiryMonth(request.expiryMonth()).expiryYear(request.expiryYear())
                .defaultMethod(request.isDefault()).build();
        return toDto(paymentMethodDao.save(method));
    }

    @Override
    public PaymentMethodDto setDefault(String email, Long id) {
        User user = lockUser(email);
        PaymentMethod method = ownedMethod(id, user);
        paymentMethodDao.clearDefault(user.getId());
        method.setDefaultMethod(true);
        return toDto(paymentMethodDao.save(method));
    }

    @Override
    public void deletePaymentMethod(String email, Long id) {
        User user = lockUser(email);
        paymentMethodDao.delete(ownedMethod(id, user));
    }

    private User lockUser(String email) {
        return userDao.findByEmailForUpdate(email).orElseThrow(() -> new UserNotFoundException("User not found."));
    }

    private PaymentMethod ownedMethod(Long id, User user) {
        return paymentMethodDao.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment method not found."));
    }

    private PaymentMethodDto toDto(PaymentMethod method) {
        return new PaymentMethodDto(method.getId(), method.getProvider(), method.getCardholderName(),
                "**** **** **** " + method.getLastFour(), method.getExpiryMonth(), method.getExpiryYear(),
                method.isDefaultMethod(), method.getCreatedAt());
    }
}
