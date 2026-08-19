import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './steps.module.css'

interface UnauthorizedOwnerStepProps {
  relayName: string
  pubkey?: string
  onTryAnotherAccount: () => void
}

/**
 * Shown when the server rejects a signed login because the pubkey isn't registered as an operator
 * at all (403 "not a registered admin/operator") - any *registered* role (OWNER, ADMIN, general
 * operator) is admitted into the console, this screen is only for accounts with no access at all.
 * No session or local credential is ever persisted for a rejected identity - "Try another account"
 * just clears the in-memory attempt and drops back to the nsec step.
 */
export default function UnauthorizedOwnerStep({ relayName, pubkey, onTryAnotherAccount }: UnauthorizedOwnerStepProps) {
  return (
    <div className={styles.card}>
      <Chamfer size={4} className={styles.iconMarkWarning}>
        <Icon name="block" size={20} />
      </Chamfer>
      <h1 className={styles.title}>Not authorized</h1>
      <p className={styles.subtitle}>
        This Nostr account isn't registered as an operator for {relayName || 'this relay'}. Ask the relay owner to
        add it, or try a different account.
      </p>
      {pubkey && <p className={styles.pubkey}>{pubkey}</p>}

      <Chamfer as="button" type="button" size={8} className={styles.submit} onClick={onTryAnotherAccount}>
        Try another account &gt;
      </Chamfer>
    </div>
  )
}
