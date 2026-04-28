ALTER TABLE public.venues
    ADD COLUMN city_id INT REFERENCES public.cities(id) ON DELETE SET NULL;
