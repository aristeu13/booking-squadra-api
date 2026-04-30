CREATE TABLE public.user_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX user_refresh_tokens_user_id_idx
    ON public.user_refresh_tokens (user_id);

CREATE INDEX user_refresh_tokens_active_lookup_idx
    ON public.user_refresh_tokens (token_hash, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX user_refresh_tokens_expires_at_idx
    ON public.user_refresh_tokens (expires_at);

CREATE TRIGGER trg_user_refresh_tokens_updated_at
    BEFORE UPDATE ON public.user_refresh_tokens
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
