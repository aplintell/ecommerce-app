INSERT INTO category (id, name, slug) VALUES (1, 'Electronics', 'electronics');
INSERT INTO category (id, name, slug) VALUES (2, 'Books', 'books');
INSERT INTO category (id, name, slug) VALUES (3, 'Home & Kitchen', 'home-kitchen');
INSERT INTO category (id, name, slug) VALUES (4, 'Clothing', 'clothing');

INSERT INTO product (sku, name, description, price, stock_quantity, image_url, active, category_id) VALUES
('ELEC-001', 'Aurora 4K Laptop', 'A 15.6 inch ultrabook with a 4K display, 32 GB of memory and an all-day battery.', 1234.56, 50, '/images/products/placeholder.svg', TRUE, 1),
('ELEC-002', 'Bluetooth Speaker Mini', 'Pocket-sized speaker with rich sound, 12 hour battery life and splash resistance.', 39.99, 75, '/images/products/placeholder.svg', TRUE, 1),
('ELEC-003', 'Wireless Ergonomic Mouse', 'Sculpted vertical mouse that reduces wrist strain, with silent clicks.', 24.99, 120, '/images/products/placeholder.svg', TRUE, 1),
('ELEC-004', 'Mechanical Keyboard', 'Tenkeyless keyboard with tactile switches and white backlighting.', 89.00, 40, '/images/products/placeholder.svg', TRUE, 1),
('ELEC-005', 'USB-C Charging Hub', 'Seven port hub with power delivery, HDMI output and an SD card reader.', 34.50, 60, '/images/products/placeholder.svg', TRUE, 1),
('ELEC-LAST', 'Studio Monitor Headphones', 'Closed-back studio headphones with a flat frequency response. Last unit in stock.', 299.00, 1, '/images/products/placeholder.svg', TRUE, 1),
('BOOK-001', 'Clean Architecture Handbook', 'A practical guide to structuring software that stays easy to change.', 19.99, 100, '/images/products/placeholder.svg', TRUE, 2),
('BOOK-002', 'Domain Modeling Made Practical', 'Learn to capture business rules in code with clear, expressive models.', 42.00, 30, '/images/products/placeholder.svg', TRUE, 2),
('BOOK-003', 'Gardening Through the Seasons', 'Month by month advice for growing vegetables, herbs and flowers.', 27.50, 25, '/images/products/placeholder.svg', TRUE, 2),
('BOOK-004', 'Mystery at Harbor Point', 'A gripping detective novel set in a quiet fishing town.', 12.99, 80, '/images/products/placeholder.svg', TRUE, 2),
('BOOK-OOS', 'Rare Poetry Collection', 'A limited edition anthology of modern poetry. Currently sold out.', 9.99, 0, '/images/products/placeholder.svg', TRUE, 2),
('HOME-LOW', 'Cast Iron Skillet', 'Pre-seasoned 12 inch skillet that goes from stovetop to oven.', 49.50, 3, '/images/products/placeholder.svg', TRUE, 3),
('HOME-PCT', '100% Cotton Tea Towel', 'Soft, absorbent and lint-free kitchen towel woven from long-staple cotton.', 15.00, 20, '/images/products/placeholder.svg', TRUE, 3),
('HOME-001', 'Ceramic Pour-Over Coffee Set', 'Hand-glazed dripper and carafe for a clean, bright cup of coffee.', 38.00, 35, '/images/products/placeholder.svg', TRUE, 3),
('HOME-002', 'Bamboo Cutting Board', 'Durable, knife-friendly board with a juice groove and side handles.', 22.00, 70, '/images/products/placeholder.svg', TRUE, 3),
('HOME-003', 'Electric Kettle', 'A 1.7 litre stainless steel kettle with temperature presets.', 45.99, 45, '/images/products/placeholder.svg', TRUE, 3),
('CLOTH-001', 'Merino Wool Sweater', 'Lightweight, breathable crew-neck sweater made from fine merino wool.', 79.00, 30, '/images/products/placeholder.svg', TRUE, 4),
('CLOTH-002', 'Organic Cotton T-Shirt', 'Everyday crew-neck tee made from organic cotton.', 18.00, 150, '/images/products/placeholder.svg', TRUE, 4),
('CLOTH-003', 'Waterproof Rain Jacket', 'Packable shell jacket with taped seams and an adjustable hood.', 129.00, 20, '/images/products/placeholder.svg', TRUE, 4),
('CLOTH-OFF', 'Discontinued Denim Jacket', 'Classic denim jacket that is no longer sold.', 29.00, 10, '/images/products/placeholder.svg', FALSE, 4);
