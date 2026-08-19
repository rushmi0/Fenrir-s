import { useEffect, useState } from 'react'
import type { Event as NostrEvent } from '@rust-nostr/nostr-sdk'
import { fetchEventById } from '@/features/feed/nostr/client'
import { formatRelativeTime, shortenBech32 } from '@/features/feed/nostr/format'
import Avatar from './Avatar'
import NoteContent from './NoteContent'
import styles from './EmbeddedNote.module.css'

interface EmbeddedNoteProps {
  /** Event id in any form fetchEventById accepts - hex, note1/nevent1 bech32, or a nostr: uri. */
  eventId: string
}

/** Compact "note in the note" card for a repost (NIP-18) or an inline note/nevent mention -
 * resolves and renders the quoted event's own author/content. `allowEmbeds={false}` on the nested
 * NoteContent stops a quoted note that itself quotes another note from recursing. */
export default function EmbeddedNote({ eventId }: EmbeddedNoteProps) {
  const [event, setEvent] = useState<NostrEvent | null | undefined>(undefined)

  useEffect(() => {
    setEvent(undefined)
    let cancelled = false
    fetchEventById(eventId).then((result) => {
      if (!cancelled) setEvent(result)
    })
    return () => {
      cancelled = true
    }
  }, [eventId])

  if (event === undefined) {
    return (
      <div className={styles.card}>
        <span className={styles.status}>Loading note…</span>
      </div>
    )
  }

  if (event === null) {
    return (
      <div className={styles.card}>
        <span className={styles.status}>Note unavailable.</span>
      </div>
    )
  }

  const npub = event.author.toBech32()

  return (
    <div className={styles.card} onClick={(e) => e.stopPropagation()}>
      <div className={styles.header}>
        <Avatar pubkeyHex={event.author.toHex()} seed={npub} size="small" />
        <span className={styles.author}>{shortenBech32(npub)}</span>
        <span className={styles.timestamp}>{formatRelativeTime(event.createdAt.asSecs())}</span>
      </div>
      <div className={styles.text}>
        <NoteContent text={event.content} allowEmbeds={false} />
      </div>
    </div>
  )
}
