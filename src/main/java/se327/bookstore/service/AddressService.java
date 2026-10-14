package se327.bookstore.service;

import se327.bookstore.dto.AddressDto;
import se327.bookstore.dto.AddressRequest;

import java.util.List;

public interface AddressService {
    List<AddressDto> getAddresses(String email);
    AddressDto addAddress(String email, AddressRequest request);
    AddressDto updateAddress(String email, Long id, AddressRequest request);
    AddressDto setDefault(String email, Long id);
    void deleteAddress(String email, Long id);
}
