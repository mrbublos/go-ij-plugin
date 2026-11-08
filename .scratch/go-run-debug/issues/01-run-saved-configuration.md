# 01: Run a Go package from a saved configuration

**What to build:** Install the standalone plugin, select a Go main package in a saved Go Application configuration, and build and run it in IntelliJ's console. This is the first complete path from configuration to application execution.

**Blocked by:** None (can start immediately).

Status: ready-for-agent
Progress: resolved

- [x] Bootstrap a Kotlin plugin with Gradle IntelliJ Platform tooling and Java 25 for IDEA 2026.2.2; provide a sandbox launch task and installable ZIP without requiring JetBrains' Go plugin.
- [x] Detect Go through PATH and common macOS locations; provide a persistent explicit executable path that takes precedence and actionable missing/invalid-tool errors. Run does not require Delve.
- [x] Create and persist a Go Application configuration selecting a main-package directory; initially use that directory as the working directory.
- [x] Validate the selected executable package using background Go tooling; reject invalid or non-main packages without searching for another application.
- [x] Build the entire package into a session-specific temporary executable and launch it directly without a shell. Show compilation errors, application output, and actual exit status in the console.
- [x] Keep builds and process work off the UI thread. Stop during build or execution, normal exit, launch failure, and project closure clean up owned processes and temporary artifacts; Rerun starts a fresh session.
- [x] Include a small Go module fixture and tests through the configuration launch boundary covering successful output, nonzero exit, build failure, invalid package, missing Go, and paths containing spaces. Verify configuration persistence and stop/rerun behavior.
- [x] Smoke-test installation and a saved-configuration run on macOS with JetBrains' Go plugin disabled. Keep all later debugger, editor, and remote features outside this slice.

## Bootstrap conventions and verification

- [x] Follow [the architecture](../../../docs/architecture.md): one Gradle module, responsibility-based packages introduced as needed, and the documented dependency directions.
- [x] Add the Gradle wrapper and pin build tooling and formatter versions. Automate compilation, formatting verification, and deterministic tests.
- [x] Document verified commands in the [README](../../../README.md) for formatting checks, compilation, tests, sandbox launch, ZIP creation, and target-IDE compatibility verification.
- [x] Build the ZIP and run target-IDE compatibility verification before declaring the plugin installable. Report real Go and sandbox validation separately, with any blockers; Delve checks arrive with debugger work.

## Answer

Implemented the standalone Kotlin plugin and saved-configuration Run slice. The
registered Go Application configuration persists the package directory, detects
Go or uses the explicit application setting, validates the main package in the
background, builds a session-specific executable, and exposes output/exit status
through IntelliJ's process console. Stop and project disposal terminate owned
processes; completion removes temporary executables; rerun uses fresh state.

Validation on macOS Apple Silicon, Java 25, Go 1.25.1, IDEA 2026.2.2:

- `gradle spotlessApply check buildPlugin -PlocalIde="/Applications/IntelliJ IDEA.app"`
  passed; the final `check` rerun includes all 10 configuration platform tests.
- Tests cover registered plugin loading without an enabled JetBrains Go plugin,
  saved XML round-trip followed by a real Go launch, whole-package output with
  paths containing spaces, nonzero exit, compiler errors, non-main rejection,
  missing explicit Go, Stop during build, Stop before start, fresh reruns and
  executable cleanup, and project-disposal cancellation.
- `buildPlugin` produced `build/distributions/go-ij-plugin-0.1.0.zip` and the
  headless searchable-options sandbox loaded the plugin successfully.
- `gradle verifyPlugin --offline -PlocalIde="/Applications/IntelliJ IDEA.app"`
  passed: compatible with IU-262.10315.125. Verifier reports two deprecated API
  references to `DefaultProgramRunner`, with no compatibility errors.
- The first test was written before implementation, but the initial red run
  stopped at build-tool/SDK resolution errors rather than a behavioral assertion.
  Later checks exercised real platform behavior; no stronger TDD claim is made.

The installation/run smoke evidence is automated and headless, not an interactive
UI walkthrough or a test of the user's actual project. Broader native workflow
validation remains in ticket 06. Initial network stalls required fetching two
published platform-test JARs into a temporary Gradle cache; no repository dependency
substitution was made. Plugin Verifier's online dependency download stalled;
supported offline verification used the installed target SDK successfully.
