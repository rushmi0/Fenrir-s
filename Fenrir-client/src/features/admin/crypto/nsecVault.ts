import { hash as sha256 } from '@stablelib/sha256'
import { ChaCha20Poly1305 } from '@stablelib/chacha20poly1305'
import { encode as b64encode, decode as b64decode } from '@stablelib/base64'

const ENCRYPTED_NSEC_KEY = 'fenrir.admin.encryptedNsec'
const ENCRYPTION_METADATA_KEY = 'fenrir.admin.encryptionMetadata'

const NONCE_LENGTH = 12

interface EncryptionMetadata {
  version: 2
  kdf: 'SHA-256'
  cipher: 'ChaCha20-Poly1305'
  nonceB64: string
}

/** PIN -> 32-byte key via a single unsalted SHA-256 pass (no PBKDF2 work factor - see the summary caveat). */
function deriveKey(pin: string): Uint8Array {
  return sha256(new TextEncoder().encode(pin))
}

/**
 * Whether this browser already holds a PIN-encrypted `nsec` (see AuthFlow's "Case A" vs "Case B" -
 * this is the sole signal used to decide between the PIN-login and nsec-registration flows).
 */
export function hasStoredNsec(): boolean {
  return localStorage.getItem(ENCRYPTED_NSEC_KEY) !== null && localStorage.getItem(ENCRYPTION_METADATA_KEY) !== null
}

/**
 * Encrypts `nsec` with ChaCha20-Poly1305 under a SHA-256(pin) key and a random nonce, then stores
 * the sealed (ciphertext + auth tag) and nonce as base64 - the PIN itself and the raw `nsec` never
 * touch storage. Pure-JS (@stablelib) throughout, so this works the same regardless of secure-context
 * status (unlike the WebCrypto SubtleCrypto API, `@stablelib` doesn't require HTTPS/localhost).
 */
export async function encryptAndStoreNsec(nsec: string, pin: string): Promise<void> {
  const nonce = crypto.getRandomValues(new Uint8Array(NONCE_LENGTH))
  const sealed = new ChaCha20Poly1305(deriveKey(pin)).seal(nonce, new TextEncoder().encode(nsec))

  const metadata: EncryptionMetadata = {
    version: 2,
    kdf: 'SHA-256',
    cipher: 'ChaCha20-Poly1305',
    nonceB64: b64encode(nonce),
  }

  localStorage.setItem(ENCRYPTED_NSEC_KEY, b64encode(sealed))
  localStorage.setItem(ENCRYPTION_METADATA_KEY, JSON.stringify(metadata))
}

/**
 * Decrypts the stored `nsec` with a candidate PIN. ChaCha20-Poly1305's auth tag makes a wrong PIN
 * fail decryption outright (`open` returns `null`), which is what turns into "Incorrect PIN" here.
 */
export async function decryptStoredNsec(pin: string): Promise<string> {
  const sealedB64 = localStorage.getItem(ENCRYPTED_NSEC_KEY)
  const metadataRaw = localStorage.getItem(ENCRYPTION_METADATA_KEY)
  if (!sealedB64 || !metadataRaw) {
    throw new Error('No saved credentials found in this browser')
  }

  const metadata = JSON.parse(metadataRaw) as EncryptionMetadata
  const nonce = b64decode(metadata.nonceB64)
  const plaintext = new ChaCha20Poly1305(deriveKey(pin)).open(nonce, b64decode(sealedB64))
  if (!plaintext) {
    throw new Error('Incorrect PIN')
  }
  return new TextDecoder().decode(plaintext)
}

/** Wipes the encrypted credential from this browser (e.g. the user chooses "use nsec instead"). */
export function clearStoredNsec(): void {
  localStorage.removeItem(ENCRYPTED_NSEC_KEY)
  localStorage.removeItem(ENCRYPTION_METADATA_KEY)
}
