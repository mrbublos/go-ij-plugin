# Architecture and engineering rules

The plugin uses one Gradle module with responsibility-based Kotlin packages.
[ADR 0001](adr/0001-single-module.md) records the rationale. The
[feature specification](../.scratch/go-run-debug/spec.md) owns product behavior
and toolchain targets; this document owns engineering conventions.

## Package responsibilities

Create packages beneath the plugin base package when their first behavior is implemented.
Use standard Gradle source/resource directories. Mirror responsibility packages
in tests and keep small Go module fixtures under `src/test/testData/`.

| Package | Owns | Allowed project-package dependencies |
| --- | --- | --- |
| `configuration` | Persisted run configurations, editors, context actions, reuse | `execution`, `debugger`, `toolchain` |
| `execution` | Go builds, application processes, console integration, run-session cleanup | `toolchain` |
| `debugger` | Delve DAP sessions, breakpoints, XDebugger integration, debug-session cleanup | `toolchain` |
| `toolchain` | Executable settings, discovery, validation, Go package inspection | None |

These are code dependency directions, not restrictions on runtime callbacks.
Execution and debugger entry points own their launch-input types; configuration
translates persisted settings into those inputs before launch. Neither package
reads configuration UI state, and neither depends on the other. Platform and
library dependencies may be used where the responsibility requires them.

Keep interfaces small and implementation details private where practical.
Introduce interfaces for actual variation or external integrations, rather than
one interface per class. Keep helpers beside their behavior. Extract shared code
only when multiple callers need the same behavior, place it with its owner, and
revisit this dependency map if no existing owner fits. Avoid generic `utils`,
`common`, and `managers` packages and empty scaffolding. There is no public plugin API.

## Threading and resource ownership

Keep builds, tool discovery, package inspection, and protocol work off the UI
thread. Follow IntelliJ platform threading rules for UI and project-model access.
Make threading and cancellation obligations clear at module entry points.

Each run or debug session owns its processes, connections, and temporary artifacts.
Cleanup must tolerate repeated calls and partial startup, and must run on stop,
failure, normal completion, and project closure. Rerun creates fresh session state.
Clean up only resources owned by that session.

Use structured process arguments rather than shell command strings. Preserve
cancellation, useful failure messages, application output, and exit status.
Keep credentials and environment values out of plugin diagnostic logging;
application console output remains application output.

## Dependencies and testing

Prefer platform facilities and existing dependencies. A new dependency needs a
concrete purpose and a compatibility/license check recorded with the change.
Pin build tooling and formatter versions at bootstrap.

Test observable behavior through module interfaces. Use a fake server at the
external DAP seam for deterministic protocol ordering and failure cases. Keep
real Go/Delve and IDE sandbox validation for behavior fakes cannot establish.
Avoid tests coupled to private method calls or class structure. Follow the
[README verification expectations](../README.md#development-and-verification)
and feature acceptance scenarios.

## Maintaining the design

Update this document when responsibilities or dependency directions change.
Record durable choices involving dependencies, persistence, lifecycle, or module
relationships in numbered ADRs under `docs/adr/`, with context, decision, and
consequences. Identify any superseded decision and why it changed. Routine local
implementation choices do not need an ADR.

Review changes for responsibility ownership, dependency direction, threading,
cleanup, observable behavior tests, and documentation consistency. Keep unrelated
cleanup in separate work and add abstractions only for a demonstrated need.
