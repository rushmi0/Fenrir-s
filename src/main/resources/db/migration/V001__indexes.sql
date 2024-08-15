CREATE INDEX idx_event_id ON event(event_id);
CREATE INDEX idx_pubkey ON event(pubkey);
CREATE INDEX idx_kind ON event(kind);
CREATE INDEX idx_created_at ON event(created_at);
CREATE INDEX idx_tags ON event USING GIN(tags);
CREATE INDEX idx_event_content_fts ON event USING gin (to_tsvector('simple', content));

CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 0;
CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 1;
CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 2;
CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 4;
CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 5;
CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 6;

CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 9735;
CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 10002;
