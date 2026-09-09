package org.admin.npapplication;

import org.admin.npapplication.dto.AddToCartRequest;
import org.admin.npapplication.dto.CheckoutQuoteDto;
import org.admin.npapplication.dto.CreateOrderRequest;
import org.admin.npapplication.dto.FlutterwavePaymentResponse;
import org.admin.npapplication.dto.OrderDto;
import org.admin.npapplication.model.OrderStatus;
import org.admin.npapplication.model.PaymentStatus;
import org.admin.npapplication.model.Product;
import org.admin.npapplication.model.User;
import org.admin.npapplication.repository.CartRepository;
import org.admin.npapplication.repository.OrderRepository;
import org.admin.npapplication.repository.ProductRepository;
import org.admin.npapplication.repository.UserRepository;
import org.admin.npapplication.service.CartService;
import org.admin.npapplication.service.FlutterwaveClient;
import org.admin.npapplication.service.FlutterwavePaymentService;
import org.admin.npapplication.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ShoppingFlowIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private FlutterwavePaymentService paymentService;

    @MockBean private FlutterwaveClient flutterwaveClient;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        customer = new User();
        customer.setFullname("Checkout Customer");
        customer.setEmail("checkout@example.com");
        customer.setPassword("not-used-in-this-service-test");
        customer.setRole("ROLE_USER");
        customer = userRepository.save(customer);

        product = new Product();
        product.setName("Test Product");
        product.setDescription("Checkout integration test product");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStock(5);
        product.setCategory("General");
        product.setBadge("Test");
        product.setFeatured(false);
        product.setActive(true);
        product = productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void shouldCreateOrderBeforePaymentAndVerifyIdempotently() {
        cartService.addItem(customer, new AddToCartRequest(product.getId(), 2));

        CheckoutQuoteDto quote = orderService.getCheckoutQuote(customer, null);
        assertEquals(new BigDecimal("2000.00"), quote.getSubtotal());
        assertEquals(new BigDecimal("1500.00"), quote.getShippingCost());
        assertEquals(new BigDecimal("150.00"), quote.getVatAmount());
        assertEquals(new BigDecimal("3650.00"), quote.getTotal());

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(Map.of(
                        "firstName", "Checkout",
                        "lastName", "Customer",
                        "email", "checkout@example.com",
                        "phone", "+2348000000000",
                        "address", "1 Test Street",
                        "city", "Lagos",
                        "state", "Lagos"
                ))
                .build();
        OrderDto pending = orderService.createOrder(customer, request);

        assertEquals(PaymentStatus.PENDING.name(), pending.getPaymentStatus());
        assertEquals(OrderStatus.PENDING.name(), pending.getStatus());
        assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertTrue(cartService.getOrCreateCart(customer).getItems().isEmpty());

        when(flutterwaveClient.createPaymentLink(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(User.class)
        ))
                .thenReturn("https://checkout.flutterwave.com/test-link");
        FlutterwavePaymentResponse initialized = paymentService.initializePayment(customer, pending.getId());
        assertEquals("https://checkout.flutterwave.com/test-link", initialized.getPaymentLink());

        when(flutterwaveClient.verifyTransaction("transaction-1"))
                .thenReturn(new FlutterwaveClient.VerifiedTransaction(
                        "transaction-1",
                        pending.getPaymentReference(),
                        "successful",
                        "NGN",
                        pending.getTotal(),
                        customer.getEmail()
                ));

        OrderDto paid = paymentService.verifyCustomerPayment(
                customer,
                "transaction-1",
                pending.getPaymentReference()
        );
        OrderDto repeated = paymentService.verifyCustomerPayment(
                customer,
                "transaction-1",
                pending.getPaymentReference()
        );

        assertEquals(PaymentStatus.PAID.name(), paid.getPaymentStatus());
        assertEquals(OrderStatus.CONFIRMED.name(), paid.getStatus());
        assertEquals(PaymentStatus.PAID.name(), repeated.getPaymentStatus());
        assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void shouldValidateCurrentFlutterwaveWebhookSignature() throws Exception {
        byte[] payload = "{\"type\":\"charge.completed\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(payload));

        assertTrue(paymentService.hasValidWebhookSignature(payload, signature));
        assertFalse(paymentService.hasValidWebhookSignature(payload, "invalid"));
    }

    private void cleanDatabase() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }
}
