// API client: attaches the JWT, keeps the short-lived session alive while the user is active,
// and signs the user out cleanly when the session ends.
const KEY = 'payflow_session'
const API_BASE = (import.meta.env && import.meta.env.VITE_API_URL) || ''

export const IDLE_LIMIT_MS = 15 * 60 * 1000      // PCI-DSS 8.2.8: re-authenticate after 15 min idle
export const IDLE_WARNING_MS = 13 * 60 * 1000

export function getSession() {
  try { return JSON.parse(localStorage.getItem(KEY)) } catch { return null }
}
export function saveSession(s) {
  try { localStorage.setItem(KEY, JSON.stringify(s)) } catch { /* storage unavailable */ }
}
export function clearSession() {
  try { localStorage.removeItem(KEY) } catch { /* ignore */ }
}

/** Seconds until the token expires (decoded from the JWT payload; signature is checked by the server). */
export function tokenSecondsLeft(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload.exp - Date.now() / 1000
  } catch { return 0 }
}

function forceLogout(reason) {
  clearSession()
  window.dispatchEvent(new CustomEvent('payflow:logout', { detail: reason }))
}

export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const session = getSession()
  let res
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
        ...headers
      },
      body: body ? JSON.stringify(body) : undefined
    })
  } catch {
    const err = new Error('Network error — check your connection and try again.')
    err.network = true
    throw err
  }
  if (res.status === 401 && session?.token && !path.startsWith('/api/auth/')) {
    forceLogout('Your session has expired. Please sign in again.')
    throw new Error('Session expired')
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => ({}))
  // 403 with status BLOCKED = the fraud engine stopped a transfer; the UI shows the reason
  if (!res.ok && !(res.status === 403 && data.status === 'BLOCKED')) {
    const err = new Error(data.error || `Request failed (${res.status})`)
    err.status = res.status
    throw err
  }
  return data
}

/** Swap in a fresh token if the current one expires within 5 minutes. */
export async function refreshIfNeeded() {
  const s = getSession()
  if (!s?.token || tokenSecondsLeft(s.token) > 5 * 60) return
  try {
    const fresh = await api('/api/me/refresh-token', { method: 'POST' })
    saveSession({ ...s, token: fresh.token })
  } catch { /* api() already handles expiry */ }
}
