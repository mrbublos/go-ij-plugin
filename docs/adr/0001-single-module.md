# ADR 0001: One Gradle module with responsibility-based packages

Status: accepted
Date: 2026-11-08

## Context

This is a personal-use Go run/debug plugin with no implementation yet. Its current
scope needs configuration, execution, toolchain access, and debugger integration.
There is no independent distribution or public plugin API requirement.

## Decision

Use one Gradle module. Organize Kotlin packages by responsibility and enforce the
dependency directions documented in [architecture](../architecture.md) through
review. Add packages as behavior arrives. Keep a small automated core of build,
formatting, and deterministic test checks, introduced with bootstrap.

## Consequences

Build and navigation remain simple, with fewer interfaces and artifacts to manage.
Package separation alone does not enforce dependency direction; review must check
it. Revisit build-module separation when an actual independent artifact or repeated
dependency violations justify the additional build and maintenance cost.

This decision complements the feature specification and contradicts no prior ADR.
