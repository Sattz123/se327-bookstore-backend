package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.*;
import se327.bookstore.dto.*;
import se327.bookstore.entity.*;
import se327.bookstore.exception.UserNotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional
public class CheckoutServiceImpl implements CheckoutService {
    private final UserDao userDao;
    private final CartDao cartDao;
    private final ProductDao productDao;
    private final AddressDao addressDao;
    private final PaymentMethodDao paymentMethodDao;
    private final OrderDao orderDao;
    private final CartService cartService;

    @Override @Transactional(readOnly = true)
    public CheckoutReviewDto review(String email) {
        CartDto cart = cartService.getCart(email);
        return new CheckoutReviewDto(cart, fingerprint(cart.items()));
    }

    @Override
    public OrderDto checkout(String email, CheckoutRequest request) {
        User user = userDao.findByEmailForUpdate(email).orElseThrow(() -> new UserNotFoundException("User not found."));
        String requestHash = hash(request.addressId() + "|" + request.paymentType() + "|" + request.paymentMethodId() + "|" + request.cartFingerprint());
        Optional<PurchaseOrder> previous = orderDao.findByUserIdAndCheckoutKey(user.getId(), request.checkoutKey());
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(requestHash))
                throw conflict("This checkout key was already used for a different request.");
            return toDto(previous.get());
        }
        Address address = validateAddress(user, request.addressId());
        String paymentDisplay = validateAndFormatPayment(user, request);
        List<CartItem> entries = cartDao.findByUserId(user.getId());
        ValidatedCart validatedCart = validateAndLockCart(entries, request.cartFingerprint());

        PurchaseOrder order = PurchaseOrder.builder().user(user).checkoutKey(request.checkoutKey()).requestHash(requestHash)
                .status(request.paymentType() == PaymentType.CASH_ON_DELIVERY ? OrderStatus.PENDING : OrderStatus.COMPLETED)
                .paymentType(request.paymentType()).paymentDisplay(paymentDisplay).recipientName(address.getFullName())
                .shippingPhone(address.getPhone()).shippingAddress(shippingAddress(address))
                .total(validatedCart.items().stream().map(CartItemDto::lineTotal).reduce(new BigDecimal("0.00"), BigDecimal::add)).build();
        for (CartItemDto item : validatedCart.items()) {
            order.getItems().add(OrderItem.builder().order(order).productId(item.productId()).title(item.name()).format(item.format())
                    .quantity(item.quantity()).unitPrice(item.unitPrice()).lineTotal(item.lineTotal()).build());
        }
        for (var entry : validatedCart.quantities().entrySet()) {
            Product p = validatedCart.locked().get(entry.getKey());
            p.setStockQuantity(p.getStockQuantity() - Math.toIntExact(entry.getValue()));
        }
        PurchaseOrder saved = orderDao.save(order);
        entries.forEach(cartDao::delete);
        return toDto(saved);
    }

    @Override @Transactional(readOnly = true)
    public OrderDto confirmation(String email, Long orderId) {
        User user = userDao.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found."));
        return toDto(orderDao.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found.")));
    }

    private Address validateAddress(User user, Long addressId) {
        return addressDao.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found."));
    }

    private ValidatedCart validateAndLockCart(List<CartItem> entries, String expectedFingerprint) {
        if (entries.isEmpty()) throw conflict("Your cart is empty.");

        // Every checkout locks products in the same order, including purchases by different users.
        Map<Long, Product> locked = new TreeMap<>();
        for (Long id : entries.stream().map(i -> i.getProduct().getId()).distinct().sorted().toList()) {
            locked.put(id, productDao.findByIdForUpdate(id).orElseThrow(() -> conflict("A book is no longer available.")));
        }
        Map<Long, Long> quantities = entries.stream().collect(Collectors.groupingBy(i -> i.getProduct().getId(),
                Collectors.summingLong(CartItem::getQuantity)));
        List<CartItemDto> current = new ArrayList<>();
        for (CartItem item : entries) {
            Product p = locked.get(item.getProduct().getId());
            if (!p.isVisible() || !p.getCategory().isVisible() || !p.getFormats().contains(item.getFormat()))
                throw conflict("A selected book or format is no longer available. Review your cart.");
            if (quantities.get(p.getId()) > p.getStockQuantity())
                throw conflict("Insufficient stock. Review your cart.");
            current.add(new CartItemDto(item.getId(), p.getId(), p.getName(), null, item.getFormat(), item.getQuantity(),
                    p.getPrice(), p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())), p.getStockQuantity(), true));
        }
        if (!fingerprint(current).equals(expectedFingerprint))
            throw conflict("Your cart or its prices changed. Refresh the review before placing the order.");

        return new ValidatedCart(current, quantities, locked);
    }

    private record ValidatedCart(List<CartItemDto> items, Map<Long, Long> quantities, Map<Long, Product> locked) {}

    private String validateAndFormatPayment(User user, CheckoutRequest request) {
        if (request.paymentType() == PaymentType.CASH_ON_DELIVERY) {
            if (request.paymentMethodId() != null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cash on delivery does not use a saved card.");
            return "Cash on delivery";
        }
        if (request.paymentMethodId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a saved simulated card.");
        PaymentMethod method = paymentMethodDao.findByIdAndUserId(request.paymentMethodId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment method not found."));
        if (YearMonth.of(method.getExpiryYear(), method.getExpiryMonth()).isBefore(YearMonth.now(ZoneOffset.UTC)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected card has expired.");
        return method.getProvider() + " **** **** **** " + method.getLastFour();
    }
    private String shippingAddress(Address address) {
        return java.util.stream.Stream.of(address.getAddressLine(), address.getCity(), address.getProvince(), address.getPostalCode())
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining("\n"));
    }
    private String fingerprint(List<CartItemDto> items) {
        return hash(items.stream().sorted(Comparator.comparing(CartItemDto::id))
                .map(i -> i.id() + ":" + i.productId() + ":" + i.format() + ":" + i.quantity() + ":" + i.unitPrice().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(";")));
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
    private OrderDto toDto(PurchaseOrder order) {
        return OrderDto.fromEntity(order);
    }
}
