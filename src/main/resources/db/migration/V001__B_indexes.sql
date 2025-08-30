CREATE INDEX IF NOT EXISTS idx_event_id ON nostr_t_event(event_id);
CREATE INDEX IF NOT EXISTS idx_pubkey ON nostr_t_event(pubkey);
CREATE INDEX IF NOT EXISTS idx_kind ON nostr_t_event(kind);
CREATE INDEX IF NOT EXISTS idx_created_at ON nostr_t_event(created_at);
CREATE INDEX IF NOT EXISTS idx_tags ON nostr_t_event USING GIN(tags);

CREATE INDEX IF NOT EXISTS idx_kind_0 ON nostr_t_event(kind) WHERE kind = 0;
CREATE INDEX IF NOT EXISTS idx_kind_2 ON nostr_t_event(kind) WHERE kind = 1;
CREATE INDEX IF NOT EXISTS idx_kind_3 ON nostr_t_event(kind) WHERE kind = 2;
CREATE INDEX IF NOT EXISTS idx_kind_4 ON nostr_t_event(kind) WHERE kind = 4;
CREATE INDEX IF NOT EXISTS idx_kind_5 ON nostr_t_event(kind) WHERE kind = 5;
CREATE INDEX IF NOT EXISTS idx_kind_6 ON nostr_t_event(kind) WHERE kind = 6;
CREATE INDEX IF NOT EXISTS idx_kind_7 ON nostr_t_event(kind) WHERE kind = 7;

CREATE INDEX IF NOT EXISTS idx_kind_9735 ON nostr_t_event(kind) WHERE kind = 9735;
CREATE INDEX IF NOT EXISTS idx_kind_30023 ON nostr_t_event(kind) WHERE kind = 30023;
CREATE INDEX IF NOT EXISTS idx_kind_10002 ON nostr_t_event(kind) WHERE kind = 10002;
