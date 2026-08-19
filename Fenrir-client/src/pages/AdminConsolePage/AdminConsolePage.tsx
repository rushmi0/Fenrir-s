import { useState } from 'react'
import RelayInfoTab from '@/features/admin/components/sections/RelayInfoTab'
import PolicyTab from '@/features/admin/components/sections/PolicyTab'
import RoleTab from '@/features/admin/components/sections/RoleTab'
import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './AdminConsolePage.module.css'

const SECTIONS = [
  { key: 'relay-info', label: 'Relay Info', icon: 'info', Panel: RelayInfoTab },
  { key: 'policy', label: 'Policy', icon: 'lock', Panel: PolicyTab },
  { key: 'role', label: 'Role', icon: 'group', Panel: RoleTab },
] as const

type SectionKey = (typeof SECTIONS)[number]['key']

/** Admin Console content - session guard and top-level chrome now live in AppShell. */
export default function AdminConsolePage() {
  const [section, setSection] = useState<SectionKey>('relay-info')

  const active = SECTIONS.find((s) => s.key === section) ?? SECTIONS[0]
  const ActivePanel = active.Panel

  return (
    <div className={styles.wrap}>
      <nav className={styles.tabs}>
        {SECTIONS.map((s) => (
          <Chamfer
            key={s.key}
            as="button"
            type="button"
            size={4}
            className={s.key === section ? `${styles.tab} ${styles.tabActive}` : styles.tab}
            onClick={() => setSection(s.key)}
          >
            <Icon name={s.icon} size={16} />
            {s.label}
          </Chamfer>
        ))}
      </nav>

      <div className={styles.panelScroll}>
        <ActivePanel />
      </div>
    </div>
  )
}
