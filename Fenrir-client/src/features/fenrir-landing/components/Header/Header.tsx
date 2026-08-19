import Chamfer from '@/components/ui/Chamfer'
import styles from './Header.module.css'

export default function Header() {
  return (
    <header className={styles.header}>
      <div className={styles.brand}>
        <Chamfer size={6} className={styles.logoMark} />
        <span className={styles.wordmark}>FENRIR RELAY</span>
      </div>
      <nav className={styles.nav}>
        <a className={styles.navLink} href="#nips">
          NIPs
        </a>
        <a className={styles.navLink} href="#features">
          Features
        </a>
        <a className={styles.navLink} href="#quickstart">
          Quick Start
        </a>
        <a
          className={styles.navLink}
          href="https://github.com/rushmi0/Fenrir-s"
          target="_blank"
          rel="noreferrer"
        >
          GitHub
        </a>
      </nav>
    </header>
  )
}