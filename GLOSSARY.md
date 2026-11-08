# Glossary

- **Main package**: a Go package declared as `package main` that can be built as an executable. A launch selects its directory and includes the whole package.
- **Run configuration**: IntelliJ's persisted settings for a repeatable launch, including the selected main-package directory and launch options.
- **Launch**: one request to start a selected main package in Run or Debug mode using a snapshot of its configuration.
- **Run session**: the resources and lifecycle of one Run launch, including build and application processes and its temporary executable.
- **Debug session**: the resources and lifecycle of one Debug launch, including Delve, its DAP connection, and debugger state presented through XDebugger.
- **Toolchain**: the installed Go and Delve executables used by the plugin. Run requires Go; Debug requires both.
- **DAP**: Debug Adapter Protocol, used to communicate with Delve.
- **Module**: behavior behind an interface; a package-level design unit need not be a separate Gradle module.
- **Interface**: what a caller must know to use a module, including inputs, outputs, failure modes, lifecycle, and threading requirements.
- **Seam**: a location where an interface allows behavior to vary, such as the external DAP connection used by real and fake servers.
