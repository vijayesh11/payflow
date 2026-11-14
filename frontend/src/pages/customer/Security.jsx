import { useEffect, useState } from 'react'
import { api } from '../../api'
import { ACTION_LABELS, dateOnly, dateTime, device, relative } from '../../format'
import { Avatar, DetailRows, Empty, Icon, Loading, Modal, PageHeader, Pill, useToast, usePoll } from '../../ui'
import { DigitInput, OtpStep } from '../../otp.jsx'
import { PASSWORD_RULES, PasswordChecklist } from '../Login.jsx'

const RISKY = new Set(['LOGIN_FAILED', 'LOGIN_BLOCKED', 'ACCOUNT_LOCKED', 'PASSWORD_CHANGE_FAILED', 'OTP_FAILED', 'PIN_FAILED', 'PIN_LOCKED', 'TWO_FACTOR_OFF'])
const WEAK_PINS = ['123456', '654321', '111111', '000000', '121212', '112233', '123123']

export default function Security({ params = {} }) {
  const [pinModal, setPinModal] = useState(!!params.setPin)
  const [twoFactorModal, setTwoFactorModal] = useState(false)
  const [data, error, reload] = usePoll(async () => {
    const [profile, activity] = await Promise.all([api('/api/me'), api('/api/me/activity')])
    return { profile, activity }
  })

  if (!data) return <Loading label={error || 'Loading…'} />
  const { profile, activity } = data

  return (
    <>
      <PageHeader title="Security" subtitle="Manage your password and review recent account activity" />
      <div className="grid-1-1">
        <section className="panel">
          <div className="profile-head">
            <Avatar name={profile.fullName} size={52} />
            <div>
              <h2>{profile.fullName}</h2>
              <p className="muted">{profile.email}</p>
            </div>
          </div>
          <DetailRows rows={[
            ['Account status', <Pill tone={profile.status === 'ACTIVE' ? 'success' : 'danger'}>{profile.status === 'ACTIVE' ? 'Active' : 'Frozen'}</Pill>],
            ['Member since', dateOnly(profile.memberSince)],
            ['Previous sign-in', profile.previousLoginAt ? dateTime(profile.previousLoginAt) : 'This is your first session'],
            ['Password last changed', relative(profile.passwordChangedAt)]
          ]} />
          <ul className="info-list compact">
            <li><Icon name="clock" /><span>You're signed out automatically after 15 minutes of inactivity.</span></li>
            <li><Icon name="lock" /><span>5 wrong passwords in a row lock the account for 30 minutes.</span></li>
          </ul>
        </section>

        <ChangePassword onChanged={reload} />
      </div>

      <div className="grid-1-1">
        <section className="panel">
          <div className="panel-head">
            <h2>UPI PIN</h2>
            {profile.pinLocked ? <Pill tone="danger">Locked</Pill> : profile.hasPin ? <Pill tone="success">Active</Pill> : <Pill tone="warning">Not set</Pill>}
          </div>
          <p className="muted">Your 6-digit UPI PIN authorises every payment. Never share it — PayFlow staff will never ask for it.</p>
          <DetailRows rows={[
            ['Status', profile.pinLocked ? 'Locked after 3 wrong attempts' : profile.hasPin ? 'Set' : 'Not set'],
            profile.pinSetAt && ['Last set', dateTime(profile.pinSetAt)]
          ]} />
          <button className="btn btn-primary" onClick={() => setPinModal(true)}>
            <Icon name="lock" size={16} /> {profile.hasPin ? 'Reset UPI PIN' : 'Set UPI PIN'}
          </button>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>2-step verification</h2>
            {profile.twoFactorEnabled ? <Pill tone="success">On</Pill> : <Pill tone="danger">Off</Pill>}
          </div>
          <p className="muted">When it's on, signing in needs your password <em>and</em> a one-time code sent to {profile.email}.</p>
          <button className={`btn ${profile.twoFactorEnabled ? 'btn-ghost' : 'btn-primary'}`} onClick={() => setTwoFactorModal(true)}>
            {profile.twoFactorEnabled ? 'Turn off' : 'Turn on'}
          </button>
          {!profile.twoFactorEnabled && <p className="fine-print"><Icon name="alert" size={13} /> Recommended: keep 2-step verification on.</p>}
        </section>
      </div>

      {pinModal && <SetPinModal hasPin={profile.hasPin} onClose={() => setPinModal(false)} onDone={reload} />}
      {twoFactorModal && <TwoFactorModal enable={!profile.twoFactorEnabled} onClose={() => setTwoFactorModal(false)} onDone={reload} />}

      <section className="panel">
        <div className="panel-head"><h2>Recent account activity</h2></div>
        {activity.length === 0 ? <Empty icon="shield" title="No activity yet" /> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Event</th><th>Details</th><th>Device</th><th>IP address</th><th>When</th></tr></thead>
              <tbody>
                {activity.map((a) => (
                  <tr key={a.id}>
                    <td>{RISKY.has(a.action) ? <Pill tone="danger">{ACTION_LABELS[a.action] || a.action}</Pill> : (ACTION_LABELS[a.action] || a.action)}</td>
                    <td className="muted">{a.details || '—'}</td>
                    <td>{device(a.userAgent)}</td>
                    <td className="mono">{a.ipAddress || '—'}</td>
                    <td className="nowrap">{dateTime(a.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="fine-print">Don't recognise something? Change your password straight away.</p>
      </section>
    </>
  )
}

function SetPinModal({ hasPin, onClose, onDone }) {
  const toast = useToast()
  const [step, setStep] = useState('intro')      // intro -> otp -> pin -> confirm
  const [challenge, setChallenge] = useState(null)
  const [code, setCode] = useState('')
  const [pin, setPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const sendCode = async () => {
    setBusy(true); setError('')
    try { setChallenge(await api('/api/me/pin/otp', { method: 'POST' })); setStep('otp') }
    catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  // PIN step: check it isn't trivially guessable before asking to confirm
  useEffect(() => {
    if (step !== 'pin' || pin.length !== 6) return
    if (WEAK_PINS.includes(pin) || new Set(pin).size === 1) { setError('That PIN is too easy to guess. Avoid repeated or sequential digits.'); setPin(''); return }
    setError(''); setStep('confirm')
  }, [pin, step])

  useEffect(() => {
    if (step !== 'confirm' || confirmPin.length !== 6) return
    if (confirmPin !== pin) { setError("PINs don't match. Try again."); setPin(''); setConfirmPin(''); setStep('pin'); return }
    ;(async () => {
      setBusy(true); setError('')
      try {
        await api('/api/me/pin', { method: 'POST', body: { challengeId: challenge.challengeId, code, pin } })
        toast(hasPin ? 'UPI PIN reset' : 'UPI PIN set — you can now send money')
        onDone(); onClose()
      } catch (err) {
        setError(err.message); setPin(''); setConfirmPin('')
        setStep(/code/i.test(err.message) ? 'otp' : 'pin')
        if (/code/i.test(err.message)) setCode('')
      } finally { setBusy(false) }
    })()
  }, [confirmPin, step]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Modal title={hasPin ? 'Reset UPI PIN' : 'Set UPI PIN'} onClose={onClose} width={420}>
      {step === 'intro' && (
        <div className="stack">
          <p>To keep your account safe, we'll first verify it's you with a one-time code.</p>
          {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
          <button className="btn btn-primary" onClick={sendCode} disabled={busy}>{busy ? 'Sending…' : 'Send code'}</button>
        </div>
      )}
      {step === 'otp' && (
        <>
          {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
          <OtpStep challenge={challenge} submitLabel="Continue"
                   onSubmit={async (c) => { setCode(c); setError(''); setStep('pin') }}
                   onResend={() => api('/api/me/pin/otp', { method: 'POST' })} />
        </>
      )}
      {(step === 'pin' || step === 'confirm') && (
        <div className="pin-sheet">
          <p className="center"><strong>{step === 'pin' ? 'Choose a 6-digit UPI PIN' : 'Re-enter your UPI PIN'}</strong></p>
          {step === 'pin'
            ? <DigitInput key="pin" value={pin} onChange={setPin} masked label="New UPI PIN" />
            : <DigitInput key="confirm" value={confirmPin} onChange={setConfirmPin} masked label="Confirm UPI PIN" />}
          {busy && <p className="muted center">Saving…</p>}
          {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
          <p className="fine-print"><Icon name="info" size={13} /> Avoid birthdays, phone numbers and patterns like 123456.</p>
        </div>
      )}
    </Modal>
  )
}

function TwoFactorModal({ enable, onClose, onDone }) {
  const toast = useToast()
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await api('/api/me/two-factor', { method: 'POST', body: { enabled: enable, password } })
      toast(`2-step verification turned ${enable ? 'on' : 'off'}`)
      onDone(); onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return (
    <Modal title={enable ? 'Turn on 2-step verification' : 'Turn off 2-step verification'} onClose={onClose} width={420}>
      <form className="stack" onSubmit={submit}>
        {!enable && <div className="alert alert-danger"><Icon name="alert" /> Without it, anyone with your password can sign in.</div>}
        <label className="field">Confirm your password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoFocus required />
        </label>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className={`btn ${enable ? 'btn-primary' : 'btn-danger'}`} disabled={busy || !password}>{enable ? 'Turn on' : 'Turn off'}</button>
        </div>
      </form>
    </Modal>
  )
}

function ChangePassword({ onChanged }) {
  const toast = useToast()
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const valid = PASSWORD_RULES.every((r) => r.test(form.newPassword)) && form.newPassword === form.confirm

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await api('/api/me/password', { method: 'POST', body: { currentPassword: form.currentPassword, newPassword: form.newPassword } })
      setForm({ currentPassword: '', newPassword: '', confirm: '' })
      toast('Password updated')
      onChanged()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  return (
    <section className="panel">
      <div className="panel-head"><h2>Change password</h2></div>
      <form className="stack" onSubmit={submit}>
        <label className="field">Current password
          <input type="password" value={form.currentPassword} onChange={set('currentPassword')} autoComplete="current-password" required />
        </label>
        <label className="field">New password
          <input type="password" value={form.newPassword} onChange={set('newPassword')} autoComplete="new-password" required />
        </label>
        <PasswordChecklist password={form.newPassword} />
        <label className="field">Confirm new password
          <input type="password" value={form.confirm} onChange={set('confirm')} autoComplete="new-password" required />
          {form.confirm && form.confirm !== form.newPassword && <span className="field-hint text-danger">Passwords don't match</span>}
        </label>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <button className="btn btn-primary" disabled={busy || !valid || !form.currentPassword}>{busy ? 'Updating…' : 'Update password'}</button>
      </form>
    </section>
  )
}
