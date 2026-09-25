import { Check } from 'lucide-react'

export function SuccessPanel({ detail }: { detail?: string }) {
  return (
    <div className="flex flex-col items-center gap-3 py-2 text-center">
      <span className="flex size-12 items-center justify-center rounded-full bg-success/12 text-success">
        <Check className="size-6" />
      </span>
      {detail && (
        <p data-numeric className="text-sm text-muted-foreground">
          {detail}
        </p>
      )}
    </div>
  )
}
