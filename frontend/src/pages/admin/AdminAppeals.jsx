import { useState } from 'react'
import { api } from '../../api'
import { dateTime, money } from '../../format'
import { Empty, Icon, Loading, Modal, PageHeader, Pill, Until, useToast, usePoll } from '../../ui'
import { Timeline } from '../customer/Escrow.jsx'

const TYPE_LABEL = { HOLD: 'Early release', BLOCKED_TRANSFER: 'Blocked payment', ACCOUNT_FREEZE: 'Account freeze' }
const APPROVE_EFFECT = {
  HOLD: 'The held money is released to the customer immediately.',
  BLOCKED_TRANSFER: 'The payment is executed now (if the sender still has the funds) and its alerts are resolved.',
  ACCOUNT_FREEZE: 'The account is unfrozen immediately.'
}

export default function AdminAppeals() {
  const [tab, setTab] = useState('appeals')
  const [openOnly, setOpenOnly] = useState(true)
  const [deciding, setDeciding] = useState(null)
  const [resolving, setResolving] = useState(null)
  const [appeals, aErr, reloadAppeals] = usePoll(() => api(`/api/admin/appeals?open=${openOnly}`), 10000, [openOnly])
  const [disputes, dErr, reloadDisputes] = usePoll(() => api('/api/admin/disputes'), 10000)

  return (
    <>
      <PageHeader title="Appeals & disputes"
                  subtitle="Human review of automated decisions. Every decision needs a reason, and the customer sees it." />
      <div className="tabs">
        <button className={`tab ${tab === 'appeals' ? 'active' : ''}`} onClick={() => setTab('appeals')}>
          Appeals {appeals && openOnly ? `(${appeals.length})` : ''}
        </button>
        <button className={`tab ${tab === 'disputes' ? 'active' : ''}`} onClick={() => setTab('disputes')}>
          Escrow disputes {disputes ? `(${disputes.length})` : ''}
        </button>
      </div>

      {tab === 'appeals' && (
        <section className="panel">
          <div className="toolbar">
            <div className="tabs">
              <button className={`tab ${openOnly ? 'active' : ''}`} onClick={() => setOpenOnly(true)}>Open</button>
              <button className={`tab ${!openOnly ? 'active' : ''}`} onClick={() => setOpenOnly(false)}>All</button>
            </div>
            <span className="muted small">Sorted by deadline · SLA 48 hours</span>
          </div>
          {!appeals ? <Loading label={aErr || 'Loading…'} /> : appeals.length === 0 ? <Empty icon="check" title="No appeals waiting" /> : (
            <ul className="case-list">
              {appeals.map((a) => (
                <li key={a.id} className={`case ${a.overdue ? 'overdue' : ''}`}>
                  <div className="case-top">
                    <span><Pill tone="info">{TYPE_LABEL[a.type]}</Pill> <strong>{a.userName}</strong> <span className="muted small">{a.userEmail} · Trust {a.userTrustScore}</span></span>
                    {a.status === 'OPEN'
                      ? <span className={`small ${a.overdue ? 'text-danger' : 'muted'}`}><Icon name="clock" size={13} /> {a.overdue ? 'Overdue' : <Until iso={a.dueAt} prefix="Due in " />}</span>
                      : <Pill tone={a.status === 'APPROVED' ? 'success' : 'danger'}>{a.status}</Pill>}
                  </div>
                  <div className="context-box">{a.context}</div>
                  <p><Icon name="message" size={14} /> <em>“{a.message}”</em></p>
                  {a.status === 'OPEN'
                    ? <button className="btn btn-small btn-primary" onClick={() => setDeciding(a)}>Review & decide</button>
                    : <p className="muted small">Decided by {a.decidedBy} on {dateTime(a.decidedAt)}: {a.decisionNote}</p>}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {tab === 'disputes' && (
        <section className="panel">
          {!disputes ? <Loading label={dErr || 'Loading…'} /> : disputes.length === 0 ? <Empty icon="scale" title="No open disputes" /> : (
            <ul className="case-list">
              {disputes.map((d) => (
                <li key={d.milestone.id} className="case">
                  <div className="case-top">
                    <strong>{d.contractTitle} · {d.milestone.title}</strong>
                    <strong>{money(d.milestone.amount)}</strong>
                  </div>
                  <p className="muted small">Client: {d.clientName} ({d.clientEmail}) · Freelancer: {d.freelancerName} ({d.freelancerEmail})</p>
                  <Timeline events={d.milestone.events} />
                  <button className="btn btn-small btn-primary" onClick={() => setResolving(d)}>Resolve</button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {deciding && <DecideModal appeal={deciding} onClose={() => setDeciding(null)} onDone={reloadAppeals} />}
      {resolving && <ResolveModal dispute={resolving} onClose={() => setResolving(null)} onDone={reloadDisputes} />}
    </>
  )
}

function DecideModal({ appeal, onClose, onDone }) {
  const toast = useToast()
  const [approve, setApprove] = useState(true)
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try {
      await api(`/api/admin/appeals/${appeal.id}/decide`, { method: 'POST', body: { approve, note } })
      toast(`Appeal ${approve ? 'approved' : 'rejected'} — the customer has been told why`)
      onDone(); onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return (
    <Modal title={`Appeal #${appeal.id} · ${TYPE_LABEL[appeal.type]}`} onClose={onClose} width={560}>
      <form className="stack" onSubmit={submit}>
        <div className="context-box">{appeal.context}</div>
        <p><em>“{appeal.message}”</em> — {appeal.userName}</p>
        <div className="choice">
          <label className={approve ? 'on' : ''}><input type="radio" checked={approve} onChange={() => setApprove(true)} /> Approve</label>
          <label className={!approve ? 'on' : ''}><input type="radio" checked={!approve} onChange={() => setApprove(false)} /> Reject</label>
        </div>
        <p className="muted small">{approve ? APPROVE_EFFECT[appeal.type] : 'Nothing changes; the customer sees your reason.'}</p>
        <label className="field">Reason (shown to the customer)
          <textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} minLength={5} maxLength={500} required
                    placeholder={approve ? 'e.g. Verified the recipient is the customer’s landlord.' : 'e.g. Recipient is linked to confirmed fraud reports.'} />
        </label>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className={`btn ${approve ? 'btn-primary' : 'btn-danger'}`} disabled={busy || note.trim().length < 5}>{approve ? 'Approve' : 'Reject'}</button>
        </div>
      </form>
    </Modal>
  )
}

function ResolveModal({ dispute, onClose, onDone }) {
  const toast = useToast()
  const [outcome, setOutcome] = useState('RELEASE')
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try {
      await api(`/api/admin/disputes/${dispute.milestone.id}/resolve`, { method: 'POST', body: { outcome, note } })
      toast('Dispute resolved — both parties can see the decision')
      onDone(); onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return (
    <Modal title={`Resolve · ${dispute.milestone.title}`} onClose={onClose} width={520}>
      <form className="stack" onSubmit={submit}>
        <div className="choice">
          <label className={outcome === 'RELEASE' ? 'on' : ''}><input type="radio" checked={outcome === 'RELEASE'} onChange={() => setOutcome('RELEASE')} /> Pay freelancer ({dispute.freelancerName})</label>
          <label className={outcome === 'REFUND' ? 'on' : ''}><input type="radio" checked={outcome === 'REFUND'} onChange={() => setOutcome('REFUND')} /> Refund client ({dispute.clientName})</label>
        </div>
        <label className="field">Reason (added to the shared timeline)
          <textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} minLength={5} maxLength={500} required />
        </label>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy || note.trim().length < 5}>Resolve · {money(dispute.milestone.amount)}</button>
        </div>
      </form>
    </Modal>
  )
}
