export function ErrorMessage({ message }: { message: string | null }) {
  if (!message) {
    return null
  }
  return <p role="alert">{message}</p>
}
