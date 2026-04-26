# Feature: Quizzes Screen — Hierarchical Drill-Down Navigation

Drill-down навигация по иерархии Catalog → Quest → Section → Theme → Lesson с breadcrumb-путём наверху. Порт легасного `QuizFragment` на новый стек (Decompose Components + Compose).

Entry points: HomeQuestsScreen (тап каталога) + MyQuestsScreen (тап своего квеста).

## Status: implemented

## Documents

| Document | Status | Description |
|----------|--------|-------------|
| `0-spec.md` | Updated в research-фазе | ТЗ, requirements, scope, AC#1-39, primary journeys, invariant check |
| `1-research.md` | Complete | Codebase mapping, integration points, web research findings, cross-feature dependency graph, state matrix validation |
| `2-grounding.md` | Complete (gate passed, zero contradictions) | Grounding cards для 7 problems, Independent Verification Protocol results, invariant conflicts check |
| `01-architecture.md` | Designed | C4 L1/L2 (HL) + L3 (Component); module dependency graph; integration with app-shell; data flow; cross-cutting concerns |
| `02-behavior.md` | Designed | 4 DFDs (HL) + 7 sequences (Component) + 3 state machines + State Matrix expansion (Tap action / Empty/Loading/Loaded / Breadcrumb path) |
| `03-decisions.md` | Designed | 12 ADRs: ADR-QS-01..05 (HL) + ADR-QS-06..12 (Component) |
| `04-testing.md` | Designed | Test strategy: JVM unit + Compose UI + StateKeeper restore contract + DAO; AC#1-39 coverage table |
| `06-api-contract.md` | Designed | Canonical SSoT: QuizzesNavigator, QuizzesConfig (6 variants), QuizzesComponent + 5 child interfaces, UiState types, designsystem signatures, repository extension, DI |
| `_codex-review/` | Complete | Adversarial review логи: realist (3 pass), skeptic (2 pass), architect (2 pass), plan-review (3 pass) — final verdict PASS |
| `plan/` | Complete | 7 phase directories + plan/README.md dashboard. Codex plan review pass-3 PASS. |

## Key Decisions (после design-фазы)

- **Architecture**: внутренний ChildStack в `QuizzesComponent` с `Idle` anchor (config[0] всегда). Sibling в `DefaultRootComponent`. ADR-QS-03/04/11.
- **Cross-feature wiring**: stdlib lambda callbacks через `DefaultRootComponent` (`(CatalogId, String) -> Unit`, `(QuestDisplayItem) -> Unit`). `QuizzesNavigator` interface живёт ТОЛЬКО в quizzes-screen/presentation. ADR-QS-01.
- **Process death**: `@Serializable QuizzesConfig` + `serializer = QuizzesConfig.serializer()` — first stack в проекте с включённым serializer. Schema evolution: fallback to `[Idle]` при unknown variant. ADR-QS-02.
- **Back handling**: `childStack(handleBackButton=false)` + manual `BackCallback(priority=PRIORITY_OVERLAY)` + `subscribe { isEnabled = stack.backStack.isNotEmpty() }`. ADR-QS-12.
- **Quest sort**: `lastModifiedAt DESC` (Quest model не имеет `order` — no migration). ADR-QS-05.
- **Breadcrumb**: frozen titles в `QuizzesConfig.titles`; `popTo(uiLevel + 1)` mapping (Idle anchor offset). ADR-QS-10.
- **MyQuests catalog resolve**: `QuestDisplayItem` расширяется полем `catalogId: CatalogId` (single SSoT, не wrapper). ADR-QS-05.
- **Long-press menu**: `QuestCard` +onLongClick параметр (backward compat); Material3 standalone `DropdownMenu` + `combinedClickable` — first usage в проекте. ADR-QS-06/07.
- **Share**: `Intent.createChooser` через `LocalContext.current` + try/catch `ActivityNotFoundException`; menu закрывается ВСЕГДА перед startActivity. ADR-QS-08.
- **HierarchyItemCard**: один универсальный компонент в designsystem с примитивными params (title, orderLabel?, subtitleCount?). ADR-QS-09.
- **Walking Skeleton**: skip (Feature Domain Contract = N/A).

## Conditional Documents — Not Required

- `07-events.md` — N/A (нет realtime events)
- `08-storage-model.md` — N/A (только additive `QuestDao.observeByCatalog`, описан в 06 §4; нет Room migration)
- `09-modules.md` / `10-tests.md` — content уже в 01 (module deps) и 04 (test plan); отдельные docs не требуются

## Codex Review Track Record

- **Realist** (01+02): pass-1 REJECT (6 findings, 1 BLOCKER) → pass-2 PARTIAL (3 cleanup) → pass-3 PASS
- **Skeptic** (03): pass-1 CONTESTED (12 findings, 3 BLOCKER) → pass-2 PASS
- **Architect** (04): pass-1 CONTESTED (8 findings, 2 BLOCKER) → pass-2 PARTIAL → lead fix → PASS
- **Total**: ~31 finding, 6 BLOCKER, все исправлены

## Open Questions для Implement-фазы (не блокеры)

- `BackCallback.PRIORITY_OVERLAY` — verify константа в Essenty 2.x перед реализацией ADR-QS-12.
- `instanceKeeper` retention для AC#22 (rotation scroll position) — уточнить в реализации.
- `QuestDisplayItem.catalogId` field — расширение модели в frontend-dev на phase-01 (canonical в `06-api-contract.md §3`).

## Pipeline next step

`/feature-implement quizzes-screen` — реализация по утверждённому плану в `plan/`.

Финальная разбивка фаз (см. `plan/README.md`):
1. **Phase-01** — Data layer: `QuestRepository.observeByCatalog` + Room DAO + 3 fakes
2. **Phase-02** — Designsystem: `BreadcrumbBar` + `HierarchyItemCard` + `QuestCard.onLongClick` + `QuestDisplayItem.catalogId`
3. **Phase-03** — Module skeleton (complex): новый Gradle module + `QuizzesConfig` + `DefaultQuizzesComponent` (Idle anchor) + Koin DI
4. **Phase-04** — Drill-down children: 5 `Default*Component` + UiState types + mappers
5. **Phase-05** — Compose screens: `QuizzesScreen` router + 5 child screens
6. **Phase-06** — Long-press Share menu: `DropdownMenu` + `Intent.ACTION_SEND`
7. **Phase-07** — Cross-feature wiring (complex): `DefaultRootComponent` + `AppShellScreen` overlay + HomeQuests/MyQuests TODO replacements

## Cross-feature broadcast (для plan/implement phase)

- **quizzes-screen импортирует**: `android/core/designsystem`, `shared/feature/{quest,section,theme,lesson}/domain`, `shared/core/catalog/domain`
- **quizzes-screen используется**: `apps/android-next` через Koin DI; `android/feature/app-shell/presentation` через factory injection (как `homeQuestsFactory`/`myQuestsFactory`)
- **Forbidden imports**: `android/feature/quest/presentation`, `android/feature/app-shell/presentation` (preserved Invariant 3)
- **Bidirectional risks**: NONE
- **Shared SDK**: Decompose ChildStack — second usage; kotlinx-serialization — first stack-level usage в проекте
- **Undocumented patterns**: combinedClickable, DropdownMenu, Intent.ACTION_SEND — все first usages, задокументированы как ADRs.

## Scaffold Ownership (per Invariant 7)

- `backend-dev`: `build.gradle.kts` (новый module), `settings.gradle.kts`, `kotlinx-serialization` plugin, Koin module list update в `AppApplication.kt`
- `frontend-dev`: presentation module content (Components, Composables, Screens), HomeQuests/MyQuests TODO replacements
- `test-dev`: 3 копии `FakeQuestRepository.observeByCatalog` impl, ничего другого
