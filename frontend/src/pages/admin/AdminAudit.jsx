import { useMemo, useState } from 'react'
import { api } from '../../api'
import { ACTION_LABELS, dateTime, device, download, toCsv } from '../../format'
import { Empty, Icon, Loading, PageHeader, Pill, usePoll } from '../../ui'

const RISKY = new Set(['LOGIN_FAILED', 'LOGIN_BLOCKED', 'ACCOUNT_LOCKED', 'PASSWORD_CHANGE_FAILED', 'USER_FROZEN'])

export default function AdminAudit() {
  const [action, setAction] = useState('ALL')
  const [query, setQuery] = useState('')
  const [logs, error] = usePoll(() => api('/api/admin/audit'), 10000)

  const actions = useMemo(() => [...new Set((logs || []).map((l) => l.action))].sort(), [logs])
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (logs || []).filter((l) => (action === 'ALL' || l.action === action) &&
      (!q || [l.actorEmail, l.details, l.ipAddress].some((v) => v && v.toLowerCase().includes(q))))
  }, [logs, action, query])

  const exportCsv = () => {
    const rows = [['Time (UTC)', 'Actor', 'Action', 'Details', 'IP', 'User agent']]
    visible.forEach((l) => rows.push([l.createdAt, l.actorEmail, l.action, l.details || '', l.ipAddress || '', l.userAgent || '']))
    download(`payflow-audit-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(rows))
  }

  return (
    <>
      <PageHeader title="Audit log" subtitle="Security events: sign-ins, lockouts, card and admin actions (latest 300)"
                  actions={<button className="btn btn-ghost" onClick={exportCsv} disabled={!visible.length}><Icon name="download" /> Export CSV</button>} />
      <section className="panel">
        <div className="toolbar">
          <select className="select-sm" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="ALL">All events</option>
            {actions.map((a) => <option key={a} value={a}>{ACTION_LABELS[a] || a}</option>)}
          </select>
          <div className="search">
            <Icon name="search" size={16} />
            <input placeholder="Search user, details or IP" value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
        </div>
        {!logs ? <Loading label={error || 'Loading…'} /> : visible.length === 0 ? <Empty icon="file" title="No events" /> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Time</th><th>Actor</th><th>Event</th><th>Details</th><th>Device</th><th>IP</th></tr></thead>
              <tbody>
                {visible.map((l) => (
                  <tr key={l.id}>
                    <td className="nowrap">{dateTime(l.createdAt)}</td>
                    <td>{l.actorEmail}</td>
                    <td>{RISKY.has(l.action) ? <Pill tone="danger">{ACTION_LABELS[l.action] || l.action}</Pill> : (ACTION_LABELS[l.action] || l.action)}</td>
                    <td className="muted">{l.details || '—'}</td>
                    <td className="nowrap">{device(l.userAgent)}</td>
                    <td className="mono">{l.ipAddress || '—'}</td>
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
