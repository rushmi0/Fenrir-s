CREATE TABLE event
(
    event_id   VARCHAR(64)  NOT NULL PRIMARY KEY,
    pubkey     VARCHAR(64)  NOT NULL,
    created_at INT          NOT NULL,
    kind       INT          NOT NULL,
    tags       jsonb        NOT NULL,
    content    TEXT         NOT NULL,
    sig        VARCHAR(128) NOT NULL
);
