# ADR 0002: Plugin build and test tooling

Status: accepted
Date: 2026-11-08

## Decision

Use Gradle 9.1.0, Kotlin 2.3.20, IntelliJ Platform Gradle plugin 2.20.0,
and Java 25 to target IntelliJ IDEA 2026.2.2. The platform plugin resolves the
IDE SDK, packages the plugin and runs Plugin Verifier. `-PlocalIde=/path/to/IDE`
selects an installed SDK for development and verification.

Use JUnit 4.13.2 and the matching platform test framework for configuration-level
tests. Use Spotless 8.0.0 with ktfmt 0.58 for a pinned, reproducible format.
These are build/test dependencies; no new third-party runtime dependency is
introduced. Gradle, Kotlin, IntelliJ build tooling, JUnit and Spotless/ktfmt use
Apache-2.0 or EPL-1.0 (JUnit) licenses suitable for this development use.

## Consequences

The wrapper and build version pins make local checks repeatable. The target IDE
requires Java 25. IDE test framework artifacts must match the SDK build. Build
plugin 2.10.5 cannot parse the target SDK's namespaced module descriptors; 2.20.0
is required. This complements ADR 0001 without changing package boundaries.
