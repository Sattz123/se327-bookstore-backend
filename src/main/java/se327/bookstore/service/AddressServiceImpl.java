package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.AddressDao;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.AddressDto;
import se327.bookstore.dto.AddressRequest;
import se327.bookstore.entity.Address;
import se327.bookstore.entity.User;
import se327.bookstore.exception.UserNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AddressServiceImpl implements AddressService {
    private final AddressDao addressDao;
    private final UserDao userDao;

    @Override
    @Transactional(readOnly = true)
    public List<AddressDto> getAddresses(String email) {
        User user = userDao.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found."));
        return addressDao.findByUserId(user.getId()).stream().map(this::toDto).toList();
    }

    @Override
    public AddressDto addAddress(String email, AddressRequest request) {
        User user = lockUser(email);
        Address address = Address.builder().user(user).build();
        if (request.isDefault()) addressDao.clearDefault(user.getId());
        apply(address, request);
        return toDto(addressDao.save(address));
    }

    @Override
    public AddressDto updateAddress(String email, Long id, AddressRequest request) {
        User user = lockUser(email);
        Address address = ownedAddress(id, user);
        if (request.isDefault()) addressDao.clearDefault(user.getId());
        apply(address, request);
        return toDto(addressDao.save(address));
    }

    @Override
    public AddressDto setDefault(String email, Long id) {
        User user = lockUser(email);
        Address address = ownedAddress(id, user);
        addressDao.clearDefault(user.getId());
        address.setDefaultAddress(true);
        return toDto(addressDao.save(address));
    }

    @Override
    public void deleteAddress(String email, Long id) {
        User user = lockUser(email);
        addressDao.delete(ownedAddress(id, user));
    }

    private User lockUser(String email) {
        // Serialize address writes for one customer, including their first default address.
        return userDao.findByEmailForUpdate(email)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }

    private Address ownedAddress(Long id, User user) {
        return addressDao.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found."));
    }

    private void apply(Address address, AddressRequest request) {
        address.setLabel(request.label().strip());
        address.setFullName(request.fullName().strip());
        address.setAddressLine(request.addressLine().strip());
        address.setPhone(optional(request.phone()));
        address.setCity(optional(request.city()));
        address.setProvince(optional(request.province()));
        address.setPostalCode(optional(request.postalCode()));
        address.setDefaultAddress(request.isDefault());
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private AddressDto toDto(Address address) {
        return new AddressDto(address.getId(), address.getLabel(), address.getFullName(), address.getPhone(),
                address.getAddressLine(), address.getCity(), address.getProvince(), address.getPostalCode(),
                address.isDefaultAddress(), address.getCreatedAt(), address.getUpdatedAt());
    }
}
