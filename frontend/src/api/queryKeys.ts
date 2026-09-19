export const queryKeys = {
  currentUser: ['currentUser'] as const,
  accounts: ['accounts'] as const,
  lien: ['lien'] as const,
  myCircle: ['myCircle'] as const,
  circleMembers: (circleId: string) => ['circleMembers', circleId] as const,
}
