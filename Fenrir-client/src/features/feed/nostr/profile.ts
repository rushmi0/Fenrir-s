import { Duration, Filter, Kind, PublicKey } from '@rust-nostr/nostr-sdk'
import { getClient } from './client'

export interface Profile {
  name?: string
  picture?: string
}

const cache = new Map<string, Profile | null>()
const inFlight = new Map<string, Promise<Profile | null>>()

/** Fetches (and caches, for this tab's lifetime) the `picture`/`name` fields off a pubkey's
 * kind-0 metadata event, for rendering a real avatar instead of the generated color swatch.
 * Misses cache as `null` too, same reasoning as getLinkPreview: an account with no metadata
 * shouldn't get re-queried every time its avatar scrolls back into view. */
export function getProfile(pubkeyHex: string): Promise<Profile | null> {
  if (cache.has(pubkeyHex)) return Promise.resolve(cache.get(pubkeyHex) ?? null)

  const existing = inFlight.get(pubkeyHex)
  if (existing) return existing

  const promise = (async () => {
    const client = await getClient()
    const filter = new Filter().kinds([new Kind(0)]).authors([PublicKey.parse(pubkeyHex)]).limit(1)
    const events = await client.fetchEvents(filter, Duration.fromSecs(5))
    const event = events.toVec()[0]
    if (!event) return null
    const parsed = JSON.parse(event.content) as { name?: unknown; picture?: unknown }
    const name = typeof parsed.name === 'string' ? parsed.name : undefined
    const picture = typeof parsed.picture === 'string' ? parsed.picture : undefined
    return { name, picture }
  })()
    .then((profile) => {
      cache.set(pubkeyHex, profile)
      return profile
    })
    .catch(() => {
      cache.set(pubkeyHex, null)
      return null
    })
    .finally(() => {
      inFlight.delete(pubkeyHex)
    })

  inFlight.set(pubkeyHex, promise)
  return promise
}
