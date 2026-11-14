import { useEffect, useState } from 'react'
import { api } from '../../api'
import { dateTime, money } from '../../format'
import { Confirm, DetailRows, Empty, Icon, Loading, Modal, PageHeader, Pill, useToast, usePoll } from '../../ui'

const CURRENCIES = ['USD', 'EUR', 'GBP', 'AED', 'SGD']
const fx = (n, c) => Number(n || 0).toLocaleString('en-US', { style: 'currency', currency: c })
export const payLink = (token) => `${window.location.origin}/pay/${token}`

export function QuoteBreakdown({ q, compact }) {
  if (!q) return null
  return (
    <div className="quote">
      <DetailRows rows={[
        ['Client pays', <strong>{fx(q.amount, q.currency)}</strong>],
        ['Exchange rate', <span>1 {q.currency} = ₹{Number(q.midMarketRate).toFixed(4)} <span className="muted small">(mid-market, no markup)</span></span>],
        ['Converted', money(q.grossInr)],
        [`PayFlow fee (${q.feePercent}%)`, `− ${money(q.feeInr)}`],
        ['You receive', <strong className="text-success">{money(q.netInr)}</strong>]
      ]} />
      {!compact && (
        <div className="savings">
          <Icon name="check" size={16} />
          <span>A typical provider ({q.typicalMarkupPercent}% hidden FX markup + {q.typicalFeePercent}% fee) would pay you about <strong>{money(q.typicalNetInr)}</strong>.
            You keep <strong>{money(q.savingsInr)}</strong> more.</span>
        </div>
      )}
      <p className="fine-print"><Icon name="info" size={13} /> {q.rateSource}. Comparison is illustrative, based on commonly published provider rates.</p>
    </div>
  )
}

export default function GetPaid() {
  const toast = useToast()
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState(null)
  const [cancelling, setCancelling] = useState(null)
  const [invoices, error, reload] = usePoll(() => api('/api/invoices'), 15000)

  const copy = async (inv) => {
    try { await navigator.clipboard.writeText(payLink(inv.token)); toast('Payment link copied') }
    catch { toast(payLink(inv.token), 'success') }
  }
  const paid = (invoices || []).filter((i) => i.status === 'PAID')
  const saved = paid.reduce((s, i) => s + (Number(i.netInr) - Number(i.typicalNetInr || i.netInr)), 0)

  return (
    <>
      <PageHeader title="Get paid from abroad"
                  subtitle="Invoice overseas clients in their currency. You see the exact rate and fee before they pay — nothing hidden in the exchange rate."
                  actions={<button className="btn btn-primary" onClick={() => setCreating(true)}><Icon name="plus" /> New invoice</button>} />

      {paid.length > 0 && (
        <div className="savings big">
          <Icon name="globe" size={18} />
          <span>You've received <strong>{money(paid.reduce((s, i) => s + Number(i.netInr), 0))}</strong> from {paid.length} international payment{paid.length > 1 ? 's' : ''},
            keeping about <strong>{money(saved)}</strong> more than with a typical provider.</span>
        </div>
      )}

      <section className="panel">
        {!invoices ? <Loading label={error || 'Loading…'} /> : invoices.length === 0 ? (
          <Empty icon="globe" title="No invoices yet">Create one and share the link — your client pays by card, you get rupees.</Empty>
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Client</th><th>For</th><th className="right">Amount</th><th className="right">You received</th><th>Status</th><th></th></tr></thead>
              <tbody>
                {invoices.map((i) => (
                  <tr key={i.id}>
                    <td><strong>{i.clientName}</strong><span className="muted small block">{dateTime(i.createdAt)}</span></td>
                    <td className="muted">{i.description}</td>
                    <td className="right nowrap">{fx(i.amount, i.currency)}</td>
                    <td className="right nowrap">{i.netInr ? money(i.netInr) : '—'}</td>
                    <td><Pill tone={i.status === 'PAID' ? 'success' : i.status === 'OPEN' ? 'info' : 'neutral'}>{i.status === 'OPEN' ? 'Awaiting payment' : i.status === 'PAID' ? 'Paid' : 'Cancelled'}</Pill></td>
                    <td className="nowrap">
                      {i.status === 'OPEN' && <>
                        <button className="btn btn-small btn-ghost" onClick={() => copy(i)}><Icon name="copy" size={14} /> Copy link</button>
                        <a className="btn btn-small btn-ghost" href={`/pay/${i.token}`} target="_blank" rel="noreferrer">Open</a>
                        <button className="btn btn-small btn-ghost text-danger" onClick={() => setCancelling(i)}>Cancel</button>
                      </>}
                      {i.status === 'PAID' && <button className="btn btn-small btn-ghost" onClick={() => setViewing(i)}>Breakdown</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {creating && <CreateInvoiceModal onClose={() => setCreating(false)} onCreated={(inv) => { reload(); copy(inv) }} />}
      {viewing && (
        <Modal title={`Payment from ${viewing.clientName}`} onClose={() => setViewing(null)} width={520}>
          <DetailRows rows={[['Paid by', `${viewing.payerName} · card •••• ${viewing.payerCardLast4}`], ['Paid on', dateTime(viewing.paidAt)], ['Reference', `PF-I${viewing.id}`]]} />
          <QuoteBreakdown q={{
            currency: viewing.currency, amount: viewing.amount, midMarketRate: viewing.rate, grossInr: viewing.grossInr,
            feePercent: 0.5, feeInr: viewing.feeInr, netInr: viewing.netInr, typicalMarkupPercent: 3.5, typicalFeePercent: 4.4,
            typicalNetInr: viewing.typicalNetInr, savingsInr: Number(viewing.netInr) - Number(viewing.typicalNetInr),
            rateSource: 'Rate locked when your client opened the invoice'
          }} />
        </Modal>
      )}
      {cancelling && <Confirm title="Cancel invoice?" danger confirmLabel="Cancel invoice" message="The payment link will stop working."
                              onClose={() => setCancelling(null)}
                              onConfirm={async () => { await api(`/api/invoices/${cancelling.id}/cancel`, { method: 'POST' }); reload() }} />}
    </>
  )
}

function CreateInvoiceModal({ onClose, onCreated }) {
  const [form, setForm] = useState({ clientName: '', clientEmail: '', description: '', currency: 'USD', amount: '' })
  const [quote, setQuote] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  useEffect(() => {
    const a = Number(form.amount)
    if (!a || a < 1) { setQuote(null); return }
    const id = setTimeout(() => {
      api(`/api/fx/quote?currency=${form.currency}&amount=${a}`).then(setQuote).catch(() => setQuote(null))
    }, 350)
    return () => clearTimeout(id)
  }, [form.amount, form.currency])

  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try {
      const inv = await api('/api/invoices', { method: 'POST', body: { ...form, amount: Number(form.amount), clientEmail: form.clientEmail || null } })
      onCreated(inv); onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  return (
    <Modal title="New international invoice" onClose={onClose} width={560}>
      <form className="stack" onSubmit={submit}>
        <div className="row">
          <label className="field grow">Client name<input value={form.clientName} onChange={set('clientName')} maxLength={120} required /></label>
          <label className="field grow">Client email <span className="optional">optional</span><input type="email" value={form.clientEmail} onChange={set('clientEmail')} /></label>
        </div>
        <label className="field">What's it for?<input value={form.description} onChange={set('description')} maxLength={500} placeholder="e.g. Logo design — final files" required /></label>
        <div className="row">
          <label className="field" style={{ width: 120 }}>Currency
            <select value={form.currency} onChange={set('currency')}>{CURRENCIES.map((c) => <option key={c}>{c}</option>)}</select>
          </label>
          <label className="field grow">Amount<input type="number" min="1" max="100000" step="0.01" value={form.amount} onChange={set('amount')} required /></label>
        </div>
        {quote && <><p className="muted small">If your client paid right now:</p><QuoteBreakdown q={quote} /></>}
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create & copy link'}</button>
        </div>
      </form>
    </Modal>
  )
}
