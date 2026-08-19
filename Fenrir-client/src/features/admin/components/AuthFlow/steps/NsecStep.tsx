import { useState } from 'react'
import { isNip07Available, isValidNsec } from '@/features/admin/nostr/signLogin'
import NostrMethodPicker from '@/features/admin/components/NostrMethodPicker'
import type { SigningMethod } from '@/features/admin/components/NostrMethodPicker'
import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './steps.module.css'

interface NsecStepProps {
  relayName: string
  /** nsec parsed and held only in memory - the caller moves on to PIN creation with it. */
  onNsecReady: (nsec: string) => void
  /** NIP-07 never exposes a raw key, so it signs in directly instead of going through PIN steps. */
  onNip07Submit: () => Promise<void>
  onBack?: () => void
}

/** auth-03-nostr-nsec.png - collects the Nostr identity that will be encrypted/stored for this browser. */
export default function NsecStep({ relayName, onNsecReady, onNip07Submit, onBack }: NsecStepProps) {
  const [method, setMethod] = useState<SigningMethod>(isNip07Available() ? 'nip07' : 'nsec')
  const [nsec, setNsec] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    setBusy(true)
    setError(null)
    try {
      if (method === 'nip07') {
        await onNip07Submit()
        return
      }
      const trimmed = nsec.trim()
      if (!(await isValidNsec(trimmed))) {
        setError('That nsec doesn’t look valid')
        return
      }
      onNsecReady(trimmed)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'sign-in failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.card}>
      {onBack && (
        <button type="button" className={styles.backLink} onClick={onBack}>
          <Icon name="arrow_back" size={14} /> Back
        </button>
      )}
      <h1 className={styles.title}>Sign in with Nostr</h1>
      <p className={styles.subtitle}>{relayName || 'The relay'} · your key never leaves this device.</p>

      <NostrMethodPicker method={method} onMethodChange={setMethod} nsec={nsec} onNsecChange={setNsec} />

      {error && <p className={styles.error}>{error}</p>}

      <Chamfer
        as="button"
        type="button"
        size={8}
        className={styles.submit}
        disabled={busy || (method === 'nsec' && nsec.trim().length === 0)}
        onClick={handleSubmit}
      >
        {busy ? 'Signing in…' : method === 'nip07' ? 'Sign In >' : 'Continue >'}
      </Chamfer>
    </div>
  )
}
