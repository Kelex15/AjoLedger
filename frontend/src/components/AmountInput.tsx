import type { ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from 'cn'

function sanitize(raw: string) {
  const cleaned = raw.replace(/[^\d.]/g, '')
  const [whole, ...fractions] = cleaned.split('.')
  if (fractions.length === 0) {
    return whole
  }
  return `${whole}.${fractions.join('').slice(0, 2)}`
}

export function AmountInput({
  value,
  onValueChange,
  className,
  ...props
}: Omit<ComponentProps<typeof Input>, 'value' | 'onChange' | 'type'> & {
  value: string
  onValueChange: (value: string) => void
}) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute inset-y-0 left-2.5 flex items-center text-sm text-muted-foreground">
        $
      </span>
      <Input
        type="text"
        inputMode="decimal"
        autoComplete="off"
        placeholder="0.00"
        value={value}
        onChange={(event) => onValueChange(sanitize(event.target.value))}
        data-numeric
        className={cn('pl-6', className)}
        {...props}
      />
    </div>
  )
}
