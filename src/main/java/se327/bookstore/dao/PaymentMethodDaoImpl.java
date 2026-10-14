package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.PaymentMethod;
import se327.bookstore.repository.PaymentMethodRepository;
import java.util.List;
import java.util.Optional;

@Repository @RequiredArgsConstructor
public class PaymentMethodDaoImpl implements PaymentMethodDao {
    private final PaymentMethodRepository repository;

    @Override public List<PaymentMethod> findByUserId(Long userId) {
        return repository.findByUserIdOrderByDefaultMethodDescCreatedAtDescIdDesc(userId);
    }
    @Override public Optional<PaymentMethod> findByIdAndUserId(Long id, Long userId) {
        return repository.findByIdAndUserId(id, userId);
    }
    @Override public PaymentMethod save(PaymentMethod method) { return repository.saveAndFlush(method); }
    @Override public void delete(PaymentMethod method) { repository.delete(method); }
    @Override public void clearDefault(Long userId) {
        repository.findByUserIdAndDefaultMethodTrue(userId).ifPresent(method -> {
            method.setDefaultMethod(false);
            repository.saveAndFlush(method);
        });
    }
}
