package com.example.ecommerce.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.ecommerce.catalog.Product;
import com.example.ecommerce.catalog.ProductNotFoundException;
import com.example.ecommerce.catalog.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartService {

    public static final int MAX_QUANTITY = 99;

    private final Cart cart;
    private final ProductRepository productRepository;

    public CartService(Cart cart, ProductRepository productRepository) {
        this.cart = cart;
        this.productRepository = productRepository;
    }

    /**
     * Adds a product to the cart (or increases its quantity).
     *
     * @return the product name, for the confirmation message
     * @throws ProductNotFoundException if the product does not exist or is inactive
     * @throws CartException if the resulting quantity exceeds stock or the 99 cap
     */
    public String add(Long productId, int quantity) {
        if (quantity < 1) {
            throw new CartException("Quantity must be at least 1");
        }
        Product product = findActive(productId);
        int newQuantity = cart.quantityOf(productId) + quantity;
        checkQuantity(product, newQuantity);
        cart.add(productId, quantity);
        return product.getName();
    }

    /**
     * Sets a line quantity from a raw request value. 0 removes the line.
     *
     * @return the product name if known, otherwise null
     */
    public String update(Long productId, String rawQuantity) {
        int quantity = parseQuantity(rawQuantity);
        if (quantity == 0) {
            cart.remove(productId);
            return productRepository.findById(productId).map(Product::getName).orElse(null);
        }
        Product product = productRepository.findById(productId).filter(Product::isActive).orElse(null);
        if (product == null) {
            cart.remove(productId);
            throw new CartException("That product is no longer available and was removed from your cart");
        }
        checkQuantity(product, quantity);
        cart.setQuantity(productId, quantity);
        return product.getName();
    }

    /** Removes a line. Removing a missing line is a no-op. */
    public void remove(Long productId) {
        cart.remove(productId);
    }

    /**
     * Builds the cart view from current database prices. Lines whose product is missing or inactive are
     * dropped from the cart with a notice; lines with more quantity than stock are flagged and block checkout.
     */
    public CartView view() {
        Map<Long, Integer> lines = cart.lines();
        if (lines.isEmpty()) {
            return new CartView(List.of(), zero(), zero(), 0, List.of(), false);
        }
        Map<Long, Product> products = productRepository.findAllById(lines.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartLine> cartLines = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        BigDecimal subtotal = zero();
        int itemCount = 0;
        boolean checkoutAllowed = true;

        for (Map.Entry<Long, Integer> entry : lines.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product == null || !product.isActive()) {
                cart.remove(entry.getKey());
                notices.add(product == null
                        ? "An item that is no longer available was removed from your cart."
                        : product.getName() + " is no longer available and was removed from your cart.");
                continue;
            }
            int quantity = entry.getValue();
            BigDecimal unitPrice = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            boolean insufficient = quantity > product.getStockQuantity();
            if (insufficient) {
                checkoutAllowed = false;
            }
            cartLines.add(new CartLine(product.getId(), product.getName(), unitPrice, quantity, lineTotal,
                    product.getStockQuantity(), insufficient));
            subtotal = subtotal.add(lineTotal);
            itemCount += quantity;
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal;
        return new CartView(List.copyOf(cartLines), subtotal, total, itemCount, List.copyOf(notices),
                checkoutAllowed && !cartLines.isEmpty());
    }

    private Product findActive(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private static void checkQuantity(Product product, int quantity) {
        if (quantity > MAX_QUANTITY) {
            throw new CartException("You can buy at most " + MAX_QUANTITY + " of " + product.getName());
        }
        if (quantity > product.getStockQuantity()) {
            if (product.getStockQuantity() <= 0) {
                throw new CartException(product.getName() + " is out of stock");
            }
            throw new CartException("Only " + product.getStockQuantity() + " of " + product.getName() + " available");
        }
    }

    private static int parseQuantity(String raw) {
        String message = "Quantity must be a number between 0 and " + MAX_QUANTITY;
        if (raw == null || raw.isBlank()) {
            throw new CartException(message);
        }
        int quantity;
        try {
            quantity = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new CartException(message);
        }
        if (quantity < 0 || quantity > MAX_QUANTITY) {
            throw new CartException(message);
        }
        return quantity;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
