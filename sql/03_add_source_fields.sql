ALTER TABLE care_order ADD COLUMN source_system VARCHAR(50);
ALTER TABLE care_order ADD COLUMN external_order_id VARCHAR(100);
ALTER TABLE care_order ADD COLUMN raw_data TEXT;