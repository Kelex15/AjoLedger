import { ChevronDown, ChevronUp } from 'lucide-react'
import type { ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from 'cn'

export function NumberInput({
  value,
  onValueChange,
  min,
  max,
  className,
  disabled,
  ...props
}: Omit<ComponentProps<typeof Input>, 'value' | 'onChange' | 'type' | 'min' | 'max'> & {
  value: string
  onValueChange: (value: string) => void
  min?: number
  max?: number
}) {
  const floor = min ?? 0

  function step(delta: number) {
    const next = value === '' ? floor : Number(value) + delta
    const clamped = Math.min(max ?? Number.MAX_SAFE_INTEGER, Math.max(floor, next))
    onValueChange(String(clamped))
  }

  return (
    <div className="relative">
      <Input
        type="text"
        inputMode="numeric"
        autoComplete="off"
        value={value}
        disabled={disabled}
        onChange={(event) => onValueChange(event.target.value.replace(/\D/g, ''))}
        onKeyDown={(event) => {
          if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
            event.preventDefault()
            step(event.key === 'ArrowUp' ? 1 : -1)
          }
        }}
        data-min={min}
        data-numeric
        className={cn('pr-7', className)}
        {...props}
      />
      <div className="absolute inset-y-0 right-0.5 flex flex-col justify-center">
        {(
          [
            { label: 'Increase', delta: 1, Icon: ChevronUp },
            { label: 'Decrease', delta: -1, Icon: ChevronDown },
          ] as const
        ).map(({ label, delta, Icon }) => (
          <button
            key={label}
            type="button"
            tabIndex={-1}
            aria-label={label}
            disabled={disabled}
            onClick={() => step(delta)}
            className="flex h-3 w-6 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:text-foreground disabled:pointer-events-none disabled:opacity-50"
          >
            <Icon className="size-3" />
          </button>
        ))}
      </div>
    </div>
  )
}
