import { useState } from 'react'
import Chamfer from '@/components/ui/Chamfer'
import Icon from '@/components/ui/Icon'
import styles from './CounterPage.module.css'

/** Simple +/- counter utility page, per the app shell nav. Local state only. */
export default function CounterPage() {
  const [count, setCount] = useState(0)

  return (
    <div className={styles.wrap}>
      <Chamfer size={12} className={styles.card}>
        <h1 className={styles.title}>Counter</h1>
        <p className={styles.value}>{count}</p>

        <div className={styles.buttons}>
          <Chamfer as="button" type="button" size={8} className={styles.button} onClick={() => setCount((c) => c - 1)}>
            <Icon name="remove" size={20} />
          </Chamfer>
          <Chamfer as="button" type="button" size={8} className={styles.button} onClick={() => setCount((c) => c + 1)}>
            <Icon name="add" size={20} />
          </Chamfer>
        </div>

        <button type="button" className={styles.reset} onClick={() => setCount(0)}>
          <Icon name="refresh" size={14} /> Reset
        </button>
      </Chamfer>
    </div>
  )
}
