# 04: Debug to a line breakpoint

**What to build:** Debug a saved Go Application configuration with installed Delve, stop at a verified Go line breakpoint in IntelliJ's Debug window, then resume or terminate the application.

**Blocked by:** 02 — Customize application launches.

Status: ready-for-agent

- [ ] Detect Delve through PATH and common macOS locations and provide a persistent explicit path override. Missing or invalid Delve produces setup guidance for Debug while Run remains usable; do not automatically install tools.
- [ ] Launch an owned Delve DAP server on loopback with an ephemeral port; use the bundled LSP4J client and IntelliJ XDebugger. Honor package, working directory, arguments, environment, and build flags.
- [ ] Complete initialization and initial breakpoint setup before execution. Allow Go line breakpoints without JetBrains' Go plugin, synchronize additions/removals, and display Delve's verification result or rejection message.
- [ ] Hit a breakpoint, show the suspended source location, resume execution, and stop or rerun a session from the native debugger UI. Defer full goroutine/frame/variable browsing and stepping to ticket 05.
- [ ] Keep protocol and build work off the UI thread. Report build/startup failures, rejected requests, unexpected disconnects, and normal exits. Stop, failure, and project closure release owned processes, connections, and temporary executables, including partial startup.
- [ ] Test initialization ordering, breakpoint changes, request failures, disconnects, and cleanup using a fake server at the external DAP boundary rather than internal-method mocks.
- [ ] Validate a real Delve session against the sample module: initial breakpoint hit, unverified breakpoint feedback, resume, stop/rerun, and build failure. Locate or install Delve separately for development validation if necessary; clearly report any validation blocker.
