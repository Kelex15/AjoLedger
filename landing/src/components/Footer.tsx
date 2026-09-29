import { LogoMark } from './Logo'
import { links } from '../site'

export function Footer() {
  return (
    <footer className="border-t border-border">
      <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10 sm:px-6 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <LogoMark className="size-5" />
          <span>AjoLedger</span>
          <span className="opacity-50">·</span>
          <span>Portfolio project, sandbox payments only</span>
        </div>

        <nav className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
          <a href="/engineering" className="text-muted-foreground transition-colors hover:text-foreground">
            How it is built
          </a>
          <a href={links.repo} className="text-muted-foreground transition-colors hover:text-foreground">
            GitHub
          </a>
          <a href={links.signIn} className="text-muted-foreground transition-colors hover:text-foreground">
            Sign in
          </a>
        </nav>
      </div>
    </footer>
  )
}
