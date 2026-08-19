import Icon from '@/components/ui/Icon'
import styles from './PinPad.module.css'

export const PIN_LENGTH = 6

interface PinPadProps {
  value: string
  onChange: (value: string) => void
  disabled?: boolean
}

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9']

/** The dotted-progress + numeric keypad shared by PIN create/confirm/login steps. */
export default function PinPad({ value, onChange, disabled = false }: PinPadProps) {
  function press(digit: string) {
    if (disabled || value.length >= PIN_LENGTH) return
    onChange(value + digit)
  }

  function backspace() {
    if (disabled) return
    onChange(value.slice(0, -1))
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    if (e.key >= '0' && e.key <= '9') press(e.key)
    else if (e.key === 'Backspace') backspace()
  }

  return (
    <div className={styles.pad} role="group" tabIndex={0} onKeyDown={handleKeyDown}>
      <div className={styles.dots}>
        {Array.from({ length: PIN_LENGTH }).map((_, i) => (
          <span key={i} className={i < value.length ? `${styles.dot} ${styles.dotFilled}` : styles.dot} />
        ))}
      </div>

      <div className={styles.keypad}>
        {KEYS.map((digit) => (
          <button
            key={digit}
            type="button"
            className={styles.key}
            disabled={disabled}
            onClick={() => press(digit)}
          >
            {digit}
          </button>
        ))}
        <span className={styles.spacer} aria-hidden />
        <button type="button" className={styles.key} disabled={disabled} onClick={() => press('0')}>
          0
        </button>
        <button
          type="button"
          className={styles.key}
          disabled={disabled}
          onClick={backspace}
          aria-label="Backspace"
        >
          <Icon name="backspace" size={18} />
        </button>
      </div>
    </div>
  )
}
