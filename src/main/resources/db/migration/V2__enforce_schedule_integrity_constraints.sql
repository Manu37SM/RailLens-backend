
ALTER TABLE train_schedule
    ALTER COLUMN train_id SET NOT NULL;

ALTER TABLE train_schedule
    ALTER COLUMN station_id SET NOT NULL;

ALTER TABLE train_schedule
    ALTER COLUMN sequence_no SET NOT NULL;

ALTER TABLE train_schedule
    ADD CONSTRAINT uk_train_schedule_train_id_sequence_no UNIQUE (train_id, sequence_no);

