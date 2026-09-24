# ADR 0001: API Versioning Strategy

**Status:** Accepted

## Context

I need a way to ship changes to the API without breaking whatever's already calling it, the classic problem of shipping v2 while v1 clients are still out there. The framework I'm on has native support for this now, so I don't need to hand-roll it with parallel URL prefixes or duplicated controller packages the way I would have a year ago.

My first instinct was the familiar `/api/v1/...` prefix, it's what most tutorials show, and it's simple to demo. But for something banking-shaped, where a URL is supposed to identify a resource, I didn't love that the same user resource would live at two different addresses depending on which version you asked for. That pushed me toward resolving the version out of a header instead, and letting the framework handle the dispatch.

## Decision

Version is resolved from a request header rather than the URL. I set a default version so a client that never sends the header just gets the current baseline instead of failing outright, I don't want an unaware client to break the moment this ships.

Individual endpoints declare which version they serve, and multiple versions of the same endpoint can live side by side, same controller to start, splitting into version-specific controllers later if the divergence actually gets large enough to justify it. Resource URLs don't change across versions; only the header does.

## Alternatives considered

I looked at the usual three: URL path versioning, a query parameter, and a media-type parameter.

Path versioning is what I described above, simple and common, but it ties a resource's identity to its protocol version, which bugged me more the longer I thought about it. A query param is easy too, but versioning feels like a transport concern more than part of a resource's query semantics, and it's the kind of thing that's easy to forget when building a URL by hand. Media-type parameter versioning is probably the most "correct" by HTTP content-negotiation standards, but it adds friction to manual testing (curl, Postman) that isn't worth it for a solo project at this stage.

## Consequences

Once there's more than one version of an endpoint, every mapping needs an explicit version, the default keeps that from being a hard requirement immediately, but it also means retiring v1 later is a deliberate decision (changing or removing the default), not something that happens automatically just because v2 exists.
