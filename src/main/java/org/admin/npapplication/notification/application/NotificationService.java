package org.admin.npapplication.notification.application;

import org.admin.npapplication.ordering.domain.Order;
import org.admin.npapplication.prescription.domain.Prescription;
import org.admin.npapplication.prescription.domain.PrescriptionStatus;
import org.admin.npapplication.identity.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class NotificationService {

    private final ApplicationEventPublisher eventPublisher;
    private final String customerFrontendUrl;
    private final String brandName;

    public NotificationService(
            ApplicationEventPublisher eventPublisher,
            @Value("${app.frontend.customer-url}") String customerFrontendUrl,
            @Value("${app.mail.brand-name:Nuges Pharmacy}") String brandName
    ) {
        this.eventPublisher = eventPublisher;
        this.customerFrontendUrl = stripTrailingSlash(customerFrontendUrl);
        this.brandName = brandName;
    }

    public void emailVerification(User user, String rawToken) {
        // The token is placed in the URL fragment so it is not sent to Vercel
        // access logs or in the HTTP Referer header.
        String link = customerFrontendUrl + "/verify-email#token=" + rawToken;
        publish(
                user,
                "Verify your " + brandName + " email",
                greeting(user) + "\n\nVerify your email address using this secure link:\n"
                        + link
                        + "\n\nThis link expires automatically and can be used once.",
                "EMAIL_VERIFICATION"
        );
    }

    public void passwordReset(User user, String rawToken) {
        String link = customerFrontendUrl + "/reset-password#token=" + rawToken;
        publish(
                user,
                "Reset your " + brandName + " password",
                greeting(user) + "\n\nUse this secure link to choose a new password:\n"
                        + link
                        + "\n\nThis link expires automatically and can be used once. "
                        + "If you did not request a password reset, ignore this email.",
                "PASSWORD_RESET"
        );
    }

    public void orderCreated(Order order) {
        publish(
                order.getUser(),
                "Order #" + order.getId() + " received",
                greeting(order.getUser()) + "\n\nWe received order #" + order.getId()
                        + ". The amount due is " + amount(order)
                        + ". Complete payment before the payment session expires.\n\n"
                        + customerFrontendUrl + "/orders",
                "ORDER_CREATED"
        );
    }

    public void paymentConfirmed(Order order) {
        publish(
                order.getUser(),
                "Payment confirmed for order #" + order.getId(),
                greeting(order.getUser()) + "\n\nYour payment of " + amount(order)
                        + " for order #" + order.getId()
                        + " has been verified. We will notify you as the order progresses.\n\n"
                        + customerFrontendUrl + "/orders",
                "PAYMENT_CONFIRMED"
        );
    }

    public void orderStatusChanged(Order order) {
        publish(
                order.getUser(),
                "Order #" + order.getId() + " is now " + order.getStatus().name(),
                greeting(order.getUser()) + "\n\nThe status of order #" + order.getId()
                        + " is now " + order.getStatus().name() + ".\n\n"
                        + customerFrontendUrl + "/orders",
                "ORDER_STATUS_CHANGED"
        );
    }

    public void prescriptionReviewed(Prescription prescription) {
        boolean approved = prescription.getStatus() == PrescriptionStatus.APPROVED;
        String detail = approved
                ? "Approved quantity: " + prescription.getApprovedQuantity() + "."
                : "Reason: " + prescription.getReviewReason() + ".";

        publish(
                prescription.getUser(),
                "Prescription " + prescription.getStatus().name().toLowerCase(Locale.ROOT),
                greeting(prescription.getUser()) + "\n\nYour prescription for "
                        + prescription.getProduct().getName() + " was "
                        + prescription.getStatus().name().toLowerCase(Locale.ROOT) + ". " + detail
                        + "\n\n" + customerFrontendUrl + "/prescriptions",
                "PRESCRIPTION_REVIEWED"
        );
    }

    private void publish(User user, String subject, String body, String category) {
        eventPublisher.publishEvent(new TransactionalEmailEvent(
                user.getEmail(),
                subject,
                body + "\n\n— " + brandName,
                category
        ));
    }

    private String greeting(User user) {
        String name = user.getFullname();
        return "Hello " + (name == null || name.isBlank() ? "there" : name.trim()) + ",";
    }

    private String amount(Order order) {
        return order.getCurrency() + " " + order.getTotal().toPlainString();
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
