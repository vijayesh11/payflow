import { Component, createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { initials, money } from './format'

// ---------- icons (simple stroke icons, no dependency) ----------
const PATHS = {
  home: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z',
  send: 'M22 2 11 13M22 2l-7 20-4-9-9-4z',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  card: 'M2 7a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2zM2 10h20M6 15h4',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  users: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  alert: 'M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01',
  activity: 'M22 12h-4l-3 9L9 3l-3 9H2',
  file: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M8 13h8M8 17h8',
  plus: 'M12 5v14M5 12h14',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  download: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3',
  x: 'M18 6 6 18M6 6l12 12',
  check: 'M20 6 9 17l-5-5',
  up: 'M7 17 17 7M7 7h10v10',
  down: 'M17 7 7 17M17 17H7V7',
  lock: 'M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2zM7 11V7a5 5 0 0 1 10 0v4',
  wallet: 'M20 12V8H6a2 2 0 0 1 0-4h12v4M4 6v12a2 2 0 0 0 2 2h14v-4M18 12a2 2 0 0 0 0 4h4v-4z',
  ban: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM4.93 4.93l14.14 14.14',
  menu: 'M3 12h18M3 6h18M3 18h18',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  info: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 16v-4M12 8h.01',
  briefcase: 'M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2zM16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16',
  globe: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z',
  scale: 'M12 3v18M5 21h14M7 7l-4 8a4 4 0 0 0 8 0L7 7zM17 7l-4 8a4 4 0 0 0 8 0l-4-8zM5 7h14',
  copy: 'M20 9h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zM5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1',
  pause: 'M10 4H6v16h4zM18 4h-4v16h4z',
  message: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'
}

export function Icon({ name, size = 18, className = '' }) {
  return (
    <svg className={`icon ${className}`} width={size} height={size} viewBox="0 0 24 24" fill="none"
         stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={PATHS[name]} />
    </svg>
  )
}

// ---------- small building blocks ----------
export const Pill = ({ tone = 'neutral', children }) => <span className={`pill pill-${tone}`}>{children}</span>

export function statusTone(status) {
  return { COMPLETED: 'success', BLOCKED: 'danger', ACTIVE: 'success', FROZEN: 'danger',
           HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[status] || 'neutral'
}

export const Avatar = ({ name, size = 36 }) => (
  <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.38 }}>{initials(name)}</span>
)

export const Spinner = () => <span className="spinner" aria-label="Loading" />

export function Loading({ label = 'Loading…' }) {
  return <div className="loading"><Spinner /> {label}</div>
}

export function Empty({ icon = 'list', title, children }) {
  return (
    <div className="empty">
      <div className="empty-icon"><Icon name={icon} size={22} /></div>
      <p className="empty-title">{title}</p>
      {children && <p className="muted">{children}</p>}
    </div>
  )
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  )
}

export function Stat({ label, value, hint, tone, icon }) {
  return (
    <div className="stat">
      <div className="stat-top">
        <span className="stat-label">{label}</span>
        {icon && <span className={`stat-icon tone-${tone || 'neutral'}`}><Icon name={icon} size={16} /></span>}
      </div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  )
}

export function Modal({ title, onClose, children, width = 480 }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: width }} role="dialog" aria-modal="true">
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close"><Icon name="x" /></button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  )
}

export function Confirm({ title, message, confirmLabel = 'Confirm', danger, onConfirm, onClose }) {
  const [busy, setBusy] = useState(false)
  return (
    <Modal title={title} onClose={onClose} width={420}>
      <p>{message}</p>
      <div className="modal-actions">
        <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
        <button className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} disabled={busy}
                onClick={async () => { setBusy(true); try { await onConfirm() } finally { setBusy(false); onClose() } }}>
          {busy ? 'Working…' : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}

export function DetailRows({ rows }) {
  return (
    <dl className="details">
      {rows.filter(Boolean).map(([k, v]) => (
        <div key={k} className="details-row"><dt>{k}</dt><dd>{v}</dd></div>
      ))}
    </dl>
  )
}

// ---------- toasts ----------
const ToastCtx = createContext(() => {})
export const useToast = () => useContext(ToastCtx)

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const push = useCallback((text, tone = 'success') => {
    const id = Math.random().toString(36).slice(2)
    setToasts((t) => [...t, { id, text, tone }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4500)
  }, [])
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`}>
            <Icon name={t.tone === 'success' ? 'check' : 'alert'} size={16} /> {t.text}
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  )
}

// ---------- hooks ----------
/** Loads data on mount and (optionally) every `intervalMs`; returns [data, error, reload]. */
export function usePoll(loader, intervalMs, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const loaderRef = useRef(loader)
  loaderRef.current = loader
  const reload = useCallback(async () => {
    try { setData(await loaderRef.current()); setError('') } catch (e) { setError(e.message) }
  }, [])
  useEffect(() => {
    reload()
    if (!intervalMs) return
    const id = setInterval(reload, intervalMs)
    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  return [data, error, reload]
}

// ---------- charts (plain SVG) ----------
/**
 * Grouped or stacked bar chart.
 * data: [{ label, values: [n1, n2, ...] }], series: [{ name, color }]
 */
export function BarChart({ data, series, height = 220, stacked = false, format = (v) => v, labelEvery = 1 }) {
  const W = 640, H = height, padL = 52, padB = 26, padT = 10, padR = 8
  const innerW = W - padL - padR, innerH = H - padT - padB
  const totals = data.map((d) => stacked ? d.values.reduce((a, b) => a + Number(b), 0) : Math.max(...d.values.map(Number)))
  const max = Math.max(1, ...totals)
  const nice = niceMax(max)
  const slot = innerW / Math.max(1, data.length)
  const groupW = slot * 0.7
  const barW = stacked ? groupW : groupW / series.length
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * nice)

  return (
    <div className="chart">
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img">
        {ticks.map((t) => {
          const y = padT + innerH - (t / nice) * innerH
          return (
            <g key={t}>
              <line x1={padL} x2={W - padR} y1={y} y2={y} className="chart-grid" />
              <text x={padL - 8} y={y + 4} textAnchor="end" className="chart-axis">{format(t)}</text>
            </g>
          )
        })}
        {data.map((d, i) => {
          const x0 = padL + i * slot + (slot - groupW) / 2
          let acc = 0
          return (
            <g key={i}>
              {d.values.map((v, j) => {
                const h = (Number(v) / nice) * innerH
                const x = stacked ? x0 : x0 + j * barW
                const y = stacked ? padT + innerH - ((acc + Number(v)) / nice) * innerH : padT + innerH - h
                acc += Number(v)
                return (
                  <rect key={j} x={x} y={y} width={Math.max(1, barW - (stacked ? 0 : 2))} height={Math.max(0, h)}
                        rx="2" fill={series[j].color}>
                    <title>{`${d.label} · ${series[j].name}: ${format(v)}`}</title>
                  </rect>
                )
              })}
              {i % labelEvery === 0 && (
                <text x={x0 + groupW / 2} y={H - 8} textAnchor="middle" className="chart-axis">{d.label}</text>
              )}
            </g>
          )
        })}
      </svg>
      <div className="legend">
        {series.map((s) => <span key={s.name}><i style={{ background: s.color }} />{s.name}</span>)}
      </div>
    </div>
  )
}

function niceMax(v) {
  const p = Math.pow(10, Math.floor(Math.log10(v)))
  const n = v / p
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p
}

export function HBars({ items, color = 'var(--primary)' }) {
  const max = Math.max(1, ...items.map((i) => i.value))
  return (
    <div className="hbars">
      {items.map((i) => (
        <div key={i.label} className="hbar">
          <span className="hbar-label">{i.label}</span>
          <span className="hbar-track"><span className="hbar-fill" style={{ width: `${(i.value / max) * 100}%`, background: color }} /></span>
          <span className="hbar-value">{i.value}</span>
        </div>
      ))}
    </div>
  )
}

export const compactMoney = (v) => v >= 100000 ? `₹${(v / 100000).toFixed(1)}L` : v >= 1000 ? `₹${(v / 1000).toFixed(v >= 10000 ? 0 : 1)}k` : `₹${Math.round(v)}`

/** Circular gauge for the Trust Score. */
export function TrustRing({ score, size = 96 }) {
  const r = 40, c = 2 * Math.PI * r
  const color = score >= 70 ? 'var(--success)' : score >= 40 ? 'var(--warning)' : 'var(--danger)'
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" className="trust-ring" role="img" aria-label={`Trust Score ${score} of 100`}>
      <circle cx="50" cy="50" r={r} fill="none" stroke="var(--bg)" strokeWidth="10" />
      <circle cx="50" cy="50" r={r} fill="none" stroke={color} strokeWidth="10" strokeLinecap="round"
              strokeDasharray={`${(score / 100) * c} ${c}`} transform="rotate(-90 50 50)" />
      <text x="50" y="56" textAnchor="middle" className="trust-ring-text">{score}</text>
    </svg>
  )
}

export function Progress({ value, tone = 'primary' }) {
  return <span className="progress"><span className={`progress-fill tone-bg-${tone}`} style={{ width: `${Math.max(0, Math.min(100, value))}%` }} /></span>
}

/** Small "time until" label that updates every 30 s. */
export function Until({ iso, prefix = '' }) {
  const [, tick] = useState(0)
  useEffect(() => { const id = setInterval(() => tick((x) => x + 1), 30000); return () => clearInterval(id) }, [])
  if (!iso) return null
  const s = Math.round((new Date(iso) - Date.now()) / 1000)
  if (s <= 0) return <span>{prefix}any moment now</span>
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.ceil((s % 3600) / 60)
  return <span>{prefix}{d ? `${d}d ${h}h` : h ? `${h}h ${m}m` : `${m} min`}</span>
}

/** Keeps a rendering error on one page from blanking the whole app. */
export class ErrorBoundary extends Component {
  constructor(props) { super(props); this.state = { error: null } }
  static getDerivedStateFromError(error) { return { error } }
  componentDidCatch(error) { console.error(error) }
  render() {
    if (this.state.error) {
      return (
        <div className="panel error-panel">
          <h2>Something went wrong on this page</h2>
          <p className="muted">Try again — if it keeps happening, sign out and back in.</p>
          <button className="btn btn-primary" onClick={() => this.setState({ error: null })}>Try again</button>
        </div>
      )
    }
    return this.props.children
  }
}
