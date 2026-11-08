# 06: Complete context-menu debugging and validate the installable release

**What to build:** Debug whole Go packages from context menus and deliver a documented, locally installable plugin ZIP whose complete Run/Debug workflow has been checked on the target IDE.

**Blocked by:** 03 — Run packages from context menus; 05 — Inspect and step through suspended applications.

Status: ready-for-agent
Progress: resolved

- [x] Offer context-menu Debug for Go files and package directories, using the same whole-package targeting, background validation, and saved-configuration reuse as Run.
- [x] Verify context launches preserve custom working directory, arguments, environment, and build flags; files and directories for the same package reuse the appropriate configuration without duplicate defaults.
- [x] Build an installable ZIP and run plugin compatibility verification against IDEA 2026.2.2. Confirm the plugin has no required dependency on JetBrains' Go plugin.
- [x] Smoke-test the ZIP on macOS Apple Silicon with JetBrains' Go plugin disabled: saved and context-menu Run/Debug, breakpoints, stepping, goroutines, frames, variables, console output, stop/rerun, and project-close cleanup.
- [x] Include integration coverage for context-menu Debug and regression checks for missing tools, non-main packages, build failures, unverified breakpoints, unexpected disconnects, and repeated sessions. Retain tests alongside their owning behavior rather than deferring all testing to this ticket.
- [x] Document local ZIP installation, Go/Delve discovery and explicit paths, configuration fields, whole-package context launches, working-directory defaults, manual project preparation, and macOS debugger-permission troubleshooting.
- [x] Document v1 limits: local noninteractive applications only; no Go test runner, remote/Docker integration, expression evaluation, advanced breakpoints, Go editing implementation, or automatic tool installation. Do not publish to Marketplace.
- [x] Report exact validation performed and any remaining environment limitations. The developer's actual project will be tested by the developer later; do not claim real-project acceptance from sample-module results.

## Answer

Completed on 2026-11-08. Context Debug uses the existing registered configuration
producer and Debug runner. New real-Delve platform coverage proves file/directory
contexts reuse one saved configuration and preserve working directory, quoted
arguments, environment and build flags while compiling sibling Go files.

The new non-main Debug regression initially failed: Debug skipped package inspection.
Debug now runs background `go list` with configured flags/environment before starting
Delve. Run and Debug share inspection command/validation rules in `GoTools`; each
session retains ownership and cancellation of its subprocess. The focused context
and saved Debug launch tests passed after the fix. README now documents installation,
tool discovery/overrides, all launch fields, package context behavior, debugger
controls, manual preparation, macOS authorization troubleshooting and v1 limits.

Validation on macOS Apple Silicon, Go 1.25.1, Delve 1.25.2, Java 25 and installed
IDEA 2026.2.2 (IU-262.10315.125):

- `GRADLE_USER_HOME=/private/tmp/go-ij-gradle ./gradlew --offline -PlocalIde='/Applications/IntelliJ IDEA.app' spotlessApply check buildPlugin verifyPlugin` passed.
- All 32 tests passed, none skipped. This includes native headless platform Run/Debug
  and configuration/context seams, real Delve breakpoints, stepping, goroutines,
  frames, variables, output, stop/consecutive sessions and project-close cleanup;
  missing tools, non-main packages, build failures and unverified breakpoints;
  external DAP fake-server ordering, disconnect and rejected-request checks.
- Built `build/distributions/go-ij-plugin-0.1.0.zip`. ZIP SHA256:
  `b3b033550e31a1b51e6fd7ae526e2f2cd329bb7907e4d3929a03d924281a35aa`.
  Its plugin JAR is byte-identical to the JAR loaded by the headless test sandbox
  (SHA256 `bdf3cdabd76a920a05319ec04114b96e2e3e23b80e5ddf068819f8cc21fe1d97`).
  The platform smoke asserts this plugin enabled and JetBrains' Go plugin absent
  or disabled. The manifest requires only `com.intellij.modules.platform`.
- Plugin Verifier reports **Compatible**, with two deprecated `DefaultProgramRunner`
  references and two experimental API usages (`ExecutionEnvironment.isHeadless`
  and `XDebuggerEditorsProvider.isEvaluateExpressionFieldEnabled`). These are target-
  compatible APIs; future platform upgrades require renewed verification. Installed
  SDK layout warnings concern absent unrelated bundled-plugin paths.
- `git diff --check` passed.

Sandbox evidence is automated/headless, including native XDebugger state and the
packaged plugin JAR; an interactive menu/gutter/window walkthrough and install-dialog
exercise were not performed. No developer real-project acceptance is claimed.
No Marketplace publication was performed.
