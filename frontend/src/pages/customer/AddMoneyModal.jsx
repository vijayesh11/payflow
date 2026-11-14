import { useEffect, useState } from 'react'
import { api } from '../../api'
import { money } from '../../format'
import { DetailRows, Icon, Loading, Modal, useToast } from '../../ui'
import { OtpStep } from '../../otp.jsx'

const QUICK = [500, 1000, 2000, 5000, 10000]

export default function AddMoneyModal({ onClose, onDone, navigate }) {
  const toast = useToast()
  const [cards, setCards] = useState(null)
  const [cardId, setCardId] = useState('')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [challenge, setChallenge] = useState(null)   // step 2: bank OTP (3-D Secure style)

  useEffect(() => {
    api('/api/cards').then((c) => { setCards(c); if (c[0]) setCardId(String(c[0].id)) })
      .catch((e) => setError(e.message))
  }, [])

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      setChallenge(await startTopUp())
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  const startTopUp = () => api('/api/wallet/deposit', { method: 'POST', body: { cardId: Number(cardId), amount: Number(amount) } })

  const confirm = async (code, c) => {
    await api('/api/wallet/deposit/confirm', { method: 'POST', body: { challengeId: c.challengeId, code } })
    toast(`${money(amount)} added to your wallet`)
    onDone && onDone()
    onClose()
  }

  const card = cards && cards.find((c) => String(c.id) === cardId)

  return (
    <Modal title={challenge ? 'Verify with your bank' : 'Add money'} onClose={onClose}>
      {challenge ? (
        <div className="stack">
          <div className="secure-head">
            <span className="secure-badge"><Icon name="lock" size={14} /> SecureCode</span>
            <span className="muted small">Simulated card issuer check</span>
          </div>
          <DetailRows rows={[['Merchant', 'PayFlow Wallet'], ['Amount', <strong>{money(amount)}</strong>],
                             ['Card', card ? `${card.brand} •••• ${card.last4}` : '']]} />
          <OtpStep challenge={challenge} onSubmit={confirm} onResend={startTopUp} submitLabel={`Pay ${money(amount)}`}
                   onCancel={() => setChallenge(null)} />
        </div>
      ) : !cards ? <Loading /> : cards.length === 0 ? (
        <div className="stack">
          <p>Link a debit or credit card first — you'll use it to add money to your wallet.</p>
          <button className="btn btn-primary" onClick={() => { onClose(); navigate('cards', { add: true }) }}>
            <Icon name="card" /> Link a card
          </button>
        </div>
      ) : (
        <form className="stack" onSubmit={submit}>
          <label className="field">From card
            <select value={cardId} onChange={(e) => setCardId(e.target.value)}>
              {cards.map((c) => <option key={c.id} value={c.id}>{c.brand} •••• {c.last4}</option>)}
            </select>
          </label>
          <label className="field">Amount (₹)
            <input type="number" min="1" max="50000" step="0.01" value={amount}
                   onChange={(e) => setAmount(e.target.value)} placeholder="0.00" required autoFocus />
          </label>
          <div className="chips">
            {QUICK.map((q) => <button type="button" key={q} className="chip" onClick={() => setAmount(String(q))}>{money(q).replace('.00', '')}</button>)}
          </div>
          {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
            <button className="btn btn-primary" disabled={busy || !amount}>{busy ? 'Please wait…' : 'Continue'}</button>
          </div>
        </form>
      )}
    </Modal>
  )
}
