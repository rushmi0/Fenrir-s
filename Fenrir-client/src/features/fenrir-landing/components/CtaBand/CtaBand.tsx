import Chamfer from '@/components/ui/Chamfer'
import styles from './CtaBand.module.css'

export default function CtaBand() {
  return (
    <section className={styles.section}>
      <Chamfer size={20} className={styles.panel}>
        <div>
          <h2 className={styles.heading}>Ready to run your own relay?</h2>
          <p className={styles.subtext}>Free, open source, and yours to self-host.</p>
        </div>
      </Chamfer>
    </section>
  )
}