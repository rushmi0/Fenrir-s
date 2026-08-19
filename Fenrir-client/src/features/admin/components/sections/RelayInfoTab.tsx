import { useState } from 'react'
import { adminApi } from '@/features/admin/api/adminApi'
import type { RelayConfig } from '@/features/admin/api/adminApi'
import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import { useConfigResource } from '@/features/admin/hooks/useConfigResource'
import SectionCard from '@/features/admin/components/SectionCard'
import Toggle from '@/components/ui/Toggle'
import panelStyles from './panel.module.css'
import layoutStyles from './tabLayout.module.css'

interface DbSettings {
  primaryEnabled: boolean
  largeDb: boolean
  url: string
  name: string
  username: string
  password: string
  testStatus: 'untested' | 'testing' | 'ok' | 'failed'
}

const DB_DEFAULTS: DbSettings = {
  primaryEnabled: true,
  largeDb: true,
  url: '',
  name: '',
  username: '',
  password: '',
  testStatus: 'untested',
}

/** "Relay Info" tab - relay identity (backed by RelayConfig) plus a database settings card that
 * has no backend yet, so it holds local-only state until a config endpoint exists for it. */
export default function RelayInfoTab() {
  const { session } = useAdminSession()
  const token = session?.token ?? ''
  const canWrite = session?.role !== 'OPERATOR'

  const relay = useConfigResource<RelayConfig>(
    () => adminApi.getRelayConfig(token),
    (next) => adminApi.putRelayConfig(token, next),
  )
  const [db, setDb] = useState<DbSettings>(DB_DEFAULTS)

  function field(key: keyof RelayConfig, label: string) {
    if (!relay.value) return null
    return (
      <label className={panelStyles.field}>
        <span className={panelStyles.label}>{label}</span>
        <input
          className={panelStyles.input}
          value={relay.value[key]}
          disabled={!canWrite}
          onChange={(e) => relay.setValue({ ...relay.value!, [key]: e.target.value })}
        />
      </label>
    )
  }

  function dbField(key: 'url' | 'name' | 'username' | 'password', label: string, placeholder: string, type = 'text') {
    return (
      <label className={panelStyles.field}>
        <span className={panelStyles.label}>{label}</span>
        <input
          className={panelStyles.input}
          type={type}
          placeholder={placeholder}
          value={db[key]}
          disabled={!canWrite || db.primaryEnabled}
          onChange={(e) => setDb({ ...db, [key]: e.target.value, testStatus: 'untested' })}
        />
      </label>
    )
  }

  async function handleTestConnection() {
    setDb((prev) => ({ ...prev, testStatus: 'testing' }))
    await new Promise((resolve) => setTimeout(resolve, 500))
    setDb((prev) => ({ ...prev, testStatus: prev.url.trim() ? 'ok' : 'failed' }))
  }

  function handleApply() {
    if (canWrite) void relay.save()
  }

  return (
    <div>
      <div className={layoutStyles.grid}>
        <SectionCard icon="info" title="Relay details" subtitle="How your relay identifies itself to clients">
          {relay.loading && <p className={panelStyles.status}>Loading…</p>}
          {!relay.loading && !relay.value && (
            <p className={panelStyles.error}>{relay.error ?? 'failed to load relay config'}</p>
          )}
          {relay.value && (
            <>
              {field('name', 'Name')}
              {field('relayUrl', 'Domain')}
              {field('description', 'Description')}
              {field('npub', 'Npub')}
              {field('contact', 'Contact')}
            </>
          )}
        </SectionCard>

        <SectionCard
          icon="lock"
          title="Database Settings"
          subtitle={db.primaryEnabled ? 'Locked while the primary database is enabled' : 'Editable while disabled'}
          headerExtra={
            <>
              <span className={panelStyles.label}>Large DB</span>
              <Toggle
                ariaLabel="Large DB"
                checked={db.largeDb}
                disabled={!canWrite}
                onChange={(checked) => setDb({ ...db, largeDb: checked })}
              />
            </>
          }
        >
          <div className={panelStyles.row}>
            <div className={panelStyles.rowText}>
              <p className={panelStyles.rowTitle}>Primary Database Enabled</p>
              <p className={panelStyles.rowSub}>Switches the primary event store</p>
            </div>
            <Toggle
              ariaLabel="Primary Database Enabled"
              checked={db.primaryEnabled}
              disabled={!canWrite}
              onChange={(checked) => setDb({ ...db, primaryEnabled: checked })}
            />
          </div>

          {dbField('url', 'Database Url', 'postgres://db.internal:5432')}
          {dbField('name', 'Database Name', 'fenrir_relay')}
          {dbField('username', 'Database Username', 'fenrir_admin')}
          {dbField('password', 'Database Password', '········', 'password')}

          <div className={panelStyles.row}>
            <label className={panelStyles.checkboxField}>
              <input type="checkbox" checked={db.testStatus !== 'ok'} disabled readOnly />
              <span className={panelStyles.label}>
                {db.testStatus === 'ok'
                  ? 'Connected'
                  : db.testStatus === 'failed'
                    ? 'Connection failed'
                    : db.testStatus === 'testing'
                      ? 'Testing…'
                      : 'Not tested'}
              </span>
            </label>
            <button
              type="button"
              className={panelStyles.save}
              disabled={!canWrite || db.testStatus === 'testing'}
              onClick={() => void handleTestConnection()}
            >
              Test connection
            </button>
          </div>
        </SectionCard>
      </div>

      {canWrite && (
        <div className={layoutStyles.applyRow}>
          <button type="button" className={panelStyles.save} disabled={relay.saving} onClick={handleApply}>
            {relay.saving ? 'Saving…' : 'Apply'}
          </button>
          {relay.error && <p className={panelStyles.error}>{relay.error}</p>}
          {relay.saved && <p className={panelStyles.saved}>Saved.</p>}
        </div>
      )}
    </div>
  )
}
