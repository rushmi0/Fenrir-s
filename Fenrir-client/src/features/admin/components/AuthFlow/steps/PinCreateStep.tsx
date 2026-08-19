import { useEffect, useState } from 'react'
import PinPad, { PIN_LENGTH } from '@/features/admin/components/AuthFlow/PinPad'
import Icon from '@/components/ui/Icon'
import styles from './steps.module.css'

interface PinCreateStepProps {
  onCreated: (pin: string) => void
  onBack?: () => void
}

/** auth-06-pin-enter.png - the PIN encrypts the nsec before it's written to this browser. */
export default function PinCreateStep({ onCreated, onBack }: PinCreateStepProps) {
  const [pin, setPin] = useState('')

  useEffect(() => {
    if (pin.length === PIN_LENGTH) onCreated(pin)
  }, [pin, onCreated])

  return (
    <div className={styles.card}>
      {onBack && (
        <button type="button" className={styles.backLink} onClick={onBack}>
          <Icon name="arrow_back" size={14} /> Back
        </button>
      )}
      <h1 className={styles.title}>Set a PIN</h1>
      <p className={styles.subtitle}>
        Your PIN encrypts your Nostr <code>nsec</code> before it's stored in this browser, and unlocks it on future
        logins.
      </p>

      <PinPad value={pin} onChange={setPin} />
    </div>
  )
}
