package com.example.ecommerce.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CartTest {

    private final Cart cart = new Cart();

    @Test
    void newCartShouldBeEmpty() {
        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.itemCount()).isZero();
        assertThat(cart.lines()).isEmpty();
    }

    @Test
    void addShouldMergeQuantitiesForSameProduct() {
        cart.add(1L, 2);
        cart.add(1L, 3);

        assertThat(cart.lines()).containsExactly(Map.entry(1L, 5));
        assertThat(cart.quantityOf(1L)).isEqualTo(5);
    }

    @Test
    void addShouldKeepInsertionOrder() {
        cart.add(7L, 1);
        cart.add(1L, 1);
        cart.add(3L, 1);

        assertThat(cart.lines().keySet()).containsExactly(7L, 1L, 3L);
    }

    @Test
    void addShouldRejectQuantityBelowOne() {
        assertThatThrownBy(() -> cart.add(1L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void setQuantityShouldReplaceQuantity() {
        cart.add(1L, 2);
        cart.setQuantity(1L, 7);
        assertThat(cart.quantityOf(1L)).isEqualTo(7);
    }

    @Test
    void setQuantityZeroShouldRemoveLine() {
        cart.add(1L, 2);
        cart.add(2L, 1);

        cart.setQuantity(1L, 0);

        assertThat(cart.lines()).containsOnlyKeys(2L);
    }

    @Test
    void removeShouldDeleteLineAndIgnoreMissingLine() {
        cart.add(1L, 2);
        cart.remove(1L);
        cart.remove(99L);
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void clearShouldEmptyCart() {
        cart.add(1L, 2);
        cart.add(2L, 3);
        cart.clear();
        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.itemCount()).isZero();
    }

    @Test
    void itemCountShouldSumQuantities() {
        cart.add(1L, 2);
        cart.add(2L, 3);
        cart.add(1L, 1);
        assertThat(cart.itemCount()).isEqualTo(6);
    }

    @Test
    void linesShouldBeUnmodifiableSnapshot() {
        cart.add(1L, 2);
        Map<Long, Integer> snapshot = cart.lines();

        cart.add(2L, 1);

        assertThat(snapshot).containsOnlyKeys(1L);
        assertThatThrownBy(() -> snapshot.put(3L, 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldSurviveJavaSerializationRoundTrip() throws Exception {
        cart.add(1L, 2);
        cart.add(5L, 3);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(cart);
        }
        Cart copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (Cart) in.readObject();
        }

        assertThat(copy.lines()).containsExactly(Map.entry(1L, 2), Map.entry(5L, 3));
        assertThat(copy.itemCount()).isEqualTo(5);
    }
}
