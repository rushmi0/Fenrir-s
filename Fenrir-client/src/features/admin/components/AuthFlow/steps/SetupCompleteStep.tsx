import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './steps.module.css'

interface SetupCompleteStepProps {
  onContinue: () => void
}

/** auth-08-done.png - shown only at the end of first-time system setup. */
export default function SetupCompleteStep({ onContinue }: SetupCompleteStepProps) {
  return (
    <div className={styles.card}>
      <Chamfer size={4} className={styles.iconMarkSuccess}>
        <Icon name="check" size={20} />
      </Chamfer>
      <h1 className={styles.title}>You're in</h1>
      <p className={styles.subtitle}>Relay verified. You can now continue to the Admin Console.</p>

      <Chamfer as="button" type="button" size={8} className={styles.submit} onClick={onContinue}>
        Go to Feed &gt;
      </Chamfer>
    </div>
  )
}
