import { apiFetch } from './client'

export interface Circle {
  id: string
  name: string
  description: string
  maxMembers: number
  term: number
  contributionAmount: number
  collateral: number
  currency: string
  currentRound: number
  status: string
  gated: boolean
  minReputationScore: number | null
  createdByUserId: string
  createdAt: string
}

export interface CircleMember {
  userId: string
  username: string
  joinPosition: number | null
  payoutPosition: number
  status: string
  joinedAt: string
  reputationScore: number
  paid: boolean
}

export interface CircleMembership {
  id: string
  circleId: string
  userId: string
  position: number | null
  status: string
  joinedAt: string
}

export type CircleSizingPolicyType = 'FIXED_SIZE' | 'OPEN_ENROLLMENT'

export interface CircleCreateFields {
  name: string
  description: string
  sizingPolicyType: CircleSizingPolicyType
  maxMembers: number
  minMembers?: number
  enrollmentDeadline?: string
  term: number
  contributionAmount: number
  gated: boolean
}

export interface CircleHistoryEntry {
  circleId: string
  circleName: string
  membershipStatus: string
  circleStatus: string
  position: number | null
  joinedAt: string
}

export function fetchMyCircle() {
  return apiFetch<Circle>('/api/circles/me')
}

export function fetchMyCircleHistory() {
  return apiFetch<CircleHistoryEntry[]>('/api/circles/mine')
}

export function fetchOpenCircles() {
  return apiFetch<Circle[]>('/api/circles/open')
}

export function createCircle(fields: CircleCreateFields) {
  return apiFetch<Circle>('/api/circles', {
    method: 'POST',
    body: JSON.stringify(fields),
  })
}

export function inviteToCircle(circleId: string, usernameOrEmail: { username?: string; email?: string }) {
  return apiFetch<{ id: string; circleId: string; code: string }>(`/api/circles/${circleId}/invites`, {
    method: 'POST',
    body: JSON.stringify(usernameOrEmail),
  })
}

export function redeemInvite(code: string) {
  return apiFetch<Circle>(`/api/circles/invites/${code}/redeem`, { method: 'POST' })
}

export function joinCircle(circleId: string) {
  return apiFetch<CircleMembership>(`/api/circles/${circleId}/join`, { method: 'POST' })
}

export function cancelCircle(circleId: string) {
  return apiFetch<Circle>(`/api/circles/${circleId}/cancel`, { method: 'POST' })
}

export function fetchCircleMembers(circleId: string) {
  return apiFetch<CircleMember[]>(`/api/circles/${circleId}/members`)
}

export function removeMember(circleId: string, memberUserId: string) {
  return apiFetch<void>(`/api/circles/${circleId}/members/${memberUserId}/remove`, { method: 'POST' })
}

export function banMember(circleId: string, memberUserId: string) {
  return apiFetch<void>(`/api/circles/${circleId}/members/${memberUserId}/ban`, { method: 'POST' })
}

export function leaveCircle(circleId: string) {
  return apiFetch<void>(`/api/circles/${circleId}/leave`, { method: 'POST' })
}

// Dev-only: backend only registers these routes under the "dev" profile.
export function forceRoundResolve(circleId: string) {
  return apiFetch<void>(`/api/dev/circles/${circleId}/force-round`, { method: 'POST' })
}

export function forceEnrollmentClose(circleId: string) {
  return apiFetch<void>(`/api/dev/circles/${circleId}/force-enrollment-close`, { method: 'POST' })
}
