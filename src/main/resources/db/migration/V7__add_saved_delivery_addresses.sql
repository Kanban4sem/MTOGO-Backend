ALTER TABLE addresses
    ADD COLUMN saved_by_profile_id BIGINT REFERENCES user_profiles(id) ON DELETE CASCADE;

CREATE INDEX idx_addresses_saved_by_profile_id ON addresses(saved_by_profile_id);
