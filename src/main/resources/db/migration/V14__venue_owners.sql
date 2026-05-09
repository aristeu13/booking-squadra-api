ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE public.users
    ADD CONSTRAINT users_role_check
        CHECK (role IN ('user', 'admin', 'venue_owner'));

CREATE TABLE public.venue_owners (
    user_id  UUID NOT NULL REFERENCES public.users(id)  ON DELETE CASCADE,
    venue_id UUID NOT NULL REFERENCES public.venues(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, venue_id)
);

CREATE INDEX venue_owners_user_id_idx  ON public.venue_owners (user_id);
CREATE INDEX venue_owners_venue_id_idx ON public.venue_owners (venue_id);
