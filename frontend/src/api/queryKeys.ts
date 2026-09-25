export const queryKeys = {
  currentUser: ['currentUser'] as const,
  accounts: ['accounts'] as const,
  lien: ['lien'] as const,
  activity: ['activity'] as const,
  recentActivity: (limit: number) => ['activity', 'recent', limit] as const,
  accountActivity: (accountNumber: string) => ['activity', 'account', accountNumber] as const,
  myCircle: ['myCircle'] as const,
  myCircleHistory: ['myCircleHistory'] as const,
  openCircles: ['openCircles'] as const,
  circle: (circleId: string) => ['circle', circleId] as const,
  circleMembers: (circleId: string) => ['circleMembers', circleId] as const,
  circlePayouts: (circleId: string) => ['circlePayouts', circleId] as const,
}
