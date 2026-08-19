import { loadWasmAsync } from '@rust-nostr/nostr-sdk'

let wasmReady: Promise<void> | null = null

/** Every `@rust-nostr/nostr-sdk` class needs its wasm module initialized first - shared across
 * every call site (login signing, feed fetching, ...) so it only loads once. */
export function ensureWasm(): Promise<void> {
  if (!wasmReady) wasmReady = loadWasmAsync()
  return wasmReady
}
