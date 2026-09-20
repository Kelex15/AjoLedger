export const queryKeys = {
  currentUser: ['currentUser'] as const,
  accounts: ['accounts'] as const,
  lien: ['lien'] as const,
  myCircle: ['myCircle'] as const,
  myCircleHistory: ['myCircleHistory'] as const,
  openCircles: ['openCircles'] as const,
  circleMembers: (circleId: string) => ['circleMembers', circleId] as const,
}
