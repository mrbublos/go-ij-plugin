# ADR 0003: Build debug executables before launching Delve

Status: accepted
Date: 2026-11-08

## Context

Debug must honor an explicitly selected Go compiler, including executables not named
`go`, and preserve the application's configured or inherited environment exactly.
Delve's DAP debug mode applies launch environment overrides before resolving `go`
for its build. Prepending a compiler shim to PATH therefore leaks into the debuggee;
restoring PATH in the launch request would lose the selected compiler.

## Decision

The debug session builds its temporary executable directly with the selected Go
compiler, configured build flags and environment, adding `-gcflags=all=-N -l`.
It then launches that executable through Delve DAP `exec` mode. The session owns
the build process and executable alongside the adapter and connection. Stop and
failure clean up these resources. Cleanup detaches ownership under the session
lock, then performs protocol and process waits outside that lock.

## Consequences

No compiler shim or PATH rewriting is needed. Compiler errors stream to the console
before Delve starts. Debug build behavior belongs to the debugger package; it does
not introduce a dependency on execution. This complements ADR 0001 and preserves
the specified whole-package debugging behavior.
