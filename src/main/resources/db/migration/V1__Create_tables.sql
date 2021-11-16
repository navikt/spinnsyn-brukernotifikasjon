CREATE TABLE brukernotifikasjon
(
    id VARCHAR(36) PRIMARY KEY,
    fnr VARCHAR(11) NOT NULL,
    oppgave_sendt TIMESTAMP WITH TIME ZONE NULL,
    done_sendt TIMESTAMP WITH TIME ZONE NULL
);

