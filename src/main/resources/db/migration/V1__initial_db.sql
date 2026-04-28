CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE public.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    phone TEXT,
    has_used_google_auth BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.user_otps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    
    otp_code VARCHAR(64) NOT NULL, 
    
    purpose TEXT NOT NULL DEFAULT 'login' 
        CHECK (purpose IN ('login', 'email_verification', 'delete_account')),
    
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ, 
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX user_otps_user_id_idx ON public.user_otps (user_id);
CREATE INDEX user_otps_lookup_idx ON public.user_otps (user_id, purpose, otp_code)
    WHERE used_at IS NULL;
CREATE INDEX user_otps_expires_at_idx ON public.user_otps (expires_at);

CREATE TABLE public.venues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT UNIQUE NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    image_url TEXT,
    address TEXT NOT NULL,
    city TEXT NOT NULL,
    state_code CHAR(2) NOT NULL,
    latitude FLOAT8 NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude FLOAT8 NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    sports TEXT[] NOT NULL DEFAULT '{}',
    amenities JSONB NOT NULL DEFAULT '{}',
    price_cents INT NOT NULL DEFAULT 0 CHECK (price_cents >= 0),
    slot_duration_minutes SMALLINT NOT NULL DEFAULT 60 CHECK (slot_duration_minutes > 0),
    -- offer_min_slots SMALLINT,
    -- offer_price_cents INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX venues_location_idx ON public.venues
    USING GIST (ll_to_earth(latitude, longitude));
CREATE INDEX venues_active_idx ON public.venues (active) WHERE active;
-- CREATE INDEX venues_sports_idx ON public.venues USING GIN (sports);
-- CREATE INDEX venues_amenities_idx ON public.venues USING GIN (amenities);


CREATE TABLE public.courts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id UUID NOT NULL REFERENCES public.venues(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    surface_type TEXT NOT NULL 
        CHECK (surface_type IN ('sand','synthetic','hard','clay','padel','wood','grass')),
    is_indoor BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order SMALLINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX courts_venue_idx ON public.courts (venue_id);

CREATE TABLE public.operating_hours ( -- keep that table that way
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id UUID NOT NULL REFERENCES public.venues(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (venue_id, day_of_week) 
);

CREATE TABLE public.bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_type TEXT NOT NULL DEFAULT 'reservation' 
        CHECK (booking_type IN ('reservation','block')),
    user_id UUID REFERENCES public.users(id) ON DELETE SET NULL,
    court_id UUID NOT NULL REFERENCES public.courts(id) ON DELETE RESTRICT,
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' 
        CHECK (status IN ('pending','confirmed','completed','cancelled')),
    payment_method TEXT CHECK (payment_method IN ('pix','local')),
    amount_cents INT NOT NULL CHECK (amount_cents >= 0),
    transaction_id TEXT,
    note TEXT,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (start_time < end_time),
    CONSTRAINT bookings_no_overlap EXCLUDE USING GIST (
        court_id WITH =,
        booking_date WITH =,
        tsrange(
            (booking_date + start_time)::timestamp,
            (booking_date + end_time)::timestamp
        ) WITH &&
    ) WHERE (status IN ('pending','confirmed'))
);

CREATE INDEX bookings_user_status_idx ON public.bookings (user_id, status);
CREATE INDEX bookings_court_date_idx ON public.bookings (court_id, booking_date);
CREATE UNIQUE INDEX bookings_transaction_id_uidx ON public.bookings (transaction_id) WHERE transaction_id IS NOT NULL;


CREATE TABLE public.recurring_time_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id UUID NOT NULL REFERENCES public.venues(id) ON DELETE CASCADE,
    -- court_id NULL means the block applies to every court in the venue
    court_id UUID REFERENCES public.courts(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6), -- 0 = Sunday
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (start_time < end_time)
);

CREATE INDEX recurring_time_blocks_venue_day_idx ON public.recurring_time_blocks (venue_id, day_of_week, active);
CREATE INDEX recurring_time_blocks_court_day_idx ON public.recurring_time_blocks (court_id, day_of_week, active);

CREATE TABLE public.recurring_time_block_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recurring_block_id UUID NOT NULL REFERENCES public.recurring_time_blocks(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('release', 'block')),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (recurring_block_id, exception_date)
);

CREATE INDEX recurring_time_block_exceptions_date_idx ON public.recurring_time_block_exceptions (exception_date);

CREATE TABLE public.cancel_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id UUID UNIQUE NOT NULL REFERENCES public.venues(id) ON DELETE CASCADE,
    pix_full_refund_hours SMALLINT NOT NULL DEFAULT 48,
    pix_partial_refund_hours SMALLINT NOT NULL DEFAULT 24,
    pix_partial_refund_percent SMALLINT NOT NULL DEFAULT 70
        CHECK (pix_partial_refund_percent BETWEEN 0 AND 100),
    local_cancel_hours SMALLINT NOT NULL DEFAULT 12,
    no_show_pix_threshold SMALLINT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.cities (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    state_code CHAR(2) NOT NULL,
    latitude FLOAT8 NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude FLOAT8 NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    is_capital BOOLEAN NOT NULL,
    siafi_id VARCHAR(4) NOT NULL UNIQUE,
    ddd INT NOT NULL,
    timezone VARCHAR(32) NOT NULL
);

CREATE INDEX cities_name_idx ON public.cities (name);
CREATE INDEX cities_state_name_idx ON public.cities (state_code, name);
CREATE INDEX cities_location_idx ON public.cities
    USING GIST (ll_to_earth(latitude, longitude));

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_venues_updated_at BEFORE UPDATE ON public.venues
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_courts_updated_at BEFORE UPDATE ON public.courts
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_operating_hours_updated_at BEFORE UPDATE ON public.operating_hours
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_bookings_updated_at BEFORE UPDATE ON public.bookings
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_recurring_time_blocks_updated_at BEFORE UPDATE ON public.recurring_time_blocks
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_recurring_time_block_exceptions_updated_at BEFORE UPDATE ON public.recurring_time_block_exceptions
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_cancel_policies_updated_at BEFORE UPDATE ON public.cancel_policies
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
