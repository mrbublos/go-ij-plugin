# 02: Customize application launches

**What to build:** Configure the working directory, program arguments, environment overrides, and build flags for a Go Application launch, and retain those choices across IDE restarts.

**Blocked by:** 01 — Run a Go package from a saved configuration.

Status: ready-for-agent
Progress: resolved

- [x] Expose and persist editable working directory, program arguments, environment overrides, and build flags in the configuration editor; new configurations default to the selected package directory.
- [x] Build the selected package even when its runtime working directory differs. Apply build flags and the configured environment consistently to package validation and building; apply program arguments and environment to the executable.
- [x] Preserve normal inherited environment variables unless overridden; report invalid working-directory settings clearly before launch.
- [x] Use structured process arguments and IntelliJ argument-parsing conventions rather than invoking a shell; correctly handle quoted arguments and paths containing spaces.
- [x] Verify through fixture launches that arguments, environment, relative-file access, and a build-tag-selected implementation reflect the configured values.
- [x] Test persistence and validation, including a working directory outside the selected package, missing directories, and consecutive launches with changed settings.

## Answer

Added persisted working directory, IntelliJ-parsed program arguments/build flags,
and environment overrides to the Go Application editor and immutable launch input.
Blank runtime directory defaults to the package; builds and inspection remain in
that package, sharing flags/environment, while the executable uses the configured
runtime directory and arguments. Normal environment inheritance is retained.
Inspection stdout is separate from diagnostic stderr so successful tool messages
do not invalidate the main-package result.

Validation: red/green platform persistence and launch-option tests; real Go launches
cover quoted arguments, space-containing paths, external working directory and
relative files, build-tag-only sources, GOFLAGS environment, inheritance and changed
consecutive launch settings. External fake Go verifies environment reaches list/build
and successful inspection stderr remains diagnostic. Missing directory validation
is covered. `./gradlew --offline -PlocalIde="/Applications/IntelliJ IDEA.app/Contents"
spotlessApply check` passed (14 platform tests), as did `git diff --check`.
Interactive editor walkthrough and full plugin packaging remain final-spec checks.
