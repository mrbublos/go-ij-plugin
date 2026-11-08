# 06: Complete context-menu debugging and validate the installable release

**What to build:** Debug whole Go packages from context menus and deliver a documented, locally installable plugin ZIP whose complete Run/Debug workflow has been checked on the target IDE.

**Blocked by:** 03 — Run packages from context menus; 05 — Inspect and step through suspended applications.

Status: ready-for-agent

- [ ] Offer context-menu Debug for Go files and package directories, using the same whole-package targeting, background validation, and saved-configuration reuse as Run.
- [ ] Verify context launches preserve custom working directory, arguments, environment, and build flags; files and directories for the same package reuse the appropriate configuration without duplicate defaults.
- [ ] Build an installable ZIP and run plugin compatibility verification against IDEA 2026.2.2. Confirm the plugin has no required dependency on JetBrains' Go plugin.
- [ ] Smoke-test the ZIP on macOS Apple Silicon with JetBrains' Go plugin disabled: saved and context-menu Run/Debug, breakpoints, stepping, goroutines, frames, variables, console output, stop/rerun, and project-close cleanup.
- [ ] Include integration coverage for context-menu Debug and regression checks for missing tools, non-main packages, build failures, unverified breakpoints, unexpected disconnects, and repeated sessions. Retain tests alongside their owning behavior rather than deferring all testing to this ticket.
- [ ] Document local ZIP installation, Go/Delve discovery and explicit paths, configuration fields, whole-package context launches, working-directory defaults, manual project preparation, and macOS debugger-permission troubleshooting.
- [ ] Document v1 limits: local noninteractive applications only; no Go test runner, remote/Docker integration, expression evaluation, advanced breakpoints, Go editing implementation, or automatic tool installation. Do not publish to Marketplace.
- [ ] Report exact validation performed and any remaining environment limitations. The developer's actual project will be tested by the developer later; do not claim real-project acceptance from sample-module results.
