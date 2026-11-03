# Economy constants — the server-owned table

Companion to `SPEC.md` (CAP-4, CAP-5, CAP-9). Holds the knob catalogue and the delivery rule.
The values below are the **initial** set, chosen to match today's behaviour where the input did
not change it. They are data, not contract: the contract is that they are tunable server-side.

## Where it lives

One server-owned document in the collection Firestore rules already reserve for exactly this —
`configs/{configId}`, `allow read, write: if false` (`firestore.rules:42`). The pattern is
established: `configs/nickname_policy` (`functions/index.js:114`) and `configs/arena_review`
(`functions/index.js:2825`) are both read server-side this way.

Because the collection is closed to clients, the device cannot read the document. It receives the
values through a callable, folded into the sync it already performs. A device that has synced
runs on what it pulled; a device that has never synced must still start, so the client keeps a
bootstrap copy — used only until the first successful pull, and never as an authority for
anything the server settles.

The document carries a monotonic `version`. The client stores the version it last pulled so a
sync can skip an unchanged table, and so an audit record can name which table a decision was made
under.

## Knobs

### Standard (yellow) charge

| Knob | Initial | Today's source | Note |
|---|---|---|---|
| `standard.maxOwned` | 10 | `MAX_STANDARD_HEARTS = 5` (`functions/index.js:94`) | Input raises it to 10 |
| `standard.regenMs` | 3 600 000 (1h) | `HEART_REGEN_MS` (`functions/life-points.js`) | Unchanged |
| `standard.priceLadder` | `[1000, 2000, 5000, 10000, 20000, …]` in nolics | `STANDARD_HEART_SLOT_COSTS` (`functions/index.js:93`) | One entry per slot up to `maxOwned`; the ladder must be at least as long, or the last entry repeats |
| `standard.currency` | `nolics` | `buyStandardHeart` | — |
| `standard.appliesTo` | `EASY` hints, plus every activity toll in the price list | new | Two sinks — CAP-1, CAP-13 |

### Plasma charge

| Knob | Initial | Today's source | Note |
|---|---|---|---|
| `plasma.maxOwned` | 3 | `MAX_GOLD_HEARTS = 1` (`functions/index.js:95`) | Input raises it to 3 |
| `plasma.regenMs` | 86 400 000 (24h) | none — gold hearts do not regenerate today | One per day. Chosen over 8h to answer the "жирно" worry directly |
| `plasma.priceLadder` | `[1, 2, 3]` in gold | `GOLD_HEART_COST = 10` flat (`functions/index.js:96`) | Indexed by slots owned, mirroring `standardHeartSlotCost`. All three cost 1+2+3 = **6 gold**, once |
| `plasma.currency` | `gold` | `buyGoldHeart` | Monetary class — CAP-11 |
| `plasma.appliesTo` | `HARD` skips only, tournaments included | new | One sink. Never pays a lesson toll — CAP-1 |
| `plasma.requiresSettledAccount` | `true` | new | CAP-8 |

### Activity price list

The heart of the model. A standard charge holds 100 points; a price is quoted in points, and the
charge figure is what the player is shown. Descends directly from `CoastValuesLife` in
`legacy/shop/src/main/java/com/tpov/shop/CoastValues.kt`, which already priced per activity.

| Activity | Points | Charges | Legacy source |
|---|---|---|---|
| Ordinary lesson | 33 | 0.33 | `COAST_LIFE_HOME_QUIZ = 33` — unchanged |
| Arena | 50 | 0.5 | `COAST_LIFE_ARENA_QUIZ = 33` — raised to 50 |
| Theme test (контрольная) | 100 | 1 | new |
| Final exam (экзамен) | 300 | 3 | new |
| Tournament | 500 | 5 | new |

A full tank is 10 charges = 1000 points, so it pays for two tournaments, three exams, or thirty
ordinary lessons. Standard regeneration is one point per 36 s — a whole charge per hour — so a
tournament costs five hours of regeneration.

Two things the list forces:

- **The kind must be a first-class field on the attempt, decided by the server.** Today a result
  event carries `sourceShelf`, which distinguishes a tournament from a home quest but was never
  meant to price anything. If the client declares the kind and the server bills from the
  declaration, the cheapest kind is the one every client declares.
- **`LESSON_ATTEMPT_LIFE_COST` stops being a constant.** The single flat 33 in
  `functions/life-points.js` becomes a lookup, and `spendLifePoints` is called with a price rather
  than with that constant.

### Settlement and audit

| Knob | Initial | Note |
|---|---|---|
| `settlement.maxPlasmaPerAttempt` | **none** | Explicitly declined by the owner: all three plasma charges may be spent in one run. The 24h regen is the only limiter |
| `settlement.clockSkewToleranceMs` | 0 | Grace added to the regeneration ceiling before a claim counts as overspend |
| `audit.enabled` | `true` | Whether CAP-7 writes records |

### Currency classification (CAP-11)

Not a tuning knob — a declaration the server reads to decide how strictly to guard each balance.

Three tiers, not two.

| Resource | Class | Guard |
|---|---|---|
| `nolics` | motivational | Periodic reconciliation sweep |
| `skillPoints` | motivational | Periodic reconciliation sweep |
| standard charges | motivational | Periodic reconciliation sweep; offline spend permitted, settled at sync |
| gift boxes (`boxCount`, `boxStreakDays`, `nextBoxAtMs`) | **intermediate** | Earned offline, checked hard at sync against the server clock, **never opened offline** |
| `gold` | monetary | Per-operation server authorisation, online only |
| plasma charges | monetary | Per-operation server authorisation, online only |
| trophies | monetary | Owner: "трофеи тоже платные" |
| stars, owned logos, owned nicknames | not classified | Not a build item — see the note below |

The intermediate tier exists because a box is two events, not one. *Earning* it is a schedule the
server can re-derive from its own clock, so letting it happen offline costs nothing. *Opening* it
grants contents, and contents can be monetary — so the open is a server operation and is refused
without a connection.

**This table is rationale, not a deliverable.** The owner supplied the three tiers to explain why
plasma and gold are guarded harder than nolics, and asked that it not be turned into work of its
own. What is actually built from it is narrow and already has capabilities: plasma is online-only
(CAP-8), a box is never opened offline (CAP-12), and forgeable balances are swept periodically
(CAP-11). Nothing else here needs a classification pass, and rows left unclassified above can stay
that way.

## Rules the table must satisfy

- **One source.** After this work, neither `functions/index.js` nor `UserProfile.kt` holds its own
  copy of any value in this table. Both read the document. The Kotlin bootstrap copy is the sole
  exception and is marked as such.
- **Lowering a ceiling never confiscates.** An account already holding more than a newly lowered
  `maxOwned` keeps what it has; it simply cannot buy or regenerate more until it is back under.
  The current `readEconomyBalance` clamps with `Math.min` on read, which *does* confiscate — that
  behaviour changes.
- **A price change is not retroactive.** A purchase is priced at the version in force when the
  server accepts it, never when the client rendered the shop.
- **Every knob has a server-side default.** A malformed or missing document degrades to the
  initial values above rather than to zero ceilings, which would lock every account out.
