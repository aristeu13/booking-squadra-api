-- Drop the legacy free-form users.phone column. V18 already backfilled
-- users.phone_e164 from this column and is now the source of truth for
-- a user's phone number. The bookings table keeps its own customer_phone
-- snapshot for manual bookings, so no other reference remains.
--
-- Deploy ordering note: ships atomically with the Java change that removes
-- User.phone and switches UserService to read/write phone_e164.

ALTER TABLE public.users
    DROP COLUMN phone;
