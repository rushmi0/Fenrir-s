CREATE INDEX idx_event_id ON event(event_id);
CREATE INDEX idx_pubkey ON event(pubkey);
CREATE INDEX idx_kind ON event(kind);
CREATE INDEX idx_created_at ON event(created_at);
CREATE INDEX idx_tags ON event USING GIN(tags);
CREATE INDEX idx_event_content_fts ON event USING gin (to_tsvector('simple', content));
