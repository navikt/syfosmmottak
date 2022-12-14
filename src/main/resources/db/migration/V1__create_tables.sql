create table duplikatsjekk
(
    sha256_health_information       VARCHAR PRIMARY KEY,
    mottak_id          VARCHAR(63) NOT NULL,
    msg_id             VARCHAR(63) NOT NULL,
    mottatt_date TIMESTAMP NOT NULL
);