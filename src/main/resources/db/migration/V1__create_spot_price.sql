CREATE TABLE spot_price (
    id        BIGSERIAL    PRIMARY KEY,
    timestamp TIMESTAMPTZ  NOT NULL UNIQUE,
    price_eur NUMERIC(18, 6) NOT NULL,
    price_czk NUMERIC(18, 6) NOT NULL
);

CREATE INDEX idx_spot_price_timestamp ON spot_price (timestamp);
