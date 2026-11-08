# Standalone Go Run and Debug for IntelliJ IDEA

Status: ready-for-agent

## Problem Statement

The developer wants to run and debug local Go applications inside IntelliJ IDEA without depending on JetBrains' Go plugin. The initial audience is the developer's own macOS workflow, not a broadly distributed plugin product. The repository currently contains agent configuration only; the plugin must be built from scratch.

## Solution

Provide a locally installable standalone plugin with a Go Application run configuration and context-menu Run/Debug actions for Go files and package directories. Build and launch whole main packages using installed Go tooling. Integrate installed Delve with IntelliJ's Debug window for basic source-level debugging. Keep project preparation and supporting services under the developer's control.

## User Stories

1. As a developer, I want an installable plugin ZIP, so that I can use the plugin in my existing IntelliJ IDEA installation.
2. As a developer, I want standalone Go execution support, so that JetBrains' Go plugin is not required.
3. As a developer, I want installed Go and Delve detected, so that routine setup is minimal.
4. As a developer, I want explicit executable-path settings, so that I can select my own tool installations.
5. As a developer, I want actionable missing-tool errors, so that I can repair my setup.
6. As a developer, I want Run to work without Delve, so that debugging prerequisites do not prevent ordinary execution.
7. As a developer, I want saved Go Application configurations, so that I can repeat launches after restarting the IDE.
8. As a developer, I want to select a main-package directory, so that I can choose the executable application.
9. As a developer, I want the working directory to default to the package directory, so that launch behavior is predictable.
10. As a developer, I want to edit the working directory, so that applications expecting repository-root-relative files can run correctly.
11. As a developer, I want program arguments, so that I can select application behavior.
12. As a developer, I want environment overrides, so that I can supply application configuration.
13. As a developer, I want build flags, so that I can use project-specific build settings.
14. As a developer, I want context-menu Run and Debug on Go files, so that I can launch from the editor or project view.
15. As a developer, I want file-based launches to execute the containing package, so that sibling source files participate.
16. As a developer, I want context-menu launches on package directories, so that I do not need to select a particular source file.
17. As a developer, I want matching configurations reused, so that repeated launches preserve my customizations.
18. As a developer, I want non-executable packages rejected clearly, so that an invalid selection does not silently launch something else.
19. As a developer, I want compilation errors in the console, so that I can correct build failures.
20. As a developer, I want application output and exit status displayed, so that I can understand execution results.
21. As a developer, I want Stop and Rerun controls, so that I can manage application lifecycles.
22. As a developer, I want line breakpoints in Go source files, so that I can suspend at relevant code.
23. As a developer, I want breakpoint verification feedback, so that invalid or unreachable breakpoint locations are visible.
24. As a developer, I want pause and resume, so that I can interrupt and continue execution.
25. As a developer, I want step into, over, and out, so that I can follow control flow.
26. As a developer, I want goroutine selection and stack frames, so that I can inspect execution context.
27. As a developer, I want expandable variables, so that I can inspect values at a suspended frame.
28. As a developer, I want debugger failures reported, so that I can distinguish application problems from debugger problems.
29. As a developer, I want owned processes and temporary artifacts cleaned up, so that stopping or closing a session does not leave unwanted work running.
30. As a developer, I want setup and usage documentation, so that I can test the plugin against my real project later.

## Implementation Decisions

- Bootstrap a Kotlin plugin using Gradle IntelliJ Platform tooling and Java 25. Target IntelliJ IDEA 2026.2.2, initially validated on macOS Apple Silicon. Produce an installable ZIP and a development sandbox launch task.
- Use IntelliJ platform APIs without a dependency on JetBrains' Go plugin. No public plugin API is required.
- Organize responsibilities around configuration/context actions, application execution, and debugger integration. Avoid introducing a general-purpose language framework.
- Persist main-package directory, working directory, program arguments, environment overrides, and build flags through IntelliJ run configurations. Default the working directory to the chosen package directory.
- Provide settings for Go and Delve executable paths. Detect installed tools through PATH and common macOS locations, with explicit paths taking precedence. Require Go for both execution modes and Delve only for Debug. Show setup guidance rather than installing tools.
- Context-menu actions target the selected package directory or the containing directory of a Go file. Reuse matching configurations. Validate executable packages with background Go tooling using the configured build environment; reject non-main packages rather than searching for another target. Do not require Go PSI or implement a Go parser.
- Run means building a temporary executable with go build and launching that executable directly, rather than invoking go run. Preserve configured arguments, environment, working directory, output, and exit status. Use structured process arguments rather than shell commands.
- Debug uses a locally launched Delve DAP server bound to loopback on an ephemeral port. Use the bundled LSP4J protocol client and bridge the session into IntelliJ XDebugger.
- Supply the selected package, working directory, arguments, environment, and build flags to debug launches. Complete protocol initialization and initial breakpoint configuration before allowing the application to run.
- Support ordinary suspend-on-line breakpoints with verification feedback, pause/resume, step into/over/out, goroutine selection, stack frames, source navigation, and expandable variables. Breakpoints must work in Go files without JetBrains' Go plugin.
- Keep builds and debugger protocol work off the UI thread. Handle build failures, invalid configuration, startup failure, disconnects, and normal application exits visibly. Stop/Rerun and project closure must clean up owned processes, connections, and temporary executables.
- Assume generated code, frontend builds, and supporting services have already been prepared by the developer. The plugin does not orchestrate them.

## Testing Decisions

- Test externally observable behavior, not private class structure or internal method-call sequences. There is no existing implementation or test infrastructure to reuse.
- Use launching a Go Application configuration as the primary integration seam. Exercise a small local Go module fixture and observe console output, exit status, process lifecycle, and debugger state through the plugin.
- Cover configuration persistence and context-menu configuration production/reuse with focused platform tests. Cover paths and arguments containing spaces, environment overrides, editable working directories, build flags, missing tools, and rejection of non-executable packages.
- Use a fake server at the external DAP boundary for deterministic protocol ordering and failure cases: initialization, initial and updated breakpoints, rejected requests, unexpected disconnects, and cleanup. Avoid adding test-only seams throughout the plugin.
- Validate against real Delve: launch a package, hit a verified breakpoint, inspect frames and variables, switch goroutines, step, resume, stop, and rerun. Include a breakpoint that Delve cannot verify and a build failure.
- Verify successful runs preserve output and exit status. Verify stop during build or execution and project closure release owned resources. Exercise consecutive sessions to detect stale debugger state.
- Build the plugin ZIP, run target-IDE compatibility verification, and smoke-test in an IDE sandbox with JetBrains' Go plugin disabled.
- The developer will test their actual Go project later. Do not treat sample-module success as proof of compatibility with that project or claim that user acceptance testing has occurred.

## Out of Scope

- Go test execution, coverage, remote attachment, Docker integration, and support commitments for other operating systems or older IDE versions.
- Completion, navigation features outside debugger source navigation, formatting, and syntax-highlighting implementation.
- Single-file execution and automatic searching for a different main package.
- Expression evaluation, conditional breakpoints, hit counts, and logpoints.
- Interactive terminal input during debugging; v1 targets servers and noninteractive applications.
- Automatic Go or Delve installation, project preparation, and supporting-service orchestration.
- Marketplace publishing and a public plugin API.

## Further Notes

- Environment inspection found IntelliJ IDEA 2026.2.2 and Go 1.25.1 on macOS Apple Silicon. Delve was not found on PATH; real debugger validation requires locating or installing it.
- Building and launching an executable instead of literally invoking go run was explicitly accepted, along with package-directory working defaults and whole-package context launches.
- The deliverable is a locally installable plugin for the developer's own workflow. No real-project path has been provided; the developer deferred that testing.
- Technical integration choices are implementation targets, not claims of a working prototype. No plugin code or debugging validation existed when this specification was published.
