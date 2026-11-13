package com.example.ecommerce.catalog;

import java.util.Locale;

import jakarta.persistence.criteria.Join;

import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    /** Escape character for LIKE patterns; avoids MySQL backslash quirks in string literals. */
    static final char ESCAPE = '!';

    private ProductSpecifications() {
    }

    public static Specification<Product> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> inCategory(String slug) {
        return (root, query, cb) -> {
            Join<Product, Category> category = root.join("category");
            return cb.equal(category.get("slug"), slug);
        };
    }

    /**
     * Case-insensitive literal "contains" match on name or description.
     * Percent, underscore, backslash and exclamation mark in the term are matched literally.
     */
    public static Specification<Product> matches(String term) {
        String pattern = "%" + escape(term.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE));
    }

    static String escape(String term) {
        // The escape character itself must be escaped first, otherwise the escapes added below would be doubled.
        return term.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
