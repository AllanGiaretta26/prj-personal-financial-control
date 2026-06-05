CREATE TABLE account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100)  NOT NULL,
    type            VARCHAR(20)   NOT NULL,
    initial_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE category (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20)  NOT NULL
);

CREATE TABLE transaction (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    description VARCHAR(255)  NOT NULL,
    amount      NUMERIC(15,2) NOT NULL,
    occurred_on DATE          NOT NULL,
    type        VARCHAR(20)   NOT NULL,
    account_id  UUID          NOT NULL REFERENCES account(id),
    category_id UUID          NOT NULL REFERENCES category(id),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE budget (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID          NOT NULL REFERENCES category(id),
    reference_month VARCHAR(7)    NOT NULL,
    limit_amount    NUMERIC(15,2) NOT NULL,
    UNIQUE (category_id, reference_month)
);
