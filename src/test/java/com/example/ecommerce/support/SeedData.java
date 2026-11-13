package com.example.ecommerce.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lookups against the V2 seed data by SKU, so tests never hard-code generated ids.
 */
public final class SeedData {

    private SeedData() {
    }

    public static long idOf(JdbcTemplate jdbc, String sku) {
        Long id = jdbc.queryForObject("select id from product where sku = ?", Long.class, sku);
        if (id == null) {
            throw new IllegalStateException("No seeded product " + sku);
        }
        return id;
    }

    public static int stockOf(JdbcTemplate jdbc, String sku) {
        Integer stock = jdbc.queryForObject("select stock_quantity from product where sku = ?", Integer.class, sku);
        if (stock == null) {
            throw new IllegalStateException("No seeded product " + sku);
        }
        return stock;
    }

    public static void setStock(JdbcTemplate jdbc, String sku, int stock) {
        jdbc.update("update product set stock_quantity = ? where sku = ?", stock, sku);
    }

    public static long orderCount(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject("select count(*) from customer_order", Long.class);
        return count == null ? 0 : count;
    }
}
