CREATE INDEX idx_event_id ON event(event_id);
CREATE INDEX idx_pubkey ON event(pubkey);
CREATE INDEX idx_kind ON event(kind);
CREATE INDEX idx_created_at ON event(created_at);
CREATE INDEX idx_tags ON event USING GIN(tags);
CREATE INDEX idx_event_content_fts ON event USING gin (to_tsvector('simple', content));

CREATE INDEX idx_kind_0 ON event(kind) WHERE kind = 0;
CREATE INDEX idx_kind_1 ON event(kind) WHERE kind = 1;
CREATE INDEX idx_kind_2 ON event(kind) WHERE kind = 2;
CREATE INDEX idx_kind_4 ON event(kind) WHERE kind = 4;
CREATE INDEX idx_kind_5 ON event(kind) WHERE kind = 5;
CREATE INDEX idx_kind_6 ON event(kind) WHERE kind = 6;
CREATE INDEX idx_kind_7 ON event(kind) WHERE kind = 7;

CREATE INDEX idx_kind_30023 ON event(kind) WHERE kind = 30023;
CREATE INDEX idx_kind_9735 ON event(kind) WHERE kind = 9735;
CREATE INDEX idx_kind_10002 ON event(kind) WHERE kind = 10002;
