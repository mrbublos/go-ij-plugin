# 01: Run a Go package from a saved configuration

**What to build:** Install the standalone plugin, select a Go main package in a saved Go Application configuration, and build and run it in IntelliJ's console. This is the first complete path from configuration to application execution.

**Blocked by:** None (can start immediately).

Status: ready-for-agent
Progress: open

- [ ] Bootstrap a Kotlin plugin with Gradle IntelliJ Platform tooling and Java 25 for IDEA 2026.2.2; provide a sandbox launch task and installable ZIP without requiring JetBrains' Go plugin.
- [ ] Detect Go through PATH and common macOS locations; provide a persistent explicit executable path that takes precedence and actionable missing/invalid-tool errors. Run does not require Delve.
- [ ] Create and persist a Go Application configuration selecting a main-package directory; initially use that directory as the working directory.
- [ ] Validate the selected executable package using background Go tooling; reject invalid or non-main packages without searching for another application.
- [ ] Build the entire package into a session-specific temporary executable and launch it directly without a shell. Show compilation errors, application output, and actual exit status in the console.
- [ ] Keep builds and process work off the UI thread. Stop during build or execution, normal exit, launch failure, and project closure clean up owned processes and temporary artifacts; Rerun starts a fresh session.
- [ ] Include a small Go module fixture and tests through the configuration launch boundary covering successful output, nonzero exit, build failure, invalid package, missing Go, and paths containing spaces. Verify configuration persistence and stop/rerun behavior.
- [ ] Smoke-test installation and a saved-configuration run on macOS with JetBrains' Go plugin disabled. Keep all later debugger, editor, and remote features outside this slice.

## Bootstrap conventions and verification

- [ ] Follow [the architecture](../../../docs/architecture.md): one Gradle module, responsibility-based packages introduced as needed, and the documented dependency directions.
- [ ] Add the Gradle wrapper and pin build tooling and formatter versions. Automate compilation, formatting verification, and deterministic tests.
- [ ] Document verified commands in the [README](../../../README.md) for formatting checks, compilation, tests, sandbox launch, ZIP creation, and target-IDE compatibility verification.
- [ ] Build the ZIP and run target-IDE compatibility verification before declaring the plugin installable. Report real Go and sandbox validation separately, with any blockers; Delve checks arrive with debugger work.
