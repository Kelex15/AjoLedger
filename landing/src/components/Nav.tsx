import { ButtonLink } from './Button'
import { Logo } from './Logo'
import { links } from '../site'

export function Nav({ sections = [] }: { sections?: { href: string; label: string }[] }) {
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/85 backdrop-blur-sm">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Logo />

        <nav className="hidden items-center gap-1 md:flex">
          {sections.map((section) => (
            <a
              key={section.href}
              href={section.href}
              className="rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
            >
              {section.label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <ButtonLink href={links.signIn} variant="ghost">
            Sign in
          </ButtonLink>
          <ButtonLink href={links.signUp}>Get started</ButtonLink>
        </div>
      </div>
    </header>
  )
}
