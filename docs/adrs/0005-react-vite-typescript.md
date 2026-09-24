# ADR 0005: React + Vite + TypeScript for the Frontend

**Status:** Accepted

## Context

The frontend is deliberately a lighter build than the backend — it exists to demonstrate the API and the trust/risk model working end to end, not to be the differentiating piece of engineering here. That framing shapes the choice: I want something with minimal setup friction and a huge, boring, well-documented ecosystem, so I'm not spending time debugging framework quirks on the part of the project that isn't the point.

## Decision

React 19 + Vite + TypeScript.

React because it's the most widely recognized frontend library, which means less risk of getting stuck on framework-specific weirdness and more time available for the backend. I'm also using React Compiler (`babel-plugin-react-compiler`, wired in via `@rolldown/plugin-babel` in the Vite config) so re-render optimization is handled automatically instead of me hand-writing `useMemo`/`useCallback` everywhere — appropriate given this isn't where I want to spend manual-optimization effort.

Vite for the build tool: near-instant dev server startup and HMR, minimal config to get moving, and native TypeScript support without a separate compile step for local dev.

TypeScript for the same reason `BigDecimal` is non-negotiable on the backend — money and API-response data benefit from static typing catching mistakes at compile time rather than at runtime. Keeps the "explicit over convenient" bias consistent across the whole stack, not just the Java side.

## Alternatives considered

Next.js would give me SSR and file-based routing for free, but I don't need SSR — this is an authenticated app sitting behind a Spring Boot API, not a content site that needs to rank on search. Pulling in a full meta-framework's opinions and surface area isn't worth it for a piece of the project that's supposed to stay light.

Create React App is effectively dead at this point, and hand-rolling Webpack config is exactly the kind of setup tax Vite exists to avoid.

Vue and Svelte both crossed my mind, but there's no reason to pick a less common framework here — this frontend isn't where I'm trying to prove I can learn something new, and React's ubiquity means less time lost to framework-specific debugging.

Plain JavaScript instead of TypeScript was the other real option, and I ruled it out for the same reason I ruled out `double` for money on the backend — losing compile-time safety on money and API types isn't a tradeoff worth making just to save a little setup time.

## Consequences

I'm on Vite's Rolldown-based bundler (`rolldown-vite`, via `@rolldown/plugin-babel`) rather than the classic Rollup/esbuild pipeline — faster builds, but newer and less battle-tested in production. Worth knowing if something bundler-specific ever breaks in a way that's hard to Google.

TypeScript's strictness settings (`noUnusedLocals`, `noUnusedParameters`, etc.) are on, but they're only useful if I actually keep frontend types in sync with the backend's DTOs as the API grows — worth checking on periodically rather than assuming it stays true by default.
