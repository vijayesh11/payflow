import { useEffect, useState } from 'react'
import { api } from '../api'
import { dateTime } from '../format'
import { Countdown } from '../otp.jsx'
import { DetailRows, Icon, Loading } from '../ui'

const fx = (n, c) => Number(n || 0).toLocaleString('en-US', { style: 'currency', currency: c })

/** Public page an overseas client opens from the freelancer's link. No PayFlow account needed. */
export default function PublicInvoice({ token }) {
  const [inv, setInv] = useState(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ payerName: '', cardNumber: '', expiry: '', cvv: '' })
  const [busy, setBusy] = useState(false)
  const [payError, setPayError] = useState('')
  const [expired, setExpired] = useState(false)

  const load = () => api(`/api/public/invoices/${token}`).then((i) => { setInv(i); setExpired(false) }).catch((e) => setError(e.message))
  useEffect(() => { load() }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  const pay = async (e) => {
    e.preventDefault()
    const m = form.expiry.match(/^(\d{2})\/(\d{2})$/)
    if (!m) { setPayError('Enter expiry as MM/YY'); return }
    setBusy(true); setPayError('')
    try {
      setInv(await api(`/api/public/invoices/${token}/pay`, {
        method: 'POST',
        body: { payerName: form.payerName, cardNumber: form.cardNumber.replace(/\s/g, ''), expMonth: Number(m[1]), expYear: 2000 + Number(m[2]), cvv: form.cvv }
      }))
      setForm({ payerName: '', cardNumber: '', expiry: '', cvv: '' })
    } catch (err) { setPayError(err.message) } finally { setBusy(false) }
  }

  return (
    <div className="public-page">
      <header className="public-head">
        <span className="brand-mark"><Icon name="wallet" size={18} /></span><span className="brand-name dark">PayFlow</span>
        <span className="env-badge">Sandbox</span>
      </header>
      <main className="public-card">
        {error ? <div className="alert alert-danger"><Icon name="alert" /> {error}</div> : !inv ? <Loading /> : (
          <>
            <p className="muted small">Invoice from</p>
            <h1>{inv.freelancerName}</h1>
            <p className="muted">To {inv.clientName}</p>
            <div className="invoice-amount">{fx(inv.amount, inv.currency)}</div>
            <p className="center">{inv.description}</p>

            {inv.status === 'PAID' ? (
              <div className="result">
                <div className="result-icon success"><Icon name="check" size={28} /></div>
                <h2>Paid</h2>
                <p className="muted">Paid on {dateTime(inv.paidAt)}. {inv.freelancerName} has been notified.</p>
                {inv.quote && (
                  <DetailRows rows={[['Exchange rate', `1 ${inv.currency} = ₹${Number(inv.quote.midMarketRate).toFixed(4)}`],
                                     ['Fee', `${inv.quote.feePercent}% — shown before payment, nothing hidden in the rate`]]} />
                )}
              </div>
            ) : inv.status === 'CANCELLED' ? (
              <div className="alert alert-info"><Icon name="info" /> This invoice was cancelled by the sender.</div>
            ) : (
              <>
                <div className="rate-lock">
                  <Icon name="lock" size={16} />
                  <span>Rate locked: 1 {inv.currency} = ₹{Number(inv.quote.midMarketRate).toFixed(4)} (mid-market, no markup)</span>
                  <span className="muted small">{expired ? <button className="link-btn" onClick={load}>Refresh rate</button> : <Countdown until={inv.quote.lockedUntil} onExpire={() => setExpired(true)} />}</span>
                </div>
                <p className="muted small center">{inv.freelancerName} receives ₹{Number(inv.quote.netInr).toLocaleString('en-IN')} after a single {inv.quote.feePercent}% fee.</p>
                <form className="stack" onSubmit={pay}>
                  <label className="field">Name on card<input value={form.payerName} onChange={(e) => setForm({ ...form, payerName: e.target.value })} required /></label>
                  <label className="field">Card number
                    <input inputMode="numeric" autoComplete="cc-number" placeholder="1234 5678 9012 3456" value={form.cardNumber}
                           onChange={(e) => setForm({ ...form, cardNumber: e.target.value.replace(/\D/g, '').slice(0, 19).replace(/(.{4})/g, '$1 ').trim() })} required />
                  </label>
                  <div className="row">
                    <label className="field grow">Expiry<input placeholder="MM/YY" maxLength={5} value={form.expiry} autoComplete="cc-exp"
                      onChange={(e) => { let v = e.target.value.replace(/\D/g, '').slice(0, 4); if (v.length > 2) v = v.slice(0, 2) + '/' + v.slice(2); setForm({ ...form, expiry: v }) }} required /></label>
                    <label className="field grow">CVC<input type="password" inputMode="numeric" maxLength={4} value={form.cvv} autoComplete="cc-csc"
                      onChange={(e) => setForm({ ...form, cvv: e.target.value.replace(/\D/g, '') })} required /></label>
                  </div>
                  <p className="fine-print"><Icon name="info" size={13} /> Sandbox: use test card 4242 4242 4242 4242, any future expiry, any CVC. No real charge.</p>
                  {payError && <div className="alert alert-danger"><Icon name="alert" /> {payError}</div>}
                  <button className="btn btn-primary btn-block" disabled={busy || expired}>{busy ? 'Paying…' : `Pay ${fx(inv.amount, inv.currency)}`}</button>
                </form>
              </>
            )}
          </>
        )}
      </main>
      <p className="public-foot muted small">Payments are processed by PayFlow (sandbox). Card details are validated and never stored.</p>
    </div>
  )
}
