-- Audit log of account merges performed by the identifier-change flow.
--
-- Append-only. Captures both the user IDs and a snapshot of the secondary's
-- identifying fields at merge time so the record stays useful even if the
-- user row is later purged. FKs use ON DELETE SET NULL for the same reason —
-- the audit row outlives the users it references.

CREATE TABLE public.account_merge_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    primary_user_id UUID
        REFERENCES public.users(id) ON DELETE SET NULL,
    secondary_user_id UUID
        REFERENCES public.users(id) ON DELETE SET NULL,
    target_identifier TEXT NOT NULL,
    target_kind TEXT NOT NULL
        CHECK (target_kind IN ('phone', 'email')),
    merge_acknowledged BOOLEAN NOT NULL,
    bookings_moved INT NOT NULL CHECK (bookings_moved >= 0),
    refresh_tokens_revoked INT NOT NULL CHECK (refresh_tokens_revoked >= 0),
    secondary_email TEXT,
    secondary_phone_e164 TEXT,
    secondary_google_id TEXT,
    secondary_asaas_customer_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX account_merge_events_primary_idx
    ON public.account_merge_events (primary_user_id);
CREATE INDEX account_merge_events_secondary_idx
    ON public.account_merge_events (secondary_user_id);
CREATE INDEX account_merge_events_created_idx
    ON public.account_merge_events (created_at);
