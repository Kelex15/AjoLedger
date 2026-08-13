# AjoLedger — Learning Log

Concepts worth understanding deeply, not just using, as they come up while building this.

## Spring Framework 7 API Versioning

**Where it shows up:** `backend/src/main/java/com/ajoledger/backend/system/WebConfig.java`, and the `version` attribute on `UserController`'s mappings.
**Why it matters here:** This is the actual mechanism behind ADR 0001 — how the API can evolve across versions without breaking existing clients or baking a version number into every resource URL.
**What to actually learn (not just use):** Not just "call `useRequestHeader(...)` on a configurer" — understand how `ApiVersionStrategy` resolves a version from a request (header, path segment, query param, or media-type parameter — all pluggable), how it parses/compares versions (`SemanticVersionParser` by default), how it picks the matching handler method when multiple `version` values are declared for the same path, and how `ApiVersionDeprecationHandler` can emit RFC 9745/8594 `Deprecation`/`Sunset`/`Link` headers automatically when a version is marked deprecated.
**Where to start:** The official Spring Framework reference docs' "API Version" page (docs.spring.io, Web MVC → MVC Config section) is the primary source — this is a genuinely new (2025) feature, so any blog post about it is worth checking against that page rather than trusting on its own.
**Interview angle:** "How would you version a REST API without breaking existing clients?" — being able to compare URL-prefix vs. header vs. media-type strategies, and explain *why* a resource's URL shouldn't encode its protocol version, is the real question behind "how did you do versioning."
