# ADR 0001: API Versioning Strategy

**Status:** Proposed

## Context

I need a way to evolve the API over time without breaking existing clients — specifically, to be able to ship a v2 of an endpoint while v1 keeps serving whoever is still calling it. Spring Boot 4.1 (Spring Framework 7) ships native API versioning support, so I don't need to hand-roll this with parallel URL prefixes or duplicated controller packages.

I initially reached for the familiar `/api/v1/...` URL-prefix pattern, but for this project — a banking-style system where the URL should identify a *resource*, not a *protocol detail* — I decided version negotiation belongs in a header instead, resolved by the framework rather than encoded in routing.

## Decision

Use Spring Framework 7's built-in API versioning, resolved from a request header:

- Configured once in `system/WebConfig.java` via `WebMvcConfigurer#configureApiVersioning`, using `ApiVersionConfigurer#useRequestHeader("X-API-Version")`.
- `setDefaultVersion("1")` so requests that omit the header still resolve to the current baseline version, rather than failing — existing/unaware clients keep working.
- Individual mapping methods declare the version they serve via the `version` attribute (e.g. `@GetMapping(path = "/{id}", version = "1")`). Multiple versions of the same logical endpoint can coexist as separate methods (in the same controller, or split into version-specific controllers if the divergence grows), all backed by the same service layer.
- Resource URLs stay stable across versions (`/api/user/{id}`, not `/api/v1/user/{id}`); only the header changes.

## Alternatives considered

- **URL path versioning (`/api/v1/...`)**: simplest to demo, matches a very common convention, and was the project's original stated convention. Rejected in favor of headers because it conflates a resource's identity with its protocol version — the same user resource ends up at two different URLs depending on API version, which is awkward for anything treating the URL as a stable identifier (caching, links, audit logs referencing a resource by URL).
- **Query parameter versioning**: simple, but versioning is usually a client/transport concern, not part of the resource's query semantics; also easy to drop by accident when constructing URLs.
- **Media type parameter versioning**: most "correct" per HTTP content-negotiation purism, but adds friction for a solo project's manual testing/curl workflow with limited benefit over a plain header.

## Consequences

- All future endpoints must declare an explicit `version` on their mappings once more than one version exists; the default-version fallback avoids that being a breaking requirement immediately.
- Clients that never send `X-API-Version` implicitly get v1 forever (via the default), so deprecating v1 later requires deliberately removing or changing the default, not just adding v2 — a decision to make explicitly when that day comes, not something the framework forces.
- `.guide/CLAUDE.md`'s REST API conventions section currently states `/api/v1/...` path versioning; that note is now stale and should be updated to describe header-based versioning instead, so future sessions don't reintroduce the path-prefix pattern by default.
