# 02: Customize application launches

**What to build:** Configure the working directory, program arguments, environment overrides, and build flags for a Go Application launch, and retain those choices across IDE restarts.

**Blocked by:** 01 — Run a Go package from a saved configuration.

Status: ready-for-agent

- [ ] Expose and persist editable working directory, program arguments, environment overrides, and build flags in the configuration editor; new configurations default to the selected package directory.
- [ ] Build the selected package even when its runtime working directory differs. Apply build flags and the configured environment consistently to package validation and building; apply program arguments and environment to the executable.
- [ ] Preserve normal inherited environment variables unless overridden; report invalid working-directory settings clearly before launch.
- [ ] Use structured process arguments and IntelliJ argument-parsing conventions rather than invoking a shell; correctly handle quoted arguments and paths containing spaces.
- [ ] Verify through fixture launches that arguments, environment, relative-file access, and a build-tag-selected implementation reflect the configured values.
- [ ] Test persistence and validation, including a working directory outside the selected package, missing directories, and consecutive launches with changed settings.
