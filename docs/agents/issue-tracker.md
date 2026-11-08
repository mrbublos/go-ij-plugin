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

## Wayfinding

- Store the effort map in `.scratch/<effort>/map.md`,
  with Notes, Decisions-so-far, and Fog sections.
- Store child tickets in `issues/NN-<slug>.md`.
- Record ticket type with `Type:`:
  research, prototype, grilling, or task.
- Record dependencies with `Blocked by: NN, NN`.
- Select the lowest-numbered open, unclaimed ticket whose
  dependencies are all resolved.
- Set `Status: claimed` before starting work.
- On completion, append `## Answer`, set `Status: resolved`,
  and add a summary and ticket link to the map's Decisions-so-far.
- `claimed` and `resolved` are wayfinding lifecycle states,
  separate from triage roles.
