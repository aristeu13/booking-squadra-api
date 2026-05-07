ALTER TABLE public.bookings
    ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX bookings_status_expires_at_idx
    ON public.bookings (status, expires_at)
    WHERE status = 'pending' AND expires_at IS NOT NULL;
