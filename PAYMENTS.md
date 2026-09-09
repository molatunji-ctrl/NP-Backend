# Flutterwave checkout setup

The checkout uses Flutterwave Standard. Nuges creates a pending order and reserves its stock before redirecting the customer to Flutterwave. A redirect or webhook never marks an order as paid by itself: the backend re-fetches the transaction from Flutterwave and checks its status, reference, currency, amount, and customer email.

## Deployment variables

Configure these values on the backend host. Do not expose them through a frontend `VITE_` variable or commit real values to Git.

```text
FLUTTERWAVE_SECRET_KEY=<Flutterwave secret key>
FLUTTERWAVE_WEBHOOK_SECRET_HASH=<random webhook secret hash>
APP_FRONTEND_CUSTOMER_URL=https://<customer-frontend-domain>
CHECKOUT_SHIPPING_COST=1500
CHECKOUT_VAT_RATE=0.075
CHECKOUT_CURRENCY=NGN
CHECKOUT_PAYMENT_EXPIRY_MINUTES=30
CHECKOUT_RESERVATION_GRACE_MINUTES=10
```

In the Flutterwave dashboard, configure the webhook URL as:

```text
https://<backend-domain>/api/payments/flutterwave/webhook
```

Use exactly the same secret hash in Flutterwave and `FLUTTERWAVE_WEBHOOK_SECRET_HASH`. Start with Flutterwave test credentials and complete a successful, cancelled, failed, duplicate-webhook, and expired-session test before enabling live credentials.
