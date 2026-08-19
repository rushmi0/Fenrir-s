import { useEffect, useState } from 'react'
import { getProfile } from '@/features/feed/nostr/profile'
import styles from './Avatar.module.css'

interface AvatarProps {
  /** Hex pubkey to look up a profile picture for. */
  pubkeyHex: string
  /** Any stable per-author string (npub works well) - picks the fallback color swatch when the
   * account has no picture set, or its metadata hasn't loaded/doesn't exist. */
  seed: string
  size?: 'default' | 'small'
}

const AVATAR_COLORS = ['var(--fenrir-accent)', 'var(--fenrir-green)', 'var(--fenrir-red)', 'var(--fenrir-amber)', '#4b3fa8']

/** Deterministic swatch color per author, so the same npub always renders the same fallback. */
function avatarColor(key: string): string {
  let hash = 0
  for (let i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) >>> 0
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

/** Real profile picture (kind-0 `picture` field) when the account has one, falling back to a
 * generated color swatch - while loading, on accounts with no metadata, and if the picture URL
 * itself fails to load. */
export default function Avatar({ pubkeyHex, seed, size = 'default' }: AvatarProps) {
  const [picture, setPicture] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    setPicture(null)
    setFailed(false)
    let cancelled = false
    getProfile(pubkeyHex).then((profile) => {
      if (!cancelled && profile?.picture) setPicture(profile.picture)
    })
    return () => {
      cancelled = true
    }
  }, [pubkeyHex])

  const className = size === 'small' ? styles.avatarSmall : styles.avatar

  if (picture && !failed) {
    return <img className={className} src={picture} alt="" loading="lazy" onError={() => setFailed(true)} />
  }
  return <div className={className} style={{ background: avatarColor(seed) }} />
}
