import { useState, type ComponentProps } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { cn } from 'cn'
import { Input } from '@/components/ui/input'

export function PasswordInput({ className, ...props }: Omit<ComponentProps<typeof Input>, 'type'>) {
  const [shown, setShown] = useState(false)

  return (
    <div className="relative">
      <Input {...props} type={shown ? 'text' : 'password'} className={cn('pr-9', className)} />
      <button
        type="button"
        onClick={() => setShown((current) => !current)}
        aria-label={shown ? 'Hide password' : 'Show password'}
        aria-pressed={shown}
        className="absolute inset-y-0 right-0 flex w-9 items-center justify-center rounded-r-lg text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        {shown ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </button>
    </div>
  )
}
