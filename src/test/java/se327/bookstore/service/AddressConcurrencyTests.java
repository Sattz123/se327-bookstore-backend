package se327.bookstore.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se327.bookstore.entity.Address;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.repository.AddressRepository;
import se327.bookstore.repository.UserRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=address_concurrency_test",
        "spring.flyway.default-schema=address_concurrency_test",
        "spring.flyway.schemas=address_concurrency_test",
        "spring.jpa.properties.hibernate.default_schema=address_concurrency_test"
})
@ActiveProfiles("test")
class AddressConcurrencyTests {
    @Autowired private AddressService addressService;
    @Autowired private AddressRepository addressRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void concurrentDefaultChangesBothSucceedAndLeaveExactlyOneDefault() throws Exception {
        User user = userRepository.saveAndFlush(User.builder().username("Concurrent reader")
                .email("concurrent.address@example.com").passwordHash("test-hash").role(Role.CUSTOMER).build());
        var executor = Executors.newFixedThreadPool(2);
        try {
            Address first = saveAddress(user, "First");
            Address second = saveAddress(user, "Second");
            var start = new CountDownLatch(1);
            var one = executor.submit(() -> { start.await(); return addressService.setDefault(user.getEmail(), first.getId()); });
            var two = executor.submit(() -> { start.await(); return addressService.setDefault(user.getEmail(), second.getId()); });
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS).isDefault()).isTrue();
            assertThat(two.get(15, TimeUnit.SECONDS).isDefault()).isTrue();
            assertThat(addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(user.getId()))
                    .hasSize(2).filteredOn(Address::isDefaultAddress).hasSize(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            userRepository.deleteById(user.getId());
        }
    }

    private Address saveAddress(User user, String label) {
        return addressRepository.saveAndFlush(Address.builder().user(user).label(label)
                .fullName("Reader").addressLine("Test street").build());
    }
}
