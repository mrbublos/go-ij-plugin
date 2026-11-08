# 01: Build and publish GitHub release ZIPs

Status: ready-for-agent
Progress: resolved
Blocked by: None

- [x] Add the filtered main-branch/manual workflow and publication gates.
- [x] Support CI version override without changing local defaults.
- [x] Make releases commit-specific and safe to rerun; document usage.
- [x] Lint workflow, exercise release-script branches without publishing, and
  verify versioned packaging locally. Report that hosted execution is untested.

## Answer

Added the filtered `main`/manual workflow, macOS ARM tool setup, verification gates,
and commit-specific release publication with the built-in token. CI overrides the
plugin version; local defaults remain unchanged. Documented release behavior.

Validation: actionlint 1.7.12 passed; the publish script passed stubbed new-release,
existing-release, and failed-publication scenarios without remote calls.
`spotlessCheck buildPlugin -PpluginVersion=0.1.42` passed against the installed target
IDE, and the ZIP's embedded plugin version is 0.1.42. No plugin runtime code changed,
so the previously passing runtime suite was not rerun. GitHub-hosted execution and
actual publication remain untested until the workflow is committed and pushed.
