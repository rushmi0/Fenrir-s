import { EventBuilder, Tag } from '@rust-nostr/nostr-sdk'
import type { Event as NostrEvent, NostrSigner } from '@rust-nostr/nostr-sdk'
import { getClient } from './client'

async function broadcast(builder: EventBuilder, signer: NostrSigner): Promise<NostrEvent> {
  const client = await getClient()
  const event = await builder.sign(signer)
  const output = await client.sendEvent(event)
  if (output.success.length === 0) {
    throw new Error(output.failed[0]?.error ?? 'the relay rejected this event')
  }
  return event
}

/** NIP-25 reaction ("+" = like). */
export function publishLike(signer: NostrSigner, target: NostrEvent): Promise<NostrEvent> {
  return broadcast(EventBuilder.reaction(target, '+'), signer)
}

/** NIP-18 repost ("re-note"). */
export function publishRepost(signer: NostrSigner, target: NostrEvent): Promise<NostrEvent> {
  return broadcast(EventBuilder.repost(target, null), signer)
}

/** NIP-10 reply. `root` is the top-level post the whole thread hangs off of; pass the same event
 * as `replyTo` when replying directly to the root itself.
 *
 * `EventBuilder.textNoteReply`'s wasm binding null-pointer-crashes when `replyTo` and `root`
 * resolve to the same event (confirmed against this relay - even a distinct clone of the same
 * event crashes it, so it's not an object-identity check on the Rust side). Passing `root: null`
 * avoids the crash, but the SDK then marks the lone `e` tag "reply" instead of "root" and never
 * emits a root tag at all - clients that key off the "root" marker (rather than falling back like
 * this app's own thread builder does) don't recognize the event as a reply, so it renders as a
 * top-level note in their main feed instead of nesting under the original. So replying directly
 * to the root builds the NIP-10 tags by hand instead of going through `textNoteReply`. */
export function publishReply(
  signer: NostrSigner,
  replyTo: NostrEvent,
  root: NostrEvent,
  content: string,
): Promise<NostrEvent> {
  if (replyTo === root) {
    const builder = EventBuilder.textNote(content).tags([
      Tag.parse(['e', root.id.toHex(), '', 'root']),
      Tag.parse(['p', root.author.toHex()]),
    ])
    return broadcast(builder, signer)
  }
  return broadcast(EventBuilder.textNoteReply(content, replyTo, root), signer)
}
