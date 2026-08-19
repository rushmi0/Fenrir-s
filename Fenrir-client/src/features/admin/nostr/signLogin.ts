import { BrowserSigner, EventBuilder, Keys, NostrSigner } from '@rust-nostr/nostr-sdk'
import { ensureWasm } from './wasm'

/**
 * The relay's own URL as the client sees it, embedded in the login event's `relay` tag (same
 * shape the WebSocket NIP-42 AUTH event uses - see VerifyAuth.relayUrlMatches on the backend,
 * which only compares hostnames and skips the check entirely when RELAY_URL isn't configured).
 */
export function currentRelayUrl(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${scheme}//${window.location.host}/`
}

export function isNip07Available(): boolean {
  return typeof window !== 'undefined' && 'nostr' in window
}

/** Parses a candidate `nsec` to confirm it's well-formed before it's carried through the PIN steps. */
export async function isValidNsec(nsec: string): Promise<boolean> {
  try {
    await ensureWasm()
    Keys.parse(nsec.trim())
    return true
  } catch {
    return false
  }
}

/** Derives the hex pubkey for an `nsec` - used to label the Unauthorized screen when login is rejected. */
export async function derivePubkeyFromNsec(nsec: string): Promise<string> {
  await ensureWasm()
  return Keys.parse(nsec.trim()).publicKey.toHex()
}

/**
 * Signs a kind-22242 login event (identical shape to relay NIP-42 AUTH) with the browser's NIP-07
 * extension. Returns the signed event as a JSON string, ready to POST as-is.
 */
export async function signLoginWithNip07(challenge: string, relayUrl: string): Promise<string> {
  if (!isNip07Available()) {
    throw new Error('No NIP-07 browser extension found')
  }
  await ensureWasm()
  const signer = NostrSigner.nip07(new BrowserSigner())
  const event = await EventBuilder.auth(challenge, relayUrl).sign(signer)
  return event.asJson()
}

/**
 * Signs the same login event with an nsec entered by the user. Signing happens entirely in the
 * browser via the wasm SDK - the nsec is never sent to the server and isn't retained by this
 * function beyond the synchronous call.
 */
export async function signLoginWithNsec(nsec: string, challenge: string, relayUrl: string): Promise<string> {
  await ensureWasm()
  const keys = Keys.parse(nsec.trim())
  const signer = NostrSigner.keys(keys)
  const event = await EventBuilder.auth(challenge, relayUrl).sign(signer)
  return event.asJson()
}
