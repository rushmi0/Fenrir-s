import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { SESSION_EXPIRED_EVENT, adminApi } from '@/features/admin/api/adminApi'
import { useToast } from '@/components/ui/Toast'

export interface AdminSession {
  token: string
  pubkey: string
  role: string
}

interface AdminSessionContextValue {
  session: AdminSession | null
  loading: boolean
  setSession: (session: AdminSession) => void
  clearSession: () => void
}

const STORAGE_KEY = 'fenrir.admin.session'

const AdminSessionContext = createContext<AdminSessionContextValue | undefined>(undefined)

function readStoredSession(): AdminSession | null {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AdminSession
  } catch {
    return null
  }
}

/**
 * Holds the Admin Console's HTTP bearer session (see AdminSessionStore on the backend) in
 * sessionStorage - deliberately not localStorage, so a closed browser/tab doesn't leave a
 * long-lived admin token lying around. Rehydrates role/pubkey via /admin/me on load so a page
 * refresh doesn't require re-signing a login event.
 *
 * Also listens for SESSION_EXPIRED_EVENT (adminApi.ts dispatches it on any 401 from a
 * bearer-authenticated endpoint) so a token that goes invalid/expires mid-session - not just on
 * page load - clears itself and drops the user back to the login screen. AppShell is what
 * actually does the redirect, by watching `session` for null.
 */
export function AdminSessionProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<AdminSession | null>(() => readStoredSession())
  const [loading, setLoading] = useState(true)
  const { showToast } = useToast()

  useEffect(() => {
    const stored = readStoredSession()
    if (!stored) {
      setLoading(false)
      return
    }
    adminApi
      .me(stored.token)
      .then((identity) => setSessionState({ token: stored.token, pubkey: identity.pubkey, role: identity.role }))
      .catch(() => {
        sessionStorage.removeItem(STORAGE_KEY)
        setSessionState(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const setSession = useCallback((next: AdminSession) => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setSessionState(next)
  }, [])

  const clearSession = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    setSessionState(null)
  }, [])

  useEffect(() => {
    function handleSessionExpired() {
      clearSession()
      showToast('error', 'session is invalid or has expired - please log in again')
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
  }, [clearSession, showToast])

  const value = useMemo(
    () => ({ session, loading, setSession, clearSession }),
    [session, loading, setSession, clearSession],
  )

  return <AdminSessionContext.Provider value={value}>{children}</AdminSessionContext.Provider>
}

export function useAdminSession(): AdminSessionContextValue {
  const ctx = useContext(AdminSessionContext)
  if (!ctx) throw new Error('useAdminSession must be used within AdminSessionProvider')
  return ctx
}
