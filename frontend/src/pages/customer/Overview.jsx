import { useState } from 'react'
import { api } from '../../api'
import { dateTime, money } from '../../format'
import { Avatar, BarChart, Empty, Icon, Loading, PageHeader, Stat, TrustRing, compactMoney, usePoll } from '../../ui'
import AddMoneyModal from './AddMoneyModal.jsx'
import { buildActivity, ActivityRow } from './Activity.jsx'

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

export default function Overview({ session, navigate }) {
  const [adding, setAdding] = useState(false)
  const [data, error, reload] = usePoll(async () => {
    const [summary, transfers, topUps, profile, trust] = await Promise.all([
      api('/api/wallet/summary'), api('/api/transfers?limit=20'), api('/api/wallet/topups'), api('/api/me'), api('/api/me/trust')
    ])
    return { summary, profile, trust, activity: buildActivity(transfers, topUps, session.email).slice(0, 6) }
  }, 15000)

  if (!data) return <Loading label={error || 'Loading your wallet…'} />
  const { summary, activity, profile, trust } = data
  const chart = summary.last14Days.map((d) => ({
    label: new Date(d.date + 'T00:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short' }),
    values: [Number(d.sent), Number(d.received)]
  }))
  const firstName = session.fullName.split(' ')[0]

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${firstName}`}
        subtitle={session.previousLoginAt ? `Last sign-in ${dateTime(session.previousLoginAt)} — not you? Change your password in Security.` : 'Welcome to PayFlow.'}
      />

      {profile.status === 'FROZEN' && (
        <div className="alert alert-danger"><Icon name="lock" />
          <span><strong>Your account is frozen.</strong> Reason: {profile.freezeReason}. You can still view everything.{' '}
            <button className="link-btn" onClick={() => navigate('resolution')}>See details and appeal</button></span>
        </div>
      )}
      {!profile.hasPin && profile.status !== 'FROZEN' && (
        <div className="setup-banner">
          <span className="setup-icon"><Icon name="lock" /></span>
          <span className="grow"><strong>Set your UPI PIN to start sending money</strong>
            <span className="muted block small">Takes 30 seconds — we'll verify it's you with a one-time code.</span></span>
          <button className="btn btn-primary" onClick={() => navigate('security', { setPin: true })}>Set UPI PIN</button>
        </div>
      )}
      {profile.pinLocked && (
        <div className="alert alert-danger"><Icon name="lock" />
          <span>Your UPI PIN is locked after too many wrong attempts. <button className="link-btn" onClick={() => navigate('security', { setPin: true })}>Reset it</button></span>
        </div>
      )}

      <div className="overview-top">
        <div className="balance-card">
          <span className="balance-label">Available balance</span>
          <span className="balance-amount">{money(summary.balance)}</span>
          {Number(summary.heldBalance) > 0 && (
            <button className="held-line" onClick={() => navigate('resolution')}>
              <Icon name="clock" size={14} /> {money(summary.heldBalance)} on hold · see why and when it releases
            </button>
          )}
          <div className="balance-actions">
            <button className="btn btn-light" onClick={() => navigate('send')}><Icon name="send" /> Send</button>
            <button className="btn btn-outline-light" onClick={() => setAdding(true)}><Icon name="plus" /> Add money</button>
          </div>
        </div>
        <div className="stats-grid">
          <Stat label="Sent this month" value={money(summary.sentThisMonth)} icon="up" tone="neutral" />
          <Stat label="Received this month" value={money(summary.receivedThisMonth)} icon="down" tone="success" />
          <Stat label="Added this month" value={money(summary.addedThisMonth)} icon="plus" tone="info" />
          <button className="stat trust-stat" onClick={() => navigate('resolution')}>
            <TrustRing score={trust.score} size={58} />
            <span className="trust-stat-text">
              <span className="stat-label">Trust Score</span>
              <strong>{trust.tier}</strong>
              <span className="stat-hint">{trust.nextTier ? `${trust.pointsToNext} pts to ${trust.nextTier} →` : 'Payments release instantly'}</span>
            </span>
          </button>
        </div>
      </div>

      <div className="grid-2-1">
        <section className="panel">
          <div className="panel-head"><h2>Last 14 days</h2></div>
          <BarChart data={chart} labelEvery={2} format={compactMoney}
                    series={[{ name: 'Sent', color: 'var(--chart-1)' }, { name: 'Received', color: 'var(--chart-2)' }]} />
        </section>

        <section className="panel">
          <div className="panel-head"><h2>Send again</h2></div>
          {summary.recentContacts.length === 0
            ? <Empty icon="users" title="No contacts yet">People you pay will appear here.</Empty>
            : (
              <ul className="contacts">
                {summary.recentContacts.map((c) => (
                  <li key={c.email}>
                    <button className="contact" onClick={() => navigate('send', { to: c.email })}>
                      <Avatar name={c.fullName} />
                      <span className="contact-text"><strong>{c.fullName}</strong><span>{c.transfers} payment{c.transfers > 1 ? 's' : ''} · {money(c.total)}</span></span>
                      <Icon name="send" size={16} />
                    </button>
                  </li>
                ))}
              </ul>
            )}
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2>Recent activity</h2>
          <button className="link-btn" onClick={() => navigate('activity')}>View all</button>
        </div>
        {activity.length === 0
          ? <Empty icon="list" title="No activity yet">Add money from a card, then send your first payment.</Empty>
          : <ul className="activity-list">{activity.map((a) => <ActivityRow key={a.key} item={a} />)}</ul>}
      </section>

      {adding && <AddMoneyModal navigate={navigate} onClose={() => setAdding(false)} onDone={reload} />}
    </>
  )
}
