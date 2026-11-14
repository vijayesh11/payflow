import { useEffect, useState } from 'react'
import { api, saveSession } from '../api'
import { Icon } from '../ui'
import { OtpStep } from '../otp.jsx'

export const PASSWORD_RULES = [
  { test: (p) => p.length >= 12, label: 'At least 12 characters' },
  { test: (p) => /[A-Za-z]/.test(p), label: 'Contains a letter' },
  { test: (p) => /\d/.test(p), label: 'Contains a number' }
]

export function PasswordChecklist({ password }) {
  return (
    <ul className="checklist">
      {PASSWORD_RULES.map((r) => (
        <li key={r.label} className={r.test(password) ? 'ok' : ''}>
          <Icon name={r.test(password) ? 'check' : 'x'} size={14} /> {r.label}
        </li>
      ))}
    </ul>
  )
}

export default function Login({ onLogin, notice }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ email: '', password: '', fullName: '' })
  const [showPw, setShowPw] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [challenge, setChallenge] = useState(null)   // set when 2-step verification asks for an OTP
  const [config, setConfig] = useState(null)
  useEffect(() => { api('/api/public/config').then(setConfig).catch(() => {}) }, [])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const pwValid = PASSWORD_RULES.every((r) => r.test(form.password))

  const submit = async (e) => {
    e.preventDefault()
    if (mode === 'register' && !pwValid) { setError('Please choose a stronger password.'); return }
    setError(''); setBusy(true)
    try {
      if (mode === 'login') {
        const res = await api('/api/auth/login', { method: 'POST', body: { email: form.email, password: form.password } })
        if (res.otpRequired) { setChallenge(res.challenge); return }
        finish(res.auth)
      } else {
        finish(await api('/api/auth/register', { method: 'POST', body: form }))
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const finish = (session) => { saveSession(session); onLogin(session) }

  const verifyOtp = async (code, c) => {
    finish(await api('/api/auth/login/verify', { method: 'POST', body: { challengeId: c.challengeId, code } }))
  }

  const resendOtp = async () => {
    const res = await api('/api/auth/login', { method: 'POST', body: { email: form.email, password: form.password } })
    return res.challenge
  }

  const switchMode = () => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }

  return (
    <div className="auth-page">
      <section className="auth-hero">
        <div className="brand">
          <span className="brand-mark"><Icon name="wallet" size={18} /></span>
          <span className="brand-name">PayFlow</span>
        </div>
        <div className="auth-hero-body">
          <h1>Send money to anyone, instantly.</h1>
          <p>A digital wallet with bank-grade safeguards on every payment.</p>
          <ul className="auth-features">
            <li><Icon name="shield" /> Every payment screened by real-time fraud checks</li>
            <li><Icon name="lock" /> Cards tokenized — we never store your full card number</li>
            <li><Icon name="check" /> UPI PIN on every payment, OTP on every sign-in</li>
          </ul>
        </div>
        <p className="auth-foot">Sandbox environment · simulated payments, no real money</p>
      </section>

      <section className="auth-panel">
        {challenge ? (
          <div className="auth-form">
            <span className="otp-badge"><Icon name="shield" size={22} /></span>
            <h2>2-step verification</h2>
            <OtpStep challenge={challenge} onSubmit={verifyOtp} onResend={resendOtp} submitLabel="Verify and sign in"
                     onCancel={() => { setChallenge(null); setForm({ ...form, password: '' }) }} />
          </div>
        ) : (
        <form className="auth-form" onSubmit={submit} noValidate>
          <h2>{mode === 'login' ? 'Sign in' : 'Create your account'}</h2>
          <p className="muted">{mode === 'login' ? 'Welcome back to PayFlow.' : 'It takes less than a minute.'}</p>

          {notice && <div className="alert alert-info"><Icon name="info" /> {notice}</div>}

          {mode === 'register' && (
            <label className="field">Full name
              <input value={form.fullName} onChange={set('fullName')} autoComplete="name" required />
            </label>
          )}
          <label className="field">Email
            <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
          </label>
          <label className="field">Password
            <div className="input-with-btn">
              <input type={showPw ? 'text' : 'password'} value={form.password} onChange={set('password')}
                     autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required />
              <button type="button" className="link-btn" onClick={() => setShowPw(!showPw)}>{showPw ? 'Hide' : 'Show'}</button>
            </div>
          </label>
          {mode === 'register' && <PasswordChecklist password={form.password} />}

          {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}

          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>

          <p className="auth-switch">
            {mode === 'login' ? "Don't have an account?" : 'Already have an account?'}{' '}
            <button type="button" className="link-btn" onClick={switchMode}>
              {mode === 'login' ? 'Create one' : 'Sign in'}
            </button>
          </p>
          {mode === 'login' && (
            <p className="fine-print"><Icon name="lock" size={13} /> After 5 failed attempts, accounts are locked for 30 minutes.</p>
          )}
          {mode === 'login' && config?.demoMode && config.demoAccounts.length > 0 && (
            <div className="demo-box">
              <strong>Try a demo account</strong>
              <span className="muted small">Codes appear on screen (sandbox). UPI PIN for Asha and Vikram: <code>592047</code></span>
              {config.demoAccounts.map((a) => (
                <button type="button" key={a.email} className="demo-account"
                        onClick={() => { setForm({ ...form, email: a.email, password: a.password }); setError('') }}>
                  <span><strong>{a.email.split('@')[0].replace(/^./, (c) => c.toUpperCase())}</strong> <span className="muted small">{a.label}</span></span>
                  <span className="link-btn small">Use</span>
                </button>
              ))}
            </div>
          )}
        </form>
        )}
      </section>
    </div>
  )
}
