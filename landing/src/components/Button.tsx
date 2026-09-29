import type { ReactNode } from 'react'

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg text-sm font-medium transition-colors whitespace-nowrap'

const variants = {
  primary: 'bg-primary text-primary-foreground hover:bg-primary/90',
  outline: 'border border-border bg-transparent hover:bg-accent hover:text-accent-foreground',
  ghost: 'hover:bg-accent hover:text-accent-foreground',
} as const

const sizes = {
  md: 'h-10 px-4',
  lg: 'h-12 px-6 text-base',
} as const

export function ButtonLink({
  href,
  children,
  variant = 'primary',
  size = 'md',
}: {
  href: string
  children: ReactNode
  variant?: keyof typeof variants
  size?: keyof typeof sizes
}) {
  return (
    <a href={href} className={`${base} ${variants[variant]} ${sizes[size]}`}>
      {children}
    </a>
  )
}
