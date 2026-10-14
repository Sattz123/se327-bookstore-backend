package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.Address;
import se327.bookstore.repository.AddressRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AddressDaoImpl implements AddressDao {
    private final AddressRepository addressRepository;

    @Override
    public List<Address> findByUserId(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(userId);
    }

    @Override
    public Optional<Address> findByIdAndUserId(Long id, Long userId) {
        return addressRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public Address save(Address address) {
        return addressRepository.saveAndFlush(address);
    }

    @Override
    public void clearDefault(Long userId) {
        addressRepository.findByUserIdAndDefaultAddressTrue(userId).ifPresent(address -> {
            address.setDefaultAddress(false);
            addressRepository.saveAndFlush(address);
        });
    }

    @Override
    public void delete(Address address) {
        addressRepository.delete(address);
    }
}
