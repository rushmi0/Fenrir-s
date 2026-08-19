import {useEffect} from 'react'

export interface SocialMeta {
    title: string
    description: string
    image?: string
    url?: string
    type?: 'website' | 'article' | 'profile'
}

function upsertMeta(attr: 'property' | 'name', key: string, content: string) {
    let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`)
    if (!el) {
        el = document.createElement('meta')
        el.setAttribute(attr, key)
        document.head.appendChild(el)
    }
    el.setAttribute('content', content)
}

function upsertCanonical(href: string) {
    let el = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
    if (!el) {
        el = document.createElement('link')
        el.setAttribute('rel', 'canonical')
        document.head.appendChild(el)
    }
    el.setAttribute('href', href)
}

function readMeta(attr: 'property' | 'name', key: string): string {
    return document.head.querySelector(`meta[${attr}="${key}"]`)?.getAttribute('content') ?? ''
}

/**
 * Sets document title + Open Graph/Twitter Card meta tags for the lifetime of the calling
 * page, restoring the site-wide defaults declared in index.html on unmount.
 *
 * Caveat: this only reaches clients that execute JS (browser tabs, some in-app chat
 * previewers). Twitter/X, Discord, Telegram, Slack and Facebook's unfurl bots fetch raw
 * HTML and do not run JS, so they will only ever see the static defaults from index.html —
 * not the per-note/profile title, description, or image set here. Real per-page previews on
 * those platforms require a server/edge prerender step that serves bots fully-formed HTML.
 */
export function useSocialMeta(meta: SocialMeta | null) {
    useEffect(() => {
        if (!meta) return

        const defaults = {
            title: document.title,
            ogTitle: readMeta('property', 'og:title'),
            ogDescription: readMeta('property', 'og:description'),
            ogImage: readMeta('property', 'og:image'),
            ogUrl: readMeta('property', 'og:url'),
            ogType: readMeta('property', 'og:type') || 'website',
            twitterTitle: readMeta('name', 'twitter:title'),
            twitterDescription: readMeta('name', 'twitter:description'),
            twitterImage: readMeta('name', 'twitter:image'),
            canonical: document.head.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? '',
        }

        const url = meta.url ?? window.location.href

        document.title = meta.title
        upsertMeta('property', 'og:title', meta.title)
        upsertMeta('property', 'og:description', meta.description)
        upsertMeta('property', 'og:type', meta.type ?? 'website')
        upsertMeta('property', 'og:url', url)
        upsertMeta('name', 'twitter:title', meta.title)
        upsertMeta('name', 'twitter:description', meta.description)
        upsertCanonical(url)
        if (meta.image) {
            upsertMeta('property', 'og:image', meta.image)
            upsertMeta('name', 'twitter:image', meta.image)
        }

        return () => {
            document.title = defaults.title
            upsertMeta('property', 'og:title', defaults.ogTitle)
            upsertMeta('property', 'og:description', defaults.ogDescription)
            upsertMeta('property', 'og:type', defaults.ogType)
            upsertMeta('property', 'og:url', defaults.ogUrl)
            upsertMeta('name', 'twitter:title', defaults.twitterTitle)
            upsertMeta('name', 'twitter:description', defaults.twitterDescription)
            upsertCanonical(defaults.canonical)
            if (defaults.ogImage) upsertMeta('property', 'og:image', defaults.ogImage)
            if (defaults.twitterImage) upsertMeta('name', 'twitter:image', defaults.twitterImage)
        }
    }, [meta])
}