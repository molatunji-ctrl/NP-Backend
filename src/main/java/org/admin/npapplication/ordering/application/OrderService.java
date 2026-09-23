package org.admin.npapplication.ordering.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.admin.npapplication.catalog.domain.Product;
import org.admin.npapplication.catalog.persistence.ProductRepository;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.notification.application.NotificationService;
import org.admin.npapplication.ordering.contract.CheckoutQuoteDto;
import org.admin.npapplication.ordering.contract.CreateOrderRequest;
import org.admin.npapplication.ordering.contract.OrderDto;
import org.admin.npapplication.ordering.contract.OrderItemDto;
import org.admin.npapplication.ordering.contract.OrderStatsDto;
import org.admin.npapplication.ordering.contract.UpdateOrderStatusRequest;
import org.admin.npapplication.ordering.domain.Order;
import org.admin.npapplication.ordering.domain.OrderItem;
import org.admin.npapplication.ordering.domain.OrderStatus;
import org.admin.npapplication.ordering.domain.PaymentStatus;
import org.admin.npapplication.ordering.persistence.OrderRepository;
import org.admin.npapplication.prescription.application.PrescriptionService;
import org.admin.npapplication.prescription.domain.Prescription;
import org.admin.npapplication.promotion.application.PromoCodeService;
import org.admin.npapplication.promotion.contract.ValidatePromoCodeRequest;
import org.admin.npapplication.promotion.contract.ValidatePromoCodeResponse;
import org.admin.npapplication.shopping.domain.Cart;
import org.admin.npapplication.shopping.domain.CartItem;
import org.admin.npapplication.shopping.persistence.CartRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PromoCodeService promoCodeService;
    private final PrescriptionService prescriptionService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final BigDecimal shippingCost;
    private final BigDecimal vatRate;
    private final String currency;
    private final long paymentExpiryMinutes;
    private final long reservationGraceMinutes;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            ProductRepository productRepository,
            PromoCodeService promoCodeService,
            PrescriptionService prescriptionService,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            @Value("${app.checkout.shipping-cost:1500}") BigDecimal shippingCost,
            @Value("${app.checkout.vat-rate:0.075}") BigDecimal vatRate,
            @Value("${app.checkout.currency:NGN}") String currency,
            @Value("${app.checkout.payment-expiry-minutes:30}") long paymentExpiryMinutes,
            @Value("${app.checkout.reservation-grace-minutes:10}") long reservationGraceMinutes
    ) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.promoCodeService = promoCodeService;
        this.prescriptionService = prescriptionService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.shippingCost = shippingCost;
        this.vatRate = vatRate;
        this.currency = currency.toUpperCase(Locale.ROOT);
        this.paymentExpiryMinutes = paymentExpiryMinutes;
        this.reservationGraceMinutes = Math.max(reservationGraceMinutes, 0);
    }

    @Transactional(readOnly = true)
    public CheckoutQuoteDto getCheckoutQuote(User user, String promoCode) {
        Cart cart = requireCart(user);
        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return calculateQuote(subtotal, promoCode);
    }

    public OrderDto createOrder(User user, CreateOrderRequest request) {
        validateShippingAddress(request.getShippingAddress());
        Cart cart = requireCartForUpdate(user);
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setShippingAddress(toJson(request.getShippingAddress()));
        order.setBillingAddress(request.getBillingAddress() != null
                ? toJson(request.getBillingAddress()) : null);
        order.setCurrency(currency);
        order.setPaymentReference("NUGES-" + UUID.randomUUID());
        order.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(paymentExpiryMinutes));
        order.setPromoCode(normalizePromoCode(request.getPromoCode()));

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            Product product = productRepository.findByIdForUpdate(cartItem.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            validateProductForOrder(product, cartItem.getQuantity());

            Prescription prescription = product.isPrescriptionRequired()
                    ? prescriptionService.reserveForOrder(user, product, cartItem.getQuantity())
                    : null;

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setPrescription(prescription);
            orderItem.setProductName(product.getName());
            orderItem.setProductPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setTotalPrice(product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            order.addItem(orderItem);
            subtotal = subtotal.add(orderItem.getTotalPrice());

            // Reserve inventory before opening the hosted payment page. Expired
            // unpaid reservations are released by expirePendingOrders().
            product.setStock(product.getStock() - cartItem.getQuantity());
            productRepository.save(product);
        }

        CheckoutQuoteDto quote = calculateQuote(subtotal, order.getPromoCode());
        order.setSubtotal(quote.getSubtotal());
        order.setDiscountAmount(quote.getDiscountAmount());
        order.setShippingCost(quote.getShippingCost());
        order.setVatAmount(quote.getVatAmount());
        order.setTotal(quote.getTotal());
        order.setStockReserved(true);

        Order savedOrder = orderRepository.save(order);

        // The immutable order snapshot now owns these items and prevents the
        // same cart from producing duplicate pending orders.
        cart.getItems().clear();
        cartRepository.save(cart);

        notificationService.orderCreated(savedOrder);

        return mapToDto(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getUserOrders(User user, Pageable pageable) {
        return orderRepository.findByUserId(user.getId(), pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderById(User user, Long orderId) {
        return mapToDto(requireUserOrder(user, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderByIdAdmin(Long orderId) {
        return mapToDto(orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found")));
    }

    public OrderDto updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        OrderStatus previousStatus = order.getStatus();
        OrderStatus newStatus;

        try {
            newStatus = OrderStatus.valueOf(request.getStatus().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid order status: " + request.getStatus());
        }

        if (newStatus == OrderStatus.CANCELLED) {
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                throw new IllegalArgumentException("Paid orders require a verified refund before cancellation");
            }
            releaseStockReservation(order);
            order.setPaymentStatus(PaymentStatus.FAILED);
        } else if (newStatus == OrderStatus.REFUNDED) {
            throw new IllegalArgumentException("Use the payment refund workflow for paid orders");
        } else if (newStatus == OrderStatus.CONFIRMED
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalArgumentException("An unpaid order cannot be confirmed");
        }

        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        if (previousStatus != newStatus) {
            notificationService.orderStatusChanged(savedOrder);
        }
        return mapToDto(savedOrder);
    }

    public OrderDto markPaymentPaid(String paymentReference, String providerTransactionId) {
        Order order = orderRepository.findByPaymentReferenceForUpdate(paymentReference)
                .orElseThrow(() -> new IllegalArgumentException("Order payment reference not found"));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return mapToDto(order);
        }

        if (!order.isStockReserved()) {
            reserveStockAgain(order);
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentProviderId(providerTransactionId);
        order.setPaidAt(LocalDateTime.now());
        order.setStockReserved(false);
        order.setPaymentLink(null);

        if (order.getPromoCode() != null) {
            promoCodeService.incrementUsageCount(order.getPromoCode());
        }

        Order savedOrder = orderRepository.save(order);
        notificationService.paymentConfirmed(savedOrder);
        return mapToDto(savedOrder);
    }

    @Scheduled(fixedDelayString = "${app.checkout.expiry-scan-ms:300000}")
    public void expirePendingOrders() {
        List<Order> expired = orderRepository.findByPaymentStatusAndPaymentExpiresAtBefore(
                PaymentStatus.PENDING,
                LocalDateTime.now().minusMinutes(reservationGraceMinutes)
        );

        for (Order order : expired) {
            releaseStockReservation(order);
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentLink(null);
            orderRepository.save(order);
        }
    }

    @Transactional(readOnly = true)
    public Order requirePayableOrder(User user, Long orderId) {
        Order order = requireUserOrder(user, orderId);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Order has already been paid");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getPaymentExpiresAt() == null
                || order.getPaymentExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This payment session has expired");
        }
        return order;
    }

    public Order savePaymentLink(Long orderId, String paymentLink) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        order.setPaymentLink(paymentLink);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order requireOrderByPaymentReference(String paymentReference) {
        return orderRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new IllegalArgumentException("Order payment reference not found"));
    }

    @Transactional(readOnly = true)
    public OrderStatsDto getOrderStats() {
        BigDecimal paidRevenue = orderRepository.sumTotalByPaymentStatus(PaymentStatus.PAID);
        if (paidRevenue == null) {
            paidRevenue = BigDecimal.ZERO;
        }

        OrderStatsDto stats = new OrderStatsDto();
        stats.setTotalOrders(orderRepository.count());
        stats.setPendingOrders(orderRepository.countByStatus(OrderStatus.PENDING));
        stats.setConfirmedOrders(orderRepository.countByStatus(OrderStatus.CONFIRMED));
        stats.setShippedOrders(orderRepository.countByStatus(OrderStatus.SHIPPED));
        stats.setDeliveredOrders(orderRepository.countByStatus(OrderStatus.DELIVERED));
        stats.setCancelledOrders(orderRepository.countByStatus(OrderStatus.CANCELLED));
        stats.setTotalRevenue(paidRevenue);
        stats.setPaidRevenue(paidRevenue);
        return stats;
    }

    public OrderDto mapToDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList());

        return OrderDto.builder()
                .id(order.getId())
                .customerName(order.getUser().getFullname())
                .customerEmail(order.getUser().getEmail())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .subtotal(order.getSubtotal())
                .shippingCost(order.getShippingCost())
                .discountAmount(order.getDiscountAmount())
                .vatAmount(order.getVatAmount())
                .total(order.getTotal())
                .shippingAddress(order.getShippingAddress())
                .billingAddress(order.getBillingAddress())
                .paymentReference(order.getPaymentReference())
                .paymentProviderId(order.getPaymentProviderId())
                .promoCode(order.getPromoCode())
                .currency(order.getCurrency())
                .notes(order.getNotes())
                .paymentExpiresAt(order.getPaymentExpiresAt())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemDtos)
                .build();
    }

    private CheckoutQuoteDto calculateQuote(BigDecimal subtotal, String requestedPromoCode) {
        String promoCode = normalizePromoCode(requestedPromoCode);
        BigDecimal discount = BigDecimal.ZERO;

        if (promoCode != null) {
            ValidatePromoCodeResponse validation = promoCodeService.validatePromoCode(
                    new ValidatePromoCodeRequest(promoCode, subtotal));
            if (!Boolean.TRUE.equals(validation.getValid())) {
                throw new IllegalArgumentException(validation.getMessage());
            }
            discount = validation.getDiscountAmount();
        }

        BigDecimal taxableAmount = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal vat = taxableAmount.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal delivery = subtotal.signum() > 0 ? shippingCost : BigDecimal.ZERO;
        BigDecimal total = taxableAmount.add(vat).add(delivery).setScale(2, RoundingMode.HALF_UP);

        return CheckoutQuoteDto.builder()
                .subtotal(subtotal.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(discount.setScale(2, RoundingMode.HALF_UP))
                .shippingCost(delivery.setScale(2, RoundingMode.HALF_UP))
                .vatAmount(vat)
                .total(total)
                .currency(currency)
                .promoCode(promoCode)
                .build();
    }

    private Cart requireCart(User user) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        return cart;
    }

    private Cart requireCartForUpdate(User user) {
        Cart cart = cartRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        return cart;
    }

    private void validateShippingAddress(Map<String, Object> address) {
        if (address == null) {
            throw new IllegalArgumentException("Shipping address is required");
        }
        for (String field : List.of("firstName", "lastName", "email", "phone", "address", "city", "state")) {
            Object value = address.get(field);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalArgumentException("Shipping " + field + " is required");
            }
        }
    }

    private Order requireUserOrder(User user, Long orderId) {
        return orderRepository.findByUserIdAndId(user.getId(), orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    private void validateProductForOrder(Product product, int quantity) {
        if (!product.isActive()) {
            throw new IllegalArgumentException("Product " + product.getName() + " is no longer available");
        }
        if (quantity < 1 || quantity > product.getStock()) {
            throw new IllegalArgumentException("Not enough stock for " + product.getName());
        }
    }

    private void releaseStockReservation(Order order) {
        if (!order.isStockReserved()) {
            return;
        }
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
            prescriptionService.releaseReservation(item.getPrescription(), item.getQuantity());
        }
        order.setStockReserved(false);
    }

    private void reserveStockAgain(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            validateProductForOrder(product, item.getQuantity());
            if (product.isPrescriptionRequired()) {
                item.setPrescription(prescriptionService.reserveExisting(
                        item.getPrescription(),
                        product,
                        item.getQuantity()
                ));
            }
            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }
        order.setStockReserved(true);
    }

    private String normalizePromoCode(String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            return null;
        }
        return promoCode.trim().toUpperCase(Locale.ROOT);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid address payload", exception);
        }
    }

    private OrderItemDto mapItemToDto(OrderItem item) {
        return OrderItemDto.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProductName())
                .productPrice(item.getProductPrice())
                .quantity(item.getQuantity())
                .totalPrice(item.getTotalPrice())
                .prescriptionId(item.getPrescription() == null ? null : item.getPrescription().getId())
                .prescriptionStatus(item.getPrescription() == null
                        ? null
                        : item.getPrescription().getStatus().name())
                .build();
    }
}
