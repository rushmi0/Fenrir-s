import { useCallback, useEffect, useRef, useState } from 'react'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import RefreshIcon from '@mui/icons-material/Refresh'
import RepeatIcon from '@mui/icons-material/Repeat'
import type { Event as NostrEvent, NostrSigner } from '@rust-nostr/nostr-sdk'
import { REPOST_KIND, fetchReplies, fetchRootNotes } from '@/features/feed/nostr/client'
import { buildReplyTree, insertReply } from '@/features/feed/nostr/replyTree'
import type { Reply } from '@/features/feed/nostr/replyTree'
import { formatRelativeTime, shortenBech32 } from '@/features/feed/nostr/format'
import NoteContent from '@/features/feed/components/NoteContent'
import PostActions from '@/features/feed/components/PostActions'
import Avatar from '@/features/feed/components/Avatar'
import EmbeddedNote from '@/features/feed/components/EmbeddedNote'
import Chamfer from '@/components/ui/Chamfer'
import styles from './FeedView.module.css'

interface Post {
  id: string
  npub: string
  pubkeyHex: string
  displayName: string
  timestamp: string
  createdAt: number
  text: string
  kind: number
  event?: NostrEvent
}

const FEED_LIMIT = 50

function describeError(err: unknown): string {
  return err instanceof Error ? err.message : 'failed to reach the relay'
}

function toPost(event: NostrEvent): Post {
  const npub = event.author.toBech32()
  return {
    id: event.id.toHex(),
    npub,
    pubkeyHex: event.author.toHex(),
    displayName: shortenBech32(npub),
    timestamp: formatRelativeTime(event.createdAt.asSecs()),
    createdAt: event.createdAt.asSecs(),
    text: event.content,
    kind: event.kind.asU16(),
    event,
  }
}

interface FeedViewProps {
  /** Resolves the current viewer's signer, or null when nobody's signed in - reply/like/repost
   * controls only render when this is non-null (see PostBody/ReplyThread below). Shared by the
   * admin console's /admin/feed (always signed in) and the public /feed page (signed in only once
   * a visitor picks admin or general sign-in). */
  getSigner: (() => Promise<NostrSigner>) | null
  /** Hex pubkey to label the local-only "Broadcast" composer post with, if signed in. */
  composerPubkeyHex?: string
}

/**
 * Feed screen shell - composer, thread list, thread detail. Reads real kind-1/kind-6 notes from
 * this relay's own websocket endpoint (see features/feed/nostr/client.ts). Comment/like/re-note on
 * posts and replies publish real signed events (see features/feed/nostr/publish.ts) via whichever
 * signer `getSigner` resolves - the action row on each post/reply is hidden entirely when
 * `getSigner` is null. The "Broadcast" composer itself still stays local-only - actually publishing
 * a brand-new top-level note is a separate piece of work from replying/reacting to existing ones.
 * Collapses to a single pane under 840px (see FeedView.module.css).
 */
export default function FeedView({ getSigner, composerPubkeyHex }: FeedViewProps) {
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [repliesByPost, setRepliesByPost] = useState<Record<string, Reply[]>>({})
  const [repliesLoading, setRepliesLoading] = useState(false)
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set())
  const [repostedIds, setRepostedIds] = useState<Set<string>>(new Set())
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const loadFeed = useCallback(() => {
    setLoading(true)
    setLoadError(null)
    fetchRootNotes(FEED_LIMIT)
      .then(({ events, hasMore: more }) => {
        const mapped = events.map(toPost)
        setPosts(mapped)
        setHasMore(more)
        setSelectedId((current) => current ?? mapped[0]?.id ?? null)
      })
      .catch((err: unknown) => setLoadError(describeError(err)))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    loadFeed()
  }, [loadFeed])

  const loadMore = useCallback(() => {
    if (loadingMore || !hasMore || posts.length === 0) return
    const until = Math.floor(posts[posts.length - 1].createdAt) - 1
    setLoadingMore(true)
    fetchRootNotes(FEED_LIMIT, until)
      .then(({ events, hasMore: more }) => {
        const mapped = events.map(toPost)
        setPosts((prev) => {
          const seen = new Set(prev.map((p) => p.id))
          return [...prev, ...mapped.filter((p) => !seen.has(p.id))]
        })
        setHasMore(more)
      })
      .catch(() => setHasMore(false))
      .finally(() => setLoadingMore(false))
  }, [loadingMore, hasMore, posts])

  const listRef = useRef<HTMLDivElement | null>(null)
  const sentinelRef = useRef<HTMLDivElement | null>(null)
  const loadMoreRef = useRef(loadMore)
  loadMoreRef.current = loadMore

  // IntersectionObserver instead of an onScroll threshold - fires reliably regardless of scroll
  // speed/event coalescing, which a manual "distance from bottom" check can miss.
  useEffect(() => {
    const root = listRef.current
    const sentinel = sentinelRef.current
    if (!root || !sentinel) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMoreRef.current()
      },
      { root, rootMargin: '200px', threshold: 0 },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (!selectedId || selectedId in repliesByPost) return
    setRepliesLoading(true)
    fetchReplies(selectedId, FEED_LIMIT)
      .then((events) => {
        setRepliesByPost((prev) => ({ ...prev, [selectedId]: buildReplyTree(selectedId, events) }))
      })
      .catch(() => setRepliesByPost((prev) => ({ ...prev, [selectedId]: [] })))
      .finally(() => setRepliesLoading(false))
  }, [selectedId, repliesByPost])

  const selected = posts.find((p) => p.id === selectedId) ?? null
  const selectedReplies = selectedId ? repliesByPost[selectedId] : undefined

  function markLiked(id: string) {
    setLikedIds((prev) => new Set(prev).add(id))
  }

  function markReposted(id: string) {
    setRepostedIds((prev) => new Set(prev).add(id))
  }

  function handleReplyPublished(rootId: string, parentId: string, reply: Reply) {
    setRepliesByPost((prev) => ({ ...prev, [rootId]: insertReply(prev[rootId] ?? [], parentId, reply) }))
  }

  function handlePost() {
    const text = draft.trim()
    if (!text) return
    const post: Post = {
      id: `local-${Date.now()}`,
      npub: composerPubkeyHex ?? 'you',
      pubkeyHex: composerPubkeyHex ?? '',
      displayName: composerPubkeyHex ? `${composerPubkeyHex.slice(0, 8)}…` : 'you',
      timestamp: 'now',
      createdAt: Date.now() / 1000,
      text,
      kind: 1,
    }
    setPosts((prev) => [post, ...prev])
    setSelectedId(post.id)
    setDraft('')
  }

  return (
    <div className={selectedId ? `${styles.layout} ${styles.showDetail}` : styles.layout}>
      <div className={styles.listPane}>
        <Chamfer size={8} className={styles.composer}>
          <div className={styles.composerAvatar} />
          <input
            className={styles.composerInput}
            placeholder="Broadcast to the relay…"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handlePost()
            }}
          />
          <Chamfer as="button" type="button" size={4} className={styles.postButton} disabled={!draft.trim()} onClick={handlePost}>
            Post
          </Chamfer>
          <button type="button" className={styles.refreshButton} aria-label="Reload feed" disabled={loading} onClick={loadFeed}>
            <RefreshIcon className={loading ? `${styles.refreshIcon} ${styles.refreshSpinning}` : styles.refreshIcon} />
          </button>
        </Chamfer>

        <div className={styles.list} ref={listRef}>
          {loading && posts.length === 0 && <p className={styles.status}>Loading notes…</p>}
          {loadError && posts.length === 0 && <p className={styles.errorText}>{loadError}</p>}
          {!loading && !loadError && posts.length === 0 && <p className={styles.status}>No notes on this relay yet.</p>}
          {posts.map((post) => (
            <Chamfer
              key={post.id}
              size={8}
              role="button"
              tabIndex={0}
              className={post.id === selectedId ? `${styles.postCard} ${styles.postCardActive}` : styles.postCard}
              onClick={() => setSelectedId(post.id)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  setSelectedId(post.id)
                }
              }}
            >
              <PostBody
                post={post}
                getSigner={getSigner}
                liked={likedIds.has(post.id)}
                reposted={repostedIds.has(post.id)}
                onLiked={() => markLiked(post.id)}
                onReposted={() => markReposted(post.id)}
                onReplyPublished={(reply) => post.event && handleReplyPublished(post.id, post.id, reply)}
              />
            </Chamfer>
          ))}
          {/* Always mounted (not conditional on hasMore) so the ref is attached from the very
              first render - the observer effect below only runs once, on mount. loadMore() itself
              is the guard against firing when there's nothing left or a fetch is already running. */}
          <div ref={sentinelRef} aria-hidden="true" />
          {loadingMore && <p className={styles.status}>Loading more…</p>}
          {!hasMore && posts.length > 0 && <p className={styles.status}>You&apos;ve reached the end.</p>}
        </div>
      </div>

      <div className={styles.divider} aria-hidden="true" />

      <div className={styles.detailPane}>
        {selected ? (
          <>
            <button type="button" className={styles.back} onClick={() => setSelectedId(null)}>
              <ArrowBackIcon className={styles.backIcon} /> Back
            </button>
            <Chamfer size={8} className={styles.postCard}>
              <PostBody
                post={selected}
                getSigner={getSigner}
                large
                liked={likedIds.has(selected.id)}
                reposted={repostedIds.has(selected.id)}
                onLiked={() => markLiked(selected.id)}
                onReposted={() => markReposted(selected.id)}
                onReplyPublished={(reply) => handleReplyPublished(selected.id, selected.id, reply)}
              />
            </Chamfer>

            <div className={styles.repliesSection}>
              <span className={styles.repliesHeading}>Replies</span>
              {repliesLoading && !selectedReplies && <p className={styles.status}>Loading replies…</p>}
              {selectedReplies && selectedReplies.length === 0 && <p className={styles.status}>No replies yet.</p>}
              {selectedReplies && selectedReplies.length > 0 && selected.event && (
                <div className={styles.repliesList}>
                  {selectedReplies.map((reply) => (
                    <ReplyThread
                      key={reply.id}
                      reply={reply}
                      rootId={selected.id}
                      rootEvent={selected.event!}
                      getSigner={getSigner}
                      likedIds={likedIds}
                      repostedIds={repostedIds}
                      onLiked={markLiked}
                      onReposted={markReposted}
                      onReplyPublished={handleReplyPublished}
                    />
                  ))}
                </div>
              )}
            </div>
          </>
        ) : (
          <p className={styles.empty}>Select a post to read it.</p>
        )}
      </div>
    </div>
  )
}

interface PostBodyProps {
  post: Post
  getSigner: (() => Promise<NostrSigner>) | null
  large?: boolean
  liked: boolean
  reposted: boolean
  onLiked: () => void
  onReposted: () => void
  onReplyPublished: (reply: Reply) => void
}

function PostBody({ post, getSigner, large = false, liked, reposted, onLiked, onReposted, onReplyPublished }: PostBodyProps) {
  const isRepost = post.kind === REPOST_KIND
  const repostTargetId = isRepost ? post.event?.tags.filter('e')[0]?.content() : undefined

  return (
    <div className={large ? `${styles.postBody} ${styles.postBodyLarge}` : styles.postBody}>
      <div className={styles.postHeader}>
        <Avatar pubkeyHex={post.pubkeyHex} seed={post.npub} />
        <span className={styles.author}>{post.displayName}</span>
        <span className={styles.timestamp}>{post.timestamp}</span>
      </div>
      {isRepost ? (
        <>
          <div className={styles.repostLabel}>
            <RepeatIcon className={styles.repostIcon} /> reposted
          </div>
          {repostTargetId ? (
            <EmbeddedNote eventId={repostTargetId} />
          ) : (
            <span className={styles.status}>Repost target unavailable.</span>
          )}
        </>
      ) : (
        <div className={styles.text}>
          <NoteContent text={post.text} />
        </div>
      )}
      {post.event && getSigner && (
        <PostActions
          event={post.event}
          rootEvent={post.event}
          getSigner={getSigner}
          liked={liked}
          reposted={reposted}
          onLiked={onLiked}
          onReposted={onReposted}
          onReplyPublished={onReplyPublished}
        />
      )}
    </div>
  )
}

interface ReplyThreadProps {
  reply: Reply
  rootId: string
  rootEvent: NostrEvent
  getSigner: (() => Promise<NostrSigner>) | null
  replyingTo?: string
  likedIds: Set<string>
  repostedIds: Set<string>
  onLiked: (id: string) => void
  onReposted: (id: string) => void
  onReplyPublished: (rootId: string, parentId: string, reply: Reply) => void
}

function ReplyThread({
  reply,
  rootId,
  rootEvent,
  getSigner,
  replyingTo,
  likedIds,
  repostedIds,
  onLiked,
  onReposted,
  onReplyPublished,
}: ReplyThreadProps) {
  return (
    <div className={styles.replyItem}>
      <div className={styles.replyRow}>
        <Avatar pubkeyHex={reply.event.author.toHex()} seed={reply.author} size="small" />
        <span className={styles.author}>{reply.author}</span>
        {replyingTo && <span className={styles.replyingTo}>→ replying to {replyingTo}</span>}
      </div>
      <div className={styles.text}>
        <NoteContent text={reply.text} />
      </div>
      {getSigner && (
        <PostActions
          event={reply.event}
          rootEvent={rootEvent}
          getSigner={getSigner}
          liked={likedIds.has(reply.id)}
          reposted={repostedIds.has(reply.id)}
          onLiked={() => onLiked(reply.id)}
          onReposted={() => onReposted(reply.id)}
          onReplyPublished={(newReply) => onReplyPublished(rootId, reply.id, newReply)}
        />
      )}

      {reply.replies && reply.replies.length > 0 && (
        <div className={styles.replyChildren}>
          {reply.replies.map((child) => (
            <ReplyThread
              key={child.id}
              reply={child}
              rootId={rootId}
              rootEvent={rootEvent}
              getSigner={getSigner}
              replyingTo={reply.author}
              likedIds={likedIds}
              repostedIds={repostedIds}
              onLiked={onLiked}
              onReposted={onReposted}
              onReplyPublished={onReplyPublished}
            />
          ))}
        </div>
      )}
    </div>
  )
}
