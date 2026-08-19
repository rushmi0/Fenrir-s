import { useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import { SignerProvider } from '@/features/admin/context/SignerContext'
import { authApi } from '@/features/admin/api/adminApi'
import { navConfig } from '@/features/app-shell/navConfig'
import TopBar from '@/features/app-shell/components/TopBar/TopBar'
import NavDrawer from '@/features/app-shell/components/NavDrawer/NavDrawer'
import styles from './AppShell.module.css'

/**
 * Shared layout for the authenticated app (Home / Admin Console / Counter) - owns the session
 * guard (moved here from AdminConsolePage now that three pages share it), the top bar, and the
 * nav drawer. Renders the active page via <Outlet/>.
 */
export default function AppShell() {
  const { session, loading, clearSession } = useAdminSession()
  const navigate = useNavigate()
  const location = useLocation()
  const [drawerOpen, setDrawerOpen] = useState(false)

  useEffect(() => {
    if (!loading && !session) navigate('/login', { replace: true })
  }, [loading, session, navigate])

  if (loading || !session) {
    return <div className={styles.page} />
  }

  const active = navConfig.find((entry) => location.pathname.startsWith(entry.path)) ?? navConfig[0]

  async function handleLogout() {
    if (session) await authApi.logout(session.token).catch(() => undefined)
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <SignerProvider>
      <div className={styles.page}>
        <TopBar active={active} onMenuClick={() => setDrawerOpen(true)} />
        <NavDrawer
          open={drawerOpen}
          activeKey={active.key}
          role={session.role}
          pubkey={session.pubkey}
          onClose={() => setDrawerOpen(false)}
          onLogout={() => void handleLogout()}
        />
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </SignerProvider>
  )
}
