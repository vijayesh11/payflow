import { useEffect, useRef, useState } from 'react'
import { api } from '../../api'
import { dateTime, money } from '../../format'
import { DetailRows, Icon, Modal, PageHeader, useToast } from '../../ui'
import { DigitInput } from '../../otp.jsx'
import { ExplanationList } from './AppealModal.jsx'

const COUNTRIES = [
  ['IN', 'India'], ['US', 'United States'], ['GB', 'United Kingdom'], ['SG', 'Singapore'],
  ['AE', 'United Arab Emirates'], ['RU', 'Russia'], ['NG', 'Nigeria'], ['BR', 'Brazil']
]

export default function SendMoney({ params, navigate }) {
  const toast = useToast()
  const [step, setStep] = useState('form')          // form -> review -> result
  const [form, setForm] = useState({ recipientEmail: params.to || '', amount: '', note: '' })
  const [country, setCountry] = useState('IN')
  const [balance, setBalance] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [profile, setProfile] = useState(null)
  const [pinOpen, setPinOpen] = useState(false)
  const [pin, setPin] = useState('')
  const [pinError, setPinError] = useState('')
  // One idempotency key per logical payment: created at review, reused on retries/double-clicks,
  // replaced only once the payment has a definite outcome.
  const idemKey = useRef(null)

  useEffect(() => {
    api('/api/wallet').then((w) => setBalance(Number(w.balance))).catch(() => {})
    api('/api/me').then(setProfile).catch(() => {})
  }, [])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const amount = Number(form.amount)
  const overBalance = balance != null && amount > balance

  const review = (e) => {
    e.preventDefault()
    setError('')
    if (!idemKey.current) idemKey.current = crypto.randomUUID()
    setStep('review')
  }

  const openPin = () => { setPin(''); setPinError(''); setPinOpen(true) }

  const confirm = async (enteredPin) => {
    setBusy(true); setError(''); setPinError('')
    try {
      const t = await api('/api/transfers', {
        method: 'POST',
        headers: { 'Idempotency-Key': idemKey.current, 'X-Country': country },
        body: { recipientEmail: form.recipientEmail.trim(), amount, note: form.note.trim() || null, pin: enteredPin }
      })
      idemKey.current = null
      setPinOpen(false)
      setResult(t)
      setStep('result')
      if (t.status === 'COMPLETED') toast(`${money(t.amount)} sent to ${t.recipientName}`)
    } catch (err) {
      if (/UPI PIN/i.test(err.message)) {
        // wrong or locked PIN: nothing was attempted, so the same idempotency key stays valid
        setPinError(err.message); setPin('')
        if (err.status === 423) api('/api/me').then(setProfile).catch(() => {})
      } else {
        setPinOpen(false)
        setError(err.message)
        if (!err.network) idemKey.current = null   // definite failure (e.g. insufficient funds) -> new key next time
      }
    } finally { setBusy(false) }
  }

  useEffect(() => { if (pinOpen && pin.length === 6 && !busy) confirm(pin) }, [pin]) // eslint-disable-line react-hooks/exhaustive-deps

  const reset = () => {
    setForm({ recipientEmail: '', amount: '', note: '' }); setResult(null); setError(''); setStep('form')
    api('/api/wallet').then((w) => setBalance(Number(w.balance))).catch(() => {})
  }

  return (
    <>
      <PageHeader title="Send money" subtitle="Pay anyone with a PayFlow account using their email" />
      <div className="send-layout">
        <section className="panel">
          <ol className="steps">
            {['Details', 'Review', 'Done'].map((s, i) => (
              <li key={s} className={i <= ['form', 'review', 'result'].indexOf(step) ? 'done' : ''}><span>{i + 1}</span>{s}</li>
            ))}
          </ol>

          {step === 'form' && (
            <form className="stack" onSubmit={review}>
              <label className="field">Recipient email
                <input type="email" value={form.recipientEmail} onChange={set('recipientEmail')} placeholder="name@example.com" required autoFocus={!params.to} />
              </label>
              <label className="field">Amount (₹)
                <input type="number" min="0.01" step="0.01" value={form.amount} onChange={set('amount')} placeholder="0.00" required autoFocus={!!params.to} />
                {balance != null && <span className={`field-hint ${overBalance ? 'text-danger' : ''}`}>Available: {money(balance)}</span>}
              </label>
              <label className="field">Note <span className="optional">optional</span>
                <input maxLength={140} value={form.note} onChange={set('note')} placeholder="What's it for?" />
              </label>
              <label className="field">Sending from <span className="optional">simulated location</span>
                <select value={country} onChange={(e) => setCountry(e.target.value)}>
                  {COUNTRIES.map(([c, n]) => <option key={c} value={c}>{n}</option>)}
                </select>
                <span className="field-hint">In production this comes from your IP address. Change it to see the geolocation check in action.</span>
              </label>
              <button className="btn btn-primary" disabled={!form.recipientEmail || !amount || overBalance}>Continue</button>
            </form>
          )}

          {step === 'review' && (
            <div className="stack">
              <div className="receipt-amount">{money(amount)}</div>
              <DetailRows rows={[
                ['To', form.recipientEmail],
                form.note.trim() && ['Note', form.note.trim()],
                ['Fee', money(0)],
                ['Total', <strong>{money(amount)}</strong>]
              ]} />
              {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
              {profile && !profile.hasPin && (
                <div className="alert alert-info"><Icon name="lock" />
                  <span>You need a UPI PIN to send money. <button className="link-btn" onClick={() => navigate('security', { setPin: true })}>Set it up now</button></span>
                </div>
              )}
              {profile?.pinLocked && (
                <div className="alert alert-danger"><Icon name="lock" />
                  <span>Your UPI PIN is locked. <button className="link-btn" onClick={() => navigate('security', { setPin: true })}>Reset it with OTP</button></span>
                </div>
              )}
              <div className="row">
                <button className="btn btn-ghost" onClick={() => setStep('form')} disabled={busy}>Back</button>
                <button className="btn btn-primary grow" onClick={openPin} disabled={busy || !profile || !profile.hasPin || profile.pinLocked}>
                  <Icon name="lock" size={16} /> Pay {money(amount)}
                </button>
              </div>
            </div>
          )}

          {step === 'result' && result && (
            result.status === 'COMPLETED' ? (
              <div className="result">
                <div className="result-icon success"><Icon name="check" size={28} /></div>
                <h2>Payment sent</h2>
                <p className="muted">{money(result.amount)} to {result.recipientName}</p>
                <DetailRows rows={[
                  ['Reference', `PF-T${result.id}`],
                  ['Date', dateTime(result.createdAt)],
                  result.note && ['Note', result.note],
                  result.replayed && ['Note', 'Duplicate request detected — you were not charged twice'],
                  result.heldAmount && ['Held for recipient', `${money(result.heldAmount)} (released progressively)`]
                ]} />
                <div className="row">
                  <button className="btn btn-ghost grow" onClick={() => navigate('activity')}>View activity</button>
                  <button className="btn btn-primary grow" onClick={reset}>Send another</button>
                </div>
              </div>
            ) : (
              <div className="result">
                <div className="result-icon danger"><Icon name="shield" size={28} /></div>
                <h2>Payment blocked</h2>
                <p className="muted">For your protection, this payment was stopped and no money left your wallet. Here's exactly why:</p>
                <ExplanationList explanations={result.explanations} />
                <div className="row">
                  <button className="btn btn-ghost grow" onClick={reset}>Back to send money</button>
                  <button className="btn btn-primary grow" onClick={() => navigate('resolution', { appeal: {
                    type: 'BLOCKED_TRANSFER', targetId: result.id,
                    context: `${money(result.amount)} to ${result.recipientEmail} — ${result.explanations.map((e) => e.title).join(', ')}` } })}>
                    This was me — appeal
                  </button>
                </div>
              </div>
            )
          )}
        </section>

        {pinOpen && (
          <Modal title="Enter UPI PIN" onClose={() => !busy && setPinOpen(false)} width={400}>
            <div className="pin-sheet">
              <div className="pin-summary">
                <span className="muted small">Paying</span>
                <strong>{form.recipientEmail}</strong>
                <span className="pin-amount">{money(amount)}</span>
              </div>
              <p className="muted small center">Enter your 6-digit UPI PIN to authorise this payment</p>
              <DigitInput value={pin} onChange={setPin} masked label="UPI PIN" />
              {busy && <p className="muted center">Processing payment…</p>}
              {pinError && <div className="alert alert-danger"><Icon name="alert" /> {pinError}</div>}
              <button className="link-btn center" onClick={() => navigate('security', { setPin: true })}>Forgot UPI PIN?</button>
            </div>
          </Modal>
        )}

        <aside className="panel side-info">
          <h2>How PayFlow protects you</h2>
          <ul className="info-list">
            <li><Icon name="shield" /><span><strong>Real-time fraud checks</strong> on every payment: unusual amounts, rapid repeat payments and sudden location changes.</span></li>
            <li><Icon name="check" /><span><strong>No double charges.</strong> If your connection drops and you retry, you're only charged once.</span></li>
            <li><Icon name="lock" /><span><strong>UPI PIN on every payment.</strong> Only you can authorise money leaving your wallet. Three wrong PINs lock it.</span></li>
            <li><Icon name="wallet" /><span><strong>All or nothing.</strong> Money leaves your wallet only if it reaches the recipient.</span></li>
          </ul>
        </aside>
      </div>
    </>
  )
}
