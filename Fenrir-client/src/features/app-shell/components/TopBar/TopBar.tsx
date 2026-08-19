import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import type { NavEntry } from '@/features/app-shell/navConfig'
import styles from './TopBar.module.css'

interface TopBarProps {
  active: NavEntry
  onMenuClick: () => void
}

/** Page-aware top bar: colored code badge + title + chevron + subtitle, driven by the active nav entry. */
export default function TopBar({ active, onMenuClick }: TopBarProps) {
  return (
    <header className={styles.bar}>
      <div className={styles.identity}>
        <Chamfer size={4} className={styles.badge} style={{ background: active.codeColor }}>
          {active.code}
        </Chamfer>
        <div className={styles.titleBlock}>
          <div className={styles.titleRow}>
            <span className={styles.title}>{active.title}</span>
            <Icon name="expand_more" size={16} />
          </div>
          <span className={styles.subtitle}>{active.subtitle}</span>
        </div>
      </div>

      <div className={styles.actions}>
        <button type="button" className={styles.iconButton} aria-label="Notifications">
          <Icon name="notifications" size={20} />
        </button>
        <button type="button" className={styles.iconButton} aria-label="Open menu" onClick={onMenuClick}>
          <Icon name="menu" size={20} />
        </button>
      </div>
    </header>
  )
}
