import type { CSSProperties } from 'react'
import { Header, Hero, NipMarquee, Features, QuickStart, CtaBand, Footer } from '@/features/fenrir-landing'
import styles from './LandingPage.module.css'

export interface LandingPageProps {
  accentColor?: string
  marqueeSpeed?: number
  showTerminal?: boolean
}

export default function LandingPage({
  accentColor = '#7c5cff',
  marqueeSpeed = 28,
  showTerminal = false,
}: LandingPageProps) {
  const themeVars = { '--fenrir-accent': accentColor } as CSSProperties

  return (
    <div className={styles.page} style={themeVars}>
      <Header />
      <Hero />
      <NipMarquee speed={marqueeSpeed} />
      <Features />
      {showTerminal && <QuickStart />}
      <CtaBand />
      <Footer />
    </div>
  )
}