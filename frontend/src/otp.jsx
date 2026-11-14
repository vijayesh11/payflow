import { useEffect, useRef, useState } from 'react'
import { Icon, Modal } from './ui'

/**
 * Row of digit boxes backed by one hidden input (works with paste, SMS autofill and mobile keypads).
 * masked=true shows dots instead of digits (for UPI PIN entry).
 */
export function DigitInput({ value, onChange, length = 6, masked = false, autoFocus = true, label }) {
  const ref = useRef(null)
  const [focused, setFocused] = useState(false)
  useEffect(() => { if (autoFocus) ref.current?.focus() }, [autoFocus])
  return (
    <div className="digit-input" onClick={() => ref.current?.focus()}>
      <input
        ref={ref}
        className="digit-hidden"
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, length))}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        inputMode="numeric"
        autoComplete={masked ? 'off' : 'one-time-code'}
        type={masked ? 'password' : 'text'}
        maxLength={length}
        aria-label={label || (masked ? 'PIN' : 'One-time code')}
      />
      {Array.from({ length }).map((_, i) => {
        const filled = i < value.length
        const active = focused && (i === value.length || (i === length - 1 && value.length === length))
        return (
          <span key={i} className={`digit-box ${filled ? 'filled' : ''} ${active ? 'active' : ''}`}>
            {filled ? (masked ? <span className="pin-dot" /> : value[i]) : ''}
          </span>
        )
      })}
    </div>
  )
}

export function Countdown({ until, onExpire }) {
  const [left, setLeft] = useState(() => Math.max(0, Math.round((new Date(until) - Date.now()) / 1000)))
  useEffect(() => {
    const id = setInterval(() => {
      const s = Math.max(0, Math.round((new Date(until) - Date.now()) / 1000))
      setLeft(s)
      if (s === 0) { clearInterval(id); onExpire && onExpire() }
    }, 1000)
    return () => clearInterval(id)
  }, [until, onExpire])
  return <span className={left === 0 ? 'text-danger' : ''}>{left === 0 ? 'Code expired' : `Expires in ${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`}</span>
}

/** Where the code went, plus — in sandbox mode only — the code itself, styled like a phone notification. */
export function OtpSentNote({ challenge }) {
  return (
    <>
      <p className="muted">
        {challenge.channel === 'email'
          ? <>We've emailed a 6-digit code to <strong>{challenge.destination}</strong>.</>
          : <>A 6-digit code was sent to <strong>{challenge.destination}</strong>.</>}
      </p>
      {challenge.sandboxCode && (
        <div className="sandbox-otp" role="note">
          <span className="sandbox-otp-icon"><Icon name="info" size={16} /></span>
          <span>
            <strong>Sandbox inbox</strong>
            <span className="muted small block">No SMS/email provider is configured, so your code is shown here: </span>
          </span>
          <code className="sandbox-otp-code">{challenge.sandboxCode}</code>
        </div>
      )}
    </>
  )
}

/**
 * Complete "enter the code" step: note, digit boxes, countdown, resend and submit.
 * onSubmit(code) should throw on failure (message is shown); onResend() returns a new challenge.
 */
export function OtpStep({ challenge, onSubmit, onResend, submitLabel = 'Verify', onCancel }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [current, setCurrent] = useState(challenge)
  const [expired, setExpired] = useState(false)

  const submit = async (e) => {
    e && e.preventDefault()
    if (code.length !== 6) return
    setBusy(true); setError('')
    try { await onSubmit(code, current) } catch (err) { setError(err.message); setCode('') } finally { setBusy(false) }
  }

  // auto-submit when the 6th digit is typed or pasted
  useEffect(() => { if (code.length === 6 && !busy) submit() }, [code]) // eslint-disable-line react-hooks/exhaustive-deps

  const resend = async () => {
    setError(''); setCode('')
    try { const c = await onResend(); setCurrent(c); setExpired(false) } catch (err) { setError(err.message) }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <OtpSentNote challenge={current} />
      <DigitInput value={code} onChange={setCode} />
      <div className="otp-meta">
        <Countdown key={current.challengeId} until={current.expiresAt} onExpire={() => setExpired(true)} />
        {onResend && <button type="button" className="link-btn" onClick={resend}>Resend code</button>}
      </div>
      {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
      <div className="row">
        {onCancel && <button type="button" className="btn btn-ghost" onClick={onCancel}>Back</button>}
        <button className="btn btn-primary grow" disabled={busy || code.length !== 6 || expired}>{busy ? 'Verifying…' : submitLabel}</button>
      </div>
      <p className="fine-print"><Icon name="lock" size={13} /> PayFlow will never call or message you asking for this code.</p>
    </form>
  )
}

/**
 * UPI PIN pad in a modal. onSubmit(pin) performs the action; if it throws, the message is shown
 * and the PIN cleared so the user can retry (the server counts and locks wrong attempts).
 */
export function PinModal({ title = 'Enter UPI PIN', summary, onSubmit, onClose }) {
  const [pin, setPin] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (pin.length !== 6 || busy) return
    ;(async () => {
      setBusy(true); setError('')
      try { await onSubmit(pin); onClose() } catch (err) { setError(err.message); setPin('') } finally { setBusy(false) }
    })()
  }, [pin]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <Modal title={title} onClose={() => !busy && onClose()} width={400}>
      <div className="pin-sheet">
        {summary && <div className="pin-summary">{summary}</div>}
        <p className="muted small center">Enter your 6-digit UPI PIN</p>
        <DigitInput value={pin} onChange={setPin} masked label="UPI PIN" />
        {busy && <p className="muted center">Processing…</p>}
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
      </div>
    </Modal>
  )
}
