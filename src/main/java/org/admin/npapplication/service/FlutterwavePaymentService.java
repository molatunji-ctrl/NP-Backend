package org.admin.npapplication.service;

import org.admin.npapplication.dto.FlutterwavePaymentResponse;
import org.admin.npapplication.dto.OrderDto;
import org.admin.npapplication.model.Order;
import org.admin.npapplication.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

@Service
public class FlutterwavePaymentService {

    private final FlutterwaveClient flutterwaveClient;
    private final OrderService orderService;
    private final String webhookSecretHash;

    public FlutterwavePaymentService(
            FlutterwaveClient flutterwaveClient,
            OrderService orderService,
            @Value("${flutterwave.webhook-secret-hash:}") String webhookSecretHash
    ) {
        this.flutterwaveClient = flutterwaveClient;
        this.orderService = orderService;
        this.webhookSecretHash = webhookSecretHash;
    }

    @Transactional
    public FlutterwavePaymentResponse initializePayment(User user, Long orderId) {
        Order order = orderService.requirePayableOrder(user, orderId);
        String paymentLink = order.getPaymentLink();

        if (paymentLink == null || paymentLink.isBlank()) {
            paymentLink = flutterwaveClient.createPaymentLink(order, user);
            orderService.savePaymentLink(order.getId(), paymentLink);
        }

        return FlutterwavePaymentResponse.builder()
                .orderId(order.getId())
                .paymentLink(paymentLink)
                .paymentReference(order.getPaymentReference())
                .amount(order.getTotal())
                .currency(order.getCurrency())
                .build();
    }

    @Transactional
    public OrderDto verifyCustomerPayment(
            User user,
            String transactionId,
            String paymentReference
    ) {
        Order order = orderService.requireOrderByPaymentReference(paymentReference);
        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Order payment reference not found");
        }
        return verifyAndComplete(order, transactionId);
    }

    @Transactional
    public OrderDto verifyWebhookPayment(String transactionId, String paymentReference) {
        Order order = orderService.requireOrderByPaymentReference(paymentReference);
        return verifyAndComplete(order, transactionId);
    }

    public boolean hasValidWebhookSignature(byte[] rawBody, String suppliedSignature) {
        if (webhookSecretHash == null || webhookSecretHash.isBlank()
                || suppliedSignature == null || suppliedSignature.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecretHash.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] expected = Base64.getEncoder().encode(mac.doFinal(rawBody));
            byte[] supplied = suppliedSignature.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to validate Flutterwave webhook signature", exception);
        }
    }

    private OrderDto verifyAndComplete(Order order, String transactionId) {
        FlutterwaveClient.VerifiedTransaction transaction =
                flutterwaveClient.verifyTransaction(transactionId);

        String status = transaction.status().toLowerCase(Locale.ROOT);
        boolean successful = status.equals("successful") || status.equals("succeeded");
        boolean correctReference = order.getPaymentReference().equals(transaction.reference());
        boolean correctCurrency = order.getCurrency().equalsIgnoreCase(transaction.currency());
        BigDecimal amount = transaction.amount() == null ? BigDecimal.ZERO : transaction.amount();
        boolean sufficientAmount = amount.compareTo(order.getTotal()) >= 0;
        boolean correctCustomer = transaction.customerEmail() != null
                && order.getUser().getEmail().equalsIgnoreCase(transaction.customerEmail());

        if (!successful || !correctReference || !correctCurrency
                || !sufficientAmount || !correctCustomer) {
            throw new IllegalArgumentException("Flutterwave could not verify this order payment");
        }

        return orderService.markPaymentPaid(order.getPaymentReference(), transaction.id());
    }
}
