-- Backend audit finding (2026-07-31): idx_station_code and idx_train_number
-- are redundant - Postgres already builds a B-tree index automatically to
-- enforce uk_stations_station_code / uk_trains_train_number's UNIQUE
-- constraints (see V1's CREATE TABLE statements), so these two explicit
-- indexes duplicate an index that already exists on the exact same single
-- column. Pure wasted disk space and write overhead with zero query-plan
-- benefit - on a free-tier Postgres instance with a storage cap, that's a
-- real (if small) cost for no upside.
--
-- idx_station_name and idx_train_name are NOT touched here - those cover
-- different columns than any unique constraint and are still needed for
-- the fuzzy/prefix name searches in StationRepository/TrainRepository.
DROP INDEX idx_station_code;
DROP INDEX idx_train_number;
