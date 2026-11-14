import { useMemo, useState } from 'react'
import { api } from '../../api'
import { dateOnly, dateTime, download, money, timeOnly, toCsv } from '../../format'
import { Avatar, DetailRows, Empty, Icon, Loading, Modal, PageHeader, Pill, statusTone, usePoll } from '../../ui'
import { ExplanationList } from './AppealModal.jsx'

/** Merge transfers and card top-ups into one newest-first activity feed. */
export function buildActivity(transfers, topUps, myEmail, extra = {}) {
  // A blocked payment never reached the recipient, so only the sender sees it
  const visible = transfers.filter((t) => !(t.status === 'BLOCKED' && t.senderEmail !== myEmail))
  const items = visible.map((t) => {
    const outgoing = t.senderEmail === myEmail
    return {
      key: `t${t.id}`, kind: t.status === 'BLOCKED' ? 'blocked' : outgoing ? 'sent' : 'received',
      title: outgoing ? t.recipientName : t.senderName,
      subtitle: (t.note || (outgoing ? 'Payment sent' : 'Payment received'))
        + (!outgoing && t.heldAmount ? ` · ${money(t.heldAmount)} on hold` : '') + (t.reviewNote ? ' · released after review' : ''),
      amount: Number(t.amount), sign: t.status === 'BLOCKED' ? '' : outgoing ? '−' : '+',
      date: t.createdAt, raw: t, type: 'transfer'
    }
  })
  topUps.forEach((u) => items.push({
    key: `u${u.id}`, kind: 'added', title: `Added from ${u.cardBrand} •••• ${u.cardLast4}`,
    subtitle: 'Money added', amount: Number(u.amount), sign: '+', date: u.createdAt, raw: u, type: 'topup'
  }))
  ;(extra.escrow || []).forEach((c) => c.milestones.forEach((m) => {
    if (c.role === 'CLIENT' && m.fundedAt) items.push({ key: `ef${m.id}`, kind: 'escrow', title: `Escrow: ${m.title}`,
      subtitle: `Funded for ${c.freelancerName}`, amount: Number(m.amount), sign: '−', date: m.fundedAt, raw: { ...m, contract: c }, type: 'escrow' })
    if (c.role === 'CLIENT' && m.status === 'REFUNDED') items.push({ key: `er${m.id}`, kind: 'received', title: `Escrow refund: ${m.title}`,
      subtitle: `From ${c.title}`, amount: Number(m.amount), sign: '+', date: m.closedAt, raw: { ...m, contract: c }, type: 'escrow' })
    if (c.role === 'FREELANCER' && m.status === 'RELEASED') items.push({ key: `ep${m.id}`, kind: 'received', title: `Escrow payout: ${m.title}`,
      subtitle: `From ${c.clientName}`, amount: Number(m.amount), sign: '+', date: m.closedAt, raw: { ...m, contract: c }, type: 'escrow' })
  }))
  ;(extra.invoices || []).filter((i) => i.status === 'PAID').forEach((i) => items.push({ key: `i${i.id}`, kind: 'received',
    title: `International payment · ${i.clientName}`, subtitle: `${i.currency} ${Number(i.amount).toFixed(2)} converted at ₹${Number(i.rate).toFixed(2)}`,
    amount: Number(i.netInr), sign: '+', date: i.paidAt, raw: i, type: 'invoice' }))
  return items.sort((a, b) => new Date(b.date) - new Date(a.date))
}

export function ActivityRow({ item, onClick }) {
  return (
    <li className={`activity-row ${onClick ? 'clickable' : ''}`} onClick={onClick}>
      {item.kind === 'added' ? <span className="avatar avatar-icon"><Icon name="plus" size={16} /></span>
        : item.type === 'escrow' ? <span className="avatar avatar-icon escrow"><Icon name="briefcase" size={16} /></span>
        : item.type === 'invoice' ? <span className="avatar avatar-icon intl"><Icon name="globe" size={16} /></span>
        : <Avatar name={item.title} />}
      <span className="activity-text">
        <strong>{item.title}</strong>
        <span>{item.subtitle} · {dateTime(item.date)}</span>
      </span>
      <span className="activity-amount">
        <span className={`amt amt-${item.kind}`}>{item.sign}{money(item.amount)}</span>
        {item.kind === 'blocked' && <Pill tone="danger">Blocked</Pill>}
      </span>
    </li>
  )
}

const FILTERS = [
  { id: 'all', label: 'All' }, { id: 'sent', label: 'Sent' }, { id: 'received', label: 'Received' },
  { id: 'added', label: 'Added' }, { id: 'escrow', label: 'Escrow' }, { id: 'blocked', label: 'Blocked' }
]

export default function Activity({ session }) {
  const [filter, setFilter] = useState('all')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState(null)
  const [items, error] = usePoll(async () => {
    const [transfers, topUps, escrow, invoices] = await Promise.all([
      api('/api/transfers?limit=500'), api('/api/wallet/topups'), api('/api/escrow'), api('/api/invoices')])
    return buildActivity(transfers, topUps, session.email, { escrow, invoices })
  }, 30000)

  const visible = useMemo(() => {
    if (!items) return []
    const q = query.trim().toLowerCase()
    return items.filter((i) => (filter === 'all' || i.kind === filter || (filter === 'escrow' && i.type === 'escrow')) &&
      (!q || [i.title, i.subtitle, i.raw.senderEmail, i.raw.recipientEmail].some((v) => v && v.toLowerCase().includes(q))))
  }, [items, filter, query])

  const groups = useMemo(() => {
    const g = []
    visible.forEach((i) => {
      const d = dateOnly(i.date)
      if (!g.length || g[g.length - 1].date !== d) g.push({ date: d, items: [] })
      g[g.length - 1].items.push(i)
    })
    return g
  }, [visible])

  const exportCsv = () => {
    const rows = [['Date', 'Type', 'Counterparty', 'Note', 'Amount (INR)', 'Status', 'Reference']]
    visible.forEach((i) => rows.push([
      new Date(i.date).toISOString(), i.kind, i.title, i.type === 'transfer' ? (i.raw.note || '') : '',
      `${i.sign === '−' ? '-' : ''}${i.amount.toFixed(2)}`, i.type === 'transfer' ? i.raw.status : 'COMPLETED',
      i.type === 'transfer' ? `PF-T${i.raw.id}` : `PF-A${i.raw.id}`
    ]))
    download(`payflow-statement-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(rows))
  }

  return (
    <>
      <PageHeader title="Activity" subtitle="All payments and top-ups on your wallet"
                  actions={<button className="btn btn-ghost" onClick={exportCsv} disabled={!visible.length}><Icon name="download" /> Download CSV</button>} />
      <section className="panel">
        <div className="toolbar">
          <div className="tabs">
            {FILTERS.map((f) => (
              <button key={f.id} className={`tab ${filter === f.id ? 'active' : ''}`} onClick={() => setFilter(f.id)}>{f.label}</button>
            ))}
          </div>
          <div className="search">
            <Icon name="search" size={16} />
            <input placeholder="Search name, email or note" value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
        </div>

        {!items ? <Loading label={error || 'Loading…'} /> : groups.length === 0
          ? <Empty icon="search" title="Nothing here">Try a different filter or search.</Empty>
          : groups.map((g) => (
            <div key={g.date}>
              <div className="group-label">{g.date}</div>
              <ul className="activity-list">
                {g.items.map((i) => <ActivityRow key={i.key} item={i} onClick={() => setSelected(i)} />)}
              </ul>
            </div>
          ))}
      </section>

      {selected && <ActivityDetails item={selected} onClose={() => setSelected(null)} />}
    </>
  )
}

function ActivityDetails({ item, onClose }) {
  const t = item.raw
  if (item.type === 'escrow') {
    return (
      <Modal title={item.title} onClose={onClose}>
        <div className={`receipt-amount amt-${item.kind}`}>{item.sign}{money(item.amount)}</div>
        <DetailRows rows={[['Contract', t.contract.title], ['Client', t.contract.clientName], ['Freelancer', t.contract.freelancerName],
                           ['Milestone status', t.status.replace('_', ' ').toLowerCase()], ['Date', dateTime(item.date)]]} />
      </Modal>
    )
  }
  if (item.type === 'invoice') {
    return (
      <Modal title="International payment" onClose={onClose}>
        <div className="receipt-amount amt-received">+{money(t.netInr)}</div>
        <DetailRows rows={[['From', `${t.clientName} (paid by ${t.payerName})`], ['Invoice amount', `${t.currency} ${Number(t.amount).toFixed(2)}`],
                           ['Rate (mid-market)', `₹${Number(t.rate).toFixed(4)}`], ['Converted', money(t.grossInr)], ['Fee (0.5%)', `− ${money(t.feeInr)}`],
                           ['A typical provider would have paid', money(t.typicalNetInr)], ['Date', dateTime(t.paidAt)], ['Reference', `PF-I${t.id}`]]} />
      </Modal>
    )
  }
  if (item.type === 'topup') {
    return (
      <Modal title="Money added" onClose={onClose}>
        <div className="receipt-amount amt-added">+{money(t.amount)}</div>
        <DetailRows rows={[
          ['Status', <Pill tone="success">Completed</Pill>],
          ['From', `${t.cardBrand} •••• ${t.cardLast4}`],
          ['Date', dateTime(t.createdAt)],
          ['Reference', `PF-A${t.id}`]
        ]} />
      </Modal>
    )
  }
  return (
    <Modal title={item.kind === 'blocked' ? 'Blocked payment' : item.kind === 'sent' ? 'Payment sent' : 'Payment received'} onClose={onClose}>
      <div className={`receipt-amount amt-${item.kind}`}>{item.sign}{money(t.amount)}</div>
      <DetailRows rows={[
        ['Status', <Pill tone={statusTone(t.status)}>{t.status === 'COMPLETED' ? 'Completed' : 'Blocked'}</Pill>],
        ['From', `${t.senderName} (${t.senderEmail})`],
        ['To', `${t.recipientName} (${t.recipientEmail})`],
        t.note && ['Note', t.note],
        ['Date', `${dateOnly(t.createdAt)}, ${timeOnly(t.createdAt)}`],
        t.heldAmount && item.kind === 'received' && ['On hold', `${money(t.heldAmount)} — see Resolution Center`],
        t.reviewNote && ['Review', t.reviewNote],
        t.country && ['Location', t.country],
        ['Reference', `PF-T${t.id}`]
      ]} />
      {t.status === 'BLOCKED' && (
        <>
          <div className="alert alert-danger"><Icon name="shield" /> No money moved. This payment was stopped by fraud protection.</div>
          <ExplanationList explanations={t.explanations} />
        </>
      )}
    </Modal>
  )
}
