# Publish plugin ZIPs on changes

Status: ready-for-agent
Progress: resolved

Publish a GitHub Release containing the plugin ZIP for each relevant push to `main`.
Trigger on source/tests, Gradle configuration/wrapper, and workflow changes; allow
manual runs on `main`. Exclude documentation-only changes.

Gate publication on formatting, tests, packaging, and target-IDE compatibility.
Use macOS ARM, Java 25, Go 1.25.1, and Delve 1.25.2. Assign a unique CI plugin
version and bind the release tag to the tested commit. Reruns must not duplicate
or replace an existing release. Use GitHub's built-in token.

Local builds keep their default version. No Marketplace publishing or immediate
remote release is part of this change.
