package com.example.ecommerce.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.ecommerce.catalog.Category;
import com.example.ecommerce.catalog.Product;
import com.example.ecommerce.catalog.ProductNotFoundException;
import com.example.ecommerce.catalog.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    private static final Category HOME = new Category("Home & Kitchen", "home-kitchen");

    @Mock
    private ProductRepository productRepository;

    private Cart cart;
    private CartService cartService;

    private Product skillet;   // HOME-LOW-like: stock 3
    private Product book;      // 19.99, plenty of stock
    private Product laptop;    // 1234.56
    private Product bulk;      // large stock to test 99 cap
    private Product inactive;

    @BeforeEach
    void setUp() {
        cart = new Cart();
        cartService = new CartService(cart, productRepository);

        skillet = product(12L, "Cast Iron Skillet", "49.50", 3, true);
        book = product(7L, "Clean Architecture Handbook", "19.99", 100, true);
        laptop = product(1L, "Aurora 4K Laptop", "1234.56", 50, true);
        bulk = product(30L, "Bulk Item", "1.00", 1000, true);
        inactive = product(20L, "Discontinued Denim Jacket", "29.00", 10, false);

        for (Product p : List.of(skillet, book, laptop, bulk, inactive)) {
            when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        }
        when(productRepository.findById(404L)).thenReturn(Optional.empty());
    }

    private static Product product(Long id, String name, String price, int stock, boolean active) {
        Product p = new Product("SKU-" + id, name, name + " description", new BigDecimal(price), stock,
                "/images/products/placeholder.svg", active, HOME);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    // ---- add ----

    @Test
    void addShouldPutProductInCartAndReturnName() {
        String name = cartService.add(7L, 2);

        assertThat(name).isEqualTo("Clean Architecture Handbook");
        assertThat(cart.lines()).containsExactly(Map.entry(7L, 2));
    }

    @Test
    void addShouldIncreaseExistingQuantity() {
        cartService.add(7L, 2);
        cartService.add(7L, 3);
        assertThat(cart.quantityOf(7L)).isEqualTo(5);
    }

    @Test
    void addUpToStockShouldSucceed() {
        cartService.add(12L, 3);
        assertThat(cart.quantityOf(12L)).isEqualTo(3);
    }

    @Test
    void addMoreThanStockShouldFailAndLeaveCartUnchanged() {
        assertThatThrownBy(() -> cartService.add(12L, 4))
                .isInstanceOf(CartException.class)
                .hasMessage("Only 3 of Cast Iron Skillet available");
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void addThatPushesCartQuantityOverStockShouldFailAndKeepPreviousQuantity() {
        cartService.add(12L, 2);

        assertThatThrownBy(() -> cartService.add(12L, 2))
                .isInstanceOf(CartException.class)
                .hasMessage("Only 3 of Cast Iron Skillet available");
        assertThat(cart.quantityOf(12L)).isEqualTo(2);
    }

    @Test
    void addShouldEnforce99CapEvenWithEnoughStock() {
        cartService.add(30L, 99);

        assertThatThrownBy(() -> cartService.add(30L, 1))
                .isInstanceOf(CartException.class)
                .hasMessageContaining("99");
        assertThat(cart.quantityOf(30L)).isEqualTo(99);
    }

    @Test
    void addOf100AtOnceShouldBeRejected() {
        assertThatThrownBy(() -> cartService.add(30L, 100)).isInstanceOf(CartException.class);
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void addQuantityBelowOneShouldBeRejected() {
        assertThatThrownBy(() -> cartService.add(7L, 0)).isInstanceOf(CartException.class);
        assertThatThrownBy(() -> cartService.add(7L, -2)).isInstanceOf(CartException.class);
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void addInactiveProductShouldBeRejected() {
        assertThatThrownBy(() -> cartService.add(20L, 1)).isInstanceOf(ProductNotFoundException.class);
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void addMissingProductShouldBeRejected() {
        assertThatThrownBy(() -> cartService.add(404L, 1)).isInstanceOf(ProductNotFoundException.class);
        assertThat(cart.isEmpty()).isTrue();
    }

    // ---- update ----

    @Test
    void updateShouldSetQuantity() {
        cartService.add(7L, 1);
        assertThat(cartService.update(7L, "4")).isEqualTo("Clean Architecture Handbook");
        assertThat(cart.quantityOf(7L)).isEqualTo(4);
    }

    @Test
    void updateToZeroShouldRemoveLine() {
        cartService.add(7L, 1);
        cartService.update(7L, "0");
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void updateAboveStockShouldFailAndKeepQuantity() {
        cartService.add(12L, 1);

        assertThatThrownBy(() -> cartService.update(12L, "4"))
                .isInstanceOf(CartException.class)
                .hasMessage("Only 3 of Cast Iron Skillet available");
        assertThat(cart.quantityOf(12L)).isEqualTo(1);
    }

    @Test
    void updateAbove99ShouldFail() {
        cartService.add(30L, 1);
        assertThatThrownBy(() -> cartService.update(30L, "100")).isInstanceOf(CartException.class);
        assertThat(cart.quantityOf(30L)).isEqualTo(1);
    }

    @Test
    void updateWithNonNumericNegativeOrMissingQuantityShouldFail() {
        cartService.add(7L, 2);
        for (String raw : new String[] {"abc", "-1", "", null, "1.5"}) {
            assertThatThrownBy(() -> cartService.update(7L, raw)).as("raw=%s", raw)
                    .isInstanceOf(CartException.class);
        }
        assertThat(cart.quantityOf(7L)).isEqualTo(2);
    }

    @Test
    void updateOfInactiveProductShouldFail() {
        cart.add(20L, 1);
        assertThatThrownBy(() -> cartService.update(20L, "2")).isInstanceOf(CartException.class);
        assertThat(cart.quantityOf(20L)).isNotEqualTo(2);
    }

    @Test
    void removeShouldDeleteLineAndIgnoreMissing() {
        cartService.add(7L, 1);
        cartService.remove(7L);
        cartService.remove(404L);
        assertThat(cart.isEmpty()).isTrue();
    }

    // ---- view ----

    @Test
    void viewOfEmptyCartShouldHaveZeroTotalsAndNoCheckout() {
        CartView view = cartService.view();

        assertThat(view.isEmpty()).isTrue();
        assertThat(view.subtotal()).isEqualByComparingTo("0.00");
        assertThat(view.total()).isEqualByComparingTo("0.00");
        assertThat(view.itemCount()).isZero();
        assertThat(view.checkoutAllowed()).isFalse();
    }

    @Test
    void viewShouldComputeTotalsFromRepositoryPricesAtScale2() {
        Product oddPrice = product(40L, "Odd Price Item", "10.5", 10, true);
        cart.add(1L, 1);
        cart.add(7L, 2);
        cart.add(40L, 3);
        when(productRepository.findAllById(any())).thenReturn(List.of(oddPrice, book, laptop));

        CartView view = cartService.view();

        assertThat(view.lines()).extracting(CartLine::productId).containsExactly(1L, 7L, 40L);
        CartLine laptopLine = view.lines().get(0);
        assertThat(laptopLine.name()).isEqualTo("Aurora 4K Laptop");
        assertThat(laptopLine.unitPrice()).isEqualTo(new BigDecimal("1234.56"));
        assertThat(laptopLine.lineTotal()).isEqualTo(new BigDecimal("1234.56"));

        CartLine bookLine = view.lines().get(1);
        assertThat(bookLine.quantity()).isEqualTo(2);
        assertThat(bookLine.lineTotal()).isEqualTo(new BigDecimal("39.98"));

        CartLine oddLine = view.lines().get(2);
        assertThat(oddLine.unitPrice()).isEqualTo(new BigDecimal("10.50"));
        assertThat(oddLine.unitPrice().scale()).isEqualTo(2);
        assertThat(oddLine.lineTotal()).isEqualTo(new BigDecimal("31.50"));

        // 1234.56 + 39.98 + 31.50
        assertThat(view.subtotal()).isEqualTo(new BigDecimal("1306.04"));
        assertThat(view.total()).isEqualTo(new BigDecimal("1306.04"));
        assertThat(view.subtotal().scale()).isEqualTo(2);
        assertThat(view.itemCount()).isEqualTo(6);
        assertThat(view.notices()).isEmpty();
        assertThat(view.checkoutAllowed()).isTrue();
    }

    @Test
    void viewShouldDropMissingProductWithNotice() {
        cart.add(7L, 1);
        cart.add(404L, 2);
        when(productRepository.findAllById(any())).thenReturn(List.of(book));

        CartView view = cartService.view();

        assertThat(view.lines()).extracting(CartLine::productId).containsExactly(7L);
        assertThat(view.notices()).hasSize(1);
        assertThat(view.total()).isEqualTo(new BigDecimal("19.99"));
        assertThat(view.itemCount()).isEqualTo(1);
        assertThat(cart.lines()).containsOnlyKeys(7L);
        assertThat(view.checkoutAllowed()).isTrue();
    }

    @Test
    void viewShouldDropInactiveProductWithNoticeNamingIt() {
        cart.add(20L, 1);
        cart.add(7L, 1);
        when(productRepository.findAllById(any())).thenReturn(List.of(inactive, book));

        CartView view = cartService.view();

        assertThat(view.lines()).extracting(CartLine::productId).containsExactly(7L);
        assertThat(view.notices()).singleElement().asString().contains("Discontinued Denim Jacket");
        assertThat(cart.lines()).containsOnlyKeys(7L);
    }

    @Test
    void viewShouldFlagInsufficientStockAndBlockCheckout() {
        cart.add(12L, 3);
        cart.add(7L, 1);
        Product skilletNowLow = product(12L, "Cast Iron Skillet", "49.50", 1, true);
        when(productRepository.findAllById(any())).thenReturn(List.of(skilletNowLow, book));

        CartView view = cartService.view();

        CartLine skilletLine = view.lines().get(0);
        assertThat(skilletLine.insufficientStock()).isTrue();
        assertThat(skilletLine.availableStock()).isEqualTo(1);
        assertThat(skilletLine.quantity()).isEqualTo(3);
        assertThat(view.lines().get(1).insufficientStock()).isFalse();
        assertThat(view.checkoutAllowed()).isFalse();
        // The line is kept in the cart, so the shopper can fix the quantity.
        assertThat(cart.quantityOf(12L)).isEqualTo(3);
    }

    @Test
    void viewWhereAllLinesWereDroppedShouldNotAllowCheckout() {
        cart.add(404L, 1);
        when(productRepository.findAllById(any())).thenReturn(List.of());

        CartView view = cartService.view();

        assertThat(view.isEmpty()).isTrue();
        assertThat(view.notices()).hasSize(1);
        assertThat(view.checkoutAllowed()).isFalse();
    }
}
