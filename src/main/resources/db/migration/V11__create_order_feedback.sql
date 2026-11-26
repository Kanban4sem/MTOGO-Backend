CREATE TABLE order_feedback (
    order_id BIGINT PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
    title VARCHAR(100),
    description VARCHAR(1000),
    food_rating INTEGER NOT NULL CHECK (food_rating BETWEEN 1 AND 6),
    overall_rating INTEGER NOT NULL CHECK (overall_rating BETWEEN 1 AND 6),
    delivery_rating INTEGER NOT NULL CHECK (delivery_rating BETWEEN 1 AND 6)
);
