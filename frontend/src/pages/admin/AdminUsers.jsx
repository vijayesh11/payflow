import { useMemo, useState } from 'react'
import { api } from '../../api'
import { dateOnly, money, relative } from '../../format'
import { Avatar, Confirm, Empty, Icon, Loading, Modal, PageHeader, Pill, statusTone, useToast, usePoll } from '../../ui'

export default function AdminUsers() {
  const toast = useToast()
  const [query, setQuery] = useState('')
  const [action, setAction] = useState(null)
  const [users, error, reload] = usePoll(() => api('/api/admin/users'), 10000)

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (users || []).filter((u) => !q || u.email.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q))
  }, [users, query])

  const ACTIONS = {
    freeze: { title: 'Freeze account?', label: 'Freeze', danger: true, done: 'frozen',
              message: (u) => `${u.fullName} won't be able to sign in, add money or send money until unfrozen.` },
    unfreeze: { title: 'Unfreeze account?', label: 'Unfreeze', done: 'unfrozen',
                message: (u) => `${u.fullName} will regain full access.` },
    unlock: { title: 'Unlock account?', label: 'Unlock', done: 'unlocked',
              message: (u) => `Clears the sign-in and UPI PIN locks on ${u.email}. Only do this after verifying the user's identity.` }
  }

  return (
    <>
      <PageHeader title="Users" subtitle="Customer accounts, balances and access controls" />
      <section className="panel">
        <div className="toolbar">
          <span className="muted">{users ? `${users.length} accounts` : ''}</span>
          <div className="search">
            <Icon name="search" size={16} />
            <input placeholder="Search name or email" value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
        </div>
        {!users ? <Loading label={error || 'Loading…'} /> : visible.length === 0 ? <Empty icon="users" title="No users found" /> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>User</th><th>Role</th><th>Status</th><th className="right">Balance</th><th>Trust</th><th>Security</th><th>Cards</th><th>Joined</th><th>Last sign-in</th><th></th></tr></thead>
              <tbody>
                {visible.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <div className="user-cell">
                        <Avatar name={u.fullName} size={30} />
                        <span><strong>{u.fullName}</strong><span className="muted small block">{u.email}</span></span>
                      </div>
                    </td>
                    <td>{u.role === 'ADMIN' ? <Pill tone="info">Admin</Pill> : 'Customer'}</td>
                    <td>
                      <Pill tone={statusTone(u.status)}>{u.status === 'ACTIVE' ? 'Active' : 'Frozen'}</Pill>
                      {u.freezeReason && <span className="muted small block" title={u.freezeReason}>{u.freezeReason.slice(0, 40)}{u.freezeReason.length > 40 ? '…' : ''}</span>}
                      {u.locked && <> <Pill tone="warning">Login locked</Pill></>}
                      {u.pinLocked && <> <Pill tone="warning">PIN locked</Pill></>}
                    </td>
                    <td className="right nowrap">{money(u.balance)}{Number(u.heldBalance) > 0 && <span className="muted small block">+{money(u.heldBalance)} held</span>}</td>
                    <td className="nowrap"><strong>{u.trustScore}</strong> <span className="muted small">{u.trustTier}</span></td>
                    <td className="nowrap small">
                      <span className={u.hasPin ? '' : 'muted'}>{u.hasPin ? 'PIN ✓' : 'No PIN'}</span>{' · '}
                      <span className={u.twoFactorEnabled ? '' : 'text-danger'}>{u.twoFactorEnabled ? '2FA ✓' : '2FA off'}</span>
                    </td>
                    <td>{u.cards}</td>
                    <td className="nowrap">{dateOnly(u.createdAt)}</td>
                    <td className="nowrap">{relative(u.lastLoginAt)}</td>
                    <td className="nowrap">
                      {u.role !== 'ADMIN' && (u.status === 'ACTIVE'
                        ? <button className="btn btn-small btn-ghost text-danger" onClick={() => setAction({ type: 'freeze', user: u })}>Freeze</button>
                        : <button className="btn btn-small btn-ghost" onClick={() => setAction({ type: 'unfreeze', user: u })}>Unfreeze</button>)}
                      {(u.locked || u.pinLocked) && <button className="btn btn-small btn-ghost" onClick={() => setAction({ type: 'unlock', user: u })}>Unlock</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {action?.type === 'freeze' && <FreezeModal user={action.user} onClose={() => setAction(null)} onDone={reload} />}
      {action && action.type !== 'freeze' && (() => {
        const a = ACTIONS[action.type]
        return (
          <Confirm title={a.title} confirmLabel={a.label} danger={a.danger} message={a.message(action.user)}
                   onClose={() => setAction(null)}
                   onConfirm={async () => {
                     await api(`/api/admin/users/${action.user.id}/${action.type}`, { method: 'POST' })
                     toast(`${action.user.email} ${a.done}`)
                     reload()
                   }} />
        )
      })()}
    </>
  )
}

const FREEZE_REASONS = [
  'Unusual sign-in activity — we need to confirm it was you',
  'Multiple payments to accounts reported for fraud',
  'Identity verification required',
  'Received funds linked to a fraud report'
]

/** A freeze always carries a reason the customer will see in their Resolution Center. */
function FreezeModal({ user, onClose, onDone }) {
  const toast = useToast()
  const [reason, setReason] = useState(FREEZE_REASONS[0])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try {
      await api(`/api/admin/users/${user.id}/freeze`, { method: 'POST', body: { reason } })
      toast(`${user.email} frozen — they can see the reason and appeal`)
      onDone(); onClose()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return (
    <Modal title={`Freeze ${user.fullName}?`} onClose={onClose} width={500}>
      <form className="stack" onSubmit={submit}>
        <p className="muted">They can still sign in and see their history, but can't add, send or receive money until unfrozen.</p>
        <label className="field">Reason (shown to the customer)
          <select value={FREEZE_REASONS.includes(reason) ? reason : '__custom'} onChange={(e) => setReason(e.target.value === '__custom' ? '' : e.target.value)}>
            {FREEZE_REASONS.map((r) => <option key={r} value={r}>{r}</option>)}
            <option value="__custom">Other…</option>
          </select>
        </label>
        {!FREEZE_REASONS.includes(reason) && (
          <label className="field">Custom reason<textarea rows={2} value={reason} onChange={(e) => setReason(e.target.value)} minLength={5} maxLength={300} required /></label>
        )}
        {error && <div className="alert alert-danger"><Icon name="alert" /> {error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-danger" disabled={busy || reason.trim().length < 5}>Freeze account</button>
        </div>
      </form>
    </Modal>
  )
}
