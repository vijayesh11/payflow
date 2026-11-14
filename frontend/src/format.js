export const CURRENCY = 'INR'

export const money = (n, currency = CURRENCY) =>
  Number(n || 0).toLocaleString('en-IN', { style: 'currency', currency })

export const dateTime = (iso) => iso ? new Date(iso).toLocaleString(undefined, {
  day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit'
}) : '—'

export const dateOnly = (iso) => iso ? new Date(iso).toLocaleDateString(undefined, {
  day: 'numeric', month: 'short', year: 'numeric'
}) : '—'

export const timeOnly = (iso) => iso ? new Date(iso).toLocaleTimeString(undefined, {
  hour: 'numeric', minute: '2-digit'
}) : '—'

export function relative(iso) {
  if (!iso) return 'never'
  const s = (Date.now() - new Date(iso).getTime()) / 1000
  if (s < 60) return 'just now'
  if (s < 3600) return `${Math.floor(s / 60)} min ago`
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`
  return dateOnly(iso)
}

export const initials = (name = '') =>
  name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?'

/** "Mozilla/5.0 (Windows NT 10.0...) Chrome/..." -> "Chrome on Windows" */
export function device(ua) {
  if (!ua) return 'Unknown device'
  if (/python|curl|java|okhttp/i.test(ua)) return 'API client'
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox'
    : /Safari\//.test(ua) ? 'Safari' : 'Browser'
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS'
    : /Mac OS X/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : 'Unknown OS'
  return `${browser} on ${os}`
}

export const ACTION_LABELS = {
  REGISTER: 'Account created',
  LOGIN_SUCCESS: 'Signed in',
  LOGIN_FAILED: 'Failed sign-in attempt',
  LOGIN_BLOCKED: 'Sign-in blocked',
  ACCOUNT_LOCKED: 'Account locked',
  PASSWORD_CHANGED: 'Password changed',
  PASSWORD_CHANGE_FAILED: 'Password change failed',
  CARD_LINKED: 'Card linked',
  CARD_REMOVED: 'Card removed',
  MONEY_ADDED: 'Money added',
  ALERT_RESOLVED: 'Alert resolved',
  USER_FROZEN: 'User frozen',
  USER_UNFROZEN: 'User unfrozen',
  USER_UNLOCKED: 'User unlocked',
  OTP_SENT: 'Verification code sent',
  OTP_FAILED: 'Wrong verification code',
  PIN_SET: 'UPI PIN set',
  PIN_RESET: 'UPI PIN reset',
  PIN_FAILED: 'Wrong UPI PIN',
  PIN_LOCKED: 'UPI PIN locked',
  TWO_FACTOR_ON: '2-step verification on',
  TWO_FACTOR_OFF: '2-step verification off',
  HOLD_RELEASED: 'Held money released',
  APPEAL_SUBMITTED: 'Appeal submitted',
  APPEAL_APPROVED: 'Appeal approved',
  APPEAL_REJECTED: 'Appeal rejected',
  PAYMENT_RELEASED: 'Payment released after review',
  ESCROW_CREATED: 'Escrow contract created',
  ESCROW_FUNDED: 'Escrow milestone funded',
  ESCROW_RELEASED: 'Escrow payout received',
  ESCROW_REFUNDED: 'Escrow refund',
  ESCROW_DISPUTED: 'Escrow dispute opened',
  DISPUTE_RESOLVED: 'Escrow dispute resolved',
  INVOICE_CREATED: 'Invoice created',
  INVOICE_PAID: 'Invoice paid'
}

export function toCsv(rows) {
  const esc = (v) => {
    const s = v == null ? '' : String(v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  return rows.map((r) => r.map(esc).join(',')).join('\n')
}

export function download(filename, text, type = 'text/csv') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
