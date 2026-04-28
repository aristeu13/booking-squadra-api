ALTER TABLE public.bookings
    DROP CONSTRAINT bookings_no_overlap;

ALTER TABLE public.bookings
    DROP COLUMN booking_date,
    DROP COLUMN start_time,
    DROP COLUMN end_time,
    ADD COLUMN starts_at TIMESTAMPTZ NOT NULL,
    ADD COLUMN ends_at TIMESTAMPTZ NOT NULL,
    ADD COLUMN venue_timezone TEXT NOT NULL,
    ADD CHECK (ends_at > starts_at),
    ADD CONSTRAINT bookings_no_overlap EXCLUDE USING GIST (
        court_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status IN ('pending','confirmed'));

CREATE INDEX bookings_court_starts_at_idx ON public.bookings (court_id, starts_at);

ALTER TABLE public.venues
    DROP CONSTRAINT venues_city_id_fkey,
    ALTER COLUMN city_id SET NOT NULL,
    ADD CONSTRAINT venues_city_id_fkey
        FOREIGN KEY (city_id) REFERENCES public.cities(id) ON DELETE RESTRICT,
    DROP COLUMN city,
    DROP COLUMN state_code;
