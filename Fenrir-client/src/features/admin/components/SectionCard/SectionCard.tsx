import type { ReactNode } from 'react'
import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './SectionCard.module.css'

interface SectionCardProps {
  icon: string
  title: string
  subtitle: string
  headerExtra?: ReactNode
  children: ReactNode
}

/** Bordered card with an icon-box header - the repeated unit behind every Admin Console tab. */
export default function SectionCard({ icon, title, subtitle, headerExtra, children }: SectionCardProps) {
  return (
    <Chamfer size={10} className={styles.card}>
      <div className={styles.header}>
        <Chamfer size={4} className={styles.iconBox}>
          <Icon name={icon} size={18} filled />
        </Chamfer>
        <div className={styles.headerText}>
          <h3 className={styles.title}>{title}</h3>
          <p className={styles.subtitle}>{subtitle}</p>
        </div>
        {headerExtra && <div className={styles.headerExtra}>{headerExtra}</div>}
      </div>
      <div className={styles.body}>{children}</div>
    </Chamfer>
  )
}
