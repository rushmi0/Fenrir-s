import Chamfer from '@/components/ui/Chamfer'
import styles from './QuickStart.module.css'

export default function QuickStart() {
  return (
    <section id="quickstart" className={styles.quickstart}>
      <div className={styles.grid}>
        <div>
          <h2 className={styles.heading}>
            Up and running
            <br />
            in one command
          </h2>
          <p className={styles.body}>
            Clone the repo, point it at your config, and Fenrir starts serving Nostr events
            immediately — no database setup required.
          </p>
          <Chamfer as="a" href="#" size={10} className={styles.docsButton}>
            Read the Docs &gt;
          </Chamfer>
        </div>
        <Chamfer size={14} className={styles.terminal}>
          <div className={styles.titleBar}>
            <span className={`${styles.dot} ${styles.dotRed}`} />
            <span className={`${styles.dot} ${styles.dotYellow}`} />
            <span className={`${styles.dot} ${styles.dotGreen}`} />
          </div>
          <div className={styles.terminalBody}>
            <div>
              <span className={styles.prompt}>$</span> git clone https://github.com/rushmi0/fenrir-relay
            </div>
            <div>
              <span className={styles.prompt}>$</span> cd fenrir-relay && cp config.example.toml config.toml
            </div>
            <div>
              <span className={styles.prompt}>$</span> cargo run --release
            </div>
            <div className={styles.dim}>&gt; Fenrir relay listening on ws://0.0.0.0:8080</div>
          </div>
        </Chamfer>
      </div>
    </section>
  )
}