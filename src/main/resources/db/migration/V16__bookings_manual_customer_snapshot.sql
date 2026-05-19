ALTER TABLE public.bookings
    ADD COLUMN customer_name TEXT,
    ADD COLUMN customer_phone TEXT;

-- Replace the inline CHECK on booking_type (auto-named by PostgreSQL) to add 'manual'.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'public.bookings'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%booking_type%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.bookings DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE public.bookings
    ADD CONSTRAINT bookings_booking_type_check
    CHECK (booking_type IN ('reservation','block','manual'));

ALTER TABLE public.bookings
    ADD CONSTRAINT bookings_manual_snapshot_check
    CHECK (
        booking_type <> 'manual'
        OR (customer_name IS NOT NULL AND customer_phone IS NOT NULL)
    );
