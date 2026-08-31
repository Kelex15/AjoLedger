# ADR 0002: Modular Monolith Architecture (Package-by-Feature)

**Status:** Accepted

## Context

The backend is growing past a handful of domains now — `user`, `auth`, `account`, `transaction`, `ledger`, `fundingsource`, plus `system` for the cross-cutting stuff — and I need to decide how to lay that out. I want the code to read like a set of coherent business capabilities, so someone looking at it (me in six months, or anyone else) can see the domain model without hunting across the tree. At the same time, this is a single deployable app on App Runner, and I have zero interest in paying for microservices' operational overhead on a solo project that doesn't need it.

## Decision

Package by feature, not by layer:

```
com.ajoledger.backend/
├── system/          (cross-cutting: config, security, properties)
├── auth/
├── user/
├── account/
├── transaction/     (JdbcTemplate — ledger core)
├── ledger/
├── fundingsource/
```

Each domain owns its own entity, controller, service, repository, and `dto/` folder, the same way Spring's PetClinic reference app does it. It's still one Spring Boot app, one jar, one deploy — but I'm treating the module boundaries as real boundaries, not just a folder convention: a domain should only ever touch another domain through its service/DTO surface, never its entity or repository directly.

## Alternatives considered

The obvious alternative is package-by-layer — top-level `controllers/`, `services/`, `repositories/` packages with every domain's classes scattered across all three. I've worked in codebases like that, and it makes a single feature hard to see all at once; you're jumping between three unrelated parts of the tree to understand one thing.

I also considered just going straight to microservices, mostly to have "microservices experience" on the project. Decided against it — this project's actual hard problem is financial correctness in a single ledger, not horizontal scale, and splitting into services now would mean paying for network boundaries and distributed transactions I don't need yet. A modular monolith with real internal boundaries keeps that door open without forcing it.

## Consequences

The boundaries only hold if I actually enforce them — nothing stops `ledger` from reaching straight into `account`'s repository if I'm not paying attention, so that's worth flagging if it comes up. Java's package-private visibility is a decent tool here: repositories and internal helpers can be package-private so other domains are blocked from bypassing the service layer at compile time, not just by convention.

If a domain ever needed to become its own service — unlikely for this project, but worth naming — having real boundaries drawn already would make that extraction mechanical instead of an untangling exercise.
