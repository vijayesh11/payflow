import { useState } from 'react'
import { api } from '../../api'
import { dateOnly } from '../../format'
import { Confirm, Empty, Icon, Loading, Modal, PageHeader, useToast, usePoll } from '../../ui'
import AddMoneyModal from './AddMoneyModal.jsx'

export default function Cards({ params, navigate }) {
  const toast = useToast()
  const [linking, setLinking] = useState(!!params.add)
  const [removing, setRemoving] = useState(null)
  const [adding, setAdding] = useState(false)
  const [cards, error, reload] = usePoll(() => api('/api/cards'))

  return (
    <>
      <PageHeader title="Cards" subtitle="Debit and credit cards linked to your wallet"
                  actions={<button className="btn btn-primary" onClick={() => setLinking(true)}><Icon name="plus" /> Link a card</button>} />

      <div className="alert alert-info">
        <Icon name="lock" />
        <span>Your card is tokenized when you link it. PayFlow never stores your full card number or security code —
          only the brand, last 4 digits and expiry date.</span>
      </div>

      {!cards ? <Loading label={error || 'Loading cards…'} /> : cards.length === 0 ? (
        <section className="panel">
          <Empty icon="card" title="No cards linked">Link a card to add money to your wallet.</Empty>
        </section>
      ) : (
        <div className="card-grid">
          {cards.map((c) => (
            <div key={c.id} className="card-tile">
              <div className={`pay-card brand-${c.brand.toLowerCase()}`}>
                <div className="pay-card-top"><span className="chip-graphic" /><span className="pay-card-brand">{c.brand}</span></div>
                <div className="pay-card-number">•••• •••• •••• {c.last4}</div>
                <div className="pay-card-bottom">
                  <span>{c.holderName}</span>
                  <span>{String(c.expMonth).padStart(2, '0')}/{String(c.expYear).slice(-2)}</span>
                </div>
              </div>
              <div className="card-tile-foot">
                <span className="muted small">Linked {dateOnly(c.createdAt)}</span>
                <div className="row">
                  <button className="btn btn-small btn-ghost" onClick={() => setAdding(true)}>Add money</button>
                  <button className="btn btn-small btn-ghost text-danger" onClick={() => setRemoving(c)}>Remove</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {linking && <LinkCardModal onClose={() => setLinking(false)} onLinked={(c) => { toast(`${c.brand} •••• ${c.last4} linked`); reload() }} />}
      {adding && <AddMoneyModal navigate={navigate} onClose={() => setAdding(false)} />}
      {removing && (
        <Confirm title="Remove card?" danger confirmLabel="Remove card"
                 message={`${removing.brand} •••• ${removing.last4} will be removed from your wallet. Past transactions are not affected.`}
                 onClose={() => setRemoving(null)}
                 onConfirm={async () => { await api(`/api/cards/${removing.id}`, { method: 'DELETE' }); toast('Card removed'); reload() }} />
      )}
    </>
  )
}

function formatNumber(v) {
  return v.replace(/\D/g, '').slice(0, 19).replace(/(.{4})/g, '$1 ').trim()
}

function LinkCardModal({ onClose, onLinked }) {
  const [form, setForm] = useState({ cardNumber: '', expiry: '', cvv: '', holderName: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    const m = form.expiry.match(/^(\d{2})\s*\/\s*(\d{2})$/)
    if (!m) { setError('Enter expiry as MM/YY'); return }
    setBusy(true); setError('')
    try {
      const card = await api('/api/cards', {
        method: 'POST',
        body: { cardNumber: form.cardNumber.replace(/\s/g, ''), expMonth: Number(m[1]), expYear: 2000 + Number(m[2]),
                cvv: form.cvv, holderName: form.holderName }
      })
      setForm({ cardNumber: '', expiry: '', cvv: '', holderName: '' })   // don't keep card data in memory
      onLinked(card)
      onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  return (
    <Modal title="Link a card" onClose={onClose}>
      <form className="stack" onSubmit={submit} autoComplete="off">
        <label className="field">Card number
          <input inputMode="numeric" value={form.cardNumber} placeholder="1234 5678 9012 3456" autoComplete="cc-number"
                 onChange={(e) => setForm({ ...form, cardNumber: formatNumber(e.target.value) })} required />
        </label>
        <div className="row">
          <label className="field grow">Expiry
            <input value={form.expiry} placeholder="MM/YY" maxLength={5} autoComplete="cc-exp"
                   onChange={(e) => {
                     let v = e.target.value.replace(/[^\d]/g, '').slice(0, 4)
                     if (v.length > 2) v = v.slice(0, 2) + '/' + v.slice(2)
                     setForm({ ...form, expiry: v })
                   }} required />
          </label>
          <label className="field grow">Security code
            <input inputMode="numeric" type="password" value={form.cvv} placeholder="CVV" maxLength={4} autoComplete="cc-csc"
                   onChange={(e) => setForm({ ...form, cvv: e.target.value.replace(/\D/g, '') })} required />
          </label>
        </div>
        <label className="field">Name on card
          <input value={form.holderName} autoComplete="cc-name" onChange={(e) => setForm({ ...form, holderName: e.target.value })} required />
        </label>
        <p className="fine-print"><Icon name="info" size={13} /> Sandbox test cards: 4242 4242 4242 4242 (Visa), 5555 5555 5555 4444 (Mastercard), any future expiry and any 3-digit CVV.</p>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Linking…' : 'Link card'}</button>
        </div>
      </form>
    </Modal>
  )
}
