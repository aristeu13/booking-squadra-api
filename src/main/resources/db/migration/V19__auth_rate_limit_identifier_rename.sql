-- Rename auth_rate_limit_events.email to identifier. The column now stores either
-- an email or an E.164 phone number depending on which identifier the OTP flow
-- targets. Postgres updates the column reference inside indexes automatically on
-- RENAME COLUMN; we additionally rename the two indexes whose names include
-- 'email' so the schema stays self-explanatory.
--
-- Deploy ordering note: this migration ships in the same release as the Java
-- change that drops `@Column(name = "email")` from AuthRateLimitEvent.identifier.
-- During a rolling deploy, old pods (which still map the field to column "email")
-- will fail rate-limit writes after the column is renamed. Either drain old pods
-- before Flyway runs, or accept brief 5xx on the OTP endpoints during cutover.

ALTER TABLE public.auth_rate_limit_events
    RENAME COLUMN email TO identifier;

ALTER INDEX public.auth_rate_limit_events_action_email_created_idx
    RENAME TO auth_rate_limit_events_action_identifier_created_idx;

ALTER INDEX public.auth_rate_limit_events_action_email_ip_created_idx
    RENAME TO auth_rate_limit_events_action_identifier_ip_created_idx;
