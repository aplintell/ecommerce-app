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
