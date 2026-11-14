import { useMemo, useState } from 'react'
import { api } from '../../api'
import { dateTime, money } from '../../format'
import { DetailRows, Empty, Icon, Loading, Modal, PageHeader, Pill, statusTone, usePoll } from '../../ui'

export default function AdminTransactions() {
  const [status, setStatus] = useState('ALL')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState(null)
  const [transfers, error] = usePoll(() => api('/api/admin/transfers?limit=500'), 5000)

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (transfers || []).filter((t) => (status === 'ALL' || t.status === status) &&
      (!q || [t.senderEmail, t.recipientEmail, t.senderName, t.recipientName, `pf-t${t.id}`].some((v) => v && v.toLowerCase().includes(q))))
  }, [transfers, status, query])

  return (
    <>
      <PageHeader title="Transactions" subtitle="Latest 500 transfer attempts across all users" />
      <section className="panel">
        <div className="toolbar">
          <div className="tabs">
            {['ALL', 'COMPLETED', 'BLOCKED'].map((s) => (
              <button key={s} className={`tab ${status === s ? 'active' : ''}`} onClick={() => setStatus(s)}>
                {s === 'ALL' ? 'All' : s === 'COMPLETED' ? 'Completed' : 'Blocked'}
              </button>
            ))}
          </div>
          <div className="search">
            <Icon name="search" size={16} />
            <input placeholder="Search user or reference" value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
        </div>
        {!transfers ? <Loading label={error || 'Loading…'} /> : visible.length === 0
          ? <Empty icon="search" title="No transactions match" /> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Reference</th><th>Time</th><th>From</th><th>To</th><th className="right">Amount</th><th>Loc.</th><th>Status</th><th>Reason</th></tr></thead>
              <tbody>
                {visible.map((t) => (
                  <tr key={t.id} className="clickable" onClick={() => setSelected(t)}>
                    <td className="mono">PF-T{t.id}</td>
                    <td className="nowrap">{dateTime(t.createdAt)}</td>
                    <td>{t.senderEmail}</td>
                    <td>{t.recipientEmail}</td>
                    <td className="right nowrap">{money(t.amount)}</td>
                    <td>{t.country || '—'}</td>
                    <td><Pill tone={statusTone(t.status)}>{t.status}</Pill></td>
                    <td className="muted truncate">{t.fraudReason || ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {selected && (
        <Modal title={`Transfer PF-T${selected.id}`} onClose={() => setSelected(null)} width={560}>
          <div className="receipt-amount">{money(selected.amount)}</div>
          <DetailRows rows={[
            ['Status', <Pill tone={statusTone(selected.status)}>{selected.status}</Pill>],
            ['From', `${selected.senderName} (${selected.senderEmail})`],
            ['To', `${selected.recipientName} (${selected.recipientEmail})`],
            selected.note && ['Note', selected.note],
            ['Time', dateTime(selected.createdAt)],
            ['Location', selected.country || 'Unknown'],
            selected.fraudReason && ['Rule hits', selected.fraudReason]
          ]} />
        </Modal>
      )}
    </>
  )
}
