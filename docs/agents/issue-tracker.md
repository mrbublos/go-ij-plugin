# Issue tracker: Local Markdown

- Store each feature in `.scratch/<feature-slug>/`.
- Store its spec in `spec.md`.
- Store implementation tickets individually in
  `issues/<NN>-<slug>.md`, numbered from `01`.
- Record triage state in a `Status:` line near the top,
  using the vocabulary in `triage-labels.md`.
- Append discussion under `## Comments`.

Publishing means creating or updating the appropriate Markdown file.
Fetching a ticket means reading its referenced file.

## Progress

Features and substantial fixes use a spec and implementation tickets. Small,
well-defined fixes may proceed directly. Keep specs and tickets in version control
so their decisions and acceptance criteria travel with the code.

Keep `Status:` for triage readiness. Track execution separately with `Progress:`:
`open`, `claimed`, or `resolved`. A missing Progress field means `open` for existing
tickets. Claiming or completing work does not change its triage role.

For agent work, choose a `ready-for-agent`, open, unclaimed ticket whose dependencies
are resolved. Set `Progress: claimed` before starting. On completion, append an
`## Answer` with the outcome and validation performed, then set `Progress: resolved`.
If acceptance criteria remain unmet, report the blocker and keep the ticket unresolved.

## Wayfinding

- Store the effort map in `.scratch/<effort>/map.md`,
  with Notes, Decisions-so-far, and Fog sections.
- Store child tickets in `issues/NN-<slug>.md`.
- Record ticket type with `Type:`:
  research, prototype, grilling, or task.
- Record dependencies with `Blocked by: NN, NN`.
- Among eligible tickets, select the lowest-numbered one.
- Follow the Progress lifecycle above. When using an effort map, add the
  completion summary and ticket link to its Decisions-so-far.
