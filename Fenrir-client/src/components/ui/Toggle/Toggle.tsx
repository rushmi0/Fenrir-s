import Chamfer from '@/components/ui/Chamfer'
import styles from './Toggle.module.css'

interface ToggleProps {
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
  ariaLabel: string
}

/** Chamfered on/off switch used across the admin console's policy and role cards. */
export default function Toggle({ checked, onChange, disabled = false, ariaLabel }: ToggleProps) {
  return (
    <Chamfer
      as="button"
      type="button"
      size={4}
      role="switch"
      aria-checked={checked}
      aria-label={ariaLabel}
      disabled={disabled}
      className={checked ? `${styles.track} ${styles.trackOn}` : styles.track}
      onClick={() => onChange(!checked)}
    >
      <span className={styles.knob} />
    </Chamfer>
  )
}
