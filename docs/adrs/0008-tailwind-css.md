# ADR 0008: Tailwind CSS for Styling

**Status:** Accepted

## Context

The frontend has had zero styling up to this point, every page is unstyled HTML, and there's no CSS anywhere in the codebase. ADR 0005 already set the framing for this half of the project: it's deliberately a lighter build, not the piece I'm trying to differentiate on, so the same "minimal setup friction, huge boring well-documented ecosystem" bias applies to styling as it did to picking React + Vite in the first place. I also want to pull from existing templates rather than design every page from scratch, and most of what's out there in the React ecosystem is built with Tailwind, so picking something else would mean re-authoring instead of adapting.

## Decision

Tailwind CSS, wired in via its Vite plugin.

Utility classes mean I never have to invent a component-level CSS architecture: naming conventions, a spacing/sizing scale, how to structure a stylesheet per component, none of that is where I want to spend design effort on this part of the project. It also keeps styling colocated with markup in the same `.tsx` file, which matches the pages/components split already in place rather than adding a parallel set of `.css` files to keep in sync.

## Alternatives considered

Plain CSS files, one per page/component. Zero dependencies, but it means hand-rolling a whole design system's worth of decisions (naming, scale, structure) myself, which is exactly the kind of manual effort ADR 0005 already ruled out spending time on for this part of the project.

CSS Modules. Same problem as plain CSS, I'd still be inventing every spacing/color/typography decision from scratch, plus more per-component boilerplate for no real benefit here since style collisions aren't a concern in an app this size.

## Consequences

Adds a real dependency and a build-time step (the Tailwind Vite plugin), where before there was none. Actual visual styling is still deferred, this ADR only settles the tool, not the design, and lands once the pages/components restructure is done and I've looked at templates to adapt from.
