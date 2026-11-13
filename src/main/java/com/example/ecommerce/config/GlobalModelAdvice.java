package com.example.ecommerce.config;

import java.util.List;

import com.example.ecommerce.cart.Cart;
import com.example.ecommerce.catalog.Category;
import com.example.ecommerce.catalog.CategoryRepository;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds attributes that every page header needs.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final Cart cart;
    private final CategoryRepository categoryRepository;

    public GlobalModelAdvice(Cart cart, CategoryRepository categoryRepository) {
        this.cart = cart;
        this.categoryRepository = categoryRepository;
    }

    @ModelAttribute("cartItemCount")
    public int cartItemCount() {
        return cart.itemCount();
    }

    @ModelAttribute("categories")
    public List<Category> categories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }
}
