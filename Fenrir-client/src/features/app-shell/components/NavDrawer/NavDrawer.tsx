import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { navConfig } from '@/features/app-shell/navConfig'
import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './NavDrawer.module.css'

interface NavDrawerProps {
  open: boolean
  activeKey: string
  role: string
  pubkey: string
  onClose: () => void
  onLogout: () => void
}

/** Right-side slide-in nav drawer (not a persistent rail) - backdrop click, item click, or Escape
 * closes it. */
export default function NavDrawer({ open, activeKey, role, pubkey, onClose, onLogout }: NavDrawerProps) {
  const navigate = useNavigate()
  const visibleEntries = navConfig.filter((entry) => entry.key !== 'console' || role !== 'OPERATOR')

  useEffect(() => {
    if (!open) return
    function handleKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [open, onClose])

  function goTo(path: string) {
    navigate(path)
    onClose()
  }

  return (
    <>
      <div
        className={open ? `${styles.backdrop} ${styles.backdropOpen}` : styles.backdrop}
        onClick={onClose}
        aria-hidden={!open}
      />
      <aside className={open ? `${styles.drawer} ${styles.drawerOpen}` : styles.drawer} aria-hidden={!open}>
        <div className={styles.brand}>
          <Chamfer size={4} className={styles.brandMark} />
          <span className={styles.brandName}>FENRIR</span>
        </div>

        <div className={styles.sectionLabel}>Navigation</div>

        <nav className={styles.list}>
          {visibleEntries.map((entry) => {
            const isActive = entry.key === activeKey
            return (
              <button
                key={entry.key}
                type="button"
                className={isActive ? `${styles.item} ${styles.itemActive}` : styles.item}
                onClick={() => goTo(entry.path)}
              >
                <Chamfer size={3} className={isActive ? `${styles.iconBox} ${styles.iconBoxActive}` : styles.iconBox}>
                  <Icon name={entry.icon} size={16} filled={isActive} />
                </Chamfer>
                {entry.label}
              </button>
            )
          })}
        </nav>

        <div className={styles.footer}>
          <div className={styles.identity}>
            <span className={styles.role}>{role}</span>
            <span className={styles.pubkey}>{pubkey.slice(0, 10)}…</span>
          </div>
          <button type="button" className={styles.logout} onClick={onLogout}>
            Log out
          </button>
        </div>
      </aside>
    </>
  )
}
