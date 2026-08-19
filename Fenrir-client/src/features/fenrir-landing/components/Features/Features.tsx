import Chamfer from '@/components/ui/Chamfer'
import { FEATURES } from '../../data'
import styles from './Features.module.css'

export default function Features() {
  return (
    <section id="features" className={styles.features}>
      <h2 className={styles.heading}>Why Fenrir?</h2>
      <p className={styles.subtext}>
        Built for developers who want a relay they can run, read, and trust.
      </p>
      <div className={styles.grid}>
        {FEATURES.map((feature) => (
          <Chamfer key={feature.title} size={14} className={styles.card}>
            <Chamfer size={7} className={styles.icon} />
            <h3 className={styles.cardTitle}>{feature.title}</h3>
            <p className={styles.cardDesc}>{feature.desc}</p>
          </Chamfer>
        ))}
      </div>
    </section>
  )
}