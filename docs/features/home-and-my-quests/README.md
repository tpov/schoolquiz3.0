# Feature: Home Quests & My Quests + Cascading Catalog Sync

## Status: implemented

Экраны "Домашние квесты" (polish) + "Мои квесты" (new) + версионированная каскадная синхронизация `Catalog → Quest → Section → Theme → Lesson → Question` (6 уровней иерархии).

## Documents

| Document | Status | Owner |
|----------|--------|-------|
| `0-spec.md` | Complete | user + spec |
| `1-research.md` | Complete | research |
| `2-grounding.md` | Complete | research |
| `01-architecture.md` | **Complete** | high-level + component |
| `02-behavior.md` | **Complete** | high-level (DFD) + component (sequences + State Matrix) |
| `03-decisions.md` | **Complete** | high-level (ADR-HMQ-*) + component (ADR-CMP-*) |
| `04-testing.md` | **Complete** | component |
| `05-prior-art.md` | **Complete** | web-researcher |
| `06-api-contract.md` | **Complete** (SSoT) | high-level |
| `07-events.md` | N/A | — (feature pull-based, no realtime events) |
| `08-storage-model.md` | **Complete** (SSoT Room) | component |
| `reviews/` | **Complete** | Codex CLI (Realist + Skeptic + Architect) |
| `plan/` | **Complete** (5 phases, 17 files, 0 hook violations) | `/feature-plan` + plan-reviewer + fix loop |
| `implementation.md` | **Complete** (5 phases + cross-fix pass, smoke test green) | `/feature-implement` |
| `retrospective.md` | **Complete** (2026-05-01) | `/feature-retrospective` — 22 findings (5 BLOCKER + 8 HIGH + 9 MEDIUM); 7 pipeline fixes applied: ADR-vs-code grep audit для architect-reviewer, Spec Contradictions Gate в Grounding, Server Contract Verification, Logout Primary Journey enforcement, §13 Koin SSoT round-trip, Deferred HIGH approval, Mechanical repository abstraction. См. `retrospective.md` + `docs/invariants.md#8` (Auth-Scoped Flow). |

## Highlights

### Архитектурные решения

- **6-уровневая иерархия**: `Catalog → Quest → Section → Theme → Lesson → Question`
- **Плоские Firestore collections + parentId** (не subcollections) — для cross-catalog фильтра "Мои квесты"
- **Двойное versioning**: `version` (сам элемент) + `contentsVersion` (вложенные); leaf = только `version`
- **`lastModifiedAt: Long` курсор** (Unix millis) — delta-sync через `where('lastModifiedAt', '>', cursor)`. Покрывает и новые, и обновлённые документы одним запросом.
- **Cascading sync**: клиент углубляется только где `contentsVersion` вырос (early-exit)
- **Visibility как `visibleOn: Array<String>`** (квест может быть на нескольких экранах одновременно)
- **Soft delete `archived: Boolean`** на каждом уровне (Catalog + Quest + Section + Theme + Lesson + Question) → локальное удаление при sync
- **Authorship**: `Quest.authorUid: String` = Firebase Auth UID (не legacy tpovId)
- **Server invariant**: при любом write в nested entity сервер обязан поднять version + contentsVersion + lastModifiedAt всех предков

### Impacted ADRs

| ADR | Status | Change |
|-----|--------|--------|
| ADR-0004 sync contract | amended | Per-entity `contentsVersion` extension (не в базовом Syncable) |
| ADR-0005 quest lifecycle | amended | `visibleOn: Set<Shelf>` используется реально вместо `shelf: Shelf?` (документ сохраняет enum как формальность) |

### New modules

- `shared/feature/quest/{domain,data}`
- `shared/feature/section/{domain,data}`
- `shared/feature/theme/{domain,data}`
- `shared/feature/lesson/{domain,data}`
- `shared/feature/question/{domain,data}`
- `android/feature/quest/presentation`

### Modified

- `shared/core/catalog/{domain,data}` — add version/contentsVersion/archived
- `shared/core/persistence` — add Entity + DAO для quest/section/theme/lesson/question; bump schema, destructive migration
- `platform/firebase/*` — add remote data sources для new entities
- `platform/android-services/SyncWorker` — add cascading steps
- `android/feature/app-shell/presentation/ui/AppShellScreen.kt` — MyQuestsRoot placeholder → MyQuestsScreen; CatalogGrid typography polish
- `android/core/designsystem/components/CatalogGrid.kt` — polish
- `android/core/designsystem/components/QuestCard.kt` + `StarRating.kt` — new universal components

## Key Decisions

35 user decisions + 18 delegated decisions — подробности в `0-spec.md`. Обновления 2026-04-21: добавлены решения 30-35 (authorUid, lastModifiedAt курсор, Home = каталоги, nested archived, order обязательный, server propagation invariant).

## Updates (2026-04-21)

**Round 1** — после Codex cross-model review (14 findings):
- `Quest.authorUid: String` (Firebase Auth UID) вместо `authorId: Int` (legacy tpovId)
- `lastModifiedAt: Long` курсор для sync вместо `version > last`
- `ObserveHomeQuestsUseCase` удалён — Home = каталоги (CatalogGrid)
- `archived: Boolean` на всех 6 сущностях
- `order: Int` обязательное для Section/Theme/Lesson/Question
- State Matrix — 4 чистые подматрицы
- 10 новых domain test scenarios (41-50)
- 2 новых Primary User Journey (cold start offline, partial cascade fail)
- ADR-0004 + ADR-0005 amendments
- PROJECT_STRUCTURE.md обновлён

**Round 2** — Codex re-review выявил 9 новых дыр в v2 spec (обнаружилось что v1 fixes недостаточны):
- **Server Invariant B** (downward cascade): при изменении `parent.visibleOn/archived` сервер каскадно обновляет `lastModifiedAt` всех потомков — решает проблему "новый parent + старые descendants" без client bootstrap
- `SyncStateRepository` interface + `InMemorySyncStateRepository` stub — архитектурный seam для будущей "sync rollback" фичи (inspired by legacy `SyncInteractor.rollbackStructureData`)
- **Drafts out-of-scope** — в phase-01 даже owner-drafts удаляются локально при `visibleOn.empty`. Draft UX реализуется в отдельной create-quest фиче через "заявку на создание"
- **Guest UX** — empty state без login CTA, ViewModel не вызывает repo при uid=null; будущий snackbar "локальные квесты не залиты" — в create-quest фиче
- **Firebase query split** — Query A (own quests) + Query B (public quests) независимо, с merge/dedupe клиентом (обходит Firebase ограничение `array-contains-any + where-in`)
- **Security rules** — единая модель: `quests` (owner-or-public read), `sections/themes/lessons/questions` (authenticated read, admin-only write MVP)
- **Primary User Journey 11** (Guest на "Мои квесты") добавлена
- **AC 41-49** — новые acceptance для sync_state, guest handling, delete semantics
- **Decisions 36-40** — четыре новых решения зафиксированы

## Pipeline Next Steps

1. ~~`/feature-research home-and-my-quests`~~ — **DONE** (`1-research.md` + `2-grounding.md`)
2. ~~Critical blocker decisions~~ — **DONE 2026-04-22**: 4 пользовательских решения (#41-44); Walking Skeleton дополнен `AuthRepository`
3. ~~`/feature-design home-and-my-quests`~~ — **DONE 2026-04-22**: 7 design docs + 3 Codex reviews + fix loop (blockers B1-B5 + C1-C9 applied)
4. ~~`/feature-plan home-and-my-quests`~~ — **DONE 2026-04-23**: 5 phases (scaffold foundation → cascade data → sync orchestration → app-shell/auth → presentation); plan-reviewer (2 lenses) + fix loop (4 blockers + 12 minor findings resolved)
5. `/feature-implement home-and-my-quests` — **NEXT**

## Cross-Feature Dependency Summary (для design phase)

- **`home-and-my-quests` импортирует**: `shared/core/catalog/domain` (CatalogId), `shared/feature/qualification/domain` (для access filter), `shared/feature/app-shell/domain` (UserStatsRepository, navigation), `shared/core/sync` (Syncable, SyncStateRepository), `shared/core/persistence`, `platform/firebase` (через DI)
- **`home-and-my-quests` используется**: пока никем (новая фича)
- **Bidirectional risks**: NONE detected — все imports один-направленные (`question → lesson → theme → section → quest → catalog`)
- **Shared SDK (Firebase)**: catalog уже использует Firestore + Storage; user-stats использует Firestore + Auth — паттерн `single<X>(named(...))` через Koin переиспользуется для quest. AppApplication.kt:41 содержит готовый `authUidFlow` — переиспользовать
- **Pre-existing violations** (не вводятся фичей, но затрагиваются): `AppShellScreen.CatalogGridSection` инжектирует Repository в Composable (нарушает `use-cases.md`); `as Syncable` runtime cast в `syncModule.kt:16-17` — расширяется при добавлении новых `Syncable` impls
- **Undocumented patterns**: Koin DSL imported в `shared/core/catalog/domain/di/CatalogDomainModule.kt:4` (pre-existing convention — не блокер)

Полные детали — в `1-research.md` секция "Cross-Feature Interactions" + `2-grounding.md` Problem 1-8.

## Critical Open Questions — RESOLVED 2026-04-22

Все 5 блокирующих вопросов закрыты пользователем (см. `0-spec.md` Decisions #41-44 + cursor field unification):

1. ✅ **FAB navigation** (Decision #41): `Destination.OpenQuestCreate` (data object, по аналогии с `OpenDesignCatalog`)
2. ✅ **UID acquisition** (Decision #42): новый `AuthRepository.currentUid()` interface в `shared/feature/app-shell/domain` (не extension `UserStatsRepository`). Walking Skeleton дополнен: `AuthRepository.kt` + `FakeAuthRepository.kt` + `AuthRepositoryContractTest` (6 тестов)
3. ✅ **Coil version** (Decision #43): bump `gradle/libs.versions.toml:44` 3.1.0 → 3.4.0 — **deferred to phase-01 implementation** (backend-dev owns scaffold files per CLAUDE.md ownership rule)
4. ✅ **Cursor field**: используется `lastModifiedAt` (FR#14, FakeCatalogRepository уже реализует). Spec обновлён, противоречие устранено
5. ✅ **Cleanup quiz/** (Decision #44): удалить пустые `quiz/` модули + placeholder Quest в `core/catalog/domain` в scope phase-01

Остальные Open Questions (5-15 в `1-research.md`) — design-level concerns, решаются в `/feature-design`.
