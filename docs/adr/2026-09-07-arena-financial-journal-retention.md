# Arena financial journal: narrow retention exception to AD-8

Status: approved for this draft; not imported, compiled, exported or wired at handoff.

## Decision and reason

Only `arena_ticket_records` and `arena_command_journal` survive an ordinary account SWITCH or SIGN_OUT. An uncertain server purchase/cancellation/settlement may already have reserved or consumed funds. Erasing its original request identity, consent ceiling or immutable terminal body would make safe recovery impossible. This is a narrow exception to the previous purge-all owner-traces rule, not a change to generic Outbox ownership or any legacy table.

The retained rows stay inert and owner-scoped. Every feature read, claim, progress write, recovery and explicit retry requires the current owner plus the captured account generation, using the existing Outbox/account-switch gate. An A → B → A transition invalidates A's older lease. Runtime generations and authentication tokens are never persisted in the journal. After a process restart, the authenticated owner captures a new lease before accessing their retained records.

Explicit ERASE_LOCAL_DATA and DELETE_ACCOUNT still erase both new tables. The old no-reason erase API defaults to ERASE_LOCAL_DATA. The new owner inventory is separate from switch-loss warnings and supports explicit erasure. This slice adds no hypothetical delete-account UI or global forget-all implementation. Existing callers must pass an explicit erase reason when implementing such consent; they must include `ownersWithArenaFinancialRecords()` when enumerating all retained owners.

## Stored facts and limits

- Ticket identity, server request ID, immutable public terms, frozen public question snapshot/hash and monotonic observed server state are separate. Immutable terms contain no `status` field. No auth token, profile, private HARD grading key or local ledger is stored.
- Original PREPARE consent contains owner, request ID, mode/difficulty and, for SELECTED, the explicit lesson and price ceiling. It commits before any remote call. Recovery never invents the lost ceiling from a ticket's paid price.
- CANCEL and SETTLE are mutually exclusive durable local decisions. LAUNCHED is not a terminal decision; abort selects CANCEL. The server remains authoritative for cross-device cancel/settle races.
- SETTLE keeps its original raw completion body independently of the generic Outbox and permanent generic receipt tables. A same-owner recovery may atomically restore that exact body/key and attempt marker after ordinary account cleanup. The existing Outbox is still the only settlement sender; no balance or charge quota is patched locally.
- At most 20 full frozen question banks plus pending prepare capacity reservations are accepted per owner. Overflow recovery retains a cancellable header/fingerprint without granting launch. Explicit snapshot eviction never removes an active offline attempt. No financial identity expires automatically.
- LAUNCHED and a validated initial runner checkpoint commit in the same Room transaction. Checkpoint CAS is bound to owner/ticket/attempt/snapshot fingerprint and revision. Maximum encoded checkpoint is 1 MiB, never truncated. Completion freezes only against the already-durable validated final checkpoint, with fixed completion time and exact raw body.

## Authority and failure behavior

A header-only or malformed snapshot is not launch permission but remains cancellable if its strict header is valid. Confirmed terminal tickets never launch. A LAUNCHED record without a checkpoint requires resume/reconciliation, not a new attempt ID. Lost responses, cancellation of a coroutine, persistence failures and RETRY_REGEN do not announce a server cancellation or new grant. They preserve pending identity.

The checkpoint codec is a required dependency, with no permissive production default. Its typed UserAnswer/draft/feedback validation and restore implementation belong to the separate runner-checkpoint slice. Storage hooks alone are not a launch-ready runner. Persisted final checkpoint validation occurs inside the same transaction that records SETTLE, the first attempt intent and its Outbox row.

The shared gate gives the same in-process owner-session guarantee as the existing application. It is not a multiprocess authentication lock, and does not claim to serialize external Auth changes that bypass the account-switch flow. Room transactions still serialize durable local CANCEL/SETTLE and revision decisions. A second process or new Auth flow requires an explicit extension, not a claim that this mutex solves it.

## Explicit follow-up boundary

If another device cancels after local SETTLE, a recovery GET records confirmed CANCELLED and marks the retained command ATTENTION. This draft does not fabricate a generic receipt or delete another sender's pending row. A server-backed typed terminal queue-resolution policy is still required to stop that financial retry safely. Root has reserved that follow-up; it is not hidden as successful cancellation here.

## Migration and verification

Migration 12 → 13 only creates the two new tables and indexes. It does not promote legacy attempts, rewrite balances or delete existing rows. The version-13 Room identity/schema must be generated by the real Room compilation task; no hand-authored schema JSON or destructive fallback is supplied. Verification is required before import approval is treated as runnable: scoped JVM gates, generated schema review, and an actual 12 → 13 migration test preserving legacy rows.
