import { Link } from 'react-router-dom'
import notFoundImg from '@/assets/images/fenrir_pixel_art_404-remove-bg.png'
import styles from './NotFoundPage.module.css'

export default function NotFoundPage() {
  return (
    <div className={styles.page}>
      <img
        src={notFoundImg}
        alt="Fenrir pixel-art husky looking confused"
        className={styles.image}
      />
      <p className={styles.code}>404</p>
      <p className={styles.message}>This page doesn't exist.</p>
      <Link to="/" className={styles.link}>
        ← Back to Fenrir
      </Link>
    </div>
  )
}