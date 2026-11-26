-- Supports the catalog listing: filter on active, order by name, then LIMIT/OFFSET.
CREATE INDEX ix_product_active_name ON product(active, name);
