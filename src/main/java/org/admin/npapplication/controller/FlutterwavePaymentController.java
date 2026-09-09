package org.admin.npapplication.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.admin.npapplication.dto.FlutterwavePaymentResponse;
import org.admin.npapplication.dto.OrderDto;
import org.admin.npapplication.dto.PaymentVerificationRequest;
import org.admin.npapplication.model.User;
import org.admin.npapplication.service.FlutterwavePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Locale;

@RestController
@RequestMapping("/api/payments/flutterwave")
public class FlutterwavePaymentController {

    private final FlutterwavePaymentService paymentService;
    private final ObjectMapper objectMapper;

    public FlutterwavePaymentController(
            FlutterwavePaymentService paymentService,
            ObjectMapper objectMapper
    ) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/initialize/{orderId}")
    public ResponseEntity<FlutterwavePaymentResponse> initializePayment(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(paymentService.initializePayment(user, orderId));
    }

    @PostMapping("/verify")
    public ResponseEntity<OrderDto> verifyPayment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PaymentVerificationRequest request
    ) {
        return ResponseEntity.ok(paymentService.verifyCustomerPayment(
                user,
                request.getTransactionId(),
                request.getTxRef()
        ));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(name = "flutterwave-signature", required = false) String signature
    ) throws IOException {
        if (!paymentService.hasValidWebhookSignature(rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }

        JsonNode payload = objectMapper.readTree(rawBody);
        JsonNode data = payload.path("data");
        String eventType = firstText(payload, "type", "event");
        String status = data.path("status").asText("").toLowerCase(Locale.ROOT);

        if (eventType != null
                && eventType.toLowerCase(Locale.ROOT).contains("completed")
                && (status.equals("successful") || status.equals("succeeded"))) {
            String paymentReference = firstText(data, "tx_ref", "reference");
            String transactionId = data.path("id").asText(null);
            if (paymentReference == null || transactionId == null) {
                return ResponseEntity.badRequest().build();
            }
            paymentService.verifyWebhookPayment(transactionId, paymentReference);
        }

        return ResponseEntity.ok().build();
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = node.path(first).asText(null);
        return value == null || value.isBlank() ? node.path(second).asText(null) : value;
    }
}
