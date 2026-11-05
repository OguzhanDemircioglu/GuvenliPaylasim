-- Paylaşılan bir veritabanında başka bir uygulamaya ait, aynı adlı tablolar.
CREATE TABLE public.categories (id integer PRIMARY KEY, legacy_name text);
INSERT INTO public.categories VALUES (1, 'other app row');
CREATE TABLE public.users (id integer PRIMARY KEY, login text);
