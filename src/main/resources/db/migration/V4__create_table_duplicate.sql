create table duplicate
(
    id VARCHAR NOT NULL PRIMARY KEY,
    mottak_id VARCHAR NOT NULL,
    msg_id VARCHAR NOT NULL,
    duplicate_mottak_id VARCHAR NOT NULL,
    duplicate_msg_id VARCHAR NOT NULL,
    mottatt_date TIMESTAMP NOT NULL
);