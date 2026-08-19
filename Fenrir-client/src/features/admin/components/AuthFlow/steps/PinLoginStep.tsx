import { useEffect, useRef, useState } from 'react'
import PinPad, { PIN_LENGTH } from '@/features/admin/components/AuthFlow/PinPad'
import styles from './steps.module.css'

interface PinLoginStepProps {
  relayName: string
  onSubmit: (pin: string) => Promise<void>
  onUseNsecInstead: () => void
}

/** Returning-visit screen for Case A: this browser already holds an encrypted nsec. */
export default function PinLoginStep({ relayName, onSubmit, onUseNsecInstead }: PinLoginStepProps) {
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
    setBusy(true)
    setError(null)
    onSubmit(pin)
      .catch((err: unknown) => {
        if (!mountedRef.current) return
        setError(err instanceof Error ? err.message : 'sign-in failed')
        setPin('')
      })
      .finally(() => {
        if (mountedRef.current) setBusy(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pin])

  return (
    <div className={styles.card}>
      <h1 className={styles.title}>Enter PIN</h1>
      <p className={styles.subtitle}>
        Unlock your Nostr key on this device to sign in to {relayName || 'the relay'}.
      </p>

      <PinPad value={pin} onChange={setPin} disabled={busy} />

      {error && <p className={styles.error}>{error}</p>}
      {busy && <p className={styles.hint}>Unlocking…</p>}

      <button
        type="button"
        className={styles.textLink}
        onClick={onUseNsecInstead}
        disabled={busy}
      >
        Forgot your PIN? Use nsec instead
      </button>
    </div>
  )
}
