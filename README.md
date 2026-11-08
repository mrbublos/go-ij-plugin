# Standalone Go Run and Debug for IntelliJ IDEA

A personal-use plugin for running and debugging local Go main packages without JetBrains' Go plugin. The initial target is macOS Apple Silicon.

## Current state

This repository contains specifications and project conventions. Plugin code, Gradle configuration, and automated checks have not been implemented. There is no installable plugin yet.

## Start here

- [Feature specification](.scratch/go-run-debug/spec.md): behavior, implementation targets, acceptance scenarios, and exclusions.
- [First implementation ticket](.scratch/go-run-debug/issues/01-run-saved-configuration.md): bootstrap and a saved-configuration run.
- [Architecture](docs/architecture.md): code organization and engineering rules.
- [Glossary](GLOSSARY.md) and [architecture decision](docs/adr/0001-single-module.md).
- [Contributor instructions](AGENTS.md) and [local issue workflow](docs/agents/issue-tracker.md).

## Development and verification

Bootstrap will add the Gradle wrapper, pinned build dependencies and formatter, and verified commands here for formatting checks, compilation, deterministic tests, sandbox launch, plugin ZIP creation, and target-IDE compatibility verification. Consult the feature specification for toolchain targets; they are not evidence of a working build.

For each change, run checks appropriate to its behavior and report what passed or could not run. Documentation-only changes need link and consistency checks. Before declaring a build installable, build its ZIP and verify target-IDE compatibility. Changes affecting launch or debugging also need the relevant real Go/Delve and sandbox scenarios from the specification.

Automated checks added at bootstrap must cover compilation, formatting verification, and deterministic tests. Real-tool and interactive checks must be reported separately when the environment cannot run them.
