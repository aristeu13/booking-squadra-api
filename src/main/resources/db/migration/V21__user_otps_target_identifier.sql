-- OTP-proven identifier add/change flow.
--
-- For the existing login/email-verification/delete-account purposes, the OTP is
-- bound to the user record itself — the code alone proves continued possession.
-- For the new phone-change / email-change purposes, the OTP must additionally
-- prove possession of the *target* identifier (i.e. the new phone or new email
-- the user is trying to claim). We carry that target on the OTP row so the
-- confirm step can re-read it without trusting client-supplied input.

ALTER TABLE public.user_otps
    ADD COLUMN target_identifier TEXT;

-- Replace the inline (auto-named) purpose CHECK to add the two new purposes.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
      FROM pg_constraint
     WHERE conrelid = 'public.user_otps'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) ILIKE '%purpose%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.user_otps DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE public.user_otps
    ADD CONSTRAINT user_otps_purpose_check
    CHECK (purpose IN ('login', 'email_verification', 'delete_account', 'phone_change', 'email_change'));

-- Enforce that the new purposes always carry a target_identifier and that the
-- legacy purposes never do — avoids accidentally repurposing a login OTP for an
-- identifier change.
ALTER TABLE public.user_otps
    ADD CONSTRAINT user_otps_target_identifier_check
    CHECK (
        (purpose IN ('phone_change', 'email_change') AND target_identifier IS NOT NULL)
        OR (purpose NOT IN ('phone_change', 'email_change') AND target_identifier IS NULL)
    );

-- Relax the users "at least one identifier" CHECK to allow soft-deleted tombstones.
-- After a merge, the secondary user gets status='deleted' and both identifiers
-- nulled so the unique-index slots are freed for the primary to claim. The row
-- itself is kept (soft-delete) for FK integrity with venue_owners / refresh
-- tokens / etc.
ALTER TABLE public.users
    DROP CONSTRAINT users_email_or_phone_check;

ALTER TABLE public.users
    ADD CONSTRAINT users_email_or_phone_check
    CHECK (status = 'deleted' OR email IS NOT NULL OR phone_e164 IS NOT NULL);
