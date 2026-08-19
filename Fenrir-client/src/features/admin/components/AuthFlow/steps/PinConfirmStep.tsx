import { useEffect, useRef, useState } from 'react'
import PinPad, { PIN_LENGTH } from '@/features/admin/components/AuthFlow/PinPad'
import Icon from '@/components/ui/Icon'
import styles from './steps.module.css'

interface PinConfirmStepProps {
  originalPin: string
  onConfirmed: (pin: string) => Promise<void>
  onBack?: () => void
}

/** auth-07-pin-confirm.png - re-enter the PIN; on match, the caller encrypts/stores/signs in. */
export default function PinConfirmStep({ originalPin, onConfirmed, onBack }: PinConfirmStepProps) {
  const [pin, setPin] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Tracks real unmounts only - the pad is disabled while busy, so `pin` can't change again (and
  // re-run this effect) until the in-flight attempt has already finished on its own. Must reset to
  // true in the effect body (not just return a cleanup) - StrictMode's dev-only double-invoke runs
  // this cleanup once right after mount, which would otherwise strand it false forever.
  const mountedRef = useRef(true)
  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    if (pin.length !== PIN_LENGTH) return

    if (pin !== originalPin) {
      setError("PINs don't match — try again")
      setPin('')
      return
    }

    setBusy(true)
    setError(null)
    onConfirmed(pin)
      .catch((err: unknown) => {
        if (!mountedRef.current) return
        setError(err instanceof Error ? err.message : 'something went wrong')
        setPin('')
      })
      .finally(() => {
        if (mountedRef.current) setBusy(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pin])

  return (
    <div className={styles.card}>
      {onBack && (
        <button type="button" className={styles.backLink} onClick={onBack} disabled={busy}>
          <Icon name="arrow_back" size={14} /> Back
        </button>
      )}
      <h1 className={styles.title}>Confirm PIN</h1>
      <p className={styles.subtitle}>Enter your PIN again to confirm.</p>

      <PinPad value={pin} onChange={setPin} disabled={busy} />

      {error && <p className={styles.error}>{error}</p>}
      {busy && <p className={styles.hint}>Encrypting and signing in…</p>}
    </div>
  )
}
