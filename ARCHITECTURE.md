# Nuges Pharmacy backend architecture

## Deployment model

The backend is a modular monolith:

- one Spring Boot application
- one deployable Docker image
- one PostgreSQL database
- one public backend URL
- separate customer and admin React clients

This keeps deployment simple while organizing the Java code around business capabilities instead of one global controller/service/repository structure.

## Module layout

| Package | Responsibility |
| --- | --- |
| `bootstrap` | Application wiring, Firebase setup, security configuration, and global HTTP error handling |
| `platform` | Small shared web contracts and infrastructure endpoints such as health and CSRF |
| `identity` | Customer/admin identities, profiles, authentication, account recovery, and token verification |
| `catalog` | Products, inventory fields, product search, and product administration |
| `shopping` | Carts and wishlists |
| `ordering` | Checkout quotes, order creation, order history, status transitions, and inventory reservation |
| `payment` | Flutterwave checkout initialization, verification, and webhooks |
| `prescription` | Private prescription uploads, pharmacist review, and medicine-release enforcement |
| `promotion` | Promo-code validation and administration |
| `notification` | Transactional email events and SMTP delivery |
| `support` | Customer contact messages and admin message management |
| `administration` | Cross-module dashboard read model |

## Structure inside a feature

Each feature uses only the layers it needs:

```text
feature/
├── web/          HTTP controllers
├── contract/     Request and response types
├── application/  Use cases and transaction orchestration
├── domain/       JPA entities and domain values
└── persistence/  Spring Data repositories
```

Security-specific identity components live in `identity/security`. Runtime wiring lives in `bootstrap` so business modules do not become responsible for starting the application.

## Compatibility rules

- Existing `/api/...` routes remain unchanged.
- Existing PostgreSQL table names and data remain unchanged.
- Firebase continues to authenticate customer and admin users from their separate projects.
- Render still builds the single root `Dockerfile`.
- Vercel customer and admin apps continue to use one `VITE_API_URL`.

## Rules for future changes

1. Add new code to the feature that owns the business capability.
2. Keep controllers focused on HTTP parsing and response status codes.
3. Put business workflows and transaction boundaries in `application`.
4. Keep persistence interfaces with the feature that owns the stored data.
5. Use another feature's application API for new cross-feature behavior where practical.
6. Do not create a second deployable backend service unless the operational need is proven.
7. Keep secrets in Render environment variables; never commit `.env` or Firebase service-account JSON files.
