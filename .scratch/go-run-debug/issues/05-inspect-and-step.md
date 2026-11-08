# 05: Inspect and step through suspended applications

**What to build:** Inspect a suspended Go application's goroutines, stack frames, and variable values, and control its execution with pause and step actions inside IntelliJ's Debug window.

**Blocked by:** 04 — Debug to a line breakpoint.

Status: ready-for-agent
Progress: resolved

- [x] Support pause, step into, step over, and step out using Delve and the native debugger controls.
- [x] List and select goroutines, show their stack frames, and navigate to the selected frame's source location.
- [x] Show variables for the selected frame and lazily expand structured values through the debugger UI; report retrieval failures without freezing or corrupting the session.
- [x] Refresh frames and variables after stops and stepping; invalidate stale state on resume, exit, goroutine/frame selection changes, and rerun. Late responses must not overwrite a newer suspension's state.
- [x] Add externally observable fake-DAP tests for stepping, selection, variable expansion, stale responses, and failures. Reuse the protocol boundary established in ticket 04.
- [x] Verify with a real multi-goroutine Go fixture that pause/resume, all three step operations, frame selection, variable expansion, and consecutive sessions work.
- [x] Keep expression evaluation, conditional breakpoints, hit counts, logpoints, and interactive debug input outside this slice.

## Answer

Implemented native goroutine and stack-frame selection/source navigation, lazy scope and
structured-variable expansion, pause/resume and all three step operations. Each stop owns
its DAP references; resume/continued, exit and selection changes invalidate pending state.
Late successful or failed requests cannot replace a newer stop or variable tree. Retrieval
errors stay on their tree node, and rejected execution commands restore an inspectable
suspension. Command submissions preserve order so immediate Pause cannot overtake Resume.

Validation: `spotlessApply check` passed against local IntelliJ IDEA 2026.2.2 with Java 25
and the full 30-test suite (zero failures/skips). Socket fake-DAP tests exercise lazy
expansion, retrieval failure/retry, stale selection/resume responses, selected-goroutine
step commands, rejected-step recovery, and a delayed continue reply after a newer stop.
The registered native runner exercised the real multi-goroutine `inspect` Go fixture in
two consecutive sessions: goroutine and non-top frame selection, navigation to unopened
Go runtime source, expandable locals (`record.Count = 41`), step into/over/out, immediate
resume/pause, and stop. Real validation used Go 1.25.1 and Delve 1.25.2.

These are headless native-model and process tests, not an interactive sandbox smoke test
or validation against the developer's real project. No expression evaluation or advanced
breakpoint features were added. Integration-wide review and sandbox verification remain
with the parent workflow.
