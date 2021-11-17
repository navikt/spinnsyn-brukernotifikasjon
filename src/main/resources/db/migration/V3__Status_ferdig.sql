ALTER TABLE brukernotifikasjon
    ADD COLUMN ferdig boolean not null default false,
    ADD COLUMN mottatt TIMESTAMP WITH TIME ZONE NOT NULL;
