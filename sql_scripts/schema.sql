CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at	  TIMESTAMP	   NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS orders (
    id           BIGSERIAL      PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(15, 2) NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status  ON orders(status);

CREATE TABLE IF NOT EXISTS items (
    id           BIGSERIAL      PRIMARY KEY,
    item_id   VARCHAR(100)   NOT NULL,
    item_name VARCHAR(255)   NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(15, 2) NOT NULL CHECK (unit_price > 0)
    );

CREATE INDEX IF NOT EXISTS idx_items_item_id ON items(item_id);

CREATE TABLE IF NOT EXISTS order_item_mappings (
    id       BIGSERIAL PRIMARY KEY,
    order_id BIGINT    NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    item_id  BIGINT    NOT NULL REFERENCES items(id)  ON DELETE CASCADE,
    UNIQUE (order_id, item_id)
    );

CREATE INDEX IF NOT EXISTS idx_oim_item_id  ON order_item_mappings(item_id);

