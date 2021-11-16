ALTER TABLE brukernotifikasjon DROP COLUMN oppgave_sendt;

ALTER TABLE brukernotifikasjon ADD COLUMN oppgave_sendt TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE brukernotifikasjon ADD COLUMN oppdatert TIMESTAMP WITH TIME ZONE NOT NULL;
ALTER TABLE brukernotifikasjon ADD COLUMN planlagt_sendes TIMESTAMP WITH TIME ZONE NOT NULL;

