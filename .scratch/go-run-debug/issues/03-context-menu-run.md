# 03: Run packages from context menus

**What to build:** Right-click a Go file or package directory to run its entire containing package, reusing matching saved configurations instead of discarding their customizations.

**Blocked by:** 01 — Run a Go package from a saved configuration.

Status: ready-for-agent

- [ ] Offer Run from the editor/project context for Go files and from package directories containing Go source, without requiring Go PSI or a new Go parser.
- [ ] A file selection targets its containing package, including sibling source files; a directory selection targets that directory. Do not execute a single file or search elsewhere for a main package.
- [ ] Create a sensibly named configuration when needed and reuse a matching configuration for the same package, preserving existing settings.
- [ ] Validate with background Go tooling using the selected configuration's environment and build flags when present. Show clear non-main-package or build errors without blocking the UI thread.
- [ ] Test file and directory selection, unrelated contexts, sibling-file compilation, rejection of library packages, and configuration reuse. Demonstrate a context-menu run in the sandbox.
- [ ] Keep this slice independently deliverable after ticket 01; expose context-menu Debug only when the debugger is available in the final integration slice.
