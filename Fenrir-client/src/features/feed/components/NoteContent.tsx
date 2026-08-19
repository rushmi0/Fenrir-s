import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import AlternateEmailIcon from '@mui/icons-material/AlternateEmail'
import { shortenBech32 } from '@/features/feed/nostr/format'
import LinkPreviewCard from './LinkPreviewCard'
import EmbeddedNote from './EmbeddedNote'
import styles from './NoteContent.module.css'

interface NoteContentProps {
  text: string
  /** Set false to render `nostr:note1…`/`nostr:nevent1…` mentions as plain chips instead of
   * fetching and embedding the quoted note - used by EmbeddedNote itself so a quoted note that
   * quotes another note doesn't recurse. Defaults to true. */
  allowEmbeds?: boolean
}

const TOKEN_RE = /(https?:\/\/\S+|nostr:[a-z0-9]+|#[a-zA-Z0-9_]+)/g
const IMAGE_RE = /\.(?:jpg|jpeg|png|gif|webp|avif)(?:[?#].*)?$/i
const VIDEO_RE = /\.(?:mp4|webm|mov|m3u8|ogv)(?:[?#].*)?$/i
const NOTE_MENTION_RE = /^nostr:(?:note1|nevent1)/i

/**
 * Renders raw kind-1 `content` the way a Nostr note viewer does (see web.nostr.technology): plain
 * text passes through untouched, image URLs become inline embeds, video URLs become an inline
 * player, other URLs become links (plus a fetched preview card for the first one - see
 * LinkPreviewCard), `nostr:note1…`/`nostr:nevent1…` mentions embed the quoted note itself (see
 * EmbeddedNote), and other `nostr:` URIs (npub/nprofile/...) become plain mention chips.
 */
export default function NoteContent({ text, allowEmbeds = true }: NoteContentProps) {
  const parts = text.split(TOKEN_RE)
  const previewUrl = parts.find(
    (part) => part && /^https?:\/\//i.test(part) && !IMAGE_RE.test(part) && !VIDEO_RE.test(part),
  )

  return (
    <div className={styles.content}>
      {parts.map((part, i) => {
        if (!part) return null

        if (/^https?:\/\//i.test(part)) {
          if (IMAGE_RE.test(part)) {
            return <img key={i} className={styles.embedImage} src={part} alt="" loading="lazy" />
          }
          if (VIDEO_RE.test(part)) {
            return (
              <video
                key={i}
                className={styles.embedVideo}
                src={part}
                controls
                preload="metadata"
                onClick={(e) => e.stopPropagation()}
              />
            )
          }
          return (
            <a
              key={i}
              className={styles.link}
              href={part}
              target="_blank"
              rel="noreferrer"
              onClick={(e) => e.stopPropagation()}
            >
              <OpenInNewIcon className={styles.icon} />
              {part}
            </a>
          )
        }

        if (/^nostr:/i.test(part)) {
          if (allowEmbeds && NOTE_MENTION_RE.test(part)) {
            return <EmbeddedNote key={i} eventId={part} />
          }
          return (
            <span key={i} className={styles.mention}>
              <AlternateEmailIcon className={styles.icon} />
              {shortenBech32(part.slice('nostr:'.length))}
            </span>
          )
        }

        if (/^#\w/.test(part)) {
          return (
            <span key={i} className={styles.hashtag}>
              {part}
            </span>
          )
        }

        return part
      })}
      {previewUrl && <LinkPreviewCard url={previewUrl} />}
    </div>
  )
}
