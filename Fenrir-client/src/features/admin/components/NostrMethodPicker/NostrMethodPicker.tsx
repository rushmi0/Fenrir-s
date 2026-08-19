import { useState } from 'react'
import { isNip07Available } from '@/features/admin/nostr/signLogin'
import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './NostrMethodPicker.module.css'

export type SigningMethod = 'nip07' | 'nsec'

interface NostrMethodPickerProps {
  method: SigningMethod
  onMethodChange: (method: SigningMethod) => void
  nsec: string
  onNsecChange: (nsec: string) => void
}

/** NIP-07 / nsec choice shared by the Setup and normal Login cards. */
export default function NostrMethodPicker({ method, onMethodChange, nsec, onNsecChange }: NostrMethodPickerProps) {
  const [revealed, setRevealed] = useState(false)

  return (
    <div className={styles.picker}>
      <div className={styles.tabs}>
        <button
          type="button"
          className={method === 'nsec' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
          onClick={() => onMethodChange('nsec')}
        >
          nsec
        </button>
        <button
          type="button"
          className={method === 'nip07' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
          onClick={() => onMethodChange('nip07')}
        >
          Extension (NIP-07)
        </button>
      </div>

      {method === 'nsec' && (
        <>
          <p className={styles.fieldLabel}>Nsec private key</p>
          <div className={styles.inputRow}>
            <input
              type={revealed ? 'text' : 'password'}
              className={styles.input}
              placeholder="nsec1…"
              value={nsec}
              onChange={(e) => onNsecChange(e.target.value)}
              autoComplete="off"
            />
            <button
              type="button"
              className={styles.reveal}
              onClick={() => setRevealed((v) => !v)}
              aria-label={revealed ? 'Hide private key' : 'Show private key'}
            >
              <Icon name={revealed ? 'visibility_off' : 'visibility'} size={18} />
            </button>
          </div>
          <p className={styles.hint}>Stored locally, never sent to any server.</p>
        </>
      )}

      {method === 'nip07' && (
        <Chamfer size={8} className={styles.extensionBox}>
          <Chamfer size={4} className={styles.extensionIcon} />
          <p className={styles.extensionText}>Uses window.nostr from your NIP-07 browser extension (Alby, nos2x…).</p>
          {!isNip07Available() && <p className={styles.warning}>No NIP-07 extension detected in this browser.</p>}
        </Chamfer>
      )}
    </div>
  )
}
