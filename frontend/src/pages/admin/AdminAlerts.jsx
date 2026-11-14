import { useMemo, useState } from 'react'
import { api } from '../../api'
import { dateTime } from '../../format'
import { Empty, Loading, PageHeader, Pill, statusTone, useToast, usePoll } from '../../ui'

export default function AdminAlerts() {
  const toast = useToast()
  const [openOnly, setOpenOnly] = useState(true)
  const [severity, setSeverity] = useState('ALL')
  const [alerts, error, reload] = usePoll(() => api(`/api/admin/alerts?open=${openOnly}`), 5000, [openOnly])

  const visible = useMemo(() => (alerts || []).filter((a) => severity === 'ALL' || a.severity === severity), [alerts, severity])

  const resolve = async (a) => {
    await api(`/api/admin/alerts/${a.id}/resolve`, { method: 'POST' })
    toast(`Alert #${a.id} marked as reviewed`)
    reload()
  }

  return (
    <>
      <PageHeader title="Fraud alerts" subtitle="Rule hits raised by the real-time engine and the scheduled scan" />
      <section className="panel">
        <div className="toolbar">
          <div className="tabs">
            <button className={`tab ${openOnly ? 'active' : ''}`} onClick={() => setOpenOnly(true)}>Open</button>
            <button className={`tab ${!openOnly ? 'active' : ''}`} onClick={() => setOpenOnly(false)}>All</button>
          </div>
          <select className="select-sm" value={severity} onChange={(e) => setSeverity(e.target.value)}>
            <option value="ALL">All severities</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option>
          </select>
        </div>
        {!alerts ? <Loading label={error || 'Loading…'} /> : visible.length === 0
          ? <Empty icon="check" title="No alerts">Nothing matches these filters.</Empty> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>#</th><th>Time</th><th>User</th><th>Rule</th><th>Severity</th><th>Details</th><th>Transfer</th><th></th></tr></thead>
              <tbody>
                {visible.map((a) => (
                  <tr key={a.id}>
                    <td className="muted">{a.id}</td>
                    <td className="nowrap">{dateTime(a.createdAt)}</td>
                    <td>{a.userEmail}</td>
                    <td><code>{a.rule}</code></td>
                    <td><Pill tone={statusTone(a.severity)}>{a.severity}</Pill></td>
                    <td className="muted">{a.details}</td>
                    <td className="mono">{a.transferId ? `PF-T${a.transferId}` : 'batch scan'}</td>
                    <td>{a.resolved ? <Pill tone="neutral">Reviewed</Pill>
                      : <button className="btn btn-small btn-ghost" onClick={() => resolve(a)}>Mark reviewed</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}
