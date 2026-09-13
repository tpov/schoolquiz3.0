# Arena matrix device flows

These flows are deliberately separate from the retained-ticket E2B runner.  They never prepare,
start, cancel, settle, rate, install, clear, or reset an application.  Each device phase takes a
non-blocking per-serial lock shared with E2B, writes an isolated result directory, and records
`PASS`, `FAIL`, or `NOT_RUN`.

`run-system-back-catalog.sh` proves the physical Android Back dispatcher on a safe route only:

```text
verified Home -> navigation-only route to unloaded catalog -> Android KEYCODE_BACK
  -> Arena root -> Android KEYCODE_BACK -> the same verified Home
```

Before it makes any navigation or sends either Back event, it captures the actual hierarchy and
refuses `NOT_RUN` unless the retained application is at Home.  It then uses only app-shell navigation
to reach an unloaded catalog — never `Load lessons` — and rechecks the hierarchy before each
physical Back.  A prepared ticket, ticket action, runner, result, synchronization, or rating
surface blocks the run.  The selector map is an explicit immutable input captured from the
installed candidate; it is checked across visible, accessibility, and hint text, with no
resource-id assumptions.

Run it only inside the controlled Maestro container and pass the candidate APK's immutable SHA-256
and certificate SHA-256.  Before the UI phase it also reads and compares the installed base APK;
it refuses split packages because a single candidate artifact would not prove their full identity.
The command has no installation or state-clearing option.
