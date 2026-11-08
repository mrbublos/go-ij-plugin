# 04: Debug to a line breakpoint

**What to build:** Debug a saved Go Application configuration with installed Delve, stop at a verified Go line breakpoint in IntelliJ's Debug window, then resume or terminate the application.

**Blocked by:** 02 — Customize application launches.

Status: ready-for-agent
Progress: resolved

- [x] Detect Delve through PATH and common macOS locations and provide a persistent explicit path override. Missing or invalid Delve produces setup guidance for Debug while Run remains usable; do not automatically install tools.
- [x] Launch an owned Delve DAP server on loopback with an ephemeral port; use the bundled LSP4J client and IntelliJ XDebugger. Honor package, working directory, arguments, environment, and build flags.
- [x] Complete initialization and initial breakpoint setup before execution. Allow Go line breakpoints without JetBrains' Go plugin, synchronize additions/removals, and display Delve's verification result or rejection message.
- [x] Hit a breakpoint, show the suspended source location, resume execution, and stop or rerun a session from the native debugger UI. Defer full goroutine/frame/variable browsing and stepping to ticket 05.
- [x] Keep protocol and build work off the UI thread. Report build/startup failures, rejected requests, unexpected disconnects, and normal exits. Stop, failure, and project closure release owned processes, connections, and temporary executables, including partial startup.
- [x] Test initialization ordering, breakpoint changes, request failures, disconnects, and cleanup using a fake server at the external DAP boundary rather than internal-method mocks.
- [x] Validate a real Delve session against the sample module: initial breakpoint hit, unverified breakpoint feedback, resume, stop/rerun, and build failure. Locate or install Delve separately for development validation if necessary; clearly report any validation blocker.

## Answer

Implemented the registered Go Debug runner and standalone Go line-breakpoint type,
bridging bundled LSP4J DAP into XDebugger. Initialization waits for Delve's
initialized event, synchronizes the latest per-source breakpoints, then sends
configurationDone without waiting prematurely for the launch reply. Additions and
removals are serialized, including changes while the adapter starts. Verified and
rejected breakpoints update native presentation; suspension shows the top source
location, and native Resume, Stop, and repeat launches work.

Delve discovery and its persistent override are available in Go Toolchain settings.
The selected Go executable builds the session-owned debug binary directly, so
renamed overrides are honored without changing application PATH. Explicit package
and `dlvCwd` directories keep separate runtime directories from changing build selection.
Each session owns its loopback server, DAP connection, build process/output, and
process tree; failure, Stop, normal exit, and project disposal clean up those resources.
Launch/build/protocol work runs off the EDT, and adapter diagnostics reach the console.

Validation: red-to-green fake DAP and configuration/native-launch tests; full `check`
passed all 26 tests; `buildPlugin` produced the installable ZIP. The final strengthened
startup-edit assertion and formatting checks also passed. Verified on IDEA IU-262.10315.125 with Go 1.25.1 and Delve 1.25.2.
The eight debugger tests cover delayed launch replies, startup breakpoint edits and
removal, rejected launch, disconnect/connection cleanup, real verified/rejected
breakpoints, configured arguments/environment/build flags/working directory and
renamed Go override, real build failure, invalid Delve while Run still works,
startup cancellation, project disposal and executable removal, plus the registered
native Debug runner's source suspension, Resume, Stop, and fresh second launch.

The native runner tests use the real plugin extension registrations in a headless
IDE. An interactive Debug-window walkthrough and the user's actual project remain
separate validation; full stack/variable browsing and stepping remain ticket 05.

Final review replaced the compiler PATH shim with a session-owned debug build and
DAP exec launch; see ADR 0003 and ticket 06 for regression and release evidence.
