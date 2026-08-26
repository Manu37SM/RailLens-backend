
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_station_name_trgm ON stations USING GIN (LOWER(station_name) gin_trgm_ops);
CREATE INDEX idx_station_code_trgm ON stations USING GIN (LOWER(station_code) gin_trgm_ops);

CREATE INDEX idx_train_name_trgm ON trains USING GIN (LOWER(train_name) gin_trgm_ops);
CREATE INDEX idx_train_number_trgm ON trains USING GIN (LOWER(train_number) gin_trgm_ops);
