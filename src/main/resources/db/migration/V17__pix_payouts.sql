-- Replace Asaas subaccount/split model with main-account custody + PIX payout.

ALTER TABLE public.venues
    ADD COLUMN pix_key TEXT,
    ADD COLUMN pix_key_type TEXT
        CHECK (pix_key_type IS NULL
               OR pix_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'EVP'));

ALTER TABLE public.venues
    DROP COLUMN asaas_wallet_id;

ALTER TABLE public.payments
    DROP COLUMN asaas_split_id,
    DROP COLUMN wallet_id;

CREATE TABLE public.venue_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL UNIQUE
        REFERENCES public.bookings(id) ON DELETE CASCADE,
    venue_id UUID NOT NULL
        REFERENCES public.venues(id),
    amount_cents INT NOT NULL CHECK (amount_cents > 0),
    pix_key TEXT NOT NULL,
    pix_key_type TEXT NOT NULL
        CHECK (pix_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'EVP')),
    scheduled_for TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL
        CHECK (status IN ('SCHEDULED', 'SENT', 'FAILED', 'CANCELLED')),
    asaas_transfer_id TEXT,
    sent_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX venue_payouts_status_scheduled_idx
    ON public.venue_payouts (status, scheduled_for);

CREATE TRIGGER trg_venue_payouts_updated_at
    BEFORE UPDATE ON public.venue_payouts
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
