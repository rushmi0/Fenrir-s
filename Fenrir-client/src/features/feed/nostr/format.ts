/** `npub1ujevvncwfe22...sgze8ry` -> `npub1ujev…ze8ry`, for any bech32 id (npub/note/nevent/...). */
export function shortenBech32(id: string): string {
  if (id.length <= 20) return id
  return `${id.slice(0, 10)}…${id.slice(-6)}`
}

/** Relative time string ("now", "5m", "3h", "2d") for a unix-seconds timestamp, falling back to a
 * plain date once it's more than a week old. */
export function formatRelativeTime(unixSecs: number): string {
  const diffSecs = Math.max(0, Date.now() / 1000 - unixSecs)
  if (diffSecs < 60) return 'now'
  if (diffSecs < 3600) return `${Math.floor(diffSecs / 60)}m`
  if (diffSecs < 86400) return `${Math.floor(diffSecs / 3600)}h`
  if (diffSecs < 86400 * 7) return `${Math.floor(diffSecs / 86400)}d`
  return new Date(unixSecs * 1000).toLocaleDateString()
}
