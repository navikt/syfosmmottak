ALTER TABLE duplicate
    ADD COLUMN duplicate_sykmelding_id VARCHAR NOT NULL,
    ADD COLUMN epj_system VARCHAR NOT NULL,
    ADD COLUMN epj_version VARCHAR NOT NULL;
