import { useAuth } from '../auth/useAuth'

export function ProfilePage() {
  const { user } = useAuth()

  if (!user) {
    return null
  }

  return (
    <div>
      <h1>Profile</h1>
      <dl>
        <dt>Username</dt>
        <dd>{user.username}</dd>

        <dt>Name</dt>
        <dd>
          {user.firstName} {user.lastName}
        </dd>

        <dt>Email</dt>
        <dd>{user.email}</dd>
      </dl>
    </div>
  )
}
