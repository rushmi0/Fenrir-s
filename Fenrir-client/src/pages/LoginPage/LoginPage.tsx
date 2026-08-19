import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { systemApi } from '@/features/admin/api/adminApi'
import type { SystemStatus } from '@/features/admin/api/adminApi'
import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import AuthFlow from '@/features/admin/components/AuthFlow'
import Chamfer from '@/components/ui/Chamfer'
import logo from '@/assets/images/fenrir-logo-horizontal-v2.png'
import styles from './LoginPage.module.css'

/**
 * Sign-in entry point (`/login`) for any registered operator - general or admin - reacts to the
 * relay's system state (see task's "System States" section) via AuthFlow, which picks the
 * setup-token/nsec-register/pin-login starting step on its own. Redirects straight to the feed if
 * a valid session already exists.
 */
export default function LoginPage() {
  const [status, setStatus] = useState<SystemStatus | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)
  const { session, loading: sessionLoading } = useAdminSession()
  const navigate = useNavigate()

  useEffect(() => {
    systemApi
      .status()
      .then(setStatus)
      .catch((err: unknown) => setStatusError(err instanceof Error ? err.message : 'failed to reach relay'))
  }, [])

  useEffect(() => {
    if (session) navigate('/feed', { replace: true })
  }, [session, navigate])

  const ready = !sessionLoading && !session && (status !== null || statusError !== null)

  return (
    <div className={styles.page}>
      <Link to="/" className={styles.brand}>
        <img src={logo} alt="Fenrir" className={styles.logo} />
      </Link>
      <Chamfer size={16} className={styles.card}>
        {!ready ? (
          <p className={styles.status}>Loading…</p>
        ) : statusError ? (
          <p className={styles.error}>{statusError}</p>
        ) : (
          <AuthFlow relayName={status?.relayName ?? ''} systemState={status?.state ?? 'READY'} />
        )}
      </Chamfer>
    </div>
  )
}
