# Connect the editor to gopls

Status: ready-for-agent
Progress: resolved
Type: task
Blocked by: none

Register Go source documents with the platform language server client. Verify local and imported declaration targets and document setup.

## Answer

Connected local Go files to IntelliJ's declaration navigation via gopls. All 37 tests passed, including registered-provider lookup within a file and across an aliased import. The installed gopls resolved both faceboxbot examples. Offline Plugin Verifier reported compatibility with IDEA 2026.2.2. Verification was headless.
