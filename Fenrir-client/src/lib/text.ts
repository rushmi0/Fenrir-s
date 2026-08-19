/** Trim `text` to `max` chars on a word boundary, appending an ellipsis when cut — for social-preview descriptions. */
export function truncate(text: string, max = 200): string {
    const collapsed = text.replace(/\s+/g, ' ').trim()
    if (collapsed.length <= max) return collapsed
    const cut = collapsed.slice(0, max)
    const lastSpace = cut.lastIndexOf(' ')
    return `${cut.slice(0, lastSpace > 0 ? lastSpace : max)}…`
}