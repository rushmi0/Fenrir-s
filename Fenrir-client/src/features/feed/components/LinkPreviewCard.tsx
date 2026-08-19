import { useEffect, useState } from 'react'
import LanguageIcon from '@mui/icons-material/Language'
import { useAdminSession } from '@/features/admin/context/AdminSessionContext'
import { getLinkPreview } from '@/features/feed/nostr/linkPreview'
import type { LinkPreview } from '@/features/admin/api/adminApi'
import styles from './LinkPreviewCard.module.css'

interface LinkPreviewCardProps {
  url: string
}

function hostnameOf(url: string): string {
  try {
    return new URL(url).hostname
  } catch {
    return url
  }
}

/**
 * Title/description/image card for an external URL found in note content - the backend does the
 * actual fetching (see features/feed/nostr/linkPreview.ts), since browsers can't read cross-origin
 * page HTML themselves. Renders nothing while loading or if the page had no usable preview data -
 * the plain link text NoteContent already renders inline covers that case either way.
 */
export default function LinkPreviewCard({ url }: LinkPreviewCardProps) {
  const { session } = useAdminSession()
  const [preview, setPreview] = useState<LinkPreview | null>(null)

  useEffect(() => {
    if (!session) return
    let cancelled = false
    getLinkPreview(session.token, url).then((result) => {
      if (!cancelled) setPreview(result)
    })
    return () => {
      cancelled = true
    }
  }, [session, url])

  if (!preview || (!preview.title && !preview.description && !preview.image)) return null

  return (
    <a className={styles.card} href={preview.url} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}>
      {preview.image && <img className={styles.image} src={preview.image} alt="" loading="lazy" />}
      <div className={styles.body}>
        <span className={styles.host}>
          <LanguageIcon className={styles.hostIcon} />
          {hostnameOf(preview.url)}
        </span>
        {preview.title && <span className={styles.title}>{preview.title}</span>}
        {preview.description && <span className={styles.description}>{preview.description}</span>}
      </div>
    </a>
  )
}
