CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION public.f_unaccent(text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
STRICT
AS $func$
    SELECT public.unaccent('public.unaccent'::regdictionary, $1);
$func$;

DROP INDEX IF EXISTS public.idx_cities_unaccented_prefix;

CREATE INDEX idx_cities_unaccented_prefix
    ON public.cities (LOWER(public.f_unaccent(name)) text_pattern_ops);
