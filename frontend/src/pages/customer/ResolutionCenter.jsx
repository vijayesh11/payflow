import { useState } from 'react'
import { api } from '../../api'
import { dateTime, money } from '../../format'
import { Empty, Icon, Loading, PageHeader, Pill, Progress, TrustRing, Until, usePoll } from '../../ui'
import AppealModal, { ExplanationList } from './AppealModal.jsx'

const APPEAL_LABEL = { HOLD: 'Early release request', BLOCKED_TRANSFER: 'Blocked payment', ACCOUNT_FREEZE: 'Account freeze' }

export default function ResolutionCenter({ params = {}, navigate }) {
  const [appeal, setAppeal] = useState(params.appeal || null)
  const [data, error, reload] = usePoll(() => api('/api/resolution'), 30000)

  if (!data) return <Loading label={error || 'Loading…'} />
  const { trust, holds, blocked, appeals, accountStatus, freezeReason, frozenAt } = data
  const held = holds.filter((h) => h.status === 'HELD')
  const openAppealFor = (type, id) => appeals.some((a) => a.status === 'OPEN' && a.type === type && a.targetId === id)

  return (
    <>
      <PageHeader title="Resolution Center"
                  subtitle="Everything about money that's held, blocked or under review — with the reason, and what you can do." />

      {accountStatus === 'FROZEN' && (
        <div className="alert alert-danger big-alert">
          <Icon name="lock" />
          <div className="grow">
            <strong>Your account is frozen</strong>
            <p>Reason: {freezeReason}</p>
            <p className="small">Since {dateTime(frozenAt)}. You can still sign in and see your history, but money can't be moved.</p>
          </div>
          {appeals.some((a) => a.type === 'ACCOUNT_FREEZE' && a.status === 'OPEN')
            ? <Pill tone="info">Appeal under review</Pill>
            : <button className="btn btn-danger" onClick={() => setAppeal({ type: 'ACCOUNT_FREEZE', context: `Frozen: ${freezeReason}` })}>Appeal</button>}
        </div>
      )}

      <section className="panel trust-panel">
        <div className="trust-head">
          <TrustRing score={trust.score} />
          <div>
            <h2>Trust Score · {trust.tier}</h2>
            <p className="muted">
              {trust.holdPercent === 0
                ? 'Payments you receive are available immediately.'
                : `Large payments you receive (₹2,000+) are ${trust.holdPercent}% held for a short time.`}
              {trust.nextTier && <> {trust.pointsToNext} more points to reach <strong>{trust.nextTier}</strong>.</>}
            </p>
            <p className="fine-print"><Icon name="info" size={13} /> Unlike most payment apps, we show you exactly how this score is worked out.</p>
          </div>
        </div>
        <ul className="trust-factors">
          {trust.factors.map((f) => (
            <li key={f.label} className={f.points < 0 ? 'neg' : f.achieved ? 'ok' : ''}>
              <span className="tf-icon"><Icon name={f.points < 0 ? 'alert' : f.achieved ? 'check' : 'plus'} size={14} /></span>
              <span className="tf-label">{f.label}{!f.achieved && f.hint && <span className="muted small block">{f.hint}</span>}</span>
              <span className="tf-points">{f.points > 0 ? `+${f.points}` : f.points}{f.maxPoints > 0 && <span className="muted"> / {f.maxPoints}</span>}</span>
            </li>
          ))}
        </ul>
        <div className="row">
          <button className="btn btn-ghost btn-small" onClick={() => navigate('security')}>Security settings</button>
          <button className="btn btn-ghost btn-small" onClick={() => navigate('cards')}>Cards</button>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Money on hold</h2>{held.length > 0 && <Pill tone="warning">{money(held.reduce((s, h) => s + Number(h.amount), 0))} held</Pill>}</div>
        {holds.length === 0 ? <Empty icon="check" title="Nothing on hold">All money you've received is available.</Empty> : (
          <ul className="case-list">
            {holds.map((h) => {
              const total = new Date(h.releaseAt) - new Date(h.createdAt)
              const done = h.status === 'RELEASED' ? 100 : ((Date.now() - new Date(h.createdAt)) / total) * 100
              return (
                <li key={h.id} className="case">
                  <div className="case-top">
                    <strong>{money(h.amount)} {h.fromName ? `from ${h.fromName}` : ''}</strong>
                    {h.status === 'HELD' ? <Pill tone="warning">On hold</Pill> : <Pill tone="success">Released</Pill>}
                  </div>
                  <p>{h.explanation}</p>
                  {h.status === 'HELD' ? (
                    <>
                      <Progress value={done} tone="warning" />
                      <p className="muted small">Releases in <Until iso={h.releaseAt} /> · {h.howToRelease}</p>
                      {h.appealOpen || openAppealFor('HOLD', h.id)
                        ? <Pill tone="info">Early release requested</Pill>
                        : <button className="btn btn-small btn-ghost" onClick={() => setAppeal({ type: 'HOLD', targetId: h.id, context: `${money(h.amount)} on hold — ${h.explanation}` })}>Request early release</button>}
                    </>
                  ) : <p className="muted small">Released {dateTime(h.releasedAt)} · {h.releasedBy}</p>}
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Blocked payments (last 30 days)</h2></div>
        {blocked.length === 0 ? <Empty icon="shield" title="No blocked payments" /> : (
          <ul className="case-list">
            {blocked.map((t) => (
              <li key={t.id} className="case">
                <div className="case-top">
                  <strong>{money(t.amount)} to {t.recipientName}</strong>
                  <span className="muted small">{dateTime(t.createdAt)} · PF-T{t.id}</span>
                </div>
                <ExplanationList explanations={t.explanations} />
                {openAppealFor('BLOCKED_TRANSFER', t.id)
                  ? <Pill tone="info">Appeal under review</Pill>
                  : <button className="btn btn-small btn-ghost" onClick={() => setAppeal({ type: 'BLOCKED_TRANSFER', targetId: t.id, context: `${money(t.amount)} to ${t.recipientEmail} — ${t.explanations.map((e) => e.title).join(', ')}` })}>This was me — appeal</button>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel">
        <div className="panel-head"><h2>My appeals</h2></div>
        {appeals.length === 0 ? <Empty icon="message" title="No appeals yet">If we ever hold or block something you think is fine, appeal it here.</Empty> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Type</th><th>About</th><th>Status</th><th>Decision</th><th>Sent</th></tr></thead>
              <tbody>
                {appeals.map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap">{APPEAL_LABEL[a.type]}</td>
                    <td className="muted">{a.context}</td>
                    <td>
                      <Pill tone={a.status === 'APPROVED' ? 'success' : a.status === 'REJECTED' ? 'danger' : 'info'}>{a.status === 'OPEN' ? 'In review' : a.status === 'APPROVED' ? 'Approved' : 'Rejected'}</Pill>
                      {a.status === 'OPEN' && <span className="muted small block">Answer due in <Until iso={a.dueAt} /></span>}
                    </td>
                    <td>{a.decisionNote || <span className="muted">—</span>}</td>
                    <td className="nowrap">{dateTime(a.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {appeal && <AppealModal type={appeal.type} targetId={appeal.targetId} context={appeal.context}
                              onClose={() => setAppeal(null)} onDone={reload} />}
    </>
  )
}
