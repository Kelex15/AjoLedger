import { apiFetch } from './client'

export interface Circle {
  id: string
  name: string
  description: string
  maxMembers: number
  term: number
  contributionAmount: number
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
}

export interface CircleMembership {
  id: string
  circleId: string
  userId: string
  position: number | null
  status: string
  joinedAt: string
}

export interface CircleCreateFields {
  name: string
  description: string
  maxMembers: number
  term: number
  contributionAmount: number
  gated: boolean
}

export function fetchMyCircle() {
  return apiFetch<Circle>('/api/circles/me')
}

export function createCircle(fields: CircleCreateFields) {
  return apiFetch<Circle>('/api/circles', {
    method: 'POST',
    body: JSON.stringify({ ...fields, sizingPolicyType: 'FIXED_SIZE' }),
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
  return apiFetch<CircleMembership>(`/api/circles/${circleId}/members/${memberUserId}/remove`, { method: 'POST' })
}
