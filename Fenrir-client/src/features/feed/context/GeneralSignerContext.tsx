import { createContext, useCallback, useContext, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { BrowserSigner, Keys, NostrSigner } from '@rust-nostr/nostr-sdk'
import { ensureWasm } from '@/features/admin/nostr/wasm'

export interface GeneralIdentity {
  pubkeyHex: string
}

interface GeneralSignerContextValue {
  identity: GeneralIdentity | null
  loginWithNip07: () => Promise<void>
  loginWithNsec: (nsec: string) => Promise<void>
  logout: () => void
  getSigner: () => Promise<NostrSigner>
}

const GeneralSignerContext = createContext<GeneralSignerContextValue | undefined>(undefined)

/**
 * Lightweight sign-in for the public /feed page - deliberately separate from the Admin Console's
 * SignerContext/nsecVault: that PIN-encrypted vault is scoped to registered operators and must
 * never be reachable from an unauthenticated route. This holds a signer only in memory for the
 * tab's lifetime (NIP-07, or a pasted nsec used directly) - nothing is persisted, and nothing ever
 * touches the backend's admin auth API, since general visitors aren't operators.
 */
export function GeneralSignerProvider({ children }: { children: ReactNode }) {
  const [identity, setIdentity] = useState<GeneralIdentity | null>(null)
  const signerRef = useRef<NostrSigner | null>(null)

  const loginWithNip07 = useCallback(async () => {
    await ensureWasm()
    const signer = NostrSigner.nip07(new BrowserSigner())
    const pubkey = await signer.publicKey()
    signerRef.current = signer
    setIdentity({ pubkeyHex: pubkey.toHex() })
  }, [])

  const loginWithNsec = useCallback(async (nsec: string) => {
    await ensureWasm()
    const keys = Keys.parse(nsec.trim())
    const signer = NostrSigner.keys(keys)
    signerRef.current = signer
    setIdentity({ pubkeyHex: keys.publicKey.toHex() })
  }, [])

  const logout = useCallback(() => {
    signerRef.current = null
    setIdentity(null)
  }, [])

  const getSigner = useCallback(async (): Promise<NostrSigner> => {
    if (!signerRef.current) throw new Error('Not signed in')
    return signerRef.current
  }, [])

  return (
    <GeneralSignerContext.Provider value={{ identity, loginWithNip07, loginWithNsec, logout, getSigner }}>
      {children}
    </GeneralSignerContext.Provider>
  )
}

export function useGeneralSigner(): GeneralSignerContextValue {
  const ctx = useContext(GeneralSignerContext)
  if (!ctx) throw new Error('useGeneralSigner must be used within GeneralSignerProvider')
  return ctx
}
