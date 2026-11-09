# ADR 0004: Go declaration navigation through gopls

Status: accepted

Use the installed gopls executable (Go project, BSD-3-Clause license) with the target IDE’s bundled Language Server Protocol client. The IDE owns the editor commands, live document synchronization and server lifecycle. gopls resolves symbols with Go module and package semantics. Keep the LSP extension descriptor optional so run and debug do not depend on editor navigation. The language server is installed separately and the plugin does not redistribute it.
