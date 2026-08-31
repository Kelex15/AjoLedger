# ADR 0003: Java + Spring Boot as the Backend Language/Framework

**Status:** Accepted

## Context

Before anything else, I needed to pick the language and framework. This project exists to demonstrate real backend/ledger engineering — financial correctness, explicit concurrency reasoning, a trust/risk model I can actually explain — not to ship a UI fast, so I optimized for correctness and explainability over raw velocity.

## Decision

Java 21 + Spring Boot.

Money handling wants strong static typing — `BigDecimal` everywhere, never `double`/`float` — which catches a class of bugs at compile time that a dynamic language wouldn't. Spring also gives me both ends of the spectrum I need: JPA for the CRUD state that doesn't need special handling, and a clean drop down to `JdbcTemplate` for the ledger where I want to control and explain transaction isolation and locking directly (more on that in ADR 0004) — I'm not fighting the framework either way.

The ecosystem lines up with what the project needs too: Spring Security for auth, Testcontainers and JUnit 5 for real-Postgres integration tests (especially concurrent writes), and a well-worn path to a containerized deploy on App Runner. And practically speaking, Java/Spring is widely used across backend and fintech-adjacent engineering, so going deep here is directly useful beyond just this project.

## Alternatives considered

Node/TypeScript was the obvious one to weigh — huge ecosystem, fast to write — but its typing story is weaker for what I actually care about (it's erased at runtime), and explicit transaction/locking control isn't as idiomatic there as it is in a Spring/JDBC stack.

Kotlin got a real look since it's the same JVM ecosystem with nicer syntax. I passed on it mainly because Java is the safer, more recognized choice for this project's purpose, and the ergonomic wins Kotlin offers don't really move the needle on what I'm trying to demonstrate — this is about ledger correctness and concurrency, not language cleverness.

Go was tempting for its concurrency model and simple deploys, but its ORM-equivalent ecosystem is thinner, and it's a weaker fit for the enterprise JVM stack this project is meant to demonstrate. Also would've meant giving up Spring Security and Spring Data, which I didn't want to do.

## Consequences

Java/Spring comes with more verbosity and ceremony than the alternatives above — I'm accepting that on purpose, since the project prioritizes explicit, auditable logic over convenience, and Lombok keeps the boilerplate manageable on the low-stakes side. Committing to the JVM end to end also means the JPA/JdbcTemplate split in ADR 0004 is a decision inside this stack, not a separate framework choice.
