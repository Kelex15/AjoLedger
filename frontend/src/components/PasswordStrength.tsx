import type { ReactNode } from 'react'
import { Circle, CircleCheck } from 'lucide-react'
import { cn } from 'cn'
import { MIN_PASSWORD_LENGTH, type PasswordStrength as Strength } from '../hooks/usePasswordStrength'

const LABELS = ['Very weak', 'Weak', 'Fair', 'Strong', 'Very strong']

export function PasswordStrength({ id, password, strength }: { id: string; password: string; strength: Strength }) {
  const { result, longEnough, hardToGuess } = strength
  // A short password can still score well, but the server rejects it, so it never reads as strong.
  const score = result ? (longEnough ? result.score : Math.min(result.score, 2)) : 0
  const accepted = longEnough && hardToGuess
  const remaining = MIN_PASSWORD_LENGTH - password.length

  const barColor = accepted ? 'bg-success' : score === 2 ? 'bg-primary' : 'bg-destructive'
  const { warning, suggestions } = result?.feedback ?? { warning: '', suggestions: [] }
  const hints = accepted ? [] : [warning, ...suggestions].filter(Boolean)

  return (
    <div id={id} className="grid gap-2">
      {result && (
        <div className="grid gap-1">
          <div className="flex gap-1" aria-hidden>
            {[1, 2, 3, 4].map((segment) => (
              <span
                key={segment}
                className={cn('h-1 flex-1 rounded-full transition-colors', segment <= Math.max(score, 1) ? barColor : 'bg-muted')}
              />
            ))}
          </div>
          <p className="text-xs text-muted-foreground" aria-live="polite">
            Strength: <span className="font-medium text-foreground">{LABELS[score]}</span>
          </p>
        </div>
      )}

      <ul className="grid gap-1 text-xs">
        <Requirement met={longEnough}>
          At least {MIN_PASSWORD_LENGTH} characters
          {password && !longEnough && <span className="text-muted-foreground"> · {remaining} more</span>}
        </Requirement>
        <Requirement met={hardToGuess}>Hard to guess</Requirement>
      </ul>

      {hints.length > 0 && (
        <ul className="grid gap-0.5 text-xs text-muted-foreground">
          {hints.map((hint) => (
            <li key={hint}>{hint}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Requirement({ met, children }: { met: boolean; children: ReactNode }) {
  const Icon = met ? CircleCheck : Circle
  return (
    <li className={cn('flex items-center gap-1.5', met ? 'text-success' : 'text-muted-foreground')}>
      <Icon className="size-3.5 shrink-0" aria-hidden />
      <span>{children}</span>
      <span className="sr-only">{met ? '(done)' : '(missing)'}</span>
    </li>
  )
}
