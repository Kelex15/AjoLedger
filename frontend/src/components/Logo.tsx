import { cn } from 'cn'

/** Eight seats around a circle with one filled: a rotating savings group, and whose turn it is. */
export function LogoMark({ className }: { className?: string }) {
  const seats = Array.from({ length: 8 }, (_, index) => {
    const angle = (index * 45 - 90) * (Math.PI / 180)
    return { cx: 12 + 9 * Math.cos(angle), cy: 12 + 9 * Math.sin(angle) }
  })

  return (
    <svg viewBox="0 0 24 24" aria-hidden className={cn('size-6', className)}>
      {seats.map((seat, index) => (
        <circle
          key={index}
          cx={seat.cx}
          cy={seat.cy}
          r={index === 0 ? 2.6 : 1.9}
          className={index === 0 ? 'fill-primary' : 'fill-current opacity-30'}
        />
      ))}
    </svg>
  )
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('flex items-center gap-2', className)}>
      <LogoMark />
      <span className="font-heading text-lg leading-snug font-semibold tracking-tight">AjoLedger</span>
    </span>
  )
}
