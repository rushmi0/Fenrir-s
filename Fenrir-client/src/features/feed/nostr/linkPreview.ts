 import { adminApi } from '@/features/admin/api/adminApi'
import type { LinkPreview } from '@/features/admin/api/adminApi'

const cache = new Map<string, LinkPreview | null>()
const inFlight = new Map<string, Promise<LinkPreview | null>>()

/** Fetches (and caches, for this tab's lifetime) a link preview for `url` via the backend's
 * unfurl endpoint - browsers can't read cross-origin page HTML themselves, see
 * LinkPreviewController on the backend. Failures cache as `null` too, so a broken link doesn't
 * get retried every time its card scrolls back into view. */
export function getLinkPreview(token: string, url: string): Promise<LinkPreview | null> {
  if (cache.has(url)) return Promise.resolve(cache.get(url) ?? null)

  const existing = inFlight.get(url)
  if (existing) return existing

  const promise = adminApi
    .linkPreview(token, url)
    .then((preview) => {
      cache.set(url, preview)
      return preview
    })
    .catch(() => {
      cache.set(url, null)
      return null
    })
    .finally(() => {
      inFlight.delete(url)
    })

  inFlight.set(url, promise)
  return promise
}
