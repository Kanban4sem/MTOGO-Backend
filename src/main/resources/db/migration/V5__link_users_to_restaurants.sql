-- Only a restaurant user has a restaurant. The role lives in another table, so
-- the application enforces that rule where a user is created.
ALTER TABLE users ADD COLUMN restaurant_id BIGINT REFERENCES restaurants(id);

CREATE INDEX idx_users_restaurant_id ON users(restaurant_id);
