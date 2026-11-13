package com.example.ecommerce.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CheckoutFormValidationTest {

    /** Fixed "now": 15 June 2030 UTC, so "current month" is 06/2030 and "previous month" is 05/2030. */
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2030-06-15T12:00:00Z"), ZoneOffset.UTC);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.byDefaultProvider().configure()
                .clockProvider(() -> FIXED_CLOCK)
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    static CheckoutForm validForm() {
        CheckoutForm form = new CheckoutForm();
        form.setFullName("Jane Doe");
        form.setEmail("jane@example.com");
        form.setAddressLine1("1 Main Street");
        form.setAddressLine2("Apt 2");
        form.setCity("Springfield");
        form.setPostalCode("12345");
        form.setCountry("United States");
        form.setCardholderName("Jane Doe");
        form.setCardNumber("4242 4242 4242 4242");
        form.setExpiryMonth(12);
        form.setExpiryYear(2032);
        form.setCvv("7373");
        return form;
    }

    private static Set<ConstraintViolation<CheckoutForm>> validate(CheckoutForm form) {
        return validator.validate(form);
    }

    private static Set<String> invalidFields(CheckoutForm form) {
        return validate(form).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static Set<String> messagesFor(CheckoutForm form, String field) {
        return validate(form).stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    /** Appends the Luhn check digit so that length, not Luhn, is the only problem. */
    private static String withLuhnCheckDigit(String partial) {
        for (int check = 0; check <= 9; check++) {
            String candidate = partial + check;
            if (com.example.ecommerce.payment.CardNumbers.luhnValid(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException();
    }

    @Test
    void validFormShouldHaveNoViolations() {
        assertThat(validate(validForm())).isEmpty();
    }

    @Test
    void addressLine2ShouldBeOptional() {
        CheckoutForm form = validForm();
        form.setAddressLine2(null);
        assertThat(validate(form)).isEmpty();
        form.setAddressLine2("");
        assertThat(validate(form)).isEmpty();
    }

    // ---- required text fields: blank and too long ----

    static Stream<Arguments> requiredTextFields() {
        return Stream.of(
                Arguments.of("fullName", 100, (BiConsumer<CheckoutForm, String>) CheckoutForm::setFullName),
                Arguments.of("addressLine1", 200, (BiConsumer<CheckoutForm, String>) CheckoutForm::setAddressLine1),
                Arguments.of("city", 100, (BiConsumer<CheckoutForm, String>) CheckoutForm::setCity),
                Arguments.of("postalCode", 20, (BiConsumer<CheckoutForm, String>) CheckoutForm::setPostalCode),
                Arguments.of("country", 100, (BiConsumer<CheckoutForm, String>) CheckoutForm::setCountry),
                Arguments.of("cardholderName", 100,
                        (BiConsumer<CheckoutForm, String>) CheckoutForm::setCardholderName));
    }

    @ParameterizedTest(name = "{0} blank is invalid")
    @MethodSource("requiredTextFields")
    void shouldRejectBlankRequiredField(String field, int max, BiConsumer<CheckoutForm, String> setter) {
        for (String blank : new String[] {null, "", "   "}) {
            CheckoutForm form = validForm();
            setter.accept(form, blank);
            assertThat(invalidFields(form)).as("value '%s'", blank).containsExactly(field);
        }
    }

    @ParameterizedTest(name = "{0} longer than {1} is invalid")
    @MethodSource("requiredTextFields")
    void shouldRejectTooLongField(String field, int max, BiConsumer<CheckoutForm, String> setter) {
        CheckoutForm form = validForm();
        setter.accept(form, "a".repeat(max));
        assertThat(validate(form)).as("exactly max length is valid").isEmpty();

        setter.accept(form, "a".repeat(max + 1));
        assertThat(invalidFields(form)).containsExactly(field);
    }

    @Test
    void shouldRejectTooLongAddressLine2() {
        CheckoutForm form = validForm();
        form.setAddressLine2("a".repeat(201));
        assertThat(invalidFields(form)).containsExactly("addressLine2");
    }

    // ---- email ----

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "jane@", "@example.com", "jane doe@example.com"})
    void shouldRejectInvalidEmail(String email) {
        CheckoutForm form = validForm();
        form.setEmail(email);
        assertThat(invalidFields(form)).containsExactly("email");
    }

    @Test
    void shouldRejectBlankOrTooLongEmail() {
        CheckoutForm form = validForm();
        form.setEmail(" ");
        assertThat(invalidFields(form)).contains("email");

        form.setEmail("a".repeat(250) + "@example.com");
        assertThat(invalidFields(form)).containsExactly("email");
    }

    // ---- card number ----

    @Test
    void shouldRejectElevenDigitCardNumberEvenIfLuhnValid() {
        CheckoutForm form = validForm();
        form.setCardNumber(withLuhnCheckDigit("4242424242"));
        assertThat(form.getCardNumber()).hasSize(11);
        assertThat(messagesFor(form, "cardNumber")).containsExactly("Card number is invalid");
    }

    @Test
    void shouldRejectTwentyDigitCardNumberEvenIfLuhnValid() {
        CheckoutForm form = validForm();
        form.setCardNumber(withLuhnCheckDigit("4242424242424242424"));
        assertThat(form.getCardNumber()).hasSize(20);
        assertThat(messagesFor(form, "cardNumber")).containsExactly("Card number is invalid");
    }

    @Test
    void shouldAcceptTwelveAndNineteenDigitLuhnValidNumbers() {
        CheckoutForm form = validForm();
        form.setCardNumber(withLuhnCheckDigit("42424242424"));
        assertThat(validate(form)).isEmpty();
        form.setCardNumber(withLuhnCheckDigit("424242424242424242"));
        assertThat(validate(form)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4242-4242 4242 4242", "4242424242424242", "4242-4242-4242-4242", "4242 4242 4242 4242"})
    void shouldAcceptCardNumberWithSpacesAndDashes(String number) {
        CheckoutForm form = validForm();
        form.setCardNumber(number);
        assertThat(validate(form)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4242abcd42424242", "4242 4242 4242 424X", "4242.4242.4242.4242"})
    void shouldRejectCardNumberWithLettersOrOtherCharacters(String number) {
        CheckoutForm form = validForm();
        form.setCardNumber(number);
        assertThat(messagesFor(form, "cardNumber")).containsExactly("Card number is invalid");
    }

    @Test
    void shouldRejectLuhnInvalidCardNumber() {
        CheckoutForm form = validForm();
        form.setCardNumber("4242424242424241");
        assertThat(messagesFor(form, "cardNumber")).containsExactly("Card number is invalid");
    }

    @Test
    void blankCardNumberShouldBeReportedOnlyByNotBlank() {
        CheckoutForm form = validForm();
        form.setCardNumber("");
        assertThat(messagesFor(form, "cardNumber")).containsExactly("Card number is required");
    }

    // ---- expiry ----

    @Test
    void shouldRequireExpiryMonthAndYear() {
        CheckoutForm form = validForm();
        form.setExpiryMonth(null);
        form.setExpiryYear(null);
        assertThat(invalidFields(form)).containsExactlyInAnyOrder("expiryMonth", "expiryYear");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1})
    void shouldRejectExpiryMonthOutOfRange(int month) {
        CheckoutForm form = validForm();
        form.setExpiryMonth(month);
        assertThat(messagesFor(form, "expiryMonth"))
                .containsExactly("Expiry month must be between 1 and 12");
    }

    @ParameterizedTest
    @ValueSource(ints = {1999, 2101, 30})
    void shouldRejectExpiryYearOutOfRange(int year) {
        CheckoutForm form = validForm();
        form.setExpiryYear(year);
        assertThat(invalidFields(form)).contains("expiryYear");
    }

    @Test
    void cardExpiringInCurrentMonthShouldBeValid() {
        CheckoutForm form = validForm();
        form.setExpiryMonth(6);
        form.setExpiryYear(2030);
        assertThat(validate(form)).isEmpty();
    }

    @Test
    void cardExpiredLastMonthShouldBeInvalidOnExpiryMonth() {
        CheckoutForm form = validForm();
        form.setExpiryMonth(5);
        form.setExpiryYear(2030);

        Set<ConstraintViolation<CheckoutForm>> violations = validate(form);

        assertThat(violations).hasSize(1);
        ConstraintViolation<CheckoutForm> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("expiryMonth");
        assertThat(violation.getMessage()).isEqualTo("Card has expired");
        assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(NotExpired.class);
    }

    @Test
    void cardExpiredLastYearShouldBeInvalid() {
        CheckoutForm form = validForm();
        form.setExpiryMonth(12);
        form.setExpiryYear(2029);
        assertThat(messagesFor(form, "expiryMonth")).containsExactly("Card has expired");
    }

    @Test
    void nullMonthOrYearShouldNotProduceNotExpiredViolation() {
        CheckoutForm noMonth = validForm();
        noMonth.setExpiryMonth(null);
        noMonth.setExpiryYear(2020);
        assertThat(validate(noMonth)).noneMatch(CheckoutFormValidationTest::isNotExpired);

        CheckoutForm noYear = validForm();
        noYear.setExpiryMonth(1);
        noYear.setExpiryYear(null);
        assertThat(validate(noYear)).noneMatch(CheckoutFormValidationTest::isNotExpired);
    }

    @Test
    void outOfRangeMonthShouldNotProduceNotExpiredViolation() {
        CheckoutForm form = validForm();
        form.setExpiryMonth(13);
        form.setExpiryYear(2020);
        assertThat(validate(form)).noneMatch(CheckoutFormValidationTest::isNotExpired);
    }

    private static boolean isNotExpired(ConstraintViolation<?> v) {
        return v.getConstraintDescriptor().getAnnotation() instanceof NotExpired;
    }

    // ---- CVV ----

    @ParameterizedTest
    @ValueSource(strings = {"123", "7373"})
    void shouldAcceptThreeOrFourDigitCvv(String cvv) {
        CheckoutForm form = validForm();
        form.setCvv(cvv);
        assertThat(validate(form)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "12345", "12a", "abc", " 123"})
    void shouldRejectCvvThatIsNotThreeOrFourDigits(String cvv) {
        CheckoutForm form = validForm();
        form.setCvv(cvv);
        assertThat(messagesFor(form, "cvv")).containsExactly("CVV must be 3 or 4 digits");
    }

    @Test
    void shouldRequireCvv() {
        CheckoutForm form = validForm();
        form.setCvv(null);
        assertThat(invalidFields(form)).containsExactly("cvv");
    }

    // ---- toString / clearing ----

    @Test
    void toStringShouldMaskCardNumberAndCvv() {
        CheckoutForm form = validForm();
        form.setCardNumber("4242424242424242");

        String text = form.toString();

        assertThat(text).doesNotContain("4242424242424242").doesNotContain("4242 4242").doesNotContain("7373");
        assertThat(text).contains("Jane Doe");
    }

    @Test
    void clearSensitiveFieldsShouldRemoveCardNumberAndCvvOnly() {
        CheckoutForm form = validForm();
        form.clearSensitiveFields();
        assertThat(form.getCardNumber()).isNull();
        assertThat(form.getCvv()).isNull();
        assertThat(form.getFullName()).isEqualTo("Jane Doe");
        assertThat(form.getExpiryMonth()).isEqualTo(12);
    }
}
