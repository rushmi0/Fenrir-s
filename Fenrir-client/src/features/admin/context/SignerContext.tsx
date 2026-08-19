import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { BrowserSigner, Keys, NostrSigner } from '@rust-nostr/nostr-sdk'
import { ensureWasm } from '@/features/admin/nostr/wasm'
import { isNip07Available } from '@/features/admin/nostr/signLogin'
import { hasStoredNsec } from '@/features/admin/crypto/nsecVault'
import { useAdminSession } from './AdminSessionContext'
import PinUnlockModal from '@/features/admin/components/PinUnlockModal'

interface SignerContextValue {
  /** Resolves a signer for actions taken after login (reactions, reposts, replies) - prompts for
   * the device PIN the first time in this tab if the session isn't a NIP-07 extension. */
  getSigner: () => Promise<NostrSigner>
}

const SignerContext = createContext<SignerContextValue | undefined>(undefined)

interface PendingNsecRequest {
  resolve: (nsec: string) => void
  reject: (err: Error) => void
}

/**
 * Resolves and caches (in-memory only, for this tab's lifetime) a `NostrSigner` for actions taken
 * after login. NIP-07 sessions never prompt again. PIN/nsec sessions unlock once per tab via a PIN
 * prompt - the decrypted key is held only in this ref, never written to storage, and is dropped the
 * moment the admin session itself clears (logout / expiry - see AdminSessionContext).
 */
export function SignerProvider({ children }: { children: ReactNode }) {
  const { session } = useAdminSession()
  const signerRef = useRef<NostrSigner | null>(null)
  const [pending, setPending] = useState<PendingNsecRequest | null>(null)

  useEffect(() => {
    if (!session) signerRef.current = null
  }, [session])

  const requestNsec = useCallback((): Promise<string> => {
    return new Promise((resolve, reject) => setPending({ resolve, reject }))
  }, [])

  const getSigner = useCallback(async (): Promise<NostrSigner> => {
    if (signerRef.current) return signerRef.current

    await ensureWasm()

    if (isNip07Available()) {
      const signer = NostrSigner.nip07(new BrowserSigner())
      signerRef.current = signer
      return signer
    }

    if (hasStoredNsec()) {
      const nsec = await requestNsec()
      const signer = NostrSigner.keys(Keys.parse(nsec.trim()))
      signerRef.current = signer
      return signer
    }

    throw new Error('No signing method available for this session')
  }, [requestNsec])

  return (
    <SignerContext.Provider value={{ getSigner }}>
      {children}
      {pending && (
        <PinUnlockModal
          onUnlocked={(nsec) => {
            pending.resolve(nsec)
            setPending(null)
          }}
          onCancel={() => {
            pending.reject(new Error('Signing cancelled'))
            setPending(null)
          }}
        />
      )}
    </SignerContext.Provider>
  )
}

export function useSigner(): SignerContextValue {
  const ctx = useContext(SignerContext)
  if (!ctx) throw new Error('useSigner must be used within SignerProvider')
  return ctx
}
