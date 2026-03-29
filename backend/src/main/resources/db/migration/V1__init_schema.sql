CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS short_links (
    id BIGSERIAL PRIMARY KEY,
    original_url VARCHAR(2048) NOT NULL,
    short_code VARCHAR(32) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_clicks BIGINT NOT NULL DEFAULT 0,
    owner_key VARCHAR(64),
    owner_user_id BIGINT,
    anonymous_expires_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    token VARCHAR(500) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS link_click_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    short_link_id BIGINT NOT NULL,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    source VARCHAR(64) NOT NULL,
    country_code VARCHAR(8),
    referrer VARCHAR(255),
    visitor_key VARCHAR(128),
    CONSTRAINT uk_link_click_events_event_id UNIQUE (event_id),
    CONSTRAINT fk_link_click_events_short_link
        FOREIGN KEY (short_link_id) REFERENCES short_links (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_short_links_short_code ON short_links (short_code);
CREATE INDEX IF NOT EXISTS idx_short_links_owner_user_id ON short_links (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_short_links_owner_key ON short_links (owner_key);
CREATE INDEX IF NOT EXISTS idx_short_links_anonymous_expires_at ON short_links (anonymous_expires_at);
CREATE INDEX IF NOT EXISTS idx_link_click_events_short_link_id ON link_click_events (short_link_id);
CREATE INDEX IF NOT EXISTS idx_link_click_events_clicked_at ON link_click_events (clicked_at);
CREATE INDEX IF NOT EXISTS idx_link_click_events_source ON link_click_events (source);