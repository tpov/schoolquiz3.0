# Feature: App Shell Menu (Bottom Navigation + Side Drawer)

## Status: implemented + retrospective applied (2026-04-19)

## Scope в одной строке
Shell-фича: 4 bottom-вкладки (Локальная / Интернет / События / Магазин), per-tab drawer на 3 из 4, минимальный TopAppBar со статистикой юзера в header drawer, placeholder-экраны для всех разделов, полная дизайн-система по ADR-0010, всё на Decompose + Compose Material3.

## Documents

| Document | Status | Notes |
|----------|--------|-------|
| `0-spec.md` | Complete | 20 FR + 5 NFR, 17 user journeys, 5 FSM + Section Visibility Rules таблица, 45 domain test scenarios, 30+7 acceptance criteria, Walking Skeleton Variant Y (229 JVM tests зелёные) |
| `1-research.md` | Complete | 7 параллельных агентов, 9 search criteria, 7 open questions |
| `2-grounding.md` | Complete | 7 grounding cards, Independent Verification Protocol — все claims [VERIFIED] |
| `05-prior-art.md` | Complete | SDK best practices: Decompose 3.1.0, Koin 3.5.6, Compose Material3, kotlinx-serialization, Firebase KMP |
| `01-architecture.md` | Complete | C4 L1/L2/L3 + Mermaid class diagrams + Koin graph + ADRs 0011 + ADR-LEAD-01/02 |
| `02-behavior.md` | Complete | DFDs (UserStats pipeline + Navigation pipeline) + 18 sequence diagrams (cover all 17 journeys) + State Matrix expansion |
| `03-decisions.md` | Complete | ADR-COMP-01..07 + ADR-LEAD-01 (state-saving deferred) + ADR-LEAD-02 (domain signature change, user-approved) |
| `04-testing.md` | Complete | Test strategy + full coverage mapping (30+7 AC × State Matrix × 45 scenarios × D1-D3 × 17 journeys) |
| `06-api-contract.md` | Complete | Navigator, Destination, DeepLink, UserStatsRepository, Koin modules, ScrollToTopRegistry, SchoolQuizTheme, DS wrappers, external SDK map |
| `08-storage-model.md` | Complete | No Room — in-memory `MutableStateFlow<AppShellState>` + Firestore remote source |
| `plan/` | Complete | 7 phases, 3 Codex review passes (REJECT → CONTESTED → PASS) |
| `retrospective.md` | Complete | 3 failure patterns analyzed; 5 pipeline fixes applied (Signature Card, hook, Options Pattern, SoT matrix, plan review lens) |

## Key decisions

- **ADR-0011** — `RootComponent` interface в domain (pure coroutines `StateFlow<AppShellState>`), `DefaultRootComponent` impl в presentation
- **ADR-LEAD-01** (user-approved) — state-saving deferred: `serializer = null`, каждый cold start = default state. Spec NFR #2 обновлён
- **ADR-LEAD-02** (user-approved) — `ObserveAppShellStateUseCase.invoke` signature change: `invoke(initialState)` → `invoke(currentStateProvider: () -> AppShellState)`. Walking Skeleton exception; phase-01 backend-dev обновит domain + 9 тестов
- **ADR-COMP-02** — `serializer = null` для всех ChildStack per ADR-LEAD-01
- **ADR-COMP-04** — `Navigator` interface в domain (Path A)
- **ADR-COMP-05** — `@Serializable` НЕ добавляется в MVP (dead code при serializer=null)
- **ADR-COMP-06** — `ScrollToTopRegistry` identity-aware unregister per-tab (`MutableMap<Tab, ScrollToTopHook>`)
- **ADR-COMP-07** — `RootComponent` Koin `factory<>` (не `single<>`)
- **startKoin** в `AppApplication.onCreate()` (per web research, updates ADR-0009)
- **Firebase KMP** — Variant A: Google Firebase BOM 33.2.0 в `platform/firebase` (androidMain), `UserStatsDataSource` interface в `shared/core/stats/`
- **`DefaultRootComponent` constructor** canonical: `(componentContext, initUseCase, navigateUseCase, observeUseCase, retapUseCase)` — НЕТ `handleBackUseCase` (back через `NavigateUseCase(state, Destination.Back)`), НЕТ `userStatsRepository` direct (injected в UseCases)

## Quick reference

- **Базовые ADR**: 0001 (модули), 0008 (навигация — модифицируется), 0009 (DI — модифицируется: startKoin в Application), 0010 (designsystem)
- **Затронутые модули**: `shared/feature/app-shell/{domain,data}`, `shared/core/stats` (new), `android/feature/app-shell/presentation`, `android/core/{navigation,designsystem}`, `platform/firebase`, `apps/android-next`
- **Walking Skeleton**: `shared/feature/app-shell/domain/` готов (229 tests green). Phase-01 = adapter-only + 1 domain signature fix per ADR-LEAD-02
- **Out of scope**: auth flow, FAB-ы, Shop/Referrals/Donate подразделы, реальные экраны фич, light theme, deep link URL регистрация, process death full state restoration
- **ADR modifications required**: ADR-0008 (убрать Shop pager; заменить @Parcelize→deferred), ADR-0009 (startKoin в Application, не Activity)

## Review trail

- **Realist (Codex Realist lens)** — 6 passes: REJECT→REJECT→REJECT→REJECT→CONTESTED→CONTESTED. Final accepted at CONTESTED (HIGH #1 recurring finding = user-approved Walking Skeleton exception per ADR-LEAD-02)
- **Skeptic (Codex Skeptic lens on 03-decisions.md)** — 1 pass: CONTESTED. 6 findings fixed
- **Architect (architect-reviewer + Codex Architect on 04/06/08)** — 2 passes: CONTESTED → REJECT → final 6 findings fixed directly by lead judge

## Next command

Feature реализована, retrospective завершён. 5 pipeline fixes применены — новые фичи используют Signature Card формат в plan, hook-блокировку готового кода, Options Pattern для complex фаз, Document Responsibility Matrix, Plan Review Lens. См. `retrospective.md` и `docs/features/lessons-learned.md`.
