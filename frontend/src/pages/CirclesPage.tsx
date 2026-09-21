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
  forceEnrollmentClose,
  forceRoundResolve,
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
import { useAuth } from '../auth/useAuth'
import { ErrorMessage } from '../components/ErrorMessage'
import { env } from '../config/env'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'

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
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-2xl font-semibold">Circles</h1>
      <MyCircleHistory />
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : circle ? (
        <MyCircle circle={circle} />
      ) : (
        <NoCircle />
      )}
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
    <Card>
      <CardHeader>
        <CardTitle>My circles</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="flex flex-col gap-3">
          {history.map((entry) => (
            <li
              key={entry.circleId}
              className="flex items-center justify-between gap-4 rounded-lg border border-border p-3"
            >
              <div className="flex flex-col gap-1 text-sm">
                <span className="font-medium">{entry.circleName}</span>
                {entry.position != null && (
                  <span className="text-muted-foreground">Position {entry.position}</span>
                )}
              </div>
              <div className="flex items-center gap-1.5">
                <Badge variant="secondary">{entry.membershipStatus}</Badge>
                <Badge variant="outline">{entry.circleStatus}</Badge>
              </div>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
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
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
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
    <Card>
      <CardHeader>
        <CardTitle>Browse circles</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {(!openCircles || openCircles.length === 0) && (
          <p className="text-sm text-muted-foreground">No circles to join right now.</p>
        )}
        {openCircles && openCircles.length > 0 && (
          <ul className="flex flex-col gap-3">
            {openCircles.map((openCircle) => (
              <li
                key={openCircle.id}
                className="flex flex-col gap-3 rounded-lg border border-border p-3 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="flex flex-col gap-1.5 text-sm">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="font-medium">{openCircle.name}</span>
                    {openCircle.gated && <Badge variant="outline">Invite-only</Badge>}
                    {openCircle.minReputationScore != null && (
                      <Badge variant="secondary">Min reputation {openCircle.minReputationScore}</Badge>
                    )}
                  </div>
                  <p className="text-muted-foreground">{openCircle.description}</p>
                  <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                    <span>
                      {openCircle.contributionAmount.toFixed(2)} {openCircle.currency}/round
                    </span>
                    <span>{openCircle.term}wk term</span>
                    <span>{openCircle.maxMembers} members</span>
                    <span>
                      {openCircle.collateral.toFixed(2)} {openCircle.currency} collateral
                    </span>
                  </div>
                </div>
                <Button
                  size="sm"
                  onClick={() => handleJoin(openCircle.id)}
                  disabled={joinMutation.isPending && joiningId === openCircle.id}
                >
                  {joinMutation.isPending && joiningId === openCircle.id ? 'Joining…' : 'Join'}
                </Button>
              </li>
            ))}
          </ul>
        )}
        <ErrorMessage message={joinError} />
      </CardContent>
    </Card>
  )
}

function NoCircle() {
  const queryClient = useQueryClient()

  function invalidateMembershipQueries() {
    queryClient.invalidateQueries({ queryKey: queryKeys.myCircle })
    queryClient.invalidateQueries({ queryKey: queryKeys.myCircleHistory })
    queryClient.invalidateQueries({ queryKey: queryKeys.openCircles })
    queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
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
    <div className="flex flex-col gap-6">
      <p className="text-sm text-muted-foreground">You're not in a circle yet.</p>

      <BrowseCircles onJoined={() => setRedeemedCircle(null)} />

      <Card>
        <CardHeader>
          <CardTitle>Start a circle</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreate} className="flex flex-col gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="circle-name">Name</Label>
              <Input id="circle-name" value={name} onChange={(event) => setName(event.target.value)} required />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="circle-description">Description</Label>
              <Input
                id="circle-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                required
              />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="circle-sizing">Sizing</Label>
              <Select
                value={sizingPolicyType}
                onValueChange={(value) => setSizingPolicyType(value as CircleSizingPolicyType)}
              >
                <SelectTrigger id="circle-sizing">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="FIXED_SIZE">Fixed size</SelectItem>
                  <SelectItem value="OPEN_ENROLLMENT">Open enrollment</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="circle-max-members">
                {sizingPolicyType === 'FIXED_SIZE' ? 'Members' : 'Max members'}
              </Label>
              <Input
                id="circle-max-members"
                type="number"
                min="2"
                value={maxMembers}
                onChange={(event) => setMaxMembers(event.target.value)}
                required
              />
            </div>

            {sizingPolicyType === 'OPEN_ENROLLMENT' && (
              <>
                <div className="grid gap-1.5">
                  <Label htmlFor="circle-min-members">Min members (optional, defaults to 2)</Label>
                  <Input
                    id="circle-min-members"
                    type="number"
                    min="2"
                    value={minMembers}
                    onChange={(event) => setMinMembers(event.target.value)}
                  />
                </div>

                <div className="grid gap-1.5">
                  <Label htmlFor="circle-enrollment-deadline">Enrollment deadline</Label>
                  <Input
                    id="circle-enrollment-deadline"
                    type="datetime-local"
                    value={enrollmentDeadline}
                    onChange={(event) => setEnrollmentDeadline(event.target.value)}
                    required
                  />
                </div>
              </>
            )}

            <div className="grid gap-1.5">
              <Label htmlFor="circle-term">Term (weeks)</Label>
              <Input
                id="circle-term"
                type="number"
                min="1"
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                required
              />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="circle-contribution">Contribution amount</Label>
              <Input
                id="circle-contribution"
                type="number"
                min="0.01"
                step="0.01"
                value={contributionAmount}
                onChange={(event) => setContributionAmount(event.target.value)}
                required
              />
            </div>

            <div className="flex items-center gap-2">
              <Checkbox id="circle-gated" checked={gated} onCheckedChange={(checked) => setGated(checked === true)} />
              <Label htmlFor="circle-gated">Invite-only</Label>
            </div>

            <ErrorMessage message={createError} />

            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating…' : 'Create circle'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Join with an invite</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form onSubmit={handleRedeem} className="flex flex-col gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="invite-code">Invite code</Label>
              <Input id="invite-code" value={code} onChange={(event) => setCode(event.target.value)} required />
            </div>

            <Button type="submit" disabled={redeemMutation.isPending}>
              {redeemMutation.isPending ? 'Checking…' : 'Redeem code'}
            </Button>
          </form>

          {redeemedCircle && (
            <>
              <Separator />
              <div className="flex items-center justify-between gap-4">
                <p className="text-sm">
                  Invite is for <strong>{redeemedCircle.name}</strong>: {redeemedCircle.description}
                </p>
                <Button onClick={handleJoin} disabled={joinMutation.isPending}>
                  {joinMutation.isPending ? 'Joining…' : 'Join circle'}
                </Button>
              </div>
            </>
          )}

          <ErrorMessage message={redeemError} />
        </CardContent>
      </Card>
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
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
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
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
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

  const [devToolsError, setDevToolsError] = useState<string | null>(null)

  const forceRoundMutation = useMutation({
    mutationFn: () => forceRoundResolve(circle.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircle })
      queryClient.invalidateQueries({ queryKey: membersQueryKey })
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
    },
  })

  const forceEnrollmentCloseMutation = useMutation({
    mutationFn: () => forceEnrollmentClose(circle.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.myCircle })
      queryClient.invalidateQueries({ queryKey: membersQueryKey })
    },
  })

  async function handleForceRound() {
    setDevToolsError(null)
    try {
      await forceRoundMutation.mutateAsync()
    } catch (err) {
      setDevToolsError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  async function handleForceEnrollmentClose() {
    setDevToolsError(null)
    try {
      await forceEnrollmentCloseMutation.mutateAsync()
    } catch (err) {
      setDevToolsError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
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
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{circle.name}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">{circle.description}</p>
          <ul className="flex flex-col gap-1 text-sm">
            <li className="flex justify-between">
              <span>Status</span>
              <Badge variant="secondary">{circle.status}</Badge>
            </li>
            <li className="flex justify-between">
              <span>Round</span>
              <span>
                {circle.currentRound} of {circle.totalRounds}
              </span>
            </li>
            <li className="flex justify-between">
              <span>Term</span>
              <span>{circle.term} week(s)</span>
            </li>
            <li className="flex justify-between">
              <span>Contribution</span>
              <span>
                {circle.contributionAmount.toFixed(2)} {circle.currency}
              </span>
            </li>
            <li className="flex justify-between">
              <span>Collateral held</span>
              <span>
                {circle.collateral.toFixed(2)} {circle.currency}
              </span>
            </li>
            <li className="flex justify-between">
              <span>Access</span>
              <Badge variant="outline">{circle.gated ? 'Invite-only' : 'Open'}</Badge>
            </li>
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Members</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <ul className="flex flex-col gap-3">
            {members?.map((member) => (
              <li key={member.userId} className="flex items-center justify-between gap-4 rounded-lg border border-border p-3">
                <div className="flex flex-col gap-1 text-sm">
                  <div className="flex items-center gap-1.5">
                    <span className="font-medium">{member.userId === user?.id ? 'You' : member.username}</span>
                    {member.userId === circle.createdByUserId && <Badge variant="outline">Creator</Badge>}
                    {member.paid && <Badge variant="secondary">Paid</Badge>}
                  </div>
                  <p className="text-muted-foreground">
                    Position {member.payoutPosition}, {member.status}, reputation{' '}
                    {member.reputationScore.toFixed(2)}
                  </p>
                </div>
                {isCreator && circle.status === 'FORMING' && member.userId !== user?.id && (
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleRemove(member.userId)}
                      disabled={removeMutation.isPending}
                    >
                      Remove
                    </Button>
                    <Button
                      size="sm"
                      variant="destructive"
                      onClick={() => handleBan(member.userId)}
                      disabled={banMutation.isPending}
                    >
                      Ban
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>

          <ErrorMessage message={actionError} />

          {!isCreator && circle.status === 'FORMING' && (
            <Button variant="outline" onClick={handleLeave} disabled={leaveMutation.isPending}>
              {leaveMutation.isPending ? 'Leaving…' : 'Leave circle'}
            </Button>
          )}

          {isCreator && circle.status === 'FORMING' && (
            <Button variant="destructive" onClick={handleCancel} disabled={cancelMutation.isPending}>
              {cancelMutation.isPending ? 'Cancelling…' : 'Cancel circle'}
            </Button>
          )}
        </CardContent>
      </Card>

      {isCreator && circle.status === 'FORMING' && circle.gated && (
        <Card>
          <CardHeader>
            <CardTitle>Invite a member</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleInvite} className="flex flex-col gap-4">
              <div className="flex items-center gap-2">
                <Checkbox
                  id="invite-by-email"
                  checked={inviteByEmail}
                  onCheckedChange={(checked) => setInviteByEmail(checked === true)}
                />
                <Label htmlFor="invite-by-email">By email instead of username</Label>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="invite-target">{inviteByEmail ? 'Email' : 'Username'}</Label>
                <Input
                  id="invite-target"
                  value={inviteTarget}
                  onChange={(event) => setInviteTarget(event.target.value)}
                  required
                />
              </div>

              <ErrorMessage message={inviteError} />

              <Button type="submit" disabled={inviteMutation.isPending}>
                {inviteMutation.isPending ? 'Inviting…' : 'Send invite'}
              </Button>
            </form>

            {inviteCode && <p className="mt-3 text-sm">Invite code: {inviteCode}</p>}
          </CardContent>
        </Card>
      )}

      {env.isDev && (
        <Card>
          <CardHeader>
            <CardTitle>Dev tools</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <div className="flex gap-2">
              {circle.status === 'ACTIVE' && (
                <Button variant="outline" size="sm" onClick={handleForceRound} disabled={forceRoundMutation.isPending}>
                  {forceRoundMutation.isPending ? 'Resolving…' : 'Force resolve round'}
                </Button>
              )}
              {circle.status === 'FORMING' && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleForceEnrollmentClose}
                  disabled={forceEnrollmentCloseMutation.isPending}
                >
                  {forceEnrollmentCloseMutation.isPending ? 'Closing…' : 'Force close enrollment'}
                </Button>
              )}
            </div>
            <ErrorMessage message={devToolsError} />
          </CardContent>
        </Card>
      )}
    </div>
  )
}
