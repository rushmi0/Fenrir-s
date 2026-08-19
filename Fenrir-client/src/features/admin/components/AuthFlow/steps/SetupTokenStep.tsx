import { useState } from 'react'
import { setupApi } from '@/features/admin/api/adminApi'
import { formatError } from '@/features/admin/lib/formatError'
import Chamfer from '@/components/ui/Chamfer'
import styles from './steps.module.css'

interface SetupTokenStepProps {
  relayName: string
  onVerified: (token: string) => void
}

/** auth-01-token.png - one-time setup token that proves ownership of the relay process. */
export default function SetupTokenStep({ relayName, onVerified }: SetupTokenStepProps) {
  const [token, setToken] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    const trimmed = token.trim()
    setBusy(true)
    setError(null)
    try {
      await setupApi.challenge(trimmed)
      onVerified(trimmed)
    } catch (err) {
      setError(formatError(err, 'setup token verification failed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.card}>
      <Chamfer size={4} className={styles.iconMark} />
      <h1 className={styles.title}>Relay Setup</h1>
      <p className={styles.subtitle}>
        Verify ownership of {relayName || 'this relay'} with the one-time setup token printed to its startup logs.
      </p>

      <p className={styles.fieldLabel}>Setup token</p>
      <input
        type="text"
        className={styles.input}
        placeholder="Paste setup token…"
        value={token}
        onChange={(e) => setToken(e.target.value)}
        autoComplete="off"
        autoFocus
      />
      <p className={styles.hint}>Find this token in your relay's startup logs. It's valid for 30 minutes.</p>

      {error && <p className={styles.error}>{error}</p>}

      <Chamfer
        as="button"
        type="button"
        size={8}
        className={styles.submit}
        disabled={busy || token.trim().length === 0}
        onClick={handleSubmit}
      >
        {busy ? 'Verifying…' : 'Confirm >'}
      </Chamfer>
    </div>
  )
}
