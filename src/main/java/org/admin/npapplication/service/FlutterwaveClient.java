package org.admin.npapplication.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.admin.npapplication.model.Order;
import org.admin.npapplication.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FlutterwaveClient {

    private final RestClient restClient;
    private final String secretKey;
    private final String customerRedirectUrl;
    private final int sessionDurationMinutes;
    private final int maximumRetryAttempts;

    public FlutterwaveClient(
            @Value("${flutterwave.api-base-url:https://api.flutterwave.com/v3}") String apiBaseUrl,
            @Value("${flutterwave.secret-key:}") String secretKey,
            @Value("${app.frontend.customer-url}") String customerFrontendUrl,
            @Value("${app.checkout.payment-expiry-minutes:30}") int sessionDurationMinutes,
            @Value("${flutterwave.max-retry-attempts:3}") int maximumRetryAttempts
    ) {
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
        this.secretKey = secretKey;
        this.customerRedirectUrl = customerFrontendUrl.replaceAll("/$", "") + "/payment/callback";
        this.sessionDurationMinutes = Math.min(Math.max(sessionDurationMinutes, 1), 1440);
        this.maximumRetryAttempts = Math.max(maximumRetryAttempts, 1);
    }

    public String createPaymentLink(Order order, User user) {
        requireConfiguration();

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("email", user.getEmail());
        customer.put("name", user.getFullname());
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            customer.put("phonenumber", user.getPhone());
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tx_ref", order.getPaymentReference());
        request.put("amount", order.getTotal().toPlainString());
        request.put("currency", order.getCurrency());
        request.put("redirect_url", customerRedirectUrl);
        request.put("payment_options", "card,banktransfer,ussd");
        request.put("customer", customer);
        request.put("customizations", Map.of(
                "title", "Nuges Pharmacy",
                "description", "Payment for order #" + order.getId()
        ));
        request.put("meta", Map.of("order_id", order.getId()));
        request.put("configurations", Map.of(
                "session_duration", sessionDurationMinutes,
                "max_retry_attempt", maximumRetryAttempts
        ));

        try {
            JsonNode response = restClient.post()
                    .uri("/payments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            String link = response == null ? null : response.path("data").path("link").asText(null);
            if (link == null || link.isBlank()) {
                throw new PaymentGatewayException("Flutterwave did not return a payment link");
            }
            return link;
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Unable to initialize Flutterwave payment", exception);
        }
    }

    public VerifiedTransaction verifyTransaction(String transactionId) {
        requireConfiguration();

        try {
            JsonNode response = restClient.get()
                    .uri("/transactions/{id}/verify", transactionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = response == null ? null : response.path("data");
            if (data == null || data.isMissingNode()) {
                throw new PaymentGatewayException("Flutterwave verification returned no transaction data");
            }

            String reference = firstText(data, "tx_ref", "reference");
            String email = data.path("customer").path("email").asText(null);
            return new VerifiedTransaction(
                    data.path("id").asText(transactionId),
                    reference,
                    data.path("status").asText(""),
                    data.path("currency").asText(""),
                    data.path("amount").decimalValue(),
                    email
            );
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Unable to verify Flutterwave payment", exception);
        }
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = node.path(first).asText(null);
        return value == null || value.isBlank() ? node.path(second).asText(null) : value;
    }

    private void requireConfiguration() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentGatewayException("Flutterwave is not configured on the server");
        }
    }

    public record VerifiedTransaction(
            String id,
            String reference,
            String status,
            String currency,
            BigDecimal amount,
            String customerEmail
    ) {
    }
}
