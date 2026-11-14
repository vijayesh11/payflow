import { api } from '../../api'
import { money, timeOnly } from '../../format'
import { BarChart, Empty, HBars, Icon, Loading, PageHeader, Pill, Stat, statusTone, usePoll } from '../../ui'

export default function AdminOverview({ navigate }) {
  const [data, error] = usePoll(async () => {
    const [stats, timeline, alerts] = await Promise.all([
      api('/api/admin/stats'), api('/api/admin/timeline'), api('/api/admin/alerts?open=true')
    ])
    return { stats, timeline, alerts }
  }, 5000)

  if (!data) return <Loading label={error || 'Loading…'} />
  const { stats, timeline, alerts } = data
  const blockRate = stats.totalTransfers ? (stats.blocked / stats.totalTransfers) * 100 : 0
  const chart = timeline.map((p) => ({
    label: new Date(p.hour).toLocaleTimeString(undefined, { hour: 'numeric' }),
    values: [p.completed, p.blocked]
  }))
  const rules = Object.entries(stats.alertsByRule).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value)

  return (
    <>
      <PageHeader title="Risk overview" subtitle="Live view of payments and fraud activity · refreshes every 5 seconds"
                  actions={<span className="live-dot">Live</span>} />
      <div className="stats-row">
        <Stat label="Payment volume (24h)" value={money(stats.volume24h)} icon="activity" tone="info" />
        <Stat label="Transfers" value={stats.totalTransfers.toLocaleString()} hint={`${stats.completed.toLocaleString()} completed`} icon="list" />
        <Stat label="Blocked" value={stats.blocked.toLocaleString()} hint={`${blockRate.toFixed(1)}% of all attempts`} icon="shield" tone="danger" />
        <Stat label="Open alerts" value={stats.openAlerts.toLocaleString()} hint="Awaiting review" icon="alert" tone={stats.openAlerts ? 'warning' : 'neutral'} />
        <Stat label="Users" value={stats.totalUsers.toLocaleString()} hint={`${stats.frozenUsers} frozen`} icon="users" />
      </div>
      <div className="stats-row">
        <button className="stat stat-link" onClick={() => navigate('appeals')}>
          <Stat label="Appeals waiting" value={stats.openAppeals} hint={stats.overdueAppeals ? `${stats.overdueAppeals} past the 48h SLA` : 'All within SLA'}
                icon="scale" tone={stats.overdueAppeals ? 'danger' : stats.openAppeals ? 'warning' : 'neutral'} />
        </button>
        <button className="stat stat-link" onClick={() => navigate('appeals')}>
          <Stat label="Escrow disputes" value={stats.openDisputes} hint="Awaiting a decision" icon="briefcase" tone={stats.openDisputes ? 'warning' : 'neutral'} />
        </button>
        <Stat label="Money on hold" value={money(stats.heldTotal)} hint="Progressive release" icon="clock" tone="info" />
        <Stat label="Held in escrow" value={money(stats.inEscrow)} hint="Funded milestones" icon="lock" tone="info" />
        <Stat label="Blocked rate" value={`${blockRate.toFixed(1)}%`} hint="of all transfer attempts" icon="shield" />
      </div>

      <div className="grid-2-1">
        <section className="panel">
          <div className="panel-head"><h2>Transfers per hour (last 24h)</h2></div>
          <BarChart data={chart} stacked labelEvery={3}
                    series={[{ name: 'Completed', color: 'var(--chart-2)' }, { name: 'Blocked', color: 'var(--danger)' }]} />
        </section>
        <section className="panel">
          <div className="panel-head"><h2>Alerts by rule</h2></div>
          {rules.length ? <HBars items={rules} color="var(--chart-1)" /> : <Empty icon="shield" title="No alerts yet" />}
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2>Needs review</h2>
          <button className="link-btn" onClick={() => navigate('alerts')}>All alerts</button>
        </div>
        {alerts.length === 0 ? <Empty icon="check" title="All clear">No open alerts.</Empty> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Time</th><th>User</th><th>Rule</th><th>Severity</th><th>Details</th></tr></thead>
              <tbody>
                {alerts.slice(0, 6).map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap">{timeOnly(a.createdAt)}</td>
                    <td>{a.userEmail}</td>
                    <td><code>{a.rule}</code></td>
                    <td><Pill tone={statusTone(a.severity)}>{a.severity}</Pill></td>
                    <td className="muted">{a.details}</td>
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
