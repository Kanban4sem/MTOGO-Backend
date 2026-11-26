CREATE TABLE feature_maintenance (
    feature VARCHAR(32) PRIMARY KEY,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO feature_maintenance (feature) VALUES ('AUTHENTICATION'), ('ORDERING');
