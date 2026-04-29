CREATE TABLE public.auth_rate_limit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action TEXT NOT NULL CHECK (action IN ('otp_request', 'otp_verify_failed')),
    email TEXT,
    ip_address INET NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX auth_rate_limit_events_action_email_created_idx
    ON public.auth_rate_limit_events (action, email, created_at);

CREATE INDEX auth_rate_limit_events_action_ip_created_idx
    ON public.auth_rate_limit_events (action, ip_address, created_at);

CREATE INDEX auth_rate_limit_events_action_email_ip_created_idx
    ON public.auth_rate_limit_events (action, email, ip_address, created_at);

CREATE INDEX auth_rate_limit_events_created_at_idx
    ON public.auth_rate_limit_events (created_at);
