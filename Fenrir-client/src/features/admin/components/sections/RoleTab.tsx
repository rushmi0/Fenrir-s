import { useState } from 'react'
import SectionCard from '@/features/admin/components/SectionCard'
import Toggle from '@/components/ui/Toggle'
import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import panelStyles from './panel.module.css'
import layoutStyles from './tabLayout.module.css'
import styles from './RoleTab.module.css'

interface Permission {
  key: string
  label: string
}

const PERMISSIONS: Permission[] = [
  { key: 'write', label: 'Publish events to the relay' },
  { key: 'moderate', label: 'Hide or flag events from other users' },
  { key: 'ban', label: 'Block specific pubkeys from writing' },
]

interface RoleDef {
  key: 'owner' | 'moderator' | 'reader'
  name: string
  badge: string
  badgeClass: keyof typeof styles
  subtitle: string
  prefix: string
}

const ROLES: RoleDef[] = [
  { key: 'owner', name: 'Owner', badge: 'FULL ACCESS', badgeClass: 'badgeOwner', subtitle: 'Full control over relay configuration', prefix: 'owner' },
  { key: 'moderator', name: 'Moderator', badge: 'ELEVATED', badgeClass: 'badgeModerator', subtitle: 'Day-to-day content moderation', prefix: 'mod' },
  { key: 'reader', name: 'Reader', badge: 'BASIC', badgeClass: 'badgeReader', subtitle: 'Read-only relay access', prefix: 'reader' },
]

type PermissionState = Record<string, boolean>

function permKey(prefix: string, perm: string) {
  return `${prefix}${perm[0].toUpperCase()}${perm.slice(1)}`
}

function initialState(): PermissionState {
  const state: PermissionState = {}
  for (const role of ROLES) {
    for (const perm of PERMISSIONS) {
      state[permKey(role.prefix, perm.key)] = false
    }
  }
  return state
}

/**
 * "Role" tab - a per-role permission matrix. There is no backend RBAC model for this yet (roles
 * today are just OWNER/ADMIN/OPERATOR strings on an operator record), so this holds local-only
 * state until that lands - see the design handoff for scope reasoning.
 */
export default function RoleTab() {
  const { session } = useAdminSession()
  const canWrite = session?.role !== 'OPERATOR'

  const [permissions, setPermissions] = useState<PermissionState>(initialState)
  const [saved, setSaved] = useState(false)

  function handleApply() {
    setSaved(true)
  }

  return (
    <div>
      <div className={layoutStyles.grid}>
        {ROLES.map((role) => (
          <SectionCard
            key={role.key}
            icon="group"
            title={role.name}
            subtitle={role.subtitle}
            headerExtra={<span className={`${styles.badge} ${styles[role.badgeClass]}`}>{role.badge}</span>}
          >
            {PERMISSIONS.map((perm) => {
              const key = permKey(role.prefix, perm.key)
              return (
                <div key={key} className={panelStyles.row}>
                  <div className={panelStyles.rowText}>
                    <p className={panelStyles.rowTitle}>{perm.label}</p>
                    <p className={panelStyles.rowSub}>{key}</p>
                  </div>
                  <Toggle
                    ariaLabel={`${role.name} ${perm.label}`}
                    checked={permissions[key] ?? false}
                    disabled={!canWrite}
                    onChange={(checked) => {
                      setPermissions((prev) => ({ ...prev, [key]: checked }))
                      setSaved(false)
                    }}
                  />
                </div>
              )
            })}
          </SectionCard>
        ))}
      </div>

      {canWrite && (
        <div className={layoutStyles.applyRow}>
          <button type="button" className={panelStyles.save} onClick={handleApply}>
            Apply
          </button>
          {saved && <p className={panelStyles.saved}>Saved.</p>}
        </div>
      )}
    </div>
  )
}
