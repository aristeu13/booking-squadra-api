ALTER TABLE public.users
    ADD COLUMN asaas_customer_id TEXT;

CREATE UNIQUE INDEX users_asaas_customer_id_uidx
    ON public.users (asaas_customer_id) WHERE asaas_customer_id IS NOT NULL;

ALTER TABLE public.venues
    ADD COLUMN asaas_wallet_id TEXT;

DROP INDEX IF EXISTS public.bookings_transaction_id_uidx;
ALTER TABLE public.bookings
    DROP COLUMN transaction_id;

CREATE TABLE public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL UNIQUE
        REFERENCES public.bookings(id) ON DELETE CASCADE,
    asaas_payment_id  TEXT NOT NULL UNIQUE,
    asaas_customer_id TEXT NOT NULL,
    asaas_split_id    TEXT NOT NULL,
    wallet_id         TEXT NOT NULL,
    billing_type      TEXT NOT NULL DEFAULT 'PIX',
    amount_cents      INT  NOT NULL CHECK (amount_cents >= 0),
    status            TEXT NOT NULL,
    invoice_url       TEXT,
    pix_payload       TEXT,
    pix_qr_image      TEXT,
    pix_expires_at    TIMESTAMPTZ,
    due_date          DATE NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    refunded_at       TIMESTAMPTZ,
    refund_amount_cents INT,
    raw_payload       JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX payments_status_expires_at_idx
    ON public.payments (status, expires_at);

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON public.payments
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
