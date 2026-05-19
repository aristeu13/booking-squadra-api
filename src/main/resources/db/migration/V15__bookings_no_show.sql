ALTER TABLE public.bookings
    ADD COLUMN no_show BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX bookings_user_venue_anchor_idx
    ON public.bookings (user_id, court_id, starts_at)
    WHERE status = 'confirmed' OR no_show = TRUE
       OR (status = 'cancelled' AND payment_method = 'local');
