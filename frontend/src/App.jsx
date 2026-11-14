import { useCallback, useEffect, useRef, useState } from 'react'
import { clearSession, getSession, IDLE_LIMIT_MS, IDLE_WARNING_MS, refreshIfNeeded } from './api'
import { Avatar, ErrorBoundary, Icon, ToastProvider } from './ui'
import Login from './pages/Login.jsx'
import Overview from './pages/customer/Overview.jsx'
import SendMoney from './pages/customer/SendMoney.jsx'
import Activity from './pages/customer/Activity.jsx'
import Cards from './pages/customer/Cards.jsx'
import Security from './pages/customer/Security.jsx'
import ResolutionCenter from './pages/customer/ResolutionCenter.jsx'
import Escrow from './pages/customer/Escrow.jsx'
import GetPaid from './pages/customer/GetPaid.jsx'
import AdminAppeals from './pages/admin/AdminAppeals.jsx'
import AdminOverview from './pages/admin/AdminOverview.jsx'
import AdminAlerts from './pages/admin/AdminAlerts.jsx'
import AdminTransactions from './pages/admin/AdminTransactions.jsx'
import AdminUsers from './pages/admin/AdminUsers.jsx'
import AdminAudit from './pages/admin/AdminAudit.jsx'

const CUSTOMER_NAV = [
  { id: 'overview', label: 'Overview', icon: 'home', component: Overview },
  { id: 'send', label: 'Send money', icon: 'send', component: SendMoney },
  { id: 'activity', label: 'Activity', icon: 'list', component: Activity },
  { id: 'escrow', label: 'Escrow', icon: 'briefcase', component: Escrow },
  { id: 'getpaid', label: 'Get paid from abroad', icon: 'globe', component: GetPaid },
  { id: 'resolution', label: 'Resolution Center', icon: 'scale', component: ResolutionCenter },
  { id: 'cards', label: 'Cards', icon: 'card', component: Cards },
  { id: 'security', label: 'Security', icon: 'shield', component: Security }
]

const ADMIN_NAV = [
  { id: 'overview', label: 'Overview', icon: 'activity', component: AdminOverview },
  { id: 'alerts', label: 'Fraud alerts', icon: 'alert', component: AdminAlerts },
  { id: 'appeals', label: 'Appeals & disputes', icon: 'scale', component: AdminAppeals },
  { id: 'transactions', label: 'Transactions', icon: 'list', component: AdminTransactions },
  { id: 'users', label: 'Users', icon: 'users', component: AdminUsers },
  { id: 'audit', label: 'Audit log', icon: 'file', component: AdminAudit }
]

export default function App() {
  return (
    <ToastProvider>
      <Shell />
    </ToastProvider>
  )
}

function Shell() {
  const [session, setSession] = useState(getSession())
  const [notice, setNotice] = useState('')

  useEffect(() => {
    const onLogout = (e) => { setSession(null); setNotice(e.detail || '') }
    window.addEventListener('payflow:logout', onLogout)
    return () => window.removeEventListener('payflow:logout', onLogout)
  }, [])

  if (!session) return <Login notice={notice} onLogin={(s) => { setNotice(''); setSession(s) }} />
  return <Workspace session={session} onLogout={(reason) => { clearSession(); setNotice(reason || ''); setSession(null) }} />
}

function Workspace({ session, onLogout }) {
  const isAdmin = session.role === 'ADMIN'
  const nav = isAdmin ? ADMIN_NAV : CUSTOMER_NAV
  const [route, setRoute] = useState({ page: 'overview', params: {} })
  const [menuOpen, setMenuOpen] = useState(false)
  const [idleWarning, setIdleWarning] = useState(false)
  const lastActivity = useRef(Date.now())

  const navigate = useCallback((page, params = {}) => {
    setRoute({ page, params })
    setMenuOpen(false)
    window.scrollTo(0, 0)
  }, [])

  // ---- inactivity timeout + sliding token refresh ----
  useEffect(() => {
    const bump = () => { lastActivity.current = Date.now(); setIdleWarning(false) }
    const events = ['mousedown', 'keydown', 'touchstart', 'scroll']
    events.forEach((e) => window.addEventListener(e, bump, { passive: true }))
    const timer = setInterval(() => {
      const idle = Date.now() - lastActivity.current
      if (idle >= IDLE_LIMIT_MS) {
        onLogout('You were signed out after 15 minutes of inactivity.')
      } else if (idle >= IDLE_WARNING_MS) {
        setIdleWarning(true)
      } else {
        refreshIfNeeded()
      }
    }, 15000)
    return () => { events.forEach((e) => window.removeEventListener(e, bump)); clearInterval(timer) }
  }, [onLogout])

  const current = nav.find((n) => n.id === route.page) || nav[0]
  const Page = current.component

  return (
    <div className="layout">
      <aside className={`sidebar ${menuOpen ? 'open' : ''}`}>
        <div className="brand">
          <span className="brand-mark"><Icon name="wallet" size={18} /></span>
          <span className="brand-name">PayFlow</span>
          {isAdmin && <span className="brand-tag">Admin</span>}
        </div>
        <nav>
          {nav.map((n) => (
            <button key={n.id} className={`nav-item ${current.id === n.id ? 'active' : ''}`} onClick={() => navigate(n.id)}>
              <Icon name={n.icon} /> {n.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="me">
            <Avatar name={session.fullName} size={34} />
            <div className="me-text">
              <strong>{session.fullName}</strong>
              <span>{session.email}</span>
            </div>
          </div>
          <button className="nav-item" onClick={() => onLogout('You have been signed out.')}>
            <Icon name="logout" /> Sign out
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-btn" onClick={() => setMenuOpen(!menuOpen)} aria-label="Menu"><Icon name="menu" /></button>
          <span className="topbar-title">{current.label}</span>
          <span className="env-badge" title="Simulated payments — no real money moves">Sandbox</span>
        </header>
        {idleWarning && (
          <div className="banner banner-warning">
            <Icon name="clock" /> For your security you'll be signed out in about 2 minutes due to inactivity.
            <button className="btn btn-small btn-ghost" onClick={() => { setIdleWarning(false); refreshIfNeeded() }}>Stay signed in</button>
          </div>
        )}
        <main className="content">
          <ErrorBoundary key={route.page}>
            <Page key={route.page + JSON.stringify(route.params)} session={session} params={route.params} navigate={navigate} />
          </ErrorBoundary>
        </main>
      </div>
      {menuOpen && <div className="scrim" onClick={() => setMenuOpen(false)} />}
    </div>
  )
}
