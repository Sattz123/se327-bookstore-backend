package se327.bookstore.dao;

import se327.bookstore.entity.Address;

import java.util.List;
import java.util.Optional;

public interface AddressDao {
    List<Address> findByUserId(Long userId);
    Optional<Address> findByIdAndUserId(Long id, Long userId);
    Address save(Address address);
    void clearDefault(Long userId);
    void delete(Address address);
}
