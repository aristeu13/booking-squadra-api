ALTER TABLE public.users
    ADD COLUMN google_id TEXT,
    ADD COLUMN cpf TEXT;

CREATE UNIQUE INDEX users_google_id_key ON public.users (google_id)
    WHERE google_id IS NOT NULL;
