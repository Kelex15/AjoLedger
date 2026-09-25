import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Crown, History, Plus, Ticket, Users } from 'lucide-react'
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
import { AmountInput } from '../components/AmountInput'
import { CircleCard } from '../components/CircleCard'
import { CircleDetailDialog } from '../components/CircleDetailDialog'
import { DatePicker } from '../components/DatePicker'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { FieldError } from '../components/FieldError'
import { NumberInput } from '../components/NumberInput'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { env } from '../config/env'
import { useFormErrors } from '../hooks/useFormErrors'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'

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
    <div className="flex flex-col gap-6">
      <PageHeader title="Circles" />
      {isLoading ? <Skeleton className="h-48 w-full rounded-xl" /> : circle ? <MyCircle circle={circle} /> : <NoCircle />}
      <MyCircleHistory />
    </div>
  )
}

function endOfDay(date: Date) {
  const end = new Date(date)
  end.setHours(23, 59, 59, 999)
  return end
}

function MyCircleHistory() {
  const [viewing, setViewing] = useState<string | null>(null)
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
        <CardTitle className="flex items-center gap-2">
          <History className="size-4 text-muted-foreground" />
          Past circles
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="-mx-2 divide-y divide-border">
          {history.map((entry) => (
            <li key={entry.circleId}>
              <button
                type="button"
                onClick={() => setViewing(entry.circleId)}
                className="flex w-full items-center justify-between gap-4 rounded-lg px-2 py-3 text-left transition-colors hover:bg-muted/60 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
              >
                <div className="flex min-w-0 flex-col gap-0.5">
                  <span className="truncate text-sm font-medium">{entry.circleName}</span>
                  {entry.position != null && (
                    <span data-numeric className="text-xs text-muted-foreground">
                      Position {entry.position}
                    </span>
                  )}
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                  <StatusBadge status={entry.membershipStatus} />
                  <StatusBadge status={entry.circleStatus} />
                </div>
              </button>
            </li>
          ))}
        </ul>
        <CircleDetailDialog circleId={viewing} onOpenChange={(open) => !open && setViewing(null)} />
      </CardContent>
    </Card>
  )
}

function BrowseCircles({ onJoined }: { onJoined: () => void }) {
  const queryClient = useQueryClient()
  const [joiningId, setJoiningId] = useState<string | null>(null)
  const [joinError, setJoinError] = useState<string | null>(null)
  const [viewing, setViewing] = useState<string | null>(null)

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
        <CardTitle>Open circles</CardTitle>
        <CardDescription>Anyone can join these, subject to their entry rules.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {(!openCircles || openCircles.length === 0) && (
          <EmptyState
            icon={Users}
            title="No circles to join right now"
            description="Start one below and invite people to it."
          />
        )}

        {openCircles && openCircles.length > 0 && (
          <ul className="flex flex-col gap-3">
            {openCircles.map((openCircle) => (
              <li key={openCircle.id}>
                <CircleCard
                  circle={openCircle}
                  onOpen={() => setViewing(openCircle.id)}
                  action={
                    <Button
                      size="sm"
                      onClick={() => handleJoin(openCircle.id)}
                      disabled={joinMutation.isPending && joiningId === openCircle.id}
                    >
                      {joinMutation.isPending && joiningId === openCircle.id ? 'Joining…' : 'Join'}
                    </Button>
                  }
                />
              </li>
            ))}
          </ul>
        )}
        <ErrorMessage message={joinError} />
        <CircleDetailDialog circleId={viewing} onOpenChange={(open) => !open && setViewing(null)} />
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
  const [enrollmentDeadline, setEnrollmentDeadline] = useState<Date | undefined>()
  const [term, setTerm] = useState('')
  const [contributionAmount, setContributionAmount] = useState('')
  const [gated, setGated] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const { errors, validate, clearError } = useFormErrors()

  const createMutation = useMutation({
    mutationFn: createCircle,
    onSuccess: invalidateMembershipQueries,
  })

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCreateError(null)
    const deadlineMissing = sizingPolicyType === 'OPEN_ENROLLMENT' && !enrollmentDeadline
    if (!validate(event.currentTarget, {
      enrollmentDeadline: deadlineMissing ? 'Pick the last day people can join.' : null,
    })) {
      return
    }
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
          // The chosen day is inclusive, and enrollment is swept daily at midnight.
          enrollmentDeadline: endOfDay(enrollmentDeadline!).toISOString(),
        }),
      })
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  const [code, setCode] = useState('')
  const [redeemedCircle, setRedeemedCircle] = useState<Circle | null>(null)
  const [redeemError, setRedeemError] = useState<string | null>(null)
  const { errors: redeemErrors, validate: validateRedeem, clearError: clearRedeemError } = useFormErrors()

  const redeemMutation = useMutation({ mutationFn: redeemInvite })
  const joinMutation = useMutation({
    mutationFn: joinCircle,
    onSuccess: () => {
      setRedeemedCircle(null)
      invalidateMembershipQueries()
    },
  })

  async function handleRedeem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setRedeemError(null)
    if (!validateRedeem(event.currentTarget)) {
      return
    }
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
      <BrowseCircles onJoined={() => setRedeemedCircle(null)} />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Plus className="size-4 text-muted-foreground" />
            Start a circle
          </CardTitle>
          <CardDescription>You'll be its first member and set the terms.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreate} noValidate className="flex flex-col gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="circle-name">Name</Label>
              <Input
                id="circle-name"
                name="name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                onInput={() => clearError('name')}
                aria-invalid={Boolean(errors.name)}
                data-required-message="Give your circle a name."
                required
              />
              <FieldError message={errors.name} />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="circle-description">Description</Label>
              <Input
                id="circle-description"
                name="description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                onInput={() => clearError('description')}
                aria-invalid={Boolean(errors.description)}
                data-required-message="Describe what this circle is for."
                required
              />
              <FieldError message={errors.description} />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
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
                <NumberInput
                  id="circle-max-members"
                  name="maxMembers"
                  min={2}
                  value={maxMembers}
                  onValueChange={(next) => {
                    setMaxMembers(next)
                    clearError('maxMembers')
                  }}
                  aria-invalid={Boolean(errors.maxMembers)}
                  data-required-message="Set how many people can join."
                  required
                />
                <FieldError message={errors.maxMembers} />
              </div>
            </div>

            {sizingPolicyType === 'OPEN_ENROLLMENT' && (
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="grid gap-1.5">
                  <Label htmlFor="circle-min-members">Min members</Label>
                  <NumberInput
                    id="circle-min-members"
                    name="minMembers"
                    min={2}
                    placeholder="2"
                    value={minMembers}
                    onValueChange={(next) => {
                      setMinMembers(next)
                      clearError('minMembers')
                    }}
                    aria-invalid={Boolean(errors.minMembers)}
                  />
                  <FieldError message={errors.minMembers} />
                </div>

                <div className="grid gap-1.5">
                  <Label htmlFor="circle-enrollment-deadline">Enrollment closes after</Label>
                  <DatePicker
                    id="circle-enrollment-deadline"
                    value={enrollmentDeadline}
                    onSelect={(date) => {
                      setEnrollmentDeadline(date)
                      clearError('enrollmentDeadline')
                    }}
                    disabled={(date) => date < new Date(new Date().toDateString())}
                  />
                  <FieldError message={errors.enrollmentDeadline} />
                </div>
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="grid gap-1.5">
                <Label htmlFor="circle-term">Term (weeks)</Label>
                <NumberInput
                  id="circle-term"
                  name="term"
                  min={1}
                  value={term}
                  onValueChange={(next) => {
                    setTerm(next)
                    clearError('term')
                  }}
                  aria-invalid={Boolean(errors.term)}
                  data-required-message="Set how many weeks the circle runs."
                  required
                />
                <FieldError message={errors.term} />
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="circle-contribution">Contribution per round</Label>
                <AmountInput
                  id="circle-contribution"
                  name="contributionAmount"
                  value={contributionAmount}
                  onValueChange={(next) => {
                    setContributionAmount(next)
                    clearError('contributionAmount')
                  }}
                  aria-invalid={Boolean(errors.contributionAmount)}
                  data-required-message="Set the amount each member pays per round."
                  required
                />
                <FieldError message={errors.contributionAmount} />
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Checkbox id="circle-gated" checked={gated} onCheckedChange={(checked) => setGated(checked === true)} />
              <Label htmlFor="circle-gated">Invite-only</Label>
            </div>

            <ErrorMessage message={createError} />

            <Button type="submit" className="self-start" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating…' : 'Create circle'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Ticket className="size-4 text-muted-foreground" />
            Join with an invite
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form onSubmit={handleRedeem} noValidate className="grid gap-1.5">
            <Label htmlFor="invite-code">Invite code</Label>
            <div className="flex gap-2">
              <Input
                id="invite-code"
                name="code"
                className="flex-1"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                onInput={() => clearRedeemError('code')}
                aria-invalid={Boolean(redeemErrors.code)}
                data-required-message="Enter the invite code you were sent."
                required
              />
              <Button type="submit" variant="outline" disabled={redeemMutation.isPending}>
                {redeemMutation.isPending ? 'Checking…' : 'Redeem'}
              </Button>
            </div>
            <FieldError message={redeemErrors.code} />
          </form>

          {redeemedCircle && (
            <>
              <Separator />
              <div className="flex flex-wrap items-center justify-between gap-4">
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
  const { errors: inviteErrors, validate: validateInvite, clearError: clearInviteError } = useFormErrors()

  const inviteMutation = useMutation({
    mutationFn: () =>
      inviteToCircle(circle.id, inviteByEmail ? { email: inviteTarget } : { username: inviteTarget }),
  })

  async function handleInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setInviteError(null)
    if (!validateInvite(event.currentTarget)) {
      return
    }
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
      <CircleCard circle={circle} />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Users className="size-4 text-muted-foreground" />
            Members
          </CardTitle>
          <CardDescription>Payout order is fixed when enrollment closes.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <ul className="divide-y divide-border">
            {members?.map((member) => {
              const isYou = member.userId === user?.id
              return (
                <li key={member.userId} className="flex items-center gap-3 py-3">
                  <span
                    data-numeric
                    className="flex size-7 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-muted-foreground"
                  >
                    {member.payoutPosition}
                  </span>
                  <Avatar className="size-8 shrink-0">
                    <AvatarFallback className="bg-primary/10 text-xs font-semibold text-primary">
                      {member.username.slice(0, 2).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="truncate text-sm font-medium">{isYou ? 'You' : member.username}</span>
                      {member.userId === circle.createdByUserId && (
                        <Badge variant="outline">
                          <Crown />
                          Creator
                        </Badge>
                      )}
                      {member.paid && (
                        <Badge variant="secondary" className="text-success">
                          <Check />
                          Paid
                        </Badge>
                      )}
                    </div>
                    <span data-numeric className="text-xs text-muted-foreground">
                      Reputation {member.reputationScore.toFixed(2)} · {member.status.toLowerCase()}
                    </span>
                  </div>

                  {isCreator && circle.status === 'FORMING' && !isYou && (
                    <div className="flex shrink-0 gap-2">
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
              )
            })}
          </ul>

          <ErrorMessage message={actionError} />

          {!isCreator && circle.status === 'FORMING' && (
            <Button variant="outline" className="self-start" onClick={handleLeave} disabled={leaveMutation.isPending}>
              {leaveMutation.isPending ? 'Leaving…' : 'Leave circle'}
            </Button>
          )}

          {isCreator && circle.status === 'FORMING' && (
            <Button
              variant="destructive"
              className="self-start"
              onClick={handleCancel}
              disabled={cancelMutation.isPending}
            >
              {cancelMutation.isPending ? 'Cancelling…' : 'Cancel circle'}
            </Button>
          )}
        </CardContent>
      </Card>

      {isCreator && circle.status === 'FORMING' && circle.gated && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Ticket className="size-4 text-muted-foreground" />
              Invite a member
            </CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleInvite} noValidate className="flex flex-col gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor="invite-target">{inviteByEmail ? 'Email' : 'Username'}</Label>
                <Input
                  id="invite-target"
                  name="inviteTarget"
                  type={inviteByEmail ? 'email' : 'text'}
                  value={inviteTarget}
                  onChange={(event) => setInviteTarget(event.target.value)}
                  onInput={() => clearInviteError('inviteTarget')}
                  aria-invalid={Boolean(inviteErrors.inviteTarget)}
                  data-required-message={inviteByEmail ? 'Enter their email address.' : 'Enter their username.'}
                  required
                />
                <FieldError message={inviteErrors.inviteTarget} />
              </div>

              <div className="flex items-center gap-2">
                <Checkbox
                  id="invite-by-email"
                  checked={inviteByEmail}
                  onCheckedChange={(checked) => setInviteByEmail(checked === true)}
                />
                <Label htmlFor="invite-by-email">By email instead of username</Label>
              </div>

              <ErrorMessage message={inviteError} />

              <Button type="submit" className="self-start" disabled={inviteMutation.isPending}>
                {inviteMutation.isPending ? 'Inviting…' : 'Send invite'}
              </Button>
            </form>

            {inviteCode && (
              <div className="mt-4 flex flex-col gap-1 rounded-lg bg-muted/50 px-3 py-2.5">
                <span className="text-xs text-muted-foreground">Invite code</span>
                <code data-numeric className="text-sm font-medium">
                  {inviteCode}
                </code>
              </div>
            )}
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
