import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/PageHeader'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Card, CardContent } from '@/components/ui/card'

export function ProfilePage() {
  const { user } = useAuth()

  if (!user) {
    return null
  }

  const details = [
    { label: 'Username', value: `@${user.username}` },
    { label: 'Name', value: `${user.firstName} ${user.lastName}` },
    { label: 'Email', value: user.email },
  ]

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Profile" />

      <Card>
        <CardContent className="flex flex-col gap-6">
          <div className="flex items-center gap-4">
            <Avatar className="size-14">
              <AvatarFallback className="bg-primary/12 text-base font-semibold text-primary">
                {`${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}`.toUpperCase()}
              </AvatarFallback>
            </Avatar>
            <div className="flex flex-col">
              <span className="font-heading text-xl font-semibold tracking-tight">
                {user.firstName} {user.lastName}
              </span>
              <span className="text-sm text-muted-foreground">@{user.username}</span>
            </div>
          </div>

          <dl className="flex flex-col divide-y divide-border border-t border-border">
            {details.map((detail) => (
              <div key={detail.label} className="flex justify-between gap-4 py-3 text-sm">
                <dt className="text-muted-foreground">{detail.label}</dt>
                <dd className="text-right font-medium">{detail.value}</dd>
              </div>
            ))}
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
