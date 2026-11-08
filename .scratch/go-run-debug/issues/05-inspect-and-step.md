# 05: Inspect and step through suspended applications

**What to build:** Inspect a suspended Go application's goroutines, stack frames, and variable values, and control its execution with pause and step actions inside IntelliJ's Debug window.

**Blocked by:** 04 — Debug to a line breakpoint.

Status: ready-for-agent

- [ ] Support pause, step into, step over, and step out using Delve and the native debugger controls.
- [ ] List and select goroutines, show their stack frames, and navigate to the selected frame's source location.
- [ ] Show variables for the selected frame and lazily expand structured values through the debugger UI; report retrieval failures without freezing or corrupting the session.
- [ ] Refresh frames and variables after stops and stepping; invalidate stale state on resume, exit, goroutine/frame selection changes, and rerun. Late responses must not overwrite a newer suspension's state.
- [ ] Add externally observable fake-DAP tests for stepping, selection, variable expansion, stale responses, and failures. Reuse the protocol boundary established in ticket 04.
- [ ] Verify with a real multi-goroutine Go fixture that pause/resume, all three step operations, frame selection, variable expansion, and consecutive sessions work.
- [ ] Keep expression evaluation, conditional breakpoints, hit counts, logpoints, and interactive debug input outside this slice.
