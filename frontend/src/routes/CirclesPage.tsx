import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  banMember,
  cancelCircle,
  createCircle,
  fetchCircleMembers,
  fetchMyCircle,
  fetchMyCircleHistory,
  fetchOpenCircles,
  inviteToCircle,
  joinCircle,
  leaveCircle,
  redeemInvite,
  removeMember,
  type Circle,
  type CircleSizingPolicyType,
} from '../api/circles'
import { ApiError } from '../api/client'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../auth/AuthContext'

export function CirclesPage() {
  const { data: circle, isLoading } = useQuery({
    queryKey: queryKeys.myCircle,
    queryFn: async () => {
      try {
        return await fetchMyCircle()
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) {
          return null
        }
        throw err
      }
    },
  })

  return (
    <div>
      <h1>Circles</h1>
      <MyCircleHistory />
      {isLoading ? <p>Loading…</p> : circle ? <MyCircle circle={circle} /> : <NoCircle />}
    </div>
  )
}

function MyCircleHistory() {
  const { data: history } = useQuery({
    queryKey: queryKeys.myCircleHistory,
    queryFn: fetchMyCircleHistory,
  })

  if (!history || history.length === 0) {
    return null
  }

  return (
    <div>
      <h2>My circles</h2>
      <ul>
        {history.map((entry) => (
          <li key={entry.circleId}>
            {entry.circleName} — {entry.membershipStatus} (circle {entry.circleStatus})
            {entry.position != null && `, position ${entry.position}`}
          </li>
        ))}
      </ul>
    </div>
  )
}

function BrowseCircles({ onJoined }: { onJoined: () => void }) {
  const queryClient = useQueryClient()
  const [joiningId, setJoiningId] = useState<string | null>(null)
  const [joinError, setJoinError] = useState<string | null>(null)

  const { data: openCircles } = useQuery({
    queryKey: queryKeys.openCircles,
    queryFn: fetchOpenCircles,
  })

  const joinMutation = useMutation({
    mutationFn: joinCircle,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircle })
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircleHistory })
      queryClient.invalidateQueries({ queryKey: queryKeys.openCircles })
      onJoined()
    },
  })

  async function handleJoin(circleId: string) {
    setJoinError(null)
    setJoiningId(circleId)
    try {
      await joinMutation.mutateAsync(circleId)
    } catch (err) {
      setJoinError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setJoiningId(null)
    }
  }

  return (
    <div>
      <h2>Browse circles</h2>
      {(!openCircles || openCircles.length === 0) && <p>No circles to join right now.</p>}
      {openCircles && openCircles.length > 0 && (
        <ul>
          {openCircles.map((openCircle) => (
            <li key={openCircle.id}>
              <strong>{openCircle.name}</strong> — {openCircle.description} ({openCircle.contributionAmount.toFixed(2)}
              {' '}{openCircle.currency}/round, {openCircle.term}wk, {openCircle.maxMembers} members, collateral{' '}
              {openCircle.collateral.toFixed(2)} {openCircle.currency})
              {openCircle.gated && ' · invite-only'}
              {openCircle.minReputationScore != null && ` · min reputation ${openCircle.minReputationScore}`}{' '}
              <button
                onClick={() => handleJoin(openCircle.id)}
                disabled={joinMutation.isPending && joiningId === openCircle.id}
              >
                {joinMutation.isPending && joiningId === openCircle.id ? 'Joining…' : 'Join'}
              </button>
            </li>
          ))}
        </ul>
      )}
      {joinError && <p role="alert">{joinError}</p>}
    </div>
  )
}

function NoCircle() {
  const queryClient = useQueryClient()

  function invalidateMembershipQueries() {
    queryClient.invalidateQueries({ queryKey: queryKeys.myCircle })
    queryClient.invalidateQueries({ queryKey: queryKeys.myCircleHistory })
    queryClient.invalidateQueries({ queryKey: queryKeys.openCircles })
  }

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [sizingPolicyType, setSizingPolicyType] = useState<CircleSizingPolicyType>('FIXED_SIZE')
  const [maxMembers, setMaxMembers] = useState('')
  const [minMembers, setMinMembers] = useState('')
  const [enrollmentDeadline, setEnrollmentDeadline] = useState('')
  const [term, setTerm] = useState('')
  const [contributionAmount, setContributionAmount] = useState('')
  const [gated, setGated] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createCircle,
    onSuccess: invalidateMembershipQueries,
  })

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    setCreateError(null)
    try {
      await createMutation.mutateAsync({
        name,
        description,
        sizingPolicyType,
        maxMembers: Number(maxMembers),
        term: Number(term),
        contributionAmount: Number(contributionAmount),
        gated,
        ...(sizingPolicyType === 'OPEN_ENROLLMENT' && {
          minMembers: minMembers ? Number(minMembers) : undefined,
          enrollmentDeadline: new Date(enrollmentDeadline).toISOString(),
        }),
      })
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const [code, setCode] = useState('')
  const [redeemedCircle, setRedeemedCircle] = useState<Circle | null>(null)
  const [redeemError, setRedeemError] = useState<string | null>(null)

  const redeemMutation = useMutation({ mutationFn: redeemInvite })
  const joinMutation = useMutation({
    mutationFn: joinCircle,
    onSuccess: () => {
      setRedeemedCircle(null)
      invalidateMembershipQueries()
    },
  })

  async function handleRedeem(event: FormEvent) {
    event.preventDefault()
    setRedeemError(null)
    try {
      setRedeemedCircle(await redeemMutation.mutateAsync(code))
    } catch (err) {
      setRedeemError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  async function handleJoin() {
    setRedeemError(null)
    if (!redeemedCircle) return
    try {
      await joinMutation.mutateAsync(redeemedCircle.id)
    } catch (err) {
      setRedeemError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <div>
      <p>You're not in a circle yet.</p>

      <BrowseCircles onJoined={() => setRedeemedCircle(null)} />

      <h2>Start a circle</h2>
      <form onSubmit={handleCreate}>
        <label>
          Name
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>

        <label>
          Description
          <input value={description} onChange={(event) => setDescription(event.target.value)} required />
        </label>

        <label>
          Sizing
          <select
            value={sizingPolicyType}
            onChange={(event) => setSizingPolicyType(event.target.value as CircleSizingPolicyType)}
          >
            <option value="FIXED_SIZE">Fixed size</option>
            <option value="OPEN_ENROLLMENT">Open enrollment</option>
          </select>
        </label>

        <label>
          {sizingPolicyType === 'FIXED_SIZE' ? 'Members' : 'Max members'}
          <input
            type="number"
            min="2"
            value={maxMembers}
            onChange={(event) => setMaxMembers(event.target.value)}
            required
          />
        </label>

        {sizingPolicyType === 'OPEN_ENROLLMENT' && (
          <>
            <label>
              Min members (optional, defaults to 2)
              <input
                type="number"
                min="2"
                value={minMembers}
                onChange={(event) => setMinMembers(event.target.value)}
              />
            </label>

            <label>
              Enrollment deadline
              <input
                type="datetime-local"
                value={enrollmentDeadline}
                onChange={(event) => setEnrollmentDeadline(event.target.value)}
                required
              />
            </label>
          </>
        )}

        <label>
          Term (weeks)
          <input type="number" min="1" value={term} onChange={(event) => setTerm(event.target.value)} required />
        </label>

        <label>
          Contribution amount
          <input
            type="number"
            min="0.01"
            step="0.01"
            value={contributionAmount}
            onChange={(event) => setContributionAmount(event.target.value)}
            required
          />
        </label>

        <label>
          <input type="checkbox" checked={gated} onChange={(event) => setGated(event.target.checked)} />
          Invite-only
        </label>

        {createError && <p role="alert">{createError}</p>}

        <button type="submit" disabled={createMutation.isPending}>
          {createMutation.isPending ? 'Creating…' : 'Create circle'}
        </button>
      </form>

      <h2>Join with an invite</h2>
      <form onSubmit={handleRedeem}>
        <label>
          Invite code
          <input value={code} onChange={(event) => setCode(event.target.value)} required />
        </label>

        <button type="submit" disabled={redeemMutation.isPending}>
          {redeemMutation.isPending ? 'Checking…' : 'Redeem code'}
        </button>
      </form>

      {redeemedCircle && (
        <div>
          <p>
            Invite is for <strong>{redeemedCircle.name}</strong>: {redeemedCircle.description}
          </p>
          <button onClick={handleJoin} disabled={joinMutation.isPending}>
            {joinMutation.isPending ? 'Joining…' : 'Join circle'}
          </button>
        </div>
      )}

      {redeemError && <p role="alert">{redeemError}</p>}
    </div>
  )
}

function MyCircle({ circle }: { circle: Circle }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isCreator = user?.id === circle.createdByUserId
  const membersQueryKey = queryKeys.circleMembers(circle.id)

  const { data: members } = useQuery({
    queryKey: membersQueryKey,
    queryFn: () => fetchCircleMembers(circle.id),
  })

  const [actionError, setActionError] = useState<string | null>(null)

  const cancelMutation = useMutation({
    mutationFn: () => cancelCircle(circle.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircle })
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircleHistory })
    },
  })

  const removeMutation = useMutation({
    mutationFn: (memberUserId: string) => removeMember(circle.id, memberUserId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: membersQueryKey }),
  })

  const banMutation = useMutation({
    mutationFn: (memberUserId: string) => banMember(circle.id, memberUserId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: membersQueryKey }),
  })

  const leaveMutation = useMutation({
    mutationFn: () => leaveCircle(circle.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircle })
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircleHistory })
      queryClient.invalidateQueries({ queryKey: queryKeys.openCircles })
    },
  })

  async function handleCancel() {
    setActionError(null)
    try {
      await cancelMutation.mutateAsync()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  async function handleRemove(memberUserId: string) {
    setActionError(null)
    try {
      await removeMutation.mutateAsync(memberUserId)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  async function handleBan(memberUserId: string) {
    setActionError(null)
    try {
      await banMutation.mutateAsync(memberUserId)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  async function handleLeave() {
    setActionError(null)
    try {
      await leaveMutation.mutateAsync()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const [inviteTarget, setInviteTarget] = useState('')
  const [inviteByEmail, setInviteByEmail] = useState(false)
  const [inviteCode, setInviteCode] = useState<string | null>(null)
  const [inviteError, setInviteError] = useState<string | null>(null)

  const inviteMutation = useMutation({
    mutationFn: () =>
      inviteToCircle(circle.id, inviteByEmail ? { email: inviteTarget } : { username: inviteTarget }),
  })

  async function handleInvite(event: FormEvent) {
    event.preventDefault()
    setInviteError(null)
    try {
      const invite = await inviteMutation.mutateAsync()
      setInviteCode(invite.code)
      setInviteTarget('')
    } catch (err) {
      setInviteError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <div>
      <h2>{circle.name}</h2>
      <p>{circle.description}</p>
      <ul>
        <li>Status: {circle.status}</li>
        <li>Round: {circle.currentRound} of {circle.maxMembers}</li>
        <li>Term: {circle.term} week(s)</li>
        <li>Contribution: {circle.contributionAmount.toFixed(2)} {circle.currency}</li>
        <li>Collateral held: {circle.collateral.toFixed(2)} {circle.currency}</li>
        <li>{circle.gated ? 'Invite-only' : 'Open'}</li>
      </ul>

      <h3>Members</h3>
      <ul>
        {members?.map((member) => (
          <li key={member.userId}>
            {member.userId === user?.id ? 'You' : member.username} — position {member.payoutPosition}, {member.status},
            reputation {member.reputationScore.toFixed(2)}
            {isCreator && circle.status === 'FORMING' && member.userId !== user?.id && (
              <>
                {' '}
                <button onClick={() => handleRemove(member.userId)} disabled={removeMutation.isPending}>
                  Remove
                </button>{' '}
                <button onClick={() => handleBan(member.userId)} disabled={banMutation.isPending}>
                  Ban
                </button>
              </>
            )}
          </li>
        ))}
      </ul>

      {actionError && <p role="alert">{actionError}</p>}

      {!isCreator && circle.status === 'FORMING' && (
        <button onClick={handleLeave} disabled={leaveMutation.isPending}>
          {leaveMutation.isPending ? 'Leaving…' : 'Leave circle'}
        </button>
      )}

      {isCreator && circle.status === 'FORMING' && (
        <>
          <button onClick={handleCancel} disabled={cancelMutation.isPending}>
            {cancelMutation.isPending ? 'Cancelling…' : 'Cancel circle'}
          </button>

          {circle.gated && (
            <div>
              <h3>Invite a member</h3>
              <form onSubmit={handleInvite}>
                <label>
                  <input
                    type="checkbox"
                    checked={inviteByEmail}
                    onChange={(event) => setInviteByEmail(event.target.checked)}
                  />
                  By email instead of username
                </label>

                <label>
                  {inviteByEmail ? 'Email' : 'Username'}
                  <input value={inviteTarget} onChange={(event) => setInviteTarget(event.target.value)} required />
                </label>

                {inviteError && <p role="alert">{inviteError}</p>}

                <button type="submit" disabled={inviteMutation.isPending}>
                  {inviteMutation.isPending ? 'Inviting…' : 'Send invite'}
                </button>
              </form>

              {inviteCode && <p>Invite code: {inviteCode}</p>}
            </div>
          )}
        </>
      )}
    </div>
  )
}
