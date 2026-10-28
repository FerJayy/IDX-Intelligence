-- Accounts: email + password (BCrypt), login sessions, password-reset links and each user's watchlist.
-- Session and reset tokens are stored only as SHA-256 hashes, so a database leak can't be used to log in.
CREATE TABLE app_user (
    id             BIGSERIAL     PRIMARY KEY,
    email          VARCHAR(254)  NOT NULL,          -- stored lower-case
    password_hash  VARCHAR(100)  NOT NULL,          -- BCrypt
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ,
    CONSTRAINT app_user_email UNIQUE (email)
);

CREATE TABLE user_session (
    token_hash    CHAR(64)     PRIMARY KEY,          -- SHA-256 hex of the cookie value
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX user_session_user ON user_session (user_id);

CREATE TABLE password_reset (
    token_hash    CHAR(64)     PRIMARY KEY,          -- SHA-256 hex of the emailed token
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    used_at       TIMESTAMPTZ                        -- NULL = still usable (if not expired)
);
CREATE INDEX password_reset_user ON password_reset (user_id);

CREATE TABLE user_watchlist (
    user_id   BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    ticker    VARCHAR(10)  NOT NULL REFERENCES stock (ticker),
    added_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, ticker)
);
