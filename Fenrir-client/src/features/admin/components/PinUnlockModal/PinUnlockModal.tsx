import { useEffect, useRef, useState } from 'react'
import { decryptStoredNsec } from '@/features/admin/crypto/nsecVault'
import PinPad, { PIN_LENGTH } from '@/features/admin/components/AuthFlow/PinPad'
import Chamfer from '@/components/ui/Chamfer'
import styles from './PinUnlockModal.module.css'

interface PinUnlockModalProps {
  onUnlocked: (nsec: string) => void
  onCancel: () => void
}

/**
 * One-time-per-tab PIN prompt for signing actions (like/re-note/reply) taken after login, when the
 * session used the nsec+PIN path rather than a NIP-07 extension - see SignerContext. Decryption
 * happens right here so a wrong PIN just reprompts inline, same UX as AuthFlow's PinLoginStep.
 */
export default function PinUnlockModal({ onUnlocked, onCancel }: PinUnlockModalProps) {
  const [pin, setPin] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    if (pin.length !== PIN_LENGTH) return
    setBusy(true)
    setError(null)
    decryptStoredNsec(pin)
      .then((nsec) => onUnlocked(nsec))
      .catch((err: unknown) => {
        if (!mountedRef.current) return
        setError(err instanceof Error ? err.message : 'Incorrect PIN')
        setPin('')
      })
      .finally(() => {
        if (mountedRef.current) setBusy(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pin])

  return (
    <div className={styles.backdrop}>
      <Chamfer size={10} className={styles.card}>
        <h2 className={styles.title}>Unlock to sign</h2>
        <p className={styles.subtitle}>Enter your PIN to sign this action with your Nostr key.</p>

        <PinPad value={pin} onChange={setPin} disabled={busy} />

        {error && <p className={styles.error}>{error}</p>}
        {busy && <p className={styles.hint}>Unlocking…</p>}

        <button type="button" className={styles.cancel} onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </Chamfer>
    </div>
  )
}
