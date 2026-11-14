import { useMemo, useState } from 'react'
import { api } from '../../api'
import { dateTime, money } from '../../format'
import { Confirm, Empty, Icon, Loading, Modal, PageHeader, Pill, Progress, Until, useToast, usePoll } from '../../ui'
import { PinModal } from '../../otp.jsx'

export const MS_LABEL = {
  PENDING_FUNDING: ['Not funded', 'neutral'], FUNDED: ['Funded · in escrow', 'info'], SUBMITTED: ['Delivered · in review', 'warning'],
  DISPUTED: ['In dispute', 'danger'], RELEASED: ['Paid out', 'success'], REFUNDED: ['Refunded', 'neutral']
}

const EVENT_LABEL = {
  CREATED: 'Milestone created', FUNDED: 'Funded into escrow', SUBMITTED: 'Work delivered', APPROVED: 'Approved and paid',
  AUTO_RELEASED: 'Auto-released after review window', DISPUTED: 'Dispute opened', EVIDENCE: 'Evidence added',
  RESOLVED_RELEASE: 'Dispute resolved: paid to freelancer', RESOLVED_REFUND: 'Dispute resolved: refunded to client', REFUNDED: 'Refunded'
}

export function Timeline({ events }) {
  return (
    <ol className="timeline">
      {events.map((e, i) => (
        <li key={i}>
          <span className="tl-dot" />
          <div>
            <strong>{EVENT_LABEL[e.type] || e.type}</strong> <span className="muted small">· {e.actor} · {dateTime(e.createdAt)}</span>
            {e.note && <p className="tl-note">{e.note}</p>}
          </div>
        </li>
      ))}
    </ol>
  )
}

export default function Escrow({ session }) {
  const toast = useToast()
  const [tab, setTab] = useState('hiring')
  const [creating, setCreating] = useState(false)
  const [action, setAction] = useState(null)          // { kind, milestone, contract }
  const [contracts, error, reload] = usePoll(() => api('/api/escrow'), 20000)

  const list = useMemo(() => (contracts || []).filter((c) => (tab === 'hiring' ? c.role === 'CLIENT' : c.role === 'FREELANCER')), [contracts, tab])
  const counts = useMemo(() => ({
    hiring: (contracts || []).filter((c) => c.role === 'CLIENT').length,
    working: (contracts || []).filter((c) => c.role === 'FREELANCER').length
  }), [contracts])

  const run = async (path, body, message) => {
    await api(`/api/escrow/milestones/${action.milestone.id}/${path}`, { method: 'POST', body })
    toast(message)
    reload()
  }

  return (
    <>
      <PageHeader title="Escrow" subtitle="Milestone payments for freelance work: the client's money is held safely until the work is delivered."
                  actions={<button className="btn btn-primary" onClick={() => setCreating(true)}><Icon name="plus" /> New contract</button>} />

      <div className="how-it-works">
        {[['1', 'Client funds a milestone', 'Money leaves the client and is held by PayFlow — not the freelancer.'],
          ['2', 'Freelancer delivers', 'They submit the work; a review window starts.'],
          ['3', 'Approve or auto-release', 'Paid on approval, or automatically if the client stays silent.'],
          ['4', 'Fair disputes', 'Both sides add evidence to one shared timeline; a reviewer decides and explains why.']].map(([n, t, d]) => (
          <div key={n} className="hiw-step"><span className="hiw-n">{n}</span><strong>{t}</strong><span className="muted small">{d}</span></div>
        ))}
      </div>

      <div className="tabs">
        <button className={`tab ${tab === 'hiring' ? 'active' : ''}`} onClick={() => setTab('hiring')}>I'm hiring ({counts.hiring})</button>
        <button className={`tab ${tab === 'working' ? 'active' : ''}`} onClick={() => setTab('working')}>I'm working ({counts.working})</button>
      </div>

      {!contracts ? <Loading label={error || 'Loading…'} /> : list.length === 0 ? (
        <section className="panel">
          <Empty icon="briefcase" title={tab === 'hiring' ? 'No contracts yet' : 'Nobody has hired you through escrow yet'}>
            {tab === 'hiring' ? 'Create a contract to pay a freelancer safely, milestone by milestone.' : 'Share your PayFlow email with clients so they can create a contract.'}
          </Empty>
        </section>
      ) : list.map((c) => (
        <section key={c.id} className="panel contract">
          <div className="contract-head">
            <div>
              <h2>{c.title}</h2>
              <p className="muted small">{c.role === 'CLIENT' ? `Freelancer: ${c.freelancerName} (${c.freelancerEmail})` : `Client: ${c.clientName} (${c.clientEmail})`} · created {dateTime(c.createdAt)}</p>
              {c.description && <p className="contract-desc">{c.description}</p>}
            </div>
            <div className="contract-totals">
              <span className="muted small">Paid out</span>
              <strong>{money(c.released)} <span className="muted">of {money(c.total)}</span></strong>
              <Progress value={(Number(c.released) / Number(c.total)) * 100} tone="success" />
              {Number(c.inEscrow) > 0 && <span className="small">{money(c.inEscrow)} held in escrow</span>}
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>#</th><th>Milestone</th><th className="right">Amount</th><th>Status</th><th></th></tr></thead>
              <tbody>
                {c.milestones.map((m) => (
                  <tr key={m.id}>
                    <td className="muted">{m.position}</td>
                    <td>
                      <strong>{m.title}</strong>
                      {m.deliveryNote && <span className="muted small block">Delivery: {m.deliveryNote}</span>}
                      {m.status === 'SUBMITTED' && m.autoReleaseAt && (
                        <span className="small block text-warning">Auto-releases to the freelancer in <Until iso={m.autoReleaseAt} /></span>
                      )}
                    </td>
                    <td className="right nowrap">{money(m.amount)}</td>
                    <td><Pill tone={MS_LABEL[m.status][1]}>{MS_LABEL[m.status][0]}</Pill></td>
                    <td className="nowrap actions-cell">
                      <MilestoneActions role={c.role} m={m} onAction={(kind) => setAction({ kind, milestone: m, contract: c })} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}

      {creating && <CreateContractModal onClose={() => setCreating(false)} onCreated={() => { setTab('hiring'); reload() }} myEmail={session.email} />}

      {action?.kind === 'fund' && (
        <PinModal title="Fund milestone"
                  summary={<><span className="muted small">Into escrow for {action.contract.freelancerName}</span><strong>{action.milestone.title}</strong><span className="pin-amount">{money(action.milestone.amount)}</span></>}
                  onSubmit={(pin) => run('fund', { pin }, `${money(action.milestone.amount)} moved into escrow`)}
                  onClose={() => setAction(null)} />
      )}
      {action?.kind === 'approve' && (
        <Confirm title="Approve and pay?" confirmLabel={`Pay ${money(action.milestone.amount)}`}
                 message={`${money(action.milestone.amount)} will be released from escrow to ${action.contract.freelancerName}. This can't be undone.`}
                 onConfirm={() => run('approve', null, 'Milestone approved and paid')} onClose={() => setAction(null)} />
      )}
      {['submit', 'dispute', 'evidence', 'refund'].includes(action?.kind) && (
        <NoteModal kind={action.kind} milestone={action.milestone} onClose={() => setAction(null)}
                   onSubmit={(note) => run(action.kind, { note }, {
                     submit: 'Delivery submitted — the client has been notified', dispute: 'Dispute opened — a reviewer will decide',
                     evidence: 'Evidence added to the timeline', refund: 'Milestone refunded to the client'
                   }[action.kind])} />
      )}
      {action?.kind === 'timeline' && (
        <Modal title={`Timeline · ${action.milestone.title}`} onClose={() => setAction(null)} width={560}>
          <Timeline events={action.milestone.events} />
        </Modal>
      )}
    </>
  )
}

function MilestoneActions({ role, m, onAction }) {
  const b = (kind, label, cls = 'btn-ghost') => <button key={kind} className={`btn btn-small ${cls}`} onClick={() => onAction(kind)}>{label}</button>
  const out = []
  if (role === 'CLIENT') {
    if (m.status === 'PENDING_FUNDING') out.push(b('fund', 'Fund', 'btn-primary'))
    if (m.status === 'SUBMITTED') { out.push(b('approve', 'Approve & pay', 'btn-primary')); out.push(b('dispute', 'Dispute')) }
  } else {
    if (m.status === 'FUNDED') { out.push(b('submit', 'Submit delivery', 'btn-primary')); out.push(b('refund', 'Refund client')) }
  }
  if (m.status === 'DISPUTED') out.push(b('evidence', 'Add evidence'))
  out.push(b('timeline', 'Timeline'))
  return <div className="row wrap">{out}</div>
}

const NOTE_COPY = {
  submit: ['Submit delivery', 'Describe what you delivered and include a link (e.g. Drive, Figma, GitHub).', 'Submit'],
  dispute: ['Open a dispute', 'What is wrong with the delivery? Be specific — both sides and the reviewer will see this.', 'Open dispute'],
  evidence: ['Add evidence', 'Add facts, links or messages that support your side. Both parties see everything.', 'Add to timeline'],
  refund: ['Refund the client', 'Why are you cancelling this milestone? The client gets their money back immediately.', 'Refund']
}

function NoteModal({ kind, milestone, onSubmit, onClose }) {
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [title, prompt, label] = NOTE_COPY[kind]
  return (
    <Modal title={`${title} · ${milestone.title}`} onClose={onClose} width={520}>
      <form className="stack" onSubmit={async (e) => {
        e.preventDefault(); setBusy(true); setError('')
        try { await onSubmit(note); onClose() } catch (err) { setError(err.message) } finally { setBusy(false) }
      }}>
        {kind === 'dispute' && <div className="alert alert-info"><Icon name="pause" /> Opening a dispute stops the automatic release. A reviewer decides based on the shared timeline.</div>}
        <label className="field">Details
          <textarea rows={5} value={note} onChange={(e) => setNote(e.target.value)} placeholder={prompt} maxLength={1000} required minLength={3} />
        </label>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className={`btn ${kind === 'dispute' || kind === 'refund' ? 'btn-danger' : 'btn-primary'}`} disabled={busy || note.trim().length < 3}>{busy ? 'Saving…' : label}</button>
        </div>
      </form>
    </Modal>
  )
}

function CreateContractModal({ onClose, onCreated, myEmail }) {
  const toast = useToast()
  const [form, setForm] = useState({ freelancerEmail: '', title: '', description: '' })
  const [milestones, setMilestones] = useState([{ title: '', amount: '' }, { title: '', amount: '' }])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const total = milestones.reduce((s, m) => s + (Number(m.amount) || 0), 0)
  const setM = (i, k, v) => setMilestones(milestones.map((m, j) => (j === i ? { ...m, [k]: v } : m)))

  const submit = async (e) => {
    e.preventDefault()
    if (form.freelancerEmail.trim().toLowerCase() === myEmail) { setError("You can't hire yourself"); return }
    setBusy(true); setError('')
    try {
      await api('/api/escrow', { method: 'POST', body: { ...form, milestones: milestones.filter((m) => m.title && m.amount).map((m) => ({ title: m.title, amount: Number(m.amount) })) } })
      toast('Contract created — fund the first milestone to get started')
      onCreated(); onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  return (
    <Modal title="New escrow contract" onClose={onClose} width={600}>
      <form className="stack" onSubmit={submit}>
        <label className="field">Freelancer's PayFlow email
          <input type="email" value={form.freelancerEmail} onChange={(e) => setForm({ ...form, freelancerEmail: e.target.value })} required />
        </label>
        <label className="field">Project title
          <input value={form.title} maxLength={120} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="e.g. Website redesign" required />
        </label>
        <label className="field">Description <span className="optional">optional</span>
          <textarea rows={2} value={form.description} maxLength={1000} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </label>
        <div className="field">Milestones
          {milestones.map((m, i) => (
            <div key={i} className="row">
              <input className="grow" placeholder={`Milestone ${i + 1}`} value={m.title} maxLength={120} onChange={(e) => setM(i, 'title', e.target.value)} />
              <input type="number" min="1" step="0.01" placeholder="₹ amount" style={{ width: 140 }} value={m.amount} onChange={(e) => setM(i, 'amount', e.target.value)} />
              {milestones.length > 1 && <button type="button" className="icon-btn" onClick={() => setMilestones(milestones.filter((_, j) => j !== i))} aria-label="Remove"><Icon name="x" /></button>}
            </div>
          ))}
          {milestones.length < 10 && <button type="button" className="link-btn left" onClick={() => setMilestones([...milestones, { title: '', amount: '' }])}>+ Add milestone</button>}
        </div>
        <p className="muted">Total: <strong>{money(total)}</strong> — you fund each milestone separately, when you're ready.</p>
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy || !milestones.some((m) => m.title && Number(m.amount) > 0)}>{busy ? 'Creating…' : 'Create contract'}</button>
        </div>
      </form>
    </Modal>
  )
}
