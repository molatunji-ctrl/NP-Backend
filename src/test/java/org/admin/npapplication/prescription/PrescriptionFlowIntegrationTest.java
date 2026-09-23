package org.admin.npapplication.prescription;

import org.admin.npapplication.shopping.contract.AddToCartRequest;
import org.admin.npapplication.ordering.contract.CreateOrderRequest;
import org.admin.npapplication.ordering.contract.OrderDto;
import org.admin.npapplication.prescription.contract.PrescriptionDto;
import org.admin.npapplication.prescription.contract.ReviewPrescriptionRequest;
import org.admin.npapplication.ordering.contract.UpdateOrderStatusRequest;
import org.admin.npapplication.catalog.domain.Product;
import org.admin.npapplication.prescription.domain.Prescription;
import org.admin.npapplication.ordering.domain.Order;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.shopping.persistence.CartRepository;
import org.admin.npapplication.ordering.persistence.OrderRepository;
import org.admin.npapplication.prescription.persistence.PrescriptionRepository;
import org.admin.npapplication.prescription.persistence.PrescriptionDocumentRepository;
import org.admin.npapplication.catalog.persistence.ProductRepository;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.admin.npapplication.shopping.application.CartService;
import org.admin.npapplication.ordering.application.OrderService;
import org.admin.npapplication.prescription.application.PrescriptionService;
import org.admin.npapplication.notification.application.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PrescriptionFlowIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private PrescriptionDocumentRepository prescriptionDocumentRepository;
    @Autowired private CartService cartService;
    @Autowired private PrescriptionService prescriptionService;
    @Autowired private OrderService orderService;
    @MockBean private NotificationService notificationService;

    private User customer;
    private Product prescriptionProduct;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        customer = new User();
        customer.setFullname("Prescription Customer");
        customer.setEmail("prescription@example.com");
        customer.setPassword("not-used-in-this-service-test");
        customer.setRole("ROLE_USER");
        customer = userRepository.save(customer);

        prescriptionProduct = new Product();
        prescriptionProduct.setName("Prescription Test Product");
        prescriptionProduct.setDescription("Requires pharmacist review");
        prescriptionProduct.setPrice(new BigDecimal("2500.00"));
        prescriptionProduct.setStock(10);
        prescriptionProduct.setCategory("Prescription");
        prescriptionProduct.setBadge("Test");
        prescriptionProduct.setFeatured(false);
        prescriptionProduct.setActive(true);
        prescriptionProduct.setPrescriptionRequired(true);
        prescriptionProduct = productRepository.save(prescriptionProduct);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void shouldRequireApprovalReserveQuantityAndRestoreItOnCancellation() {
        cartService.addItem(customer, new AddToCartRequest(prescriptionProduct.getId(), 2));

        IllegalArgumentException beforeApproval = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(customer, orderRequest())
        );
        assertTrue(beforeApproval.getMessage().contains("wait for pharmacist approval"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doctor-note.pdf",
                "application/pdf",
                "%PDF-1.4\nPrescription test".getBytes(StandardCharsets.US_ASCII)
        );
        PrescriptionDto uploaded = prescriptionService.upload(
                customer,
                prescriptionProduct.getId(),
                2,
                file
        );
        assertEquals("PENDING", uploaded.getStatus());

        PrescriptionDto approved = prescriptionService.review(
                uploaded.getId(),
                ReviewPrescriptionRequest.builder()
                        .status("APPROVED")
                        .approvedQuantity(2)
                        .reason("Verified for requested quantity")
                        .build(),
                "pharmacist@example.com"
        );
        assertEquals(2, approved.getAvailableQuantity());
        verify(notificationService).prescriptionReviewed(any(Prescription.class));

        OrderDto order = orderService.createOrder(customer, orderRequest());
        assertEquals(uploaded.getId(), order.getItems().get(0).getPrescriptionId());
        assertEquals(0, prescriptionService.getCustomerPrescriptions(
                customer,
                PageRequest.of(0, 10)
        ).getContent().get(0).getAvailableQuantity());

        orderService.updateOrderStatus(
                order.getId(),
                new UpdateOrderStatusRequest("CANCELLED")
        );
        verify(notificationService).orderCreated(any(Order.class));
        verify(notificationService).orderStatusChanged(any(Order.class));

        PrescriptionDto restored = prescriptionService.getCustomerPrescriptions(
                customer,
                PageRequest.of(0, 10)
        ).getContent().get(0);
        assertEquals(2, restored.getAvailableQuantity());
        assertEquals(10, productRepository.findById(prescriptionProduct.getId())
                .orElseThrow()
                .getStock());
    }

    @Test
    void shouldRejectAFileWhoseContentsAreNotAnAllowedFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "renamed.pdf",
                "application/pdf",
                "not really a pdf".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> prescriptionService.upload(
                        customer,
                        prescriptionProduct.getId(),
                        1,
                        file
                )
        );
        assertTrue(exception.getMessage().contains("JPEG, PNG, and PDF"));
    }

    private CreateOrderRequest orderRequest() {
        return CreateOrderRequest.builder()
                .shippingAddress(Map.of(
                        "firstName", "Prescription",
                        "lastName", "Customer",
                        "email", customer.getEmail(),
                        "phone", "+2348000000000",
                        "address", "1 Test Street",
                        "city", "Lagos",
                        "state", "Lagos"
                ))
                .build();
    }

    private void cleanDatabase() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        prescriptionDocumentRepository.deleteAll();
        prescriptionRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }
}
