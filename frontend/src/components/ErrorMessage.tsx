import { CircleAlert } from 'lucide-react'

export function ErrorMessage({ message }: { message: string | null }) {
  if (!message) {
    return null
  }
  return (
    <p role="alert" className="flex items-start gap-1.5 text-sm text-destructive">
      <CircleAlert className="mt-0.5 size-3.5 shrink-0" />
      {message}
    </p>
  )
}
