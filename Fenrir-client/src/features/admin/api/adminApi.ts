const JSON_HEADERS = { 'Content-Type': 'application/json' }

export type SystemState = 'INITIAL_SETUP' | 'READY'

export interface SystemStatus {
  state: SystemState
  relayName: string
}

export interface ChallengeResponse {
  challenge: string
}

export interface LoginResponse {
  token: string
  pubkey: string
  role: string
}

export interface AdminIdentity {
  pubkey: string
  role: string
}

export interface RelayConfig {
  name: string
  description: string
  npub: string
  contact: string
  relayUrl: string
}

export interface SystemConfig {
  maxFilters: number
  maxLimit: number
  backupEnabled: boolean
  sync: string
}

export interface SecurityPolicy {
  allPass: boolean
  followsPass: boolean
  powEnabled: boolean
  minDifficulty: number
  authEnabled: boolean
  authWhitelistPubkeys: string
}

export interface OperatorDto {
  pubkey: string
  role: string
  createdAt: number
}

export interface LinkPreview {
  url: string
  title: string | null
  description: string | null
  image: string | null
}

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = res.statusText
    try {
      const body: unknown = await res.json()
      if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
        message = body.message
      }
    } catch {
      // response had no JSON body - fall back to statusText
    }
    throw new ApiError(message, res.status)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** Dispatched whenever a bearer-authenticated request comes back 401 - AdminAuthFilter on the
 * backend returns 401 for a missing, invalid, or expired session token (never for anything else),
 * so this always means "force a re-login." AdminSessionProvider listens for it. */
export const SESSION_EXPIRED_EVENT = 'fenrir:session-expired'

/** Same as `handle`, but for endpoints that carry a Bearer token (see `authHeaders`). */
async function handleAuthed<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
  }
  return handle<T>(res)
}

function authHeaders(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` }
}

export const systemApi = {
  status: (): Promise<SystemStatus> => fetch('/inter/api/v1/system/status').then((r) => handle(r)),
}

export const authApi = {
  challenge: (): Promise<ChallengeResponse> => fetch('/inter/api/v1/auth/challenge').then((r) => handle(r)),

  login: (rawEvent: string): Promise<LoginResponse> =>
    fetch('/inter/api/v1/auth/login', { method: 'POST', headers: JSON_HEADERS, body: rawEvent }).then((r) =>
      handle(r),
    ),

  logout: (token: string): Promise<void> =>
    fetch('/inter/api/v1/auth/logout', { method: 'POST', headers: authHeaders(token) }).then((r) => handleAuthed(r)),
}

export const setupApi = {
  challenge: (setupToken: string): Promise<ChallengeResponse> =>
    fetch('/inter/api/v1/setup/challenge', { headers: { 'X-Setup-Token': setupToken } }).then((r) => handle(r)),

  complete: (setupToken: string, rawEvent: string): Promise<LoginResponse> =>
    fetch('/inter/api/v1/setup/complete', {
      method: 'POST',
      headers: { ...JSON_HEADERS, 'X-Setup-Token': setupToken },
      body: rawEvent,
    }).then((r) => handle(r)),
}

export const adminApi = {
  me: (token: string): Promise<AdminIdentity> =>
    fetch('/inter/api/v1/admin/me', { headers: authHeaders(token) }).then((r) => handleAuthed(r)),

  getRelayConfig: (token: string): Promise<RelayConfig> =>
    fetch('/inter/api/v1/admin/config/relay', { headers: authHeaders(token) }).then((r) => handleAuthed(r)),

  putRelayConfig: (token: string, body: RelayConfig): Promise<RelayConfig> =>
    fetch('/inter/api/v1/admin/config/relay', {
      method: 'PUT',
      headers: { ...JSON_HEADERS, ...authHeaders(token) },
      body: JSON.stringify(body),
    }).then((r) => handleAuthed(r)),

  getSystemConfig: (token: string): Promise<SystemConfig> =>
    fetch('/inter/api/v1/admin/config/system', { headers: authHeaders(token) }).then((r) => handleAuthed(r)),

  putSystemConfig: (token: string, body: SystemConfig): Promise<SystemConfig> =>
    fetch('/inter/api/v1/admin/config/system', {
      method: 'PUT',
      headers: { ...JSON_HEADERS, ...authHeaders(token) },
      body: JSON.stringify(body),
    }).then((r) => handleAuthed(r)),

  getPolicy: (token: string): Promise<SecurityPolicy> =>
    fetch('/inter/api/v1/admin/policy', { headers: authHeaders(token) }).then((r) => handleAuthed(r)),

  putPolicy: (token: string, body: SecurityPolicy): Promise<SecurityPolicy> =>
    fetch('/inter/api/v1/admin/policy', {
      method: 'PUT',
      headers: { ...JSON_HEADERS, ...authHeaders(token) },
      body: JSON.stringify(body),
    }).then((r) => handleAuthed(r)),

  listOperators: (token: string): Promise<OperatorDto[]> =>
    fetch('/inter/api/v1/admin/operators', { headers: authHeaders(token) }).then((r) => handleAuthed(r)),

  addOperator: (token: string, body: { pubkey: string; role: string }): Promise<OperatorDto> =>
    fetch('/inter/api/v1/admin/operators', {
      method: 'POST',
      headers: { ...JSON_HEADERS, ...authHeaders(token) },
      body: JSON.stringify(body),
    }).then((r) => handleAuthed(r)),

  removeOperator: (token: string, pubkey: string): Promise<void> =>
    fetch(`/inter/api/v1/admin/operators/${encodeURIComponent(pubkey)}`, {
      method: 'DELETE',
      headers: authHeaders(token),
    }).then((r) => handleAuthed(r)),

  linkPreview: (token: string, url: string): Promise<LinkPreview> =>
    fetch(`/inter/api/v1/admin/preview?url=${encodeURIComponent(url)}`, { headers: authHeaders(token) }).then((r) =>
      handleAuthed(r),
    ),
}
