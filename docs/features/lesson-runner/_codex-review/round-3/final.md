# Final Design Review (Round 3) — lesson-runner

## Verdict
REJECT — not implementation-ready: parser/Koin wiring is still contradictory, `IOError` survived, and `TopParticipant` serialization contract conflicts internally.

## Round 1+2 blocker closure verification
| Blocker ID | Status | Evidence (file:line + quote) |
|---|---|---|
| R-target-state | CLOSED | `01-architecture.md:11` — “target state после phase-01 implementation” |
| R-submitAnswer/no double timer | CLOSED | `02-behavior.md:371-372` — `submitAnswer(... nowMs)` “computes newDeadlineMs internally” |
| R-SaveError.IoFailure | STILL WRONG | `02-behavior.md:477` — `[IOError]`; also `:479`, `:481`, `:557` |
| R-matrix fixes | CLOSED | `02-behavior.md:505`, `:515`, `:518`, `:527` fix Ordering, EASY 75, HARD 50, Matrix 3 wording |
| R-use-case/provider names | PARTIAL | `06-api-contract.md:527-529` matches constructors, but DI wiring still wrong in `01:627` and registration `01:691-692` |
| R-RootComponent API drift | CLOSED | `01-architecture.md:379-382` uses `StateFlow`/`Flow`; `06-api-contract.md:293-301` canonical callbacks |
| R-ratingCount | CLOSED | `0-spec.md:187`, `06-api-contract.md:137`, `08-storage-model.md:71` all say `Int = 0` |
| S-ADR-LR-14 / ADR-0003 edit | CLOSED | `0003-question-schema.md:164-180` has Amendments A-D |
| S-ADR-LR-08 SerialNames | PARTIAL | `03-decisions.md:362-363` requires `@SerialName(...)`; `0003-question-schema.md:32-65` shows no `@SerialName` in schema block |
| S-ADR-LR-09 domain/data | CLOSED | `03-decisions.md:392-398` provider interfaces in domain, adapter in data |
| S-OQ9 factory boundary | PARTIAL | `03-decisions.md:337-340` says factory interface in quizzes-screen; `01-architecture.md:75` says “interface в android/core/navigation” |
| S-Risk-if-wrong sections | CLOSED | Present across ADRs, e.g. `03-decisions.md:48`, `:85`, `:156`, `:418` |
| S-TopParticipant/Provider SSoT | PARTIAL | Providers OK in `06:464-478`; TopParticipant conflicts: `06:124` “без аннотаций” vs `06:671` “должен быть @Serializable” |
| A-domain purity | CLOSED | `03-decisions.md:365` — domain imports parser interface only, not serialization |
| A-Channel→Flow | CLOSED | `07-events.md:37-38` exposes `Flow`, not `ReceiveChannel` |
| A-parser DI location | STILL WRONG | `06-api-contract.md:448-456` says `questionSchemaModule`; `01-architecture.md:617-618` still binds parser in `lessonRunnerDataModule` |
| A-module registration | STILL WRONG | `06-api-contract.md:456` says `questionSchemaModule` added to app; `06:560-562` omits it. `01:691-692` also omits adapter + question schema |
| A-migration/MT coverage | PARTIAL | `04-testing.md:215,218` has MT-04 and MT-07; `08-storage-model.md:152` still says MT-01..MT-06 |
| A-androidInstrumentedTest | CLOSED | `04-testing.md:14`, `:18` use `src/androidInstrumentedTest/` |
| A-IT-09 parser/converters | CLOSED | `04-testing.md:200-201` covers parser and converters |
| A-FLAG_SECURE rotation | CLOSED | `04-testing.md:179` adds CT-29 rotation coverage |
| A-ADR-LR-12 churn/risk | CLOSED | `03-decisions.md:488-499` lists rename impact and compile gate |
| A-full type bodies removed from 01 | CLOSED | `01-architecture.md:482` defers public API to `06`; no full Kotlin bodies for named interfaces |

## NEW issues (round 3)
- [BLOCKER] DI wiring is internally inconsistent. `01-architecture.md:617-618` binds `QuestionContentParser` in `lessonRunnerDataModule`, while `06-api-contract.md:448-452` says it must live in `questionSchemaModule`. `06-api-contract.md:560-562` then omits `questionSchemaModule` from the app module list.
- [BLOCKER] `01-architecture.md:627` redefines `val lessonRunnerDataModule` for use-case factories instead of `lessonRunnerDomainKoinAdapter`, then `01:691-692` registers only data + presentation. This would leave canonical adapter wiring ambiguous.
- [HIGH] `TopParticipant` cannot be both “без аннотаций” and used via `TopParticipant.serializer()`: see `06-api-contract.md:124` vs `06:661-667` and `06:671`.
- [MEDIUM] `01-architecture.md:709-714` still lists resolved ADRs as open phase-01 questions: migration, Koin provider strategy, LessonItemCard, ID rename.
- [MEDIUM] Behavior doc still models `LessonItemUi` with `bestStars: Stars` at `02-behavior.md:237`, while canonical API uses `bestStarsRawTenths: Int` at `06-api-contract.md:417`.

## Cross-doc consistency spot-check
| Type | Source SSoT | Other doc | Verdict |
|---|---|---|---|
| `LessonRunnerRootComponent` | `06-api-contract.md:289-301` | `01:379-382`, `04:84-96` | PASS |
| `LessonItemUi` | `06-api-contract.md:412-420` | `03:462-463`, `01:573`, `02:237` | CONTESTED — 03 agrees, 01 wrong section ref, 02 stale field |
| Providers | `06-api-contract.md:464-478` | `03:392-398`, `01:342` | PASS for interfaces |
| Provider DI adapter | `06-api-contract.md:482-531` | `01:622-667` | FAIL — 01 uses wrong module name |
| Question serial names | `03-decisions.md:362-363` | `0003-question-schema.md:32-65` | CONTESTED — design requires names, ADR block does not show them |
| Parser binding | `03:364`, `06:448-456` | `01:617-618`, `06:560-562` | FAIL |
| `Lesson.ratingCount` | `0-spec.md:187` | `06:137`, `08:71` | PASS |
| `TopParticipant` | `06-api-contract.md:117-124` | `06:653-671` | FAIL |

## Final spot-check matrix
| Claim | File:line | Verdict |
|---|---|---|
| QSP → core/navigation ← LRP, no direct QSP→LRP in module graph | `01-architecture.md:110-111` | PASS |
| But C4 L2 factory location is stale | `01-architecture.md:75` | FAIL |
| Matrix 3 allows rawTenths=20 without hard unlock | `02-behavior.md:527` | PASS |
| 01 has no full Kotlin type bodies for public API | `01-architecture.md:482` | PASS |
| Parser binding location consistent across 03 and 06 | `03:364`, `06:448-452` | PASS |
| Parser binding location consistent across all reviewed docs | `01-architecture.md:617-618` | FAIL |
| KMP instrumented tests use `androidInstrumentedTest` | `04-testing.md:14`, `:18` | PASS |
| IT-09 covers parser and TypeConverters | `04-testing.md:200-201` | PASS |
| MT-07 exists | `04-testing.md:218` | PASS |
| 07-events public API is `Flow`, not `ReceiveChannel` | `07-events.md:37-38` | PASS |
| `SaveError.IoFailure`, not `IOError` | `02-behavior.md:477` | FAIL |
| ADR-0003 Amendments A-D appended | `0003-question-schema.md:164-180` | PASS |

## Recommendation
fix-loop-round-3. Focus the fix on DI/module registration, stale `IOError`, `TopParticipant` serialization SSoT, and clearing stale open questions before another final review.