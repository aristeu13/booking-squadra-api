-- Phone-OTP login support.
--
-- Goal: allow signing in by phone number (E.164), alongside the existing email
-- and Google flows. Phone becomes a first-class identifier; either email or
-- phone_e164 must be present on every user.
--
-- This migration is intentionally idempotent-at-row-level and set-based. It
-- does NOT drop public.users.phone yet — that column is still read/written by
-- the Java side and will be removed in a follow-up migration (V19) after the
-- application has been updated.

-- ---------------------------------------------------------------------------
-- 1. Safety: refuse to proceed if case-folding emails would create duplicates.
--    The original UNIQUE on email was case-sensitive, so 'Foo@bar.com' and
--    'foo@bar.com' can both exist today. The new partial unique on LOWER(email)
--    would conflate them. If any such pair exists, fail loudly instead of
--    silently dropping data.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    duplicate_count integer;
BEGIN
    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT LOWER(email)
          FROM public.users
         WHERE email IS NOT NULL
         GROUP BY LOWER(email)
        HAVING COUNT(*) > 1
    ) d;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION
            'V18 aborted: % case-insensitive duplicate email(s) exist in public.users. '
            'Resolve manually (merge or rename) before re-running.', duplicate_count;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Drop the legacy case-sensitive UNIQUE on email and the NOT NULL.
--    The UNIQUE was inline in V1, so the constraint name is auto-generated
--    (users_email_key by convention). Look it up rather than hardcoding.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    cname text;
BEGIN
    SELECT con.conname INTO cname
      FROM pg_constraint con
      JOIN pg_class    c   ON c.oid = con.conrelid
      JOIN pg_attribute a  ON a.attrelid = con.conrelid AND a.attnum = ANY (con.conkey)
     WHERE c.relname = 'users'
       AND c.relnamespace = 'public'::regnamespace
       AND con.contype = 'u'
       AND a.attname = 'email'
       AND array_length(con.conkey, 1) = 1
     LIMIT 1;

    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.users DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE public.users
    ALTER COLUMN email DROP NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. Case-fold existing emails so the new LOWER(email) index sees consistent
--    data. Skip rows already lowercase to avoid touching updated_at needlessly.
-- ---------------------------------------------------------------------------
UPDATE public.users
   SET email = LOWER(email)
 WHERE email IS NOT NULL
   AND email <> LOWER(email);

-- ---------------------------------------------------------------------------
-- 4. Add phone_e164 and the "at least one identifier" CHECK. All existing rows
--    have email IS NOT NULL, so the CHECK validates instantly.
-- ---------------------------------------------------------------------------
ALTER TABLE public.users
    ADD COLUMN phone_e164 TEXT;

ALTER TABLE public.users
    ADD CONSTRAINT users_email_or_phone_check
        CHECK (email IS NOT NULL OR phone_e164 IS NOT NULL);

-- ---------------------------------------------------------------------------
-- 5. Indexes. Added before backfill so any dedup bug fails fast instead of
--    persisting duplicate rows.
--
--    Note for ops: these are non-CONCURRENT and take a brief ShareLock on
--    public.users. Acceptable at current scale. If the table grows large,
--    convert to CREATE UNIQUE INDEX CONCURRENTLY in its own non-transactional
--    migration.
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX users_email_lower_uidx
    ON public.users (LOWER(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX users_phone_e164_uidx
    ON public.users (phone_e164)
    WHERE phone_e164 IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. Backfill phone_e164 from the free-form users.phone column. The rules
--    below mirror com.bookingsquadra.util.BrazilPhoneNormalizer exactly:
--      * strip non-digits
--      * if 12 or 13 digits and starts with '55', strip the country code
--      * accept 10-digit landline or 11-digit mobile (mobile = digit[2] is '9')
--      * otherwise leave NULL (unparseable)
--
--    When two users normalize to the same number, keep the row with the
--    smallest id and leave the rest NULL — deterministic, and we'll surface
--    the dup separately once the app reports a phone-already-in-use on login.
-- ---------------------------------------------------------------------------
WITH digits_only AS (
    SELECT id,
           regexp_replace(COALESCE(phone, ''), '\D', '', 'g') AS d0
      FROM public.users
     WHERE phone IS NOT NULL AND phone <> ''
),
stripped AS (
    SELECT id,
           CASE
               WHEN length(d0) IN (12, 13) AND left(d0, 2) = '55'
                   THEN substring(d0 FROM 3)
               ELSE d0
           END AS d
      FROM digits_only
),
candidate AS (
    SELECT id,
           CASE
               WHEN length(d) = 10
                   THEN '+55' || d
               WHEN length(d) = 11 AND substring(d FROM 3 FOR 1) = '9'
                   THEN '+55' || d
               ELSE NULL
           END AS phone_e164
      FROM stripped
),
ranked AS (
    SELECT id,
           phone_e164,
           ROW_NUMBER() OVER (PARTITION BY phone_e164 ORDER BY id) AS rn
      FROM candidate
     WHERE phone_e164 IS NOT NULL
)
UPDATE public.users u
   SET phone_e164 = r.phone_e164
  FROM ranked r
 WHERE u.id = r.id
   AND r.rn = 1;

-- ---------------------------------------------------------------------------
-- 7. Backfill ghost users from manual bookings.
--
--    Per V16, manual bookings carry a normalized E.164 customer_phone and a
--    customer_name (both NOT NULL via bookings_manual_snapshot_check). Some of
--    those phones may already match a real user after step 6 — skip those.
--
--    DISTINCT ON keeps one row per customer_phone. ORDER BY ..., created_at
--    picks the *oldest* manual booking's name for the ghost — deterministic
--    and gives the customer's earliest known name.
-- ---------------------------------------------------------------------------
INSERT INTO public.users (name, phone_e164, has_used_google_auth, role, status)
SELECT DISTINCT ON (b.customer_phone)
       b.customer_name,
       b.customer_phone,
       FALSE,
       'user',
       'active'
  FROM public.bookings b
 WHERE b.booking_type = 'manual'
   AND b.user_id IS NULL
   AND b.customer_phone IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
         FROM public.users u
        WHERE u.phone_e164 = b.customer_phone
   )
 ORDER BY b.customer_phone, b.created_at;

-- ---------------------------------------------------------------------------
-- 8. Stitch existing manual bookings to the (now existing) phone-matched user.
--    Covers both rows backfilled in step 7 and rows that already matched a
--    real user via phone_e164 from step 6.
-- ---------------------------------------------------------------------------
UPDATE public.bookings b
   SET user_id = u.id
  FROM public.users u
 WHERE b.booking_type = 'manual'
   AND b.user_id IS NULL
   AND b.customer_phone IS NOT NULL
   AND u.phone_e164 = b.customer_phone;
