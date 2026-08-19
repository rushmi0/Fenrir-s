import type { Event as NostrEvent } from '@rust-nostr/nostr-sdk'
import { shortenBech32 } from './format'

export interface Reply {
  id: string
  author: string
  text: string
  event: NostrEvent
  replies?: Reply[]
}

/** NIP-10: prefer the marked "reply" tag (immediate parent), then the marked "root" tag, and fall
 * back to the legacy convention (unmarked `e` tags, last one = immediate parent). */
function immediateParentId(event: NostrEvent, rootId: string): string {
  const eTags = event.tags.filter('e')
  if (eTags.length === 0) return rootId

  const replyTag = eTags.find((t) => t.isReply())
  if (replyTag) return replyTag.content() ?? rootId

  const rootTag = eTags.find((t) => t.isRoot())
  if (rootTag) return rootTag.content() ?? rootId

  return eTags[eTags.length - 1].content() ?? rootId
}

/** Turns a flat set of replies-to-`rootId` into the nested shape the Replies UI renders,
 * threading each reply under its immediate parent when that parent is also in the set. */
export function buildReplyTree(rootId: string, events: NostrEvent[]): Reply[] {
  const byParent = new Map<string, NostrEvent[]>()
  const knownIds = new Set(events.map((e) => e.id.toHex()))

  for (const event of events) {
    const parentId = immediateParentId(event, rootId)
    const key = knownIds.has(parentId) ? parentId : rootId
    const siblings = byParent.get(key) ?? []
    siblings.push(event)
    byParent.set(key, siblings)
  }

  function toReplies(parentId: string): Reply[] {
    const children = byParent.get(parentId) ?? []
    return children
      .sort((a, b) => a.createdAt.asSecs() - b.createdAt.asSecs())
      .map((event) => {
        const id = event.id.toHex()
        return {
          id,
          author: shortenBech32(event.author.toBech32()),
          text: event.content,
          event,
          replies: toReplies(id),
        }
      })
      .map((reply) => (reply.replies && reply.replies.length > 0 ? reply : { ...reply, replies: undefined }))
  }

  return toReplies(rootId)
}

/** Optimistically threads a freshly-published reply under `parentId` (the root post, or any reply
 * already in the tree) without waiting for a refetch. */
export function insertReply(tree: Reply[], parentId: string, newReply: Reply): Reply[] {
  let inserted = false

  function walk(nodes: Reply[]): Reply[] {
    return nodes.map((node) => {
      if (node.id === parentId) {
        inserted = true
        return { ...node, replies: [...(node.replies ?? []), newReply] }
      }
      return node.replies ? { ...node, replies: walk(node.replies) } : node
    })
  }

  const result = walk(tree)
  return inserted ? result : [...tree, newReply]
}
