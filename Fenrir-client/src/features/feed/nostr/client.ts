import { Client, Duration, EventId, Filter, Kind, Nip19Event, Timestamp } from '@rust-nostr/nostr-sdk'
import type { Event as NostrEvent } from '@rust-nostr/nostr-sdk'
import { ensureWasm } from '@/features/admin/nostr/wasm'
import { currentRelayUrl } from '@/features/admin/nostr/signLogin'

const TEXT_KIND = 1
export const REPOST_KIND = 6
const FETCH_TIMEOUT_SECS = 8

let clientPromise: Promise<Client> | null = null

/**
 * Vite's dev server only proxies `/inter/**` to the local relay (see vite.config.ts) - it doesn't
 * (and can't, without breaking its own HMR/asset serving on `/`) proxy the relay's websocket
 * endpoint, which lives at `/`. So in dev, talk to the backend directly on its own port instead of
 * `currentRelayUrl()`'s same-origin URL; in production the client and relay share an origin.
 */
function feedRelayUrl(): string {
  return import.meta.env.DEV ? 'ws://localhost:6724/' : currentRelayUrl()
}

/** Lazily connects a single, page-lifetime `Client` to this relay's own websocket endpoint -
 * reused across fetches (and publishes - see nostr/publish.ts) instead of reconnecting every time
 * the Feed page is visited. */
export function getClient(): Promise<Client> {
  if (!clientPromise) {
    clientPromise = (async () => {
      await ensureWasm()
      const client = new Client()
      await client.addRelay(feedRelayUrl())
      await client.connect()
      return client
    })()
  }
  return clientPromise
}

export interface FetchNotesResult {
  events: NostrEvent[]
  /** False once a fetch comes back short of `limit` raw events - the relay has nothing further
   * back in time to page through. */
  hasMore: boolean
}

/** A repost (NIP-18, kind 6) is always its own feed item; a kind-1 note is a root only when it
 * carries no `e` tag - any `e` tag makes it a reply (NIP-10), threaded under its parent instead. */
function isRootNote(event: NostrEvent): boolean {
  if (event.kind.asU16() === REPOST_KIND) return true
  return event.tags.filter('e').length === 0
}

/** Safety cap on how many relay round-trips one `fetchRootNotes` call will make while trying to
 * gather `limit` roots - bounds worst-case latency on a relay with far more replies than roots. */
const MAX_FETCH_ROUNDS = 6

/**
 * Root-level kind-1 notes only, newest first - a note with any `e` tag is a reply (NIP-10) and
 * surfaces exclusively within its parent's thread (see fetchReplies), never as its own feed item.
 *
 * A single raw fetch shares its relay-side `limit` between roots and replies, so filtering after
 * one fetch can come back sparse or empty even though the relay has plenty of root notes further
 * back - this walks `until` backward across additional rounds until `limit` roots are collected,
 * the relay runs out of history, or MAX_FETCH_ROUNDS is hit.
 *
 * Pass `until` (unix seconds) to page further back than a previous call's oldest result.
 */
export async function fetchRootNotes(limit: number, until?: number): Promise<FetchNotesResult> {
  const client = await getClient()
  const collected: NostrEvent[] = []
  let cursor = until
  let hasMore = true

  for (let round = 0; round < MAX_FETCH_ROUNDS && collected.length < limit; round++) {
    let filter = new Filter().kinds([new Kind(TEXT_KIND), new Kind(REPOST_KIND)]).limit(limit)
    if (cursor !== undefined) filter = filter.until(Timestamp.fromSecs(cursor))

    const events = await client.fetchEvents(filter, Duration.fromSecs(FETCH_TIMEOUT_SECS))
    const batch = events.toVec()

    if (batch.length === 0) {
      hasMore = false
      break
    }

    collected.push(...batch.filter(isRootNote))

    if (batch.length < limit) {
      hasMore = false
      break
    }

    // Move strictly backward so the next round can't re-fetch this same batch forever. `-1`
    // means a note sharing the exact same second as the oldest one in this batch could be missed
    // - a rare tie, and a safer trade than a fetch loop that never terminates.
    cursor = batch.reduce((min, e) => Math.min(min, e.createdAt.asSecs()), Infinity) - 1
  }

  const sorted = collected.sort((a, b) => b.createdAt.asSecs() - a.createdAt.asSecs())
  // A round's raw batch can contain more roots than `limit` once filtered, so `collected` can
  // overshoot - cap what's returned so the feed actually loads in FEED_LIMIT-sized pages instead
  // of dumping everything gathered so far in one call. Anything trimmed off just gets re-fetched
  // by the next loadMore() (using the last shown post's timestamp as `until`), so nothing's lost.
  const roots = sorted.slice(0, limit)
  return { events: roots, hasMore: hasMore || sorted.length > limit }
}

/** Every kind-1 note that references `rootId` in an `e` tag (NIP-10 replies), unordered. */
export async function fetchReplies(rootId: string, limit: number): Promise<NostrEvent[]> {
  const client = await getClient()
  const filter = new Filter().kinds([new Kind(TEXT_KIND)]).event(EventId.parse(rootId)).limit(limit)
  const events = await client.fetchEvents(filter, Duration.fromSecs(FETCH_TIMEOUT_SECS))
  return events.toVec()
}

/** Resolves an event id in any form NIP-19/21 allows - hex, `note1…`/`nevent1…` bech32, or a
 * `nostr:` uri of either - so repost/quote embeds can take whatever shape shows up in a `e`/`q`
 * tag or inline mention. Cached (including misses) since the same quoted note commonly appears in
 * several feed cards at once. */
const eventCache = new Map<string, NostrEvent | null>()
const eventInFlight = new Map<string, Promise<NostrEvent | null>>()

function resolveEventId(idOrUri: string): EventId | null {
  try {
    return EventId.parse(idOrUri)
  } catch {
    try {
      return Nip19Event.fromNostrUri(idOrUri).eventId()
    } catch {
      try {
        return Nip19Event.fromBech32(idOrUri).eventId()
      } catch {
        return null
      }
    }
  }
}

export function fetchEventById(idOrUri: string): Promise<NostrEvent | null> {
  if (eventCache.has(idOrUri)) return Promise.resolve(eventCache.get(idOrUri) ?? null)

  const existing = eventInFlight.get(idOrUri)
  if (existing) return existing

  const promise = (async () => {
    const id = resolveEventId(idOrUri)
    if (!id) return null
    const client = await getClient()
    const filter = new Filter().ids([id]).limit(1)
    const events = await client.fetchEvents(filter, Duration.fromSecs(FETCH_TIMEOUT_SECS))
    return events.toVec()[0] ?? null
  })()
    .then((event) => {
      eventCache.set(idOrUri, event)
      return event
    })
    .catch(() => {
      eventCache.set(idOrUri, null)
      return null
    })
    .finally(() => {
      eventInFlight.delete(idOrUri)
    })

  eventInFlight.set(idOrUri, promise)
  return promise
}
