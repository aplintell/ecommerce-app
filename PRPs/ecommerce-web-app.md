# PRP: Ecommerce Web App (Spring Boot MVC Monolith, MVP)

**Target:** backend
**Status:** approved

## Goal
Build the first version of a server-rendered ecommerce web application as a single Spring Boot 4.0.x MVC monolith with Thymeleaf views. A guest customer can browse and search a product catalog, filter by category, view product details, manage a session-based shopping cart, and check out by paying with a credit card.

Payment goes through a pluggable `PaymentGateway` abstraction whose default implementation is a fake sandbox gateway, so the app runs end to end without external accounts. The app never stores or logs raw card numbers or CVV. It stores only the order, the payment status, the card brand and the last 4 digits.

The repo is greenfield (only `README.md` and `.gitignore` exist), so this PRP defines the whole initial project: build setup, domain, persistence, web layer, security, seed data and tests.

## User stories
- As a shopper, I want to see a list of products with name, price, image and availability, so that I can find something to buy.
- As a shopper, I want to filter products by category and search by keyword, so that I can find relevant products quickly.
- As a shopper, I want to open a product detail page, so that I can read the description and check price and stock before buying.
- As a shopper, I want to add products to a cart, change quantities and remove items, so that I can control what I buy.
- As a shopper, I want to see line totals and a cart total calculated by the store, so that I know what I will pay.
- As a shopper, I want to check out as a guest by entering my contact and shipping details and my credit card, so that I can pay without creating an account.
- As a shopper, I want a confirmation page with my order number and summary after a successful payment, so that I know the order went through.
- As a shopper, I want a clear message when my card is declined or my input is invalid, so that I can fix it and try again without losing my cart.
- As the store owner, I want card data never stored or logged and prices always computed on the server, so that the store stays secure and is not open to price tampering.
- As a developer, I want the payment provider behind an interface, so that a real provider (for example Stripe) can be added later without changing checkout logic.

## Acceptance criteria

### Catalog
1. Given seeded data, when I GET `/`, then I am redirected (302) to `/products`.
2. Given seeded data, when I GET `/products`, then I see active products only, sorted by name ascending, 12 per page. Each product shows name, price, image, category name and either "In stock" or "Out of stock", with a link to its detail page. Prices are formatted exactly like `$1,234.56`: the seeded product `ELEC-001` priced 1234.56 renders as the exact string `$1,234.56`.
3. Given seeded categories, when I GET `/products?category=<slug>`, then only active products in that category are shown, and that category is highlighted in the category navigation. An unknown slug shows an empty result with the message "No products found" (HTTP 200).
4. Given seeded products, when I GET `/products?q=<term>`, then only active products whose name or description contains the term (case-insensitive, literal match) are shown.
   - The characters `%`, `_` and `\` in the term are matched literally, not as wildcards. For example, `q=%` matches only products whose text contains a literal `%`.
   - `q` and `category` can be combined.
   - A blank `q` is ignored.
   - The search box keeps the entered term.
5. Given more than 12 matching products, when I GET `/products?page=1`, then the second page is shown, and the pagination links keep the `q` and `category` parameters. A missing, negative or non-numeric `page` (for example `page=-1`, `page=abc`) is treated as page 0 and returns HTTP 200, never 400 or 404.
6. Given an active product, when I GET `/products/{id}`, then I see name, description, price, category, stock status and an "Add to cart" form with a quantity input (min 1, default 1). If the product is out of stock, the form's button is disabled and labeled "Out of stock".
7. Given a product id that does not exist, belongs to an inactive product, or is non-numeric in the path (`/products/abc`), when I GET it, then I get HTTP 404 and the custom "Not found" page.

### Cart
8. Given an active product with stock >= 1, when I POST `/cart/items` with `productId` and `quantity` (with a valid CSRF token), then:
   - the item is added to my session cart, or its quantity is increased if it is already there;
   - I am redirected to `/cart`;
   - I see the flash message "Added <product name> to your cart".
9. Given an add or update that would make the cart quantity of a product greater than its current stock, or greater than 99, when the request is submitted, then the cart is not changed and I see an error message, for example "Only 3 of <product name> available". A quantity < 1 or a non-numeric quantity on add is rejected with a validation message ("Quantity must be a number" for non-numeric input).
10. Given items in my cart, when I GET `/cart`, then I see each line with product name (linked), unit price, quantity input and line total, plus the cart subtotal and total. All prices are read from the database at render time, never from the session or the client. The header on every page shows the total item count (sum of quantities), or 0 when the cart is empty.
11. Given an item in my cart, when I POST `/cart/items/{productId}` with a new `quantity` >= 1, then the quantity is updated. When `quantity` = 0, the line is removed.
12. Given an item in my cart, when I POST `/cart/items/{productId}/delete`, then the line is removed and I am redirected to `/cart` with a flash message.
13. Given an empty cart, when I GET `/cart`, then I see "Your cart is empty" with a link to `/products`, and no checkout button.
14. Given a cart line for a product that has since become inactive or deleted, when the cart is rendered, then that line is dropped and a notice is shown. Given a product whose stock fell below the cart quantity, the line is shown with a warning, and checkout is blocked until the quantity is fixed.
15. Given any POST request without a valid CSRF token, when it is submitted, then the response is HTTP 403 and no state changes.

### Checkout and payment
16. Given an empty cart, when I GET or POST `/checkout`, then I am redirected (302) to `/cart`. For POST this happens before any form validation, even if the submitted form is invalid.
17. Given a non-empty cart, when I GET `/checkout`, then I see an order summary (lines and a server-computed total) and a form with these fields: full name, email, address line 1, address line 2 (optional), city, postal code, country, cardholder name, card number, expiry month, expiry year, CVV.
18. Given invalid form input, when I POST `/checkout`, then the form is re-rendered (HTTP 200) with field-level error messages, no order is created, and no payment is attempted. Invalid input includes:
    - blank required fields or fields that are too long;
    - an invalid email;
    - a card number failing the Luhn check, or not 12–19 digits after stripping spaces and dashes;
    - an expiry in the past;
    - a CVV that is not 3–4 digits.

    Non-card fields keep their values. The card number and CVV inputs are always rendered without a value.
19. Given a valid form and the approved test card `4242 4242 4242 4242` (spaces and dashes allowed), when I POST `/checkout`, then in a single transaction:
    - stock is re-checked and decremented for every line;
    - the gateway is charged the server-computed total;
    - an order with status `PAID` is saved with its items (product name and unit price snapshot), card brand `VISA`, `card_last4 = 4242` and the gateway transaction id.

    The cart is then cleared, and I am redirected (302) to `/orders/{orderNumber}/confirmation`.
20. Given a successful order, when I GET `/orders/{orderNumber}/confirmation` in the same session, then I see the order number, the items, the total, the masked card (`VISA •••• 4242`) and the shipping name and address. The same URL from a different session, or with an unknown order number, returns 404.
21. Given a valid form and the decline test card `4000 0000 0000 0002`, when I POST `/checkout`, then no order is saved, stock is unchanged and the cart is unchanged. The checkout form is re-rendered with the error "Your card was declined. Please use a different card." and with empty card number and CVV inputs.
22. Given that a product's stock is now less than the cart quantity (for example, another customer bought it), when I POST `/checkout`, then no payment is attempted, no order is created, and I am redirected to `/cart` with the error "<product name> only has N left in stock".
23. Given any checkout attempt (successful, declined, invalid or gateway error), the full card number and the CVV do not appear in any of these places:
    - log output;
    - any database column;
    - the HTTP session (checked by Java-serializing every session attribute and scanning the bytes);
    - any rendered HTML response.
24. Given a submitted checkout form, the order total is computed only from database prices and cart quantities. Extra request parameters such as `price` or `total` are ignored.
25. Given a valid form, when the gateway throws an unexpected runtime exception (not a decline), then:
    - no order is saved, and stock and cart are unchanged;
    - the form is re-rendered (HTTP 200) with "Payment could not be processed. Please try again.";
    - the error is logged at ERROR without card number or CVV.

### Platform
26. Given the default profile, when I run `.\mvnw.cmd spring-boot:run` (Windows) or `./mvnw spring-boot:run`, then the app starts on port 8080 with an H2 in-memory DB, Flyway applies the migrations including seed data, and the catalog shows the seeded products. No generated security password is logged.
27. **(Manual verification, not in the automated suite.)** Given the `prod` profile and the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables, the app connects to MySQL 8. Flyway applies the same migrations, and Hibernate `ddl-auto=validate` passes.
28. `.\mvnw.cmd verify` passes with all tests listed under Validation.

## Out of scope
- User accounts, registration, login, order history, wishlists.
- Admin back office or product, category or stock management UI. Data changes come only through Flyway migrations.
- Real payment provider integration (Stripe, Adyen, etc.), 3-D Secure, refunds, saved cards, other payment methods.
- Shipping cost calculation, carriers, tax/VAT calculation, discounts and coupons, multi-currency.
- Order emails and notifications.
- Inventory reservation while items sit in the cart. Stock is only reserved at checkout.
- Product reviews, ratings, recommendations, image upload. Images are static assets.
- Internationalisation (UI text is English only), REST/JSON API, SPA frontend.
- Deployment pipelines, Docker, observability stack.
- Automated tests against MySQL (no Testcontainers in the MVP).

## Assumptions
- **A1 Currency:** a single currency, USD. All money is `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`, stored as `DECIMAL(10,2)`. It is rendered only through the `MoneyFormatter` bean (`NumberFormat.getCurrencyInstance(Locale.US)`).
- **A2 Totals:** `total = subtotal = sum(unitPrice * quantity)`. No tax, shipping or discounts in the MVP. The order stores `subtotal` and `total` separately so these can be added later.
- **A3 Quantities:** max 99 per cart line. The cart quantity can never exceed current stock.
- **A4 Stock:** stock is checked when cart lines are added or updated (a soft check). At checkout it is re-checked and decremented under a pessimistic write lock, which is the check that counts. Stock is not reserved while items sit in the cart.
- **A5 Failed payments:** declined payments and gateway errors do not save an order, because the transaction is rolled back. Only successful orders are stored, with status `PAID`. The `status` column also supports `PENDING` and `PAYMENT_FAILED` for a future real provider. Declines are logged at INFO with the order reference and last 4 digits only.
- **A6 Lock during the gateway call:** for the MVP, the gateway is called inside the same DB transaction that holds row locks on the products. This is acceptable with the in-process fake gateway. A real provider should switch to a reserve, charge, then confirm or compensate flow. This is documented as a TODO in `CheckoutService`.
- **A7 Fake gateway rules:** it approves any Luhn-valid, non-expired card except two decline test numbers:
  - `4000000000000002` is declined with code `card_declined`;
  - `4000000000009995` is declined with code `insufficient_funds`.

  Both show the same decline message to the user. Transaction ids are `fake_` plus a random UUID.
- **A8 Confirmation access:** order numbers are random UUIDs. The order numbers placed in a session are kept in that session, and the confirmation page is shown only for those numbers, to protect the guest's personal details. Any other order number returns 404.
- **A9 Security:** Spring Security is included only for CSRF protection and security headers. All URLs are `permitAll`, form login and HTTP Basic are disabled, and there are no users. An empty `InMemoryUserDetailsManager` bean is declared so that Boot does not generate and log a default password. The H2 console is not enabled.
- **A10 Images:** seed products reference static images under `/images/products/`. A single generic `placeholder.svg` is used for all products, and no external image hosts are used, so the app works offline.
- **A11 Search:** a case-insensitive `LIKE` on name and description, with `!` as the escape character, so `%`, `_`, `\` and `!` in user input are literal. There is no full-text engine.
- **A12 Country:** a free-text field, max 100 characters. There is no address validation.
- **A13 Spring Boot version:** Spring Boot 4.x, the latest GA that start.spring.io offers by default (4.0.x or 4.1.x are both fine; nothing depends on a specific minor), with Java 21. No Boot 3 compatibility is required.
- **A14 Base package:** `com.example.ecommerce`, groupId `com.example`, artifactId `ecommerce-app`.
- **A15 Cart storage:** the cart lives in the HTTP session and stores only `productId -> quantity`, never prices. It is lost when the session expires (default 30 minutes).
- **A16 Time:** all timestamps are UTC. `created_at` is `DATETIME(6)`, mapped as `java.time.Instant`.

## Technical context

### Stack (Spring Boot 4.0.x, Java 21)
- Maven via the wrapper only (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`). Maven is not installed globally.
- Main dependencies (all versions managed by the Boot parent):

  | Artifact | Scope | Purpose |
  |---|---|---|
  | `org.springframework.boot:spring-boot-starter-webmvc` | compile | Spring MVC + embedded Tomcat |
  | `org.springframework.boot:spring-boot-starter-thymeleaf` | compile | views |
  | `org.springframework.boot:spring-boot-starter-data-jpa` | compile | JPA / Hibernate 7 |
  | `org.springframework.boot:spring-boot-starter-validation` | compile | Bean Validation (Hibernate Validator) |
  | `org.springframework.boot:spring-boot-starter-security` | compile | CSRF + headers |
  | `org.springframework.boot:spring-boot-starter-flyway` | compile | Flyway auto-configuration |
  | `org.flywaydb:flyway-mysql` | runtime | Flyway MySQL support |
  | `com.h2database:h2` | runtime | dev/test DB |
  | `com.mysql:mysql-connector-j` | runtime | prod DB |
  | `org.springframework.boot:spring-boot-starter-test` | test | JUnit 5, AssertJ, Mockito |
  | `org.springframework.boot:spring-boot-starter-webmvc-test` | test | MockMvc, `@WebMvcTest`, `@AutoConfigureMockMvc` |
  | `org.springframework.boot:spring-boot-starter-data-jpa-test` | test | `@DataJpaTest` |
  | `org.springframework.security:spring-security-test` | test | `csrf()` request post-processor |

- **Flyway in Boot 4:** without `spring-boot-starter-flyway`, Boot 4 does not auto-configure or run Flyway, even if `flyway-core` is on the classpath. `ddl-auto=validate` would then fail on an empty schema.
- Do **not** add `thymeleaf-extras-springsecurity6`. It is not needed: forms rendered with `th:action` get the CSRF hidden field automatically.
- **Boot 4 test annotation packages:**
  - `@WebMvcTest` and `@AutoConfigureMockMvc` are in `org.springframework.boot.webmvc.test.autoconfigure`.
  - `@DataJpaTest` is in `org.springframework.boot.data.jpa.test.autoconfigure`.
  - `@MockitoBean` is in `org.springframework.test.context.bean.override.mockito`. `@MockBean` no longer exists.
- **Authority rule:** the `pom.xml` generated by start.spring.io is authoritative. If an artifact id or package name in this PRP differs from what the generated project contains or what compiles against it, follow the generated pom and the compiler, and do not invent coordinates.
- No Lombok. Use Java records for value objects and DTOs. Use plain classes for JPA entities and for `CheckoutForm` (mutable, for Thymeleaf binding).

### Repo bootstrap (mandatory: generate from start.spring.io)
Run this from PowerShell. Use `curl.exe`, not the `curl` alias.
```powershell
$tmp = "$env:TEMP\ecommerce-init"; New-Item -ItemType Directory -Force $tmp | Out-Null
curl.exe -fsSL https://start.spring.io/starter.zip `
  -d type=maven-project -d language=java -d javaVersion=21 -d packaging=jar `
  -d groupId=com.example -d artifactId=ecommerce-app -d name=ecommerce-app `
  -d packageName=com.example.ecommerce -d baseDir=ecommerce-app `
  -d dependencies=web,thymeleaf,data-jpa,validation,security,flyway,h2,mysql `
  -o "$tmp\starter.zip"
tar -xf "$tmp\starter.zip" -C $tmp
```
- Open `$tmp\ecommerce-app\pom.xml` and confirm the parent `spring-boot-starter-parent` version is a 4.x GA release (not a milestone/snapshot). Keep Initializr's default 4.x version.
- Confirm that the pom contains the artifacts in the table above. Initializr adds `flyway-mysql` and the per-technology test starters automatically. Add `spring-security-test` and any missing test starter if absent.
- Copy all contents of `$tmp\ecommerce-app\`, including `.mvn\`, `mvnw` and `mvnw.cmd`, into `C:\Users\Admin\Downloads\ecommerce-app\`.
- Merge `README.md` (keep the existing description) and `.gitignore` (append any missing Initializr entries).
- Add `.gitattributes` with `mvnw text eol=lf` and `*.cmd text eol=crlf`.
- Verify with `.\mvnw.cmd -v` and `.\mvnw.cmd test`.

### Package layout (create)
```
src/main/java/com/example/ecommerce/
  EcommerceApplication.java
  config/
    SecurityConfig.java          // SecurityFilterChain: permitAll, CSRF on, formLogin/httpBasic disabled; @Bean InMemoryUserDetailsManager() with no users
    ClockConfig.java             // @Bean Clock (Clock.systemUTC()); @Bean ValidationConfigurationCustomizer c -> c.clockProvider(() -> clock)
    MoneyFormatter.java          // @Component("moneyFormatter"): String format(BigDecimal) via NumberFormat.getCurrencyInstance(Locale.US)
    GlobalModelAdvice.java       // @ControllerAdvice: @ModelAttribute("cartItemCount"), @ModelAttribute("categories")
  catalog/
    Category.java                // @Entity
    Product.java                 // @Entity
    CategoryRepository.java
    ProductRepository.java       // JpaRepository + JpaSpecificationExecutor
    ProductSpecifications.java   // active(), inCategory(slug), matches(term) with escaped LIKE
    CatalogService.java
    ProductNotFoundException.java
    ProductController.java       // GET /, /products, /products/{id}
  cart/
    Cart.java                    // @Component @SessionScope, Serializable, LinkedHashMap<Long,Integer>
    CartService.java             // add/update/remove/view, stock checks, builds CartView from DB prices
    CartView.java                // record: List<CartLine> lines, BigDecimal subtotal, BigDecimal total, int itemCount, List<String> notices, boolean checkoutAllowed
    CartLine.java                // record: Long productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal, int availableStock, boolean insufficientStock
    CartController.java          // GET /cart, POST /cart/items, POST /cart/items/{productId}, POST /cart/items/{productId}/delete
    AddToCartForm.java           // plain mutable class (NOT a record) with setters: productId @NotNull, quantity @NotNull @Min(1) @Max(99)
    CartException.java           // business errors (insufficient stock, over 99)
  order/
    CustomerOrder.java           // @Entity table customer_order
    OrderItem.java               // @Entity table order_item
    OrderStatus.java             // enum PENDING, PAID, PAYMENT_FAILED
    OrderRepository.java
    PlacedOrders.java            // @Component @SessionScope, Serializable, Set<String> orderNumbers
    OrderNotFoundException.java  // -> 404
    OrderController.java         // GET /orders/{orderNumber}/confirmation
  checkout/
    CheckoutForm.java            // mutable bean with Bean Validation; toString() excludes cardNumber and cvv
    ValidCardNumber.java / ValidCardNumberValidator.java  // strips spaces/dashes, requires 12–19 digits, Luhn-valid
    NotExpired.java / NotExpiredValidator.java            // class-level constraint using ClockProvider
    CheckoutService.java         // @Transactional placeOrder(CheckoutForm, Map<Long,Integer> lines) -> CustomerOrder
    CheckoutController.java      // GET/POST /checkout
    EmptyCartException.java
    InsufficientStockException.java
    PaymentDeclinedException.java   // RuntimeException -> rollback
    PaymentProcessingException.java // wraps unexpected gateway failures -> rollback (message contains no card data)
  payment/
    PaymentGateway.java
    PaymentRequest.java          // record: orderReference, amount, currency, CardDetails card
    CardDetails.java             // record: number, expiryMonth, expiryYear, cvv, holderName; toString() masked
    PaymentResult.java           // record: approved, transactionId, declineCode, cardLast4, CardBrand brand
    CardBrand.java               // enum + static detect(String digits)
    CardNumbers.java             // digitsOnly(), luhnValid(), last4(), mask()
    FakePaymentGateway.java      // @Component @ConditionalOnProperty(name="app.payment.gateway", havingValue="fake", matchIfMissing=true)
  web/
    GlobalExceptionHandler.java  // @ControllerAdvice: 404 handling (see below)
src/main/resources/
  application.properties
  application-prod.properties
  messages.properties
  db/migration/V1__create_schema.sql
  db/migration/V2__seed_catalog.sql
  templates/
    fragments/layout.html
    products/list.html
    products/detail.html
    cart/view.html
    checkout/form.html
    orders/confirmation.html
    error/404.html
    error/error.html
  static/css/app.css
  static/images/products/placeholder.svg
src/test/resources/
  config/application.properties  // ONLY overrides the datasource URL; main application.properties still loads (see Validation)
  sql/restore-stock.sql          // resets product.stock_quantity to V2 seed values by SKU
src/test/java/com/example/ecommerce/... (see Validation)
```

### Configuration
`src/main/resources/application.properties` (default/dev):
```
spring.application.name=ecommerce-app
server.port=8080
spring.datasource.url=jdbc:h2:mem:shop;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP
spring.flyway.enabled=true
spring.thymeleaf.cache=false
spring.messages.basename=messages
server.error.include-stacktrace=never
server.error.include-message=never
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
app.payment.gateway=fake
app.currency=USD
```
`preferred_instant_jdbc_type=TIMESTAMP` makes Hibernate map `Instant` to a plain timestamp, matching `DATETIME(6)` on both H2 and MySQL under `validate`.

Do NOT enable `spring.mvc.log-request-details`, `CommonsRequestLoggingFilter`, or Hibernate bind-parameter TRACE logging (`org.hibernate.orm.jdbc.bind`). Any of these could log card data.

`application-prod.properties`:
```
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.thymeleaf.cache=true
server.servlet.session.cookie.secure=true
```

`messages.properties` (used by Spring MVC's binding error messages):
```
typeMismatch=Invalid value
typeMismatch.java.lang.Integer=Must be a number
typeMismatch.java.lang.Long=Must be a number
typeMismatch.addToCartForm.quantity=Quantity must be a number
typeMismatch.checkoutForm.expiryMonth=Expiry month must be a number
typeMismatch.checkoutForm.expiryYear=Expiry year must be a number
```

### Data model (Flyway `V1__create_schema.sql`, must run on both H2 in MySQL mode and MySQL 8)
```sql
CREATE TABLE category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  slug VARCHAR(100) NOT NULL,
  CONSTRAINT uk_category_name UNIQUE (name),
  CONSTRAINT uk_category_slug UNIQUE (slug)
);

CREATE TABLE product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(50) NOT NULL,
  name VARCHAR(200) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  stock_quantity INT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  category_id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_product_sku UNIQUE (sku),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id),
  CONSTRAINT ck_product_price CHECK (price >= 0),
  CONSTRAINT ck_product_stock CHECK (stock_quantity >= 0)
);
CREATE INDEX ix_product_category ON product(category_id);

CREATE TABLE customer_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_number VARCHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL,
  customer_name VARCHAR(100) NOT NULL,
  email VARCHAR(254) NOT NULL,
  address_line1 VARCHAR(200) NOT NULL,
  address_line2 VARCHAR(200),
  city VARCHAR(100) NOT NULL,
  postal_code VARCHAR(20) NOT NULL,
  country VARCHAR(100) NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL,
  total DECIMAL(10,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  card_brand VARCHAR(20) NOT NULL,
  card_last4 VARCHAR(4) NOT NULL,
  payment_transaction_id VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_order_number UNIQUE (order_number)
);

CREATE TABLE order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(200) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  line_total DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(id)
);
CREATE INDEX ix_item_order ON order_item(order_id);
```
- No `CHAR` columns are used anywhere. There are no columns for the full card number, CVV or expiry.
- **Entity mapping rules (required for `ddl-auto=validate`):**
  - Every `@Column` declares `nullable` and `length` exactly matching the DDL, for example `@Column(name="card_last4", length=4, nullable=false)` and `@Column(length=3, nullable=false) String currency`.
  - Every `DECIMAL` uses `@Column(precision=10, scale=2)`.
  - `created_at` is mapped as `@Column(name="created_at", nullable=false) Instant createdAt`.
  - Enums use `@Enumerated(EnumType.STRING)` with `length=20`.
  - `Product.version` uses `@Version`.
  - `Product.category` is `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name="category_id")`.
  - `CustomerOrder.items` is `@OneToMany(mappedBy="order", cascade=ALL, orphanRemoval=true)`, and `OrderItem.order` is `@ManyToOne(fetch=LAZY)`.
- If manual MySQL verification (AC 27) reports a type mismatch, fix the column definition or mapping. Never relax `validate`.

`V2__seed_catalog.sql` seeds 4 categories with explicit ids: 1 `electronics`, 2 `books`, 3 `home-kitchen`, 4 `clothing`. It also seeds at least 16 products with realistic names, descriptions and prices, all with `image_url = '/images/products/placeholder.svg'`. These SKUs are required because tests rely on them:

| SKU | Category | Price | Stock | Active | Purpose |
|---|---|---|---|---|---|
| `ELEC-001` | electronics | 1234.56 | 50 | TRUE | exact money format `$1,234.56`, happy-path checkout |
| `BOOK-001` | books | 19.99 | 100 | TRUE | multi-line cart and total math |
| `HOME-LOW` | home-kitchen | 49.50 | 3 | TRUE | stock cap ("Only 3 ... available") |
| `BOOK-OOS` | books | 9.99 | 0 | TRUE | out-of-stock display |
| `CLOTH-OFF` | clothing | 29.00 | 10 | FALSE | inactive product, never shown, detail returns 404 |
| `ELEC-LAST` | electronics | 299.00 | 1 | TRUE | insufficient stock at checkout, concurrency |
| `HOME-PCT` | home-kitchen | 15.00 | 20 | TRUE | its name contains a literal `100%` (for example "100% Cotton Tea Towel"), used by the search-escaping test |

Seed rules the tests depend on:
- `ELEC-001` must be named so it sorts into the first 12 active products by name, e.g. "Aurora 4K Laptop".
- No product name or description other than `HOME-PCT`'s may contain `%` (no "20% off" text), and no product name or description may contain `_`.

`src/test/resources/sql/restore-stock.sql` contains one `UPDATE product SET stock_quantity = <seed value> WHERE sku = '<sku>';` per seeded product, mirroring V2 exactly.

### Repositories and fetching (OSIV is off)
**Rule:** any repository result that reaches a template must eagerly fetch everything the template reads. That means `category` for product list and detail, and `items` for the order confirmation. Lazy-loading in a view fails with `LazyInitializationException`.
- `ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product>`:
  - `@Override @EntityGraph(attributePaths = "category") Page<Product> findAll(Specification<Product> spec, Pageable pageable);`
  - `@EntityGraph(attributePaths = "category") Optional<Product> findByIdAndActiveTrue(Long id);`
  - `List<Product> findAllById(Iterable<Long>)` (inherited) for cart rendering. The cart template reads only scalar fields; it does not read the category.
  - `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Product p where p.id in :ids order by p.id") List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);` Rows are ordered by id to avoid deadlocks.
- `ProductSpecifications`:
  - `active()` matches `active = true`.
  - `inCategory(slug)` joins category and compares `slug`.
  - `matches(term)` builds `pattern = "%" + escape(term.toLowerCase(Locale.ROOT)) + "%"`. `escape` prefixes each `!`, `%` and `_` with `!`. The `!` must be escaped first. A backslash needs no escaping because the escape character is `!`. It returns `cb.or(cb.like(cb.lower(name), pattern, '!'), cb.like(cb.lower(description), pattern, '!'))`.
  - The specifications are combined with `Specification.allOf(...)` (not the deprecated `Specification.where`), and `null` (blank) inputs are skipped. Using `!` instead of `\` avoids MySQL string-literal backslash quirks.
- `CategoryRepository.findAllByOrderByNameAsc()`.
- `OrderRepository`: `@EntityGraph(attributePaths = "items") Optional<CustomerOrder> findByOrderNumber(String orderNumber);`
- `CatalogService.listProducts(String categorySlug, String q, String pageParam)`:
  - It trims `categorySlug` and `q`, and treats blank values as `null`.
  - It parses `pageParam` with `Integer.parseInt` inside a try/catch. A null, non-numeric or negative value becomes 0.
  - It uses `PageRequest.of(page, 12, Sort.by("name").ascending())`.

### HTTP contract (server-rendered; all POSTs require CSRF; all successful POSTs use Post/Redirect/Get)
| Method | Path | Params | Success | Errors |
|---|---|---|---|---|
| GET | `/` | none | 302 to `/products` | none |
| GET | `/products` | `q`, `category`, `page`, all `@RequestParam(required=false) String` | 200 `products/list` (model: `page`, `selectedCategory`, `q`) | never 400; a bad `page` falls back to 0 |
| GET | `/products/{id}` | `@PathVariable Long id` | 200 `products/detail` (model: `product`, `addToCartForm`) | 404 (missing, inactive or non-numeric id) |
| GET | `/cart` | none | 200 `cart/view` (model: `cart` = CartView) | none |
| POST | `/cart/items` | `productId`, `quantity` bound to `@Valid AddToCartForm` + `BindingResult` | 302 to `/cart`, flash `success` | binding, validation or stock error: 302 back to `/products/{productId}` (or `/cart` if productId is invalid) with flash `error`; unknown or inactive product: 404 |
| POST | `/cart/items/{productId}` | `quantity` bound as `@RequestParam(required=false) String` and parsed in the controller/service (0..99) | 302 to `/cart`, flash `success` | missing, non-numeric or out-of-range quantity, or stock error: 302 to `/cart`, flash `error` (never 400) |
| POST | `/cart/items/{productId}/delete` | none | 302 to `/cart`, flash `success` | none (removing a missing line is a no-op) |
| GET | `/checkout` | none | 200 `checkout/form` (model: `checkoutForm`, `cart`) | 302 to `/cart` if the cart is empty or `!checkoutAllowed` |
| POST | `/checkout` | form fields below | 302 to `/orders/{orderNumber}/confirmation` | empty cart: 302 to `/cart` (checked first); validation, decline or gateway error: 200 `checkout/form`; stock problem: 302 to `/cart` with flash `error` |
| GET | `/orders/{orderNumber}/confirmation` | none | 200 `orders/confirmation` (model: `order`) | 404 if unknown or not in `PlacedOrders` |
| any POST | any | missing or invalid `_csrf` | none | 403 |

`GlobalExceptionHandler`:
- It maps `ProductNotFoundException` and `OrderNotFoundException` to status 404 with view `error/404`.
- It maps `MethodArgumentTypeMismatchException` to 404 / `error/404` only when `ex.getParameter().hasParameterAnnotation(PathVariable.class)`. Otherwise it returns 400 with view `error/error`. Request params on `/products` are Strings, so they never hit this handler.

`GlobalModelAdvice` adds two attributes to every model:
- `cartItemCount`, an `int` from `Cart.itemCount()`;
- `categories`, from `CategoryRepository.findAllByOrderByNameAsc()`.

Templates must be null-safe, for example `${cartItemCount ?: 0}` and `${categories ?: {}}`, so error pages rendered outside the advice still work.

**Checkout form fields and validation (`CheckoutForm`):**

| Field | Rules |
|---|---|
| `fullName` | `@NotBlank @Size(max=100)` |
| `email` | `@NotBlank @Email @Size(max=254)` |
| `addressLine1` | `@NotBlank @Size(max=200)` |
| `addressLine2` | `@Size(max=200)` (optional) |
| `city` | `@NotBlank @Size(max=100)` |
| `postalCode` | `@NotBlank @Size(max=20)` |
| `country` | `@NotBlank @Size(max=100)` |
| `cardholderName` | `@NotBlank @Size(max=100)` |
| `cardNumber` | `@NotBlank @ValidCardNumber`. Only digits, spaces and dashes are allowed. After stripping spaces and dashes the value must have 12–19 digits and pass Luhn. The validator returns true for null or blank (`@NotBlank` reports that case). Message: "Card number is invalid". |
| `expiryMonth` | `@NotNull @Min(1) @Max(12)` (Integer) |
| `expiryYear` | `@NotNull @Min(2000) @Max(2100)` (Integer, 4-digit) |
| `cvv` | `@NotBlank @Pattern(regexp="^[0-9]{3,4}$", message="CVV must be 3 or 4 digits")` |
| class level | `@NotExpired` (message "Card has expired"), attached to the `expiryMonth` field |

**`NotExpiredValidator`:**
- It returns `true` when `expiryMonth` or `expiryYear` is null, or when the month is outside 1–12 (other constraints report those cases).
- Otherwise it computes `YearMonth.now(context.getClockProvider().getClock())` and is valid if `YearMonth.of(year, month)` is not before it.
- It uses `context.buildConstraintViolationWithTemplate(...).addPropertyNode("expiryMonth").addConstraintViolation()` after `disableDefaultConstraintViolation()`.
- The app wires the clock in `ClockConfig` through a `ValidationConfigurationCustomizer` bean: `configuration -> configuration.clockProvider(() -> clock)`. The customizer interface lives in Boot's validation auto-configuration package; follow the authority rule for its exact package. This is the only clock source for validation, so there is no field injection into validators.

**Binding allow-list:** `CheckoutController` has `@InitBinder("checkoutForm")` that calls `binder.setAllowedFields("fullName","email","addressLine1","addressLine2","city","postalCode","country","cardholderName","cardNumber","expiryMonth","expiryYear","cvv")`. Extra params such as `total` and `price` are ignored (AC 24).

### Payment abstraction
```java
public interface PaymentGateway {
    PaymentResult charge(PaymentRequest request);
}
```
**`FakePaymentGateway.charge`:**
1. Compute `digits = CardNumbers.digitsOnly(number)`.
2. If the card is not Luhn-valid, decline with `invalid_card`. If it is expired (judged with the injected `Clock`), decline with `expired_card`.
3. If `digits` is `4000000000000002`, decline with `card_declined`. If it is `4000000000009995`, decline with `insufficient_funds`.
4. Otherwise approve with `transactionId = "fake_" + UUID`.

The result always includes `cardLast4` and `CardBrand.detect(digits)`. The only permitted log line is `log.info("Fake charge {} for order {} card ****{}", outcome, ref, last4)`.

**`CardBrand.detect`:**

| Prefix | Brand |
|---|---|
| `4` | VISA |
| `51`–`55` or `2221`–`2720` | MASTERCARD |
| `34`, `37` | AMEX |
| `6011`, `65` | DISCOVER |
| anything else | UNKNOWN |

**Card data rules:**
- `CardDetails.toString()` and `CheckoutForm.toString()` must never include the number or CVV. Return a masked form such as `CardDetails[**** 4242]`.
- Gateway implementations must not put card data in exception messages.
- A real provider later implements `PaymentGateway` and is selected with `app.payment.gateway=<name>`. Document this in the README.

### Checkout transaction (`CheckoutService.placeOrder`, `@Transactional`)
1. If the cart lines are empty, throw `EmptyCartException`.
2. Call `findAllByIdForUpdate(ids)`. If any product is missing or inactive, throw `InsufficientStockException` with a message naming the product.
3. For each line, if `stock < qty`, throw `InsufficientStockException("<name> only has N left in stock")`.
4. Compute line totals and the total from DB prices (`setScale(2, HALF_UP)`).
5. Generate `orderNumber = UUID.randomUUID().toString()`. Build `PaymentRequest` with `CardDetails` (digits only) from the form.
6. Wrap ONLY the `gateway.charge(request)` call in the try/catch; check the result AFTER the try block:
   ```java
   PaymentResult result;
   try {
       result = gateway.charge(request);
   } catch (RuntimeException e) {
       log.error("Payment processing failed for order {}: {}", orderNumber, e.getClass().getSimpleName());
       throw new PaymentProcessingException(); // no cause attached, no message from e
   }
   if (!result.approved()) {
       throw new PaymentDeclinedException(result.declineCode());
   }
   ```
   - A decline must never be turned into `PaymentProcessingException`.
   - Never log the exception message or stack trace (a provider could put card data in it), and do not attach it as the cause of `PaymentProcessingException`.
   - Both exceptions roll back, so there is no stock change and no order.
7. Decrement stock on each locked product. Save `CustomerOrder` (status PAID, last4, brand, transaction id, currency `USD`, `createdAt = Instant.now(clock)`) with `OrderItem` snapshots. Return the order.

**`CheckoutController.submit(@Valid @ModelAttribute CheckoutForm form, BindingResult result, ...)`:**
1. If `cart.isEmpty()`, return `redirect:/cart` immediately, before inspecting `result`.
2. If `result.hasErrors()`, clear `form.cardNumber` and `form.cvv`, add the `cart` view to the model, and return `checkout/form`.
3. Call `placeOrder`. On success, call `cart.clear()`, add the order number to `PlacedOrders`, and redirect to the confirmation page.
4. Handle exceptions:
   - `PaymentDeclinedException`: add a global error "Your card was declined. Please use a different card.", clear card fields, and re-render.
   - `PaymentProcessingException`: add a global error "Payment could not be processed. Please try again.", clear card fields, and re-render.
   - `InsufficientStockException`: set flash `error` and redirect to `/cart`.
   - `EmptyCartException`: redirect to `/cart`.

### UI (Thymeleaf)
- **Layout fragments:** `fragments/layout.html` provides:
  - `head(title)`: meta charset, viewport, `/css/app.css`.
  - `header`: store name linking to `/products`, a GET search form (`q`) to `/products`, category links from `categories`, and a "Cart (N)" link using `${cartItemCount ?: 0}`.
  - `footer`.
  - `flash`: renders the `success` and `error` flash attributes.
- All forms use `th:action="@{...}"` with `method="post"`, so the CSRF hidden input is added automatically.
- **Money:** always render money as `${@moneyFormatter.format(value)}`, where `MoneyFormatter.format` uses `NumberFormat.getCurrencyInstance(Locale.US)` on a `BigDecimal` and produces exactly `$1,234.56`. Do not use `#numbers.formatCurrency`, because its output depends on the request locale.
- **Card inputs:** the card number and CVV inputs must NOT use `th:field` or `th:value`. Render them as plain inputs with no value attribute:
  ```html
  <input type="text" id="cardNumber" name="cardNumber" autocomplete="cc-number" inputmode="numeric">
  <p class="error" th:if="${#fields.hasErrors('cardNumber')}" th:errors="*{cardNumber}"></p>
  <input type="password" id="cvv" name="cvv" autocomplete="cc-csc" inputmode="numeric">
  <p class="error" th:if="${#fields.hasErrors('cvv')}" th:errors="*{cvv}"></p>
  ```
  These inputs live inside `<form th:object="${checkoutForm}">`. All other checkout fields may use `th:field`.
- Global errors (decline or gateway error) render via `th:if="${#fields.hasGlobalErrors()}"`.
- Plain, responsive CSS. No JS framework.

## Implementation blueprint
1. **Bootstrap** from start.spring.io with the exact `curl.exe` command above. Verify the 4.0.x parent and the dependency table. Copy the files into the repo, merge `README.md` and `.gitignore`, and add `.gitattributes`. Confirm `.\mvnw.cmd test` passes (context loads after step 3).
2. **Config:** create `application.properties`, `application-prod.properties` and `messages.properties`. Then add `SecurityConfig` (permitAll, CSRF enabled by default, `formLogin(AbstractHttpConfigurer::disable)`, `httpBasic(AbstractHttpConfigurer::disable)`, empty `InMemoryUserDetailsManager`), `ClockConfig` (Clock bean plus `ValidationConfigurationCustomizer`) and `MoneyFormatter`.
3. **Migrations:** write `V1__create_schema.sql` and `V2__seed_catalog.sql` (with the required SKUs).
4. **Catalog domain:**
   - Entities `Category` and `Product`, with column lengths matching the DDL.
   - `ProductRepository` with entity graphs, the locking query and `JpaSpecificationExecutor`.
   - `ProductSpecifications`, with escaped LIKE.
   - `CatalogService`, with String page parsing.
   - `ProductNotFoundException`.
5. **Catalog web:** `ProductController`, `GlobalModelAdvice`, `GlobalExceptionHandler`. Templates `fragments/layout.html`, `products/list.html`, `products/detail.html`, `error/404.html`, `error/error.html`. Static files `app.css` and `placeholder.svg`.
6. **Payment package:** `CardNumbers`, `CardBrand`, `CardDetails`, `PaymentRequest`, `PaymentResult`, `PaymentGateway`, `FakePaymentGateway`, plus their unit tests.
7. **Cart:**
   - Session-scoped `Cart`: `Serializable`, with `add`, `setQuantity`, `remove`, `clear`, `isEmpty`, `lines()` (unmodifiable copy) and `itemCount()`.
   - `CartService`: checks quantities against DB stock and the 99 cap. `view()` builds `CartView` from DB prices, drops missing or inactive products with notices, flags insufficient stock and sets `checkoutAllowed`.
   - `CartController`, `AddToCartForm` and the `cart/view.html` template.
8. **Orders:** entities `CustomerOrder`, `OrderItem` and `OrderStatus`. `OrderRepository` with the items entity graph. Session-scoped `PlacedOrders`, `OrderNotFoundException`, `OrderController`, and the `orders/confirmation.html` template.
9. **Checkout:**
   - `CheckoutForm` with a safe `toString`.
   - `ValidCardNumber` and `NotExpired` constraints with their validators.
   - The exceptions.
   - `CheckoutService`, following the steps above, with a TODO comment about A6.
   - `CheckoutController`, with the empty-cart check first and the `@InitBinder` allow-list.
   - The `checkout/form.html` template, with plain card inputs.
10. **Tests:** create `src/test/resources/config/application.properties` and `sql/restore-stock.sql`. Write every test in the Validation section, then run `.\mvnw.cmd clean verify` until it is green.
11. **README:** document:
    - how to run in dev and prod;
    - the test cards (`4242 4242 4242 4242` approved, `4000 0000 0000 0002` declined, `4000 0000 0000 9995` insufficient funds; any future expiry; any 3-digit CVV);
    - the profiles and environment variables;
    - the manual MySQL verification steps;
    - how to plug in a real gateway.

## Edge cases & error handling
- **Price tampering:** the cart stores only ids and quantities. The checkout total is computed from locked DB rows, and the `@InitBinder` allow-list ignores extra params.
- **Price change between viewing the cart and checking out:** the current DB price is charged, and the order stores the snapshot. The checkout page always shows current prices.
- **Concurrent purchase of the last unit:** the pessimistic lock serialises it. The second buyer gets the insufficient-stock redirect and is not charged.
- **Double submit or refresh after success:** PRG plus a cleared cart means a re-POST hits the empty-cart redirect. There is no duplicate order or charge.
- **Back button after a decline:** the cart is intact and the form can be resubmitted.
- **Product deactivated while in the cart:** it is dropped on render with a notice. At checkout it raises an insufficient-stock error.
- **Invalid ids:**
  - A non-numeric path id returns 404.
  - A non-numeric `productId` or `quantity` in the add form becomes a binding error with the `messages.properties` text, shown as a flash error.
  - A quantity that is negative or 0 on add, or over 99, is rejected.
- **Bad `page` values:** a non-numeric or negative `page` becomes 0. A page beyond the last renders an empty list with "No products found" and a link to page 0.
- **Search wildcards:** `%`, `_`, `\` and `!` in `q` are literal.
- **Session expiry:** the cart becomes empty, so checkout redirects to `/cart`.
- **Card input:** spaces and dashes are accepted, letters fail validation, and the card number and CVV are never echoed back. Expired cards are rejected by form validation, and the gateway also declines them as a safeguard.
- **Gateway exception:** see AC 25. The log contains the exception class name only.
- **Error pages:** no stack traces or exception messages are shown to users (`server.error.include-*=never`).
- **CSRF failure:** Spring Security returns 403 by default. The generic error page is acceptable.

## Validation

### Commands
- Windows: `.\mvnw.cmd clean verify`
- macOS/Linux: `./mvnw clean verify`
- Run locally: `.\mvnw.cmd spring-boot:run`, then open http://localhost:8080
- Manual MySQL check (AC 27):
  ```powershell
  $env:DB_URL="jdbc:mysql://localhost:3306/shop"; $env:DB_USERNAME="..."; $env:DB_PASSWORD="..."
  .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
  ```
  The app must start (Flyway applies and `validate` passes), and the catalog must load.

### Test infrastructure
`src/test/resources/config/application.properties` overrides the datasource so that each Spring application context gets its own in-memory DB:
```
spring.datasource.url=jdbc:h2:mem:${random.uuid};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000
```
It MUST live under `config/`. A file at `src/test/resources/application.properties` would hide the main `application.properties` entirely (same classpath location, test-classes first), silently dropping `ddl-auto=validate`, `open-in-view=false` and the UTC/Instant settings. Under `classpath:/config/` Boot loads it in addition to the main file, with higher priority, so only the URL is overridden.

**Test data rules:**
- Card expiry in every test form is relative, e.g. `Year.now().getValue() + 2`, never a hard-coded year.
- Use a 4-digit CVV such as `7373` in checkout integration tests, so "body does not contain the CVV" cannot fail by chance (CSRF tokens and UUIDs can contain 3-digit runs).

**Isolation rules:**
- Do NOT use `@DirtiesContext` as the reset mechanism. Cached contexts are shared across test classes.
- Every test that changes stock (checkout service, checkout flow, concurrency) is annotated with `@Sql(scripts = "/sql/restore-stock.sql", executionPhase = BEFORE_TEST_METHOD)`.
- Tests that create orders assert order counts as deltas (`countBefore` vs `countAfter`) and never as absolute numbers.
- Cart tests use a fresh `MockHttpSession` per test.

**MockMvc rules:**
- Read bodies as UTF-8: `result.getResponse().getContentAsString(StandardCharsets.UTF_8)`. The page contains `•`.
- All POSTs use `.with(csrf())`, except the CSRF-negative tests.

### Required tests (JUnit 5, AssertJ, Mockito, MockMvc)
| Test class | Type | Covers |
|---|---|---|
| `EcommerceApplicationTests` | `@SpringBootTest` context load | Flyway runs, `validate` passes on H2, no `InMemoryUserDetailsManager` generated password (AC 26) |
| `config/MoneyFormatterTest` | unit | `format(new BigDecimal("1234.56"))` equals `"$1,234.56"`; `0` gives `"$0.00"`; `19.9` gives `"$19.90"` (AC 2, A1) |
| `payment/CardNumbersTest` | unit | Luhn valid and invalid, `digitsOnly` with spaces and dashes, `last4`, `mask` (AC 18, 23) |
| `payment/CardBrandTest` | unit, parameterized | VISA, MASTERCARD (`51..`, `2221..`), AMEX, DISCOVER and UNKNOWN detection (AC 19) |
| `payment/FakePaymentGatewayTest` | unit, fixed `Clock` | 4242 is approved (`fake_` prefix, last4, brand). 0002 gives `card_declined`. 9995 gives `insufficient_funds`. Non-Luhn gives `invalid_card`. Expired gives `expired_card`. `CardDetails.toString()` has no full number or CVV (AC 19, 21, 23) |
| `checkout/CheckoutFormValidationTest` | unit; `Validator` built with `Validation.byDefaultProvider().configure().clockProvider(() -> fixedClock).buildValidatorFactory().getValidator()` | Every field rule. Card number: 11 digits invalid; 20 digits invalid; `4242-4242 4242 4242` valid; letters invalid. `@NotExpired`: current month valid, previous month invalid, null month or year gives no `NotExpired` violation. `toString` masks (AC 18) |
| `cart/CartTest` | unit | add merges quantities, `setQuantity(0)` removes, remove, clear, `itemCount`, Java-serializable round trip (AC 8, 11, 12) |
| `cart/CartServiceTest` | unit, mocked `ProductRepository` | Stock cap (`HOME-LOW`-like product with stock 3), 99 cap, inactive product rejected. `view` computes totals from repository prices at scale 2, drops missing products with a notice, and flags insufficient stock with `checkoutAllowed=false` (AC 9, 10, 14) |
| `catalog/ProductRepositoryTest` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` (so it uses the MySQL-mode H2 URL and Flyway seed; package per the authority rule) | Search by category, by q (case-insensitive, name or description), combined. Inactive `CLOTH-OFF` excluded. `q="%"` returns only `HOME-PCT`. `q="_"` returns no product without a literal `_`. `findByIdAndActiveTrue` excludes inactive and returns `category` initialized (`Hibernate.isInitialized`) (AC 2–4, 7) |
| `catalog/ProductControllerTest` | `@SpringBootTest` + `@AutoConfigureMockMvc` | `/` redirects. The list renders seeded names and contains `$1,234.56`. Category filter, search, and `page=1`. `page=abc` and `page=-1` return 200 with the first page. Detail returns 200 with the category name. Unknown, inactive (`CLOTH-OFF`) and `/products/abc` return 404. `BOOK-OOS` shows a disabled button (AC 1–7) |
| `cart/CartControllerTest` | `@SpringBootTest` + MockMvc, shared `MockHttpSession` | Add gives a redirect and flash. Update, update to 0 removes, delete. Adding 4 of `HOME-LOW` gives an error. `quantity=abc` gives "Quantity must be a number". The empty cart message. The header count shows `Cart (0)` and then the updated value. POST without csrf returns 403 and leaves the cart unchanged (AC 8–13, 15) |
| `checkout/CheckoutServiceTest` | `@SpringBootTest` (not `@Transactional`) + `@Sql` restore-stock + `@MockitoBean PaymentGateway` | Success decrements stock and saves the order, items, last4, brand and transaction id. Decline gives order delta 0 and unchanged stock. A gateway `RuntimeException` gives `PaymentProcessingException`, order delta 0, unchanged stock. Insufficient stock (`ELEC-LAST` qty 2) results in `verify(gateway, never()).charge(any())`. The gateway is called with amount = DB price x qty (AC 19, 21, 22, 24, 25) |
| `checkout/CheckoutFlowIntegrationTest` | `@SpringBootTest` + MockMvc, real `FakePaymentGateway`, `@ExtendWith(OutputCaptureExtension.class)`, `@Sql` restore-stock | See the scenario list below (AC 16–24) |
| `checkout/CheckoutGatewayErrorIntegrationTest` | `@SpringBootTest` + MockMvc + `@MockitoBean PaymentGateway` throwing `new RuntimeException("boom 4242424242424242")`, `OutputCaptureExtension`, `@Sql` restore-stock | See the scenario list below (AC 25, 23) |
| `checkout/CheckoutConcurrencyTest` (optional) | `@SpringBootTest` + `@Sql` restore-stock | Two tasks submitted to an `ExecutorService`, released together by a `CountDownLatch`, call `placeOrder` for `ELEC-LAST` (stock 1); results collected via `Future.get()` so worker exceptions surface. Assert invariants: exactly one success, order delta 1, final stock 0, gateway charged once. The losing call may fail with `InsufficientStockException` or an optimistic-lock failure (`ObjectOptimisticLockingFailureException`), since `Product` has `@Version` (AC 22, A4) |

**`CheckoutFlowIntegrationTest` scenarios:**
- **Happy path:**
  1. Add `ELEC-001` to the cart.
  2. GET `/checkout` returns 200.
  3. POST `/checkout` with `4242 4242 4242 4242` returns 302 to the confirmation page.
  4. The confirmation returns 200 and contains `VISA •••• 4242`, the order number and `$1,234.56`.
  5. `/cart` now shows "Your cart is empty".
  6. The same confirmation URL with a new session returns 404, and an unknown UUID returns 404.
- **Empty cart:** GET `/checkout` redirects to `/cart`. POST `/checkout` with an empty cart and an invalid, blank form also redirects to `/cart`.
- **Card echo:** POST a Luhn-invalid PAN `4242424242424241` with a valid CVV `7373` (and other fields valid). The response is 200, the body contains "Card number is invalid", and the body contains neither `4242424242424241` nor `7373`. Also assert (regex on the `<input ... name="cardNumber" ...>` and `name="cvv"` tags) that neither input has a `value=` attribute. A second invalid form (blank name) with a valid PAN and CVV: the body contains neither the PAN nor the CVV. Order delta is 0 in both cases.
- **Decline:** POST with `4000 0000 0000 0002` returns 200 with the decline message. Order delta is 0, `ELEC-001` stock is unchanged, and `/cart` still lists the item.
- **Tampering:** an extra `total=0.01` param is ignored. The saved order total equals the DB price x qty.
- **No PAN anywhere:**
  - Captured log output never contains any full PAN or CVV used in the test.
  - A JDBC scan (`JdbcTemplate.queryForList("select * from customer_order")`) shows that no column value contains a full PAN.
  - Session check: for every attribute name in the `MockHttpSession`, Java-serialize the value with `ObjectOutputStream` into a byte array. Decode it as ISO-8859-1 and assert it contains none of the PANs (with or without spaces) or CVVs. Also assert `String.valueOf(value)` contains none of them.

**`CheckoutGatewayErrorIntegrationTest` scenario:** POST a valid form. The response is 200 and contains "Payment could not be processed. Please try again.". Order delta is 0, stock is unchanged, and the cart is unchanged. The captured log contains an ERROR line for the order but not `4242424242424242` and not the CVV.

## Revision log
- v1: initial draft
- v2: addressed review round 1.
  - **Spring Boot version:** committed to Spring Boot 4.0.x only and listed the exact Boot 4 artifacts, including `spring-boot-starter-flyway` with a note that Flyway does not run without it. Added the test starters and Boot 4 test annotation packages, and an authority rule that the generated pom and the compiler win. Stated plainly not to add `thymeleaf-extras-springsecurity6`. Made the `curl.exe` bootstrap from start.spring.io mandatory and removed the offline fallback.
  - **Schema and mapping:** replaced `CHAR` with `VARCHAR`, made `created_at` `DATETIME(6)` mapped as `Instant` (preferred instant JDBC type TIMESTAMP, UTC), and required `@Column` lengths and precision to match the DDL.
  - **Fetching:** added the entity graph on `findByIdAndActiveTrue` and the rule that template-bound results fetch what the template reads.
  - **Test isolation:** added a test `application.properties` with a random-UUID H2 URL, `@Sql` restore-stock instead of `@DirtiesContext`, order-count deltas, and required seed SKUs.
  - **Card inputs:** card number and CVV are plain inputs without `th:field` or a value, and a test checks that a Luhn-invalid PAN and its CVV are not echoed.
  - **Money:** a mandatory `MoneyFormatter` bean (`Locale.US`) with an exact `$1,234.56` assertion.
  - **Expiry validation:** `@NotExpired` uses the Jakarta `ClockProvider`, wired through a `ValidationConfigurationCustomizer`, with a unit-test Validator built with `clockProvider` and null-safe behaviour.
  - **Paging and 404s:** `page` is bound as a String and parsed in `CatalogService`, and the type-mismatch 404 mapping is limited to `@PathVariable`.
  - **Non-blocking items applied:** listed `EmptyCartException` and `OrderNotFoundException` in the layout. The global advice now adds `categories`, and templates are null-safe. Declared an empty `InMemoryUserDetailsManager`. POST `/checkout` checks the empty cart first. Added gateway-exception AC 25 with tests. The card validator enforces 12–19 digits after stripping. Added `typeMismatch` messages, LIKE escaping (`!` escape char) with a test, and a session-serialization check for AC 23. Marked MySQL AC 27 as manual and made MockMvc read bodies as UTF-8. Added the optional concurrency test.
  - **Renumbering:** acceptance criteria are renumbered. The new AC 25 is the gateway error, Platform criteria are now 26–28, and there are 28 criteria in total.
- v3 (approved by the user, 2026-11-14): applied review round 2 non-blocking items. Test properties moved to `src/test/resources/config/` so main config still loads; `ProductRepositoryTest` uses `replace = NONE`; seed rules for `ELEC-001` ordering and `%`/`_` text; only `charge()` inside the try, decline checked after it, no exception cause/message logged; concurrency test via `ExecutorService` + `Future.get()` accepting either loser exception; relative expiry years and 4-digit CVV `7373` in tests plus a no-`value=` assertion; `/cart/items/{productId}` quantity parsed from String; `AddToCartForm` is a mutable class; Boot 4.x latest GA allowed; `Specification.allOf`; H2 `LOCK_TIMEOUT=10000` in tests.
