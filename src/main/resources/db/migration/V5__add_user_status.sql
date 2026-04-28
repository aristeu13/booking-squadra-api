ALTER TABLE public.users
    ADD COLUMN status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'deleted'));
