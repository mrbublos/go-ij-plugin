# Standalone Go Run and Debug for IntelliJ IDEA

A personal-use plugin for running and debugging local Go main packages without JetBrains' Go plugin. The initial target is macOS Apple Silicon.

## Current state

The first slice implements saved Go Application configurations, Go toolchain
selection, whole-package builds, console output, exit codes, and Stop/Rerun.
Context Run actions are available for Go files and package directories, and saved
launch options customize arguments, environment, build flags, and working directory.
Debugging is tracked in later tickets.

## Start here

- [Feature specification](.scratch/go-run-debug/spec.md): behavior, implementation targets, acceptance scenarios, and exclusions.
- [First implementation ticket](.scratch/go-run-debug/issues/01-run-saved-configuration.md): bootstrap and a saved-configuration run.
- [Architecture](docs/architecture.md): code organization and engineering rules.
- [Glossary](GLOSSARY.md) and [architecture decision](docs/adr/0001-single-module.md).
- [Contributor instructions](AGENTS.md) and [local issue workflow](docs/agents/issue-tracker.md).

## Development and verification

Use Java 25 (the target IDE's bundled JBR also works). Gradle's wrapper pins 9.1.0.
The default SDK is IntelliJ IDEA 2026.2.2. To use an installed copy, append
`-PlocalIde="/Applications/IntelliJ IDEA.app"` to each command.

```sh
./gradlew spotlessCheck             # Formatting verification
./gradlew spotlessApply             # Apply the pinned formatter
./gradlew compileKotlin compileTestKotlin
./gradlew test                      # Configuration integration tests; requires Go
./gradlew runIde                    # Isolated development sandbox
./gradlew buildPlugin               # ZIP under build/distributions
./gradlew verifyPlugin              # Compatibility with the selected target IDE
./gradlew check                     # Compilation, formatting, and tests
```

The tests launch small Go fixtures; Go must be installed and discoverable. Build
cancellation tests use a temporary fake executable at the external tool boundary.
The tests do not require Delve or the JetBrains Go plugin. Report real-tool and
interactive sandbox checks separately from deterministic failure/persistence tests.

## Running a saved configuration

In **Settings > Tools > Go Toolchain**, optionally set the Go executable path.
A blank path detects Go from PATH, `/opt/homebrew/bin`, `/usr/local/bin`, or
`/usr/local/go/bin`. An explicit path takes precedence and is never silently
replaced if invalid.

In **Run > Edit Configurations**, add **Go Application**, choose the directory of
a main package, and run it. The entire package is built into a temporary executable;
its directory is the default working directory. Set **Working directory** to run
elsewhere (blank uses the package directory), **Program arguments** for arguments
using IntelliJ quoting conventions, **Build flags** for Go build options such as
`-tags special`, and **Environment overrides** for variables added to the inherited
environment. Build flags and environment also apply to package inspection; builds
always select the package directory even when the runtime directory differs.
All these options are saved with the configuration. Compiler errors and application output
appear in the Run console. Stop cancels builds or execution; Rerun builds a fresh
executable. Session temporary executables are removed on termination.

Before declaring a build installable, build its ZIP and run target-IDE compatibility
verification. For a sandbox smoke test, disable JetBrains' Go plugin, open the
`src/test/testData/hello` fixture, save a Go Application configuration, Run it,
and confirm `whole package works` plus exit 0. Repeat after restarting the sandbox.
Test Stop/Rerun and project closure with a long-running program. The user's actual
Go project remains a separate acceptance test.

## Validation recorded for the first slice

Compilation, formatting, all 10 platform configuration tests with real Go,
headless sandbox plugin loading, ZIP creation, and offline target-IDE verification
passed on macOS Apple Silicon. Plugin Verifier reports compatibility with
IU-262.10315.125 and two deprecated `DefaultProgramRunner` references. The automated
smoke restores a saved configuration and runs it with JetBrains' Go plugin absent;
it does not establish an interactive UI walkthrough or real-project acceptance.

## Running from context

Right-click a `.go` file in the editor/project view or a directory containing Go
source and choose **Run**. A file targets its containing package, including sibling
files. A directory targets only itself. Matching saved Go Application configurations
are reused, preserving their custom settings; otherwise the package directory name
becomes the configuration name. Package inspection and compilation happen in the
background after Run; a library package or build failure appears in the Run console.
Debug is unavailable until the debugger slice is installed.

The context platform tests exercise the registered producer for files/directories,
saved configuration reuse, unrelated selections, and a real Go context launch with
sibling files and library rejection. This is an automated sandbox equivalent, not
an interactive context-menu walkthrough.
