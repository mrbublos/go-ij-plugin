# Standalone Go Run and Debug for IntelliJ IDEA

Run and debug local Go main packages without JetBrains' Go plugin. This personal-use
plugin targets IntelliJ IDEA 2026.2.2 on macOS Apple Silicon.

## Install and prepare

Download a ZIP from [GitHub Releases](https://github.com/mrbublos/go-ij-plugin/releases),
or build one locally as described below.

Build with `./gradlew buildPlugin` and install the ZIP from `build/distributions`
using **Settings > Plugins > gear > Install Plugin from Disk**, then restart IDEA.
JetBrains' Go plugin is not required and may remain disabled.
Install Go and Delve yourself. Prepare generated code, frontend assets and any
supporting services before launching; the plugin does not orchestrate them.

In **Settings > Tools > Go Toolchain**, optionally set **Go executable** and
**Delve executable** to absolute executable paths. Blank fields search PATH and
common macOS locations (`/opt/homebrew/bin`, `/usr/local/bin`, `/usr/local/go/bin`
for Go, and `~/go/bin` for Delve). Explicit paths take precedence; invalid paths
produce setup guidance rather than silently falling back. Run requires only Go;
Debug requires both tools.

## Run a package

In **Run > Edit Configurations**, add **Go Application**:

| Field | Meaning |
| --- | --- |
| Main package directory | Directory containing the whole executable `package main` |
| Working directory | Runtime directory; blank defaults to the package directory |
| Program arguments | Arguments using IntelliJ quoting, such as `one "two words"` |
| Environment overrides | Values added to or replacing the inherited environment |
| Build flags | Go build options such as `-tags special` |

Build flags and environment also apply to background package inspection. Builds
always target the selected package even when the working directory differs. Run
builds a temporary executable and launches it directly. Compiler errors, program
output and exit status appear in the Run console. Stop cancels build or execution;
Rerun starts a fresh session. Closing the project cleans up owned resources.

Right-click a `.go` file in the editor/project view or its package directory and
choose **Run** or **Debug**. Files launch their containing package, including sibling
files; directories launch only themselves. Existing matching Go Application
configurations are reused with their custom options. Otherwise the package directory
name becomes the configuration name. Library packages are rejected visibly after
background inspection; the plugin does not search for a different main package.

## Debug a package

Add ordinary line breakpoints in `.go` file gutters, then Debug a saved or context
configuration. The native Debug window shows verification feedback, the suspended
source position, goroutines, stack frames, scopes and expandable variables. Use its
Pause, Resume, Step Into, Step Over, Step Out, Stop and Rerun controls. Unsupported
breakpoint locations are reported as unverified. Adapter/build failures appear in
the console. Each session owns a loopback Delve DAP server, connection and temporary
build output; normal exit, failure, Stop and project closure release them. Debug builds
use the selected Go compiler with optimization/inlining disabled, then Delve launches
the executable. Configured and inherited PATH values reach the application unchanged.

If macOS rejects debugging, inspect the Debug console first. Check that the selected
Delve executable runs from a terminal and matches the machine architecture. Review
any macOS developer-tool authorization prompt and **System Settings > Privacy &
Security > Developer Tools** where available, then restart the IDE after granting
access. Follow the installed Delve distribution's signing/setup guidance if the
error names signing or authorization; do not disable system security protections.

## Limits

Version 1 supports local noninteractive applications. It does not implement Go tests
or coverage, remote/Docker debugging, expression evaluation, conditional breakpoints,
hit counts, logpoints, interactive debug stdin, Go editing features, automatic tool
installation or project/service preparation. No Marketplace publishing or support
commitment for other IDE versions/platforms is included. The developer's real project
still requires its own acceptance test.

## Development and verification

Use Java 25 (the target IDE's bundled JBR works). The wrapper pins Gradle 9.1.0;
the default SDK is IntelliJ IDEA 2026.2.2. Append
`-PlocalIde="/Applications/IntelliJ IDEA.app"` to use an installed SDK.

```sh
./gradlew spotlessCheck             # Formatting verification
./gradlew spotlessApply             # Apply the pinned formatter
./gradlew compileKotlin compileTestKotlin
./gradlew test                      # Requires installed Go and Delve
./gradlew check                     # Compilation, formatting and tests
./gradlew runIde                    # Isolated development sandbox
./gradlew buildPlugin               # ZIP under build/distributions
./gradlew verifyPlugin              # Compatibility with selected target IDE
```

Tests use small Go fixtures and registered platform configurations/runners. They
cover persistence, context production/reuse, custom launch options, missing tools,
library/build failures, output and exit status, stop/rerun, project disposal, native
breakpoints, goroutines, frames, variables and stepping. External DAP fake-server
tests cover ordering, rejected requests and unexpected disconnects. No test requires
JetBrains' Go plugin. Before declaring a build installable, build the ZIP, run the
compatibility verifier and check the target sandbox with that plugin absent. Report
headless native-platform checks separately from interactive and real-project checks.

## Automatic releases

The [release workflow](.github/workflows/release.yml) runs on pushes to `main`
that change source/tests, Gradle build files or the workflow itself. Documentation-only
changes do not publish a release. It can also be run manually from Actions on `main`.

On macOS ARM, it installs Java 25, Go 1.25.1 and Delve 1.25.2, then runs `check`,
`buildPlugin` and `verifyPlugin`. Only successful checks publish the ZIP to GitHub
Releases using the built-in `GITHUB_TOKEN`; no personal token is required. Repository
or organization policy must allow the workflow's `contents: write` permission.

Each build uses plugin version `0.1.<workflow run number>` and a `build-<commit SHA>`
release tag pointing to the exact tested commit. Reruns leave an existing release
for that commit unchanged. Local builds retain version `0.1.0` unless supplied
`-PpluginVersion=...`. Releases are uploaded automatically once the workflow is
committed and pushed to GitHub; adding the file locally does not publish anything.

## Recorded validation

On 2026-11-08, all 33 tests, formatting, compilation, ZIP creation and offline
Plugin Verifier passed on macOS Apple Silicon with Go 1.25.1, Delve 1.25.2 and
IDEA IU-262.10315.125. The ZIP plugin JAR matches the headless sandbox JAR byte for
byte; the platform smoke asserts JetBrains' Go plugin absent or disabled. Verifier
reports compatibility with two deprecated Run-runner references and two experimental
API usages; recheck these before updating the IDE target. See the release ticket
for commands and artifact hashes. Interactive UI/install-dialog and developer
real-project acceptance were not performed.

## Project documentation

- [Feature specification](.scratch/go-run-debug/spec.md) and [release ticket](.scratch/go-run-debug/issues/06-context-debug-release.md): behavior and exact validation evidence.
- [Architecture](docs/architecture.md), [glossary](GLOSSARY.md), and [ADRs](docs/adr/0001-single-module.md).
- [Contributor instructions](AGENTS.md) and [local issue workflow](docs/agents/issue-tracker.md).
