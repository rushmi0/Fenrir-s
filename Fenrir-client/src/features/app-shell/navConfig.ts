export interface NavEntry {
  key: string
  /** Label shown in the nav drawer list. */
  label: string
  path: string
  icon: string
  /** Short colored badge shown in the top bar for the active page (e.g. "FN"). */
  code: string
  /** CSS color for the badge background. */
  codeColor: string
  /** Top bar heading when this page is active. */
  title: string
  /** Top bar breadcrumb subtitle when this page is active. */
  subtitle: string
}

/** Data-driven nav list for the drawer + top bar - add a page by adding one entry here. */
export const navConfig: NavEntry[] = [
  {
    key: 'feed',
    label: 'Feed',
    path: '/feed',
    icon: 'home',
    code: 'FN',
    codeColor: 'var(--fenrir-accent)',
    title: 'Fenrir Relay',
    subtitle: 'Feed · latest posts',
  },
  {
    key: 'console',
    label: 'Admin Console',
    path: '/admin/console',
    icon: 'shield',
    code: 'RS',
    codeColor: 'var(--fenrir-red)',
    title: 'Relay Settings',
    subtitle: 'Configuration · policy · roles',
  },
  {
    key: 'counter',
    label: 'Counter',
    path: '/admin/counter',
    icon: 'sell',
    code: 'C',
    codeColor: 'var(--fenrir-green)',
    title: 'Counter',
    subtitle: 'Inventory · stock count',
  },
]
