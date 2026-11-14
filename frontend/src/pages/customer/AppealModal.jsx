import { useState } from 'react'
import { api } from '../../api'
import { Icon, Modal, useToast } from '../../ui'

const COPY = {
  HOLD: { title: 'Request early release', prompt: 'What is this payment for? Anything that helps us verify it (invoice, relationship to the sender) speeds things up.' },
  BLOCKED_TRANSFER: { title: 'Appeal blocked payment', prompt: 'Tell us why this payment is genuine — who the recipient is and what it is for. If approved, it goes through immediately.' },
  ACCOUNT_FREEZE: { title: 'Appeal account freeze', prompt: 'Explain what happened. A reviewer will look at your account and reply with a decision and a reason.' }
}

export default function AppealModal({ type, targetId, context, onClose, onDone }) {
  const toast = useToast()
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const copy = COPY[type]

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await api('/api/appeals', { method: 'POST', body: { type, targetId, message } })
      toast('Appeal sent — you will get a decision within 48 hours')
      onDone && onDone()
      onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  return (
    <Modal title={copy.title} onClose={onClose} width={500}>
      <form className="stack" onSubmit={submit}>
        {context && <div className="context-box">{context}</div>}
        <label className="field">Your explanation
          <textarea rows={5} value={message} onChange={(e) => setMessage(e.target.value)} maxLength={1000}
                    placeholder={copy.prompt} required minLength={10} />
          <span className="field-hint">{message.length}/1000 · at least 10 characters</span>
        </label>
        <ul className="info-list compact">
          <li><Icon name="clock" /><span>A person reviews every appeal — you'll get a decision <strong>with the reason</strong> within 48 hours.</span></li>
        </ul>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy || message.trim().length < 10}>{busy ? 'Sending…' : 'Send appeal'}</button>
        </div>
      </form>
    </Modal>
  )
}

export function ExplanationList({ explanations }) {
  if (!explanations || explanations.length === 0) return null
  return (
    <ul className="explanations">
      {explanations.map((e, i) => (
        <li key={i}>
          <strong>{e.title}</strong>
          <span>{e.detail}</span>
          <span className="advice"><Icon name="info" size={14} /> {e.advice}</span>
        </li>
      ))}
    </ul>
  )
}
