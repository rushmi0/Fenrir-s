import styles from './Icon.module.css'

interface IconProps {
  name: string
  filled?: boolean
  size?: number
  className?: string
}

/** Renders a Google Material Symbol by its icon name. */
export default function Icon({ name, filled = false, size, className = '' }: IconProps) {
  return (
    <span
      className={`material-symbols-outlined ${styles.icon} ${filled ? styles.filled : ''} ${className}`}
      style={size ? { fontSize: size } : undefined}
      aria-hidden="true"
    >
      {name}
    </span>
  )
}