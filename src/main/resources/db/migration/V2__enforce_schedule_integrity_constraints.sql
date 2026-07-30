-- ============================================================================
-- WARNING - DO NOT APPLY THIS MIGRATION BLINDLY. Read this comment first.
-- ============================================================================
--
-- This migration implements the two P0 findings from
-- RailLens_Database_Schema_Review.md:
--   1. train_schedule.train_id and station_id are nullable - a "stop" that
--      belongs to no route and no place is meaningless in this domain and
--      causes NPEs at read time instead of being rejected at write time.
--   2. train_schedule.sequence_no is nullable with no uniqueness guarantee
--      on (train_id, sequence_no) - a NULL or duplicate sequence_no can
--      silently corrupt a route's displayed stop order.
--
-- It was written and reviewed in a sandbox with NO LIVE DATABASE ACCESS
-- this session - nothing here has been executed or validated against a
-- real database. Per CLAUDE.md ("never modify production data without
-- approval", "explain database migrations before applying them") and per
-- the project's own testing principle ("do not assume changes work"),
-- this file must be treated as a DRAFT for human review, not a
-- ready-to-run migration, until someone runs it against a real (ideally
-- staging, not production-first) copy of the database.
--
-- Why this is written to FAIL LOUDLY rather than silently fix data:
-- if any existing row already has a NULL train_id/station_id/sequence_no,
-- or if two rows already share a (train_id, sequence_no) pair, the ALTER
-- TABLE statements below will fail with a constraint-violation error and
-- roll back (Flyway runs each migration in a transaction) rather than
-- silently deleting or altering the offending rows. That failure is the
-- intended behavior - it forces a human to look at and decide what to do
-- with the specific bad rows (see the diagnostic queries below), rather
-- than this migration making that judgment call unattended. Per the
-- Database Schema Review, a direct check of the current dataset CSV found
-- zero duplicate (train_id, sequence_no) pairs and the NULL-FK rate was
-- not independently re-verified against the live table - run the
-- diagnostic queries below first.
--
-- BEFORE RUNNING THIS MIGRATION, run these against the target database
-- and resolve any non-zero counts (delete/fix the offending rows, or
-- decide this migration needs to be split/adjusted) - do NOT proceed
-- past a non-zero result without a deliberate decision on what to do
-- with those specific rows:
--
--   SELECT COUNT(*) FROM train_schedule WHERE train_id IS NULL;
--   SELECT COUNT(*) FROM train_schedule WHERE station_id IS NULL;
--   SELECT COUNT(*) FROM train_schedule WHERE sequence_no IS NULL;
--   SELECT train_id, sequence_no, COUNT(*)
--     FROM train_schedule
--     WHERE train_id IS NOT NULL AND sequence_no IS NOT NULL
--     GROUP BY train_id, sequence_no
--     HAVING COUNT(*) > 1;
--
-- The live, queryable equivalent of the first three checks is also
-- available at runtime via GET /api/v1/admin/health
-- (DatasetHealthService.orphanStationCount/duplicateScheduleRowCount) -
-- see RailLens_Database_Schema_Review.md's "Latest Audit Update" for how
-- that relates to (but does not replace) this migration.
-- ============================================================================

ALTER TABLE train_schedule
    ALTER COLUMN train_id SET NOT NULL;

ALTER TABLE train_schedule
    ALTER COLUMN station_id SET NOT NULL;

ALTER TABLE train_schedule
    ALTER COLUMN sequence_no SET NOT NULL;

-- This UNIQUE constraint also resolves the schema review's separate P1
-- finding ("no composite index on train_schedule (train_id,
-- sequence_no)") as a side effect - Postgres automatically builds a
-- composite B-tree index to enforce a multi-column UNIQUE constraint, and
-- that's exactly the index shape findByTrain_TrainNumberOrderBySequenceNo/
-- findByTrain_IdInOrderByTrain_IdAscSequenceNoAsc would benefit from. No
-- separate index-only migration is needed for that finding - adding one
-- here would just be the same "redundant index" problem the schema
-- review flagged elsewhere (P1 finding on idx_station_code/
-- idx_train_number duplicating their own unique constraints' indexes).
ALTER TABLE train_schedule
    ADD CONSTRAINT uk_train_schedule_train_id_sequence_no UNIQUE (train_id, sequence_no);

-- Also gives the two existing foreign keys explicit, self-describing
-- names in place of Hibernate's auto-generated ones (fkjgpgpiyhf...-style
-- hashes) - see the schema review's P1 "auto-generated constraint/index/
-- FK names" finding. Requires knowing the actual generated names on the
-- target database first (they're not predictable without a live DB to
-- inspect - query information_schema.table_constraints for
-- train_schedule's foreign keys), so this is commented out rather than
-- guessed at:
--
-- ALTER TABLE train_schedule
--     RENAME CONSTRAINT <actual_generated_fk_name_for_station_id> TO fk_train_schedule_station_id;
-- ALTER TABLE train_schedule
--     RENAME CONSTRAINT <actual_generated_fk_name_for_train_id> TO fk_train_schedule_train_id;
