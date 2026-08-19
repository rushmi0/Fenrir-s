import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { isNip07Available, isValidNsec } from '@/features/admin/nostr/signLogin'
import { useGeneralSigner } from '@/features/feed/context/GeneralSignerContext'
import NostrMethodPicker from '@/features/admin/components/NostrMethodPicker'
import type { SigningMethod } from '@/features/admin/components/NostrMethodPicker'
import Chamfer from '@/components/ui/Chamfer'
import { useToast } from '@/components/ui/Toast'
import styles from './GeneralLoginCard.module.css'

/** Sign-in banner for the public /feed page - browsing works without this, it only unlocks the
 * reply/like/repost row on each post. Reuses the same NIP-07/nsec picker as the Admin Console's
 * login, but signs in locally only (see GeneralSignerContext) - no backend call, since a general
 * visitor isn't a registered operator. "Sign in as admin instead" routes to the full Admin Console
 * flow for anyone who does hold operator credentials. */
export default function GeneralLoginCard() {
  const { loginWithNip07, loginWithNsec } = useGeneralSigner()
  const { showToast } = useToast()
  const navigate = useNavigate()
  const [method, setMethod] = useState<SigningMethod>(isNip07Available() ? 'nip07' : 'nsec')
  const [nsec, setNsec] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    setBusy(true)
    setError(null)
    try {
      if (method === 'nip07') {
        await loginWithNip07()
      } else {
        const trimmed = nsec.trim()
        if (!(await isValidNsec(trimmed))) {
          setError('That nsec doesn’t look valid')
          return
        }
        await loginWithNsec(trimmed)
      }
      showToast('success', 'Signed in')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'sign-in failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Chamfer size={8} className={styles.card}>
      <div className={styles.header}>
        <h2 className={styles.title}>Sign in to interact</h2>
        <p className={styles.subtitle}>Browse freely - sign in to like, reply, or repost.</p>
      </div>

      <NostrMethodPicker method={method} onMethodChange={setMethod} nsec={nsec} onNsecChange={setNsec} />

      {error && <p className={styles.error}>{error}</p>}

      <div className={styles.actions}>
        <Chamfer
          as="button"
          type="button"
          size={4}
          className={styles.submit}
          disabled={busy || (method === 'nsec' && nsec.trim().length === 0)}
          onClick={handleSubmit}
        >
          {busy ? 'Signing in…' : 'Sign In >'}
        </Chamfer>
        <button type="button" className={styles.adminLink} onClick={() => navigate('/login')}>
          Sign in as admin instead →
        </button>
      </div>
    </Chamfer>
  )
}
