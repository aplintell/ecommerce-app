# ecommerce-app

Spring Boot MVC monolith ecommerce web app: browse products, add to cart, pay by credit card.

Server-rendered with Thymeleaf. Guests can browse and search the catalog, filter by category, manage a
session-based cart and check out with a credit card. Payments go through a pluggable `PaymentGateway`;
the default is an in-process fake sandbox gateway, so the app runs end to end without external accounts.
Card numbers and CVVs are never stored or logged: an order keeps only the card brand and last 4 digits.

## Stack

- Java 21, Spring Boot 4.1 (Spring MVC, Thymeleaf, Spring Data JPA / Hibernate 7, Bean Validation,
  Spring Security for CSRF and headers only)
- Flyway migrations (`src/main/resources/db/migration`)
- H2 in-memory database (default profile and tests), MySQL 8 (`prod` profile)
- Maven via the wrapper only (`mvnw` / `mvnw.cmd`); no global Maven install is needed

The wrapper needs a JDK 21. If `JAVA_HOME` points to an older JDK, point it to JDK 21 for the session, e.g.
PowerShell: `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"`, Git Bash: `export JAVA_HOME="/c/Program Files/Java/jdk-21"`.

## Run (dev, default profile)

```powershell
.\mvnw.cmd spring-boot:run      # Windows
./mvnw spring-boot:run          # macOS / Linux
```

Open http://localhost:8080. The app uses an in-memory H2 database (MySQL mode); Flyway creates the schema
and seeds 4 categories and 20 products on every start. Data is lost when the app stops.

## Run (prod profile, MySQL 8)

The `prod` profile reads the datasource from environment variables:

| Variable      | Example                              |
|---------------|--------------------------------------|
| `DB_URL`      | `jdbc:mysql://localhost:3306/shop`   |
| `DB_USERNAME` | `shop`                               |
| `DB_PASSWORD` | (secret, never commit it)            |

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/shop"; $env:DB_USERNAME="..."; $env:DB_PASSWORD="..."
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```

The `prod` profile also enables the Thymeleaf template cache and marks the session cookie `Secure`
(serve it over HTTPS).

### Manual MySQL verification

MySQL is not covered by the automated tests. To verify:

1. Create an empty database, e.g. `CREATE DATABASE shop CHARACTER SET utf8mb4;`, and a user with DDL/DML rights on it.
2. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and start the app with the `prod` profile as shown above.
3. Check the log: Flyway applies `V1__create_schema` and `V2__seed_catalog`, and the app starts
   (Hibernate `ddl-auto=validate` passes).
4. Open http://localhost:8080/products and confirm the seeded catalog loads; optionally place a test order.

If `validate` reports a type mismatch, fix the column definition (in a new migration) or the entity
mapping; never relax `validate`.

## Test cards (fake gateway)

| Card number             | Result                                 |
|-------------------------|----------------------------------------|
| `4242 4242 4242 4242`   | Approved                               |
| `4000 0000 0000 0002`   | Declined (`card_declined`)             |
| `4000 0000 0000 9995`   | Declined (`insufficient_funds`)        |

Use any future expiry date and any 3-digit CVV (4 digits also accepted). Spaces and dashes in the card
number are allowed. Any other Luhn-valid, non-expired number is approved.

## Tests

```powershell
.\mvnw.cmd clean verify      # Windows
./mvnw clean verify          # macOS / Linux
```

Tests run against H2. `src/test/resources/config/application.properties` gives each Spring test context
its own in-memory database; it only overrides the datasource URL, so the main configuration still applies.

## Profiles and configuration

| Property               | Default | Meaning                                         |
|------------------------|---------|-------------------------------------------------|
| `app.payment.gateway`  | `fake`  | Which `PaymentGateway` implementation is active |
| `app.currency`         | `USD`   | Currency sent to the gateway and stored on orders |

Security notes: all pages are public (no accounts), CSRF protection is on for every POST, the H2 console is
not enabled, and request-detail or SQL bind-parameter logging must not be turned on because it could log
card data.

## Plugging in a real payment gateway

1. Implement `com.example.ecommerce.payment.PaymentGateway` (`PaymentResult charge(PaymentRequest)`), e.g.
   `StripePaymentGateway`.
2. Register it as a bean that is active for its own property value:
   `@Component @ConditionalOnProperty(name = "app.payment.gateway", havingValue = "stripe")`.
   The `FakePaymentGateway` is only active for `fake` (or when the property is missing).
3. Set `app.payment.gateway=stripe` (for example in `application-prod.properties`) and put provider
   credentials in environment variables, never in the repository.
4. Rules for implementations: return a declined `PaymentResult` (not an exception) for card declines;
   never log card numbers or CVVs, and never put card data in exception messages.
5. `CheckoutService` currently calls the gateway inside the database transaction that holds product row
   locks (fine for the in-process fake). For a real, networked provider, switch to a
   reserve -> charge -> confirm/compensate flow (see the TODO in `CheckoutService`).
