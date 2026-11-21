ALTER TABLE orders ADD COLUMN delivery_agent_id BIGINT REFERENCES users(id);

CREATE INDEX idx_orders_delivery_agent_id ON orders(delivery_agent_id) WHERE delivery_agent_id IS NOT NULL;
CREATE INDEX idx_orders_unassigned_ready ON orders(id) WHERE delivery_agent_id IS NULL AND status = 'READY';
