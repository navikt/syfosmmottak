create table duplicatecheck
(
    sykmelding_id VARCHAR NOT NULL PRIMARY KEY,
    sha256_health_information       VARCHAR NOT NULL,
    mottak_id          VARCHAR NOT NULL,
    msg_id             VARCHAR NOT NULL,
    mottatt_date TIMESTAMP NOT NULL,
    epj_system VARCHAR NOT NULL,
    epj_version VARCHAR NOT NULL,
    org_number VARCHAR NULL
);