import { useState } from 'react'
import { adminApi } from '@/features/admin/api/adminApi'
import type { SecurityPolicy, SystemConfig } from '@/features/admin/api/adminApi'
import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import { useConfigResource } from '@/features/admin/hooks/useConfigResource'
import SectionCard from '@/features/admin/components/SectionCard'
import Toggle from '@/components/ui/Toggle'
import panelStyles from './panel.module.css'
import layoutStyles from './tabLayout.module.css'

function splitPubkeys(raw: string): string[] {
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/** "Policy" tab - access/PoW/auth settings from SecurityPolicy, plus the REQ bounds that live on
 * SystemConfig, applied together behind a single button. */
export default function PolicyTab() {
  const { session } = useAdminSession()
  const token = session?.token ?? ''
  const canWrite = session?.role !== 'OPERATOR'

  const policy = useConfigResource<SecurityPolicy>(
    () => adminApi.getPolicy(token),
    (next) => adminApi.putPolicy(token, next),
  )
  const system = useConfigResource<SystemConfig>(
    () => adminApi.getSystemConfig(token),
    (next) => adminApi.putSystemConfig(token, next),
  )

  const loading = policy.loading || system.loading
  const saving = policy.saving || system.saving
  const error = policy.error ?? system.error
  const saved = policy.saved && system.saved

  function toggle(key: keyof SecurityPolicy) {
    if (!policy.value) return null
    return (checked: boolean) => policy.setValue({ ...policy.value!, [key]: checked })
  }

  function toggleAuthEnabled(checked: boolean) {
    if (!policy.value) return
    // Auth Enabled requires clients to authenticate before publishing - that's incompatible with
    // All Pass ("accept events from any pubkey" with no restriction), so turning auth on turns All
    // Pass back off rather than leaving a contradictory combination in place.
    policy.setValue({ ...policy.value, authEnabled: checked, allPass: checked ? false : policy.value.allPass })
  }

  async function handleApply() {
    if (!canWrite) return
    await Promise.all([policy.save(), system.save()])
  }

  if (loading) return <p className={panelStyles.status}>Loading…</p>
  if (!policy.value || !system.value) {
    return <p className={panelStyles.error}>{error ?? 'failed to load policy'}</p>
  }

  const whitelist = splitPubkeys(policy.value.authWhitelistPubkeys)

  function removePubkey(pubkey: string) {
    policy.setValue({
      ...policy.value!,
      authWhitelistPubkeys: whitelist.filter((p) => p !== pubkey).join(','),
    })
  }

  function addPubkey(pubkey: string) {
    const trimmed = pubkey.trim()
    if (!trimmed || whitelist.includes(trimmed)) return
    policy.setValue({ ...policy.value!, authWhitelistPubkeys: [...whitelist, trimmed].join(',') })
  }

  return (
    <div>
      <div className={layoutStyles.grid}>
        <SectionCard icon="lock" title="Access policy" subtitle="Who is allowed to publish events">
          <div className={panelStyles.row}>
            <div className={panelStyles.rowText}>
              <p className={panelStyles.rowTitle}>All Pass</p>
              <p className={panelStyles.rowSub}>Accept events from any pubkey</p>
            </div>
            <Toggle ariaLabel="All Pass" checked={policy.value.allPass} disabled={!canWrite} onChange={toggle('allPass')!} />
          </div>
          <div className={panelStyles.row}>
            <div className={panelStyles.rowText}>
              <p className={panelStyles.rowTitle}>Follows Pass</p>
              <p className={panelStyles.rowSub}>Accept events only from accounts the relay owner follows</p>
            </div>
            <Toggle
              ariaLabel="Follows Pass"
              checked={policy.value.followsPass}
              disabled={!canWrite}
              onChange={toggle('followsPass')!}
            />
          </div>
        </SectionCard>

        <SectionCard icon="lock" title="Proof of work" subtitle="Spam mitigation via NIP-13">
          <div className={panelStyles.row}>
            <div className={panelStyles.rowText}>
              <p className={panelStyles.rowTitle}>Pow Enabled</p>
              <p className={panelStyles.rowSub}>Require proof-of-work on incoming events</p>
            </div>
            <Toggle ariaLabel="Pow Enabled" checked={policy.value.powEnabled} disabled={!canWrite} onChange={toggle('powEnabled')!} />
          </div>
          <div className={panelStyles.sliderRow}>
            <div className={panelStyles.sliderHeader}>
              <span className={panelStyles.label}>Min Difficulty</span>
              <span className={panelStyles.sliderValue}>{policy.value.minDifficulty}</span>
            </div>
            <input
              className={panelStyles.slider}
              type="range"
              min={0}
              max={32}
              value={policy.value.minDifficulty}
              disabled={!canWrite}
              onChange={(e) => policy.setValue({ ...policy.value!, minDifficulty: Number(e.target.value) })}
            />
          </div>
        </SectionCard>

        <SectionCard icon="lock" title="Query limits" subtitle="Bounds applied to REQ subscriptions">
          <label className={panelStyles.field}>
            <span className={panelStyles.label}>Max Filters</span>
            <input
              className={panelStyles.input}
              type="number"
              min={1}
              value={system.value.maxFilters}
              disabled={!canWrite}
              onChange={(e) => system.setValue({ ...system.value!, maxFilters: Number(e.target.value) })}
            />
          </label>
          <label className={panelStyles.field}>
            <span className={panelStyles.label}>Max Limit</span>
            <input
              className={panelStyles.input}
              type="number"
              min={1}
              value={system.value.maxLimit}
              disabled={!canWrite}
              onChange={(e) => system.setValue({ ...system.value!, maxLimit: Number(e.target.value) })}
            />
          </label>
        </SectionCard>

        <SectionCard
          icon="lock"
          title="Authentication"
          subtitle="NIP-42 auth and whitelisted access — synced with FOLLOWS_PASS"
        >
          <div className={panelStyles.row}>
            <div className={panelStyles.rowText}>
              <p className={panelStyles.rowTitle}>Auth Enabled</p>
              <p className={panelStyles.rowSub}>Require clients to authenticate before publishing</p>
            </div>
            <Toggle ariaLabel="Auth Enabled" checked={policy.value.authEnabled} disabled={!canWrite} onChange={toggleAuthEnabled} />
          </div>

          <div className={panelStyles.field}>
            <span className={panelStyles.label}>Auth Whitelist Pubkeys</span>
            <div className={panelStyles.pillList}>
              {whitelist.map((pubkey) => (
                <div key={pubkey} className={panelStyles.pillRow}>
                  <span className={panelStyles.pillValue}>{pubkey}</span>
                  {canWrite && (
                    <button type="button" className={panelStyles.pillRemove} onClick={() => removePubkey(pubkey)}>
                      remove
                    </button>
                  )}
                </div>
              ))}
              {canWrite && <PubkeyAddRow onAdd={addPubkey} />}
            </div>
          </div>
        </SectionCard>
      </div>

      {canWrite && (
        <div className={layoutStyles.applyRow}>
          <button type="button" className={panelStyles.save} disabled={saving} onClick={() => void handleApply()}>
            {saving ? 'Saving…' : 'Apply'}
          </button>
          {error && <p className={panelStyles.error}>{error}</p>}
          {saved && <p className={panelStyles.saved}>Saved.</p>}
        </div>
      )}
    </div>
  )
}

function PubkeyAddRow({ onAdd }: { onAdd: (pubkey: string) => void }) {
  const [draft, setDraft] = useState('')

  function commit() {
    onAdd(draft)
    setDraft('')
  }

  return (
    <div className={panelStyles.pillRow}>
      <input
        className={panelStyles.pillValue}
        placeholder="npub1…"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
      />
      <button type="button" className={panelStyles.pillAdd} onClick={commit}>
        +
      </button>
    </div>
  )
}
