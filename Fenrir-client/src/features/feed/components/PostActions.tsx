import { useState } from 'react'
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutlineOutlined'
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder'
import FavoriteIcon from '@mui/icons-material/Favorite'
import RepeatIcon from '@mui/icons-material/Repeat'
import type { Event as NostrEvent, NostrSigner } from '@rust-nostr/nostr-sdk'
import { useToast } from '@/components/ui/Toast'
import { publishLike, publishReply, publishRepost } from '@/features/feed/nostr/publish'
import { shortenBech32 } from '@/features/feed/nostr/format'
import type { Reply } from '@/features/feed/nostr/replyTree'
import styles from './PostActions.module.css'

interface PostActionsProps {
  event: NostrEvent
  rootEvent: NostrEvent
  liked: boolean
  reposted: boolean
  onLiked: () => void
  onReposted: () => void
  onReplyPublished: (reply: Reply) => void
  /** Resolves whoever is signing for the current viewer - the Admin Console's SignerContext on
   * /admin/feed, a general NIP-07/nsec identity on the public /feed page. Left as a plain prop
   * (rather than a context this component reads itself) so the same action row works under
   * either signer source without depending on the admin-only context. */
  getSigner: () => Promise<NostrSigner>
}

function describeError(err: unknown): string {
  return err instanceof Error ? err.message : 'something went wrong'
}

/** Comment/re-note/like row shared by post cards and every level of the reply tree - each action
 * signs and broadcasts a real event (NIP-25 reaction, NIP-18 repost, NIP-10 reply) via whichever
 * signer `getSigner` resolves. */
export default function PostActions({
  event,
  rootEvent,
  liked,
  reposted,
  onLiked,
  onReposted,
  onReplyPublished,
  getSigner,
}: PostActionsProps) {
  const { showToast } = useToast()
  const [busyLike, setBusyLike] = useState(false)
  const [busyRepost, setBusyRepost] = useState(false)
  const [composerOpen, setComposerOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [busyReply, setBusyReply] = useState(false)

  async function handleLike() {
    if (liked || busyLike) return
    setBusyLike(true)
    try {
      const signer = await getSigner()
      await publishLike(signer, event)
      onLiked()
    } catch (err) {
      showToast('error', describeError(err))
    } finally {
      setBusyLike(false)
    }
  }

  async function handleRepost() {
    if (reposted || busyRepost) return
    setBusyRepost(true)
    try {
      const signer = await getSigner()
      await publishRepost(signer, event)
      onReposted()
      showToast('success', 'Reposted')
    } catch (err) {
      showToast('error', describeError(err))
    } finally {
      setBusyRepost(false)
    }
  }

  async function handleReplySubmit() {
    const text = draft.trim()
    if (!text || busyReply) return
    setBusyReply(true)
    try {
      const signer = await getSigner()
      const signed = await publishReply(signer, event, rootEvent, text)
      onReplyPublished({
        id: signed.id.toHex(),
        author: shortenBech32(signed.author.toBech32()),
        text: signed.content,
        event: signed,
      })
      setDraft('')
      setComposerOpen(false)
      showToast('success', 'Reply posted')
    } catch (err) {
      showToast('error', describeError(err))
    } finally {
      setBusyReply(false)
    }
  }

  return (
    <div className={styles.wrap} onClick={(e) => e.stopPropagation()}>
      <div className={styles.row}>
        <button
          type="button"
          className={composerOpen ? `${styles.action} ${styles.actionActive}` : styles.action}
          onClick={() => setComposerOpen((v) => !v)}
          aria-label="Reply"
        >
          <ChatBubbleOutlineIcon className={styles.icon} />
        </button>
        <button
          type="button"
          className={reposted ? `${styles.action} ${styles.actionActive}` : styles.action}
          disabled={busyRepost || reposted}
          onClick={() => void handleRepost()}
          aria-label="Re-note"
        >
          <RepeatIcon className={styles.icon} />
        </button>
        <button
          type="button"
          className={liked ? `${styles.action} ${styles.actionActive}` : styles.action}
          disabled={busyLike || liked}
          onClick={() => void handleLike()}
          aria-label="Like"
        >
          {liked ? <FavoriteIcon className={styles.icon} /> : <FavoriteBorderIcon className={styles.icon} />}
        </button>
      </div>

      {composerOpen && (
        <div className={styles.composer}>
          <input
            className={styles.composerInput}
            placeholder="Write a reply…"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void handleReplySubmit()
            }}
            disabled={busyReply}
            autoFocus
          />
          <button
            type="button"
            className={styles.composerSend}
            disabled={!draft.trim() || busyReply}
            onClick={() => void handleReplySubmit()}
          >
            {busyReply ? '…' : 'Reply'}
          </button>
        </div>
      )}
    </div>
  )
}
