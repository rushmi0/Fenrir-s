export interface Feature {
    title: string
    desc: string
}

export const NIPS: string[] = [
    'NIP-01 Basic protocol flow',
    'NIP-02 Follow List',
    'NIP-04 Encrypted Direct Message',
    'NIP-09 Event Deletion',
    'NIP-11 Relay Information',
    'NIP-13 Proof of Work',
    'NIP-15 Marketplace',
    'NIP-28 Public Chat',
    'NIP-42 Authentication',
    'NIP-45 Event Counts',
    'NIP-50 Search Capability',
]

export const FEATURES: Feature[] = [
    {
        title: 'Lightweight',
        desc: 'A small-footprint relay built for efficiency and low resource usage.',
    },
    {
        title: 'Own Your Data',
        desc: 'Self-hosted. Your keys, your events, your rules.',
    },
    {
        title: 'JVM Powered',
        desc: 'Battle-tested runtime built for uptime at scale.',
    },
    {
        title: 'Open Source',
        desc: 'MIT licensed. Fork it, ship it, own it.',
    },
]