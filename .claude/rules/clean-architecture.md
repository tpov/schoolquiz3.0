# Clean Architecture — schoolquiz4.0 KMP

## Layer boundaries

| Layer | Responsibility | Depends on |
|-------|---------------|------------|
| **domain** | Models, repository interfaces, use cases. Pure Kotlin, no Android deps | nothing (or core) |
| **data** | Room DB, DAO, entities, mappers, repository implementations, network | domain |
| **presentation** | Decompose Components, presentation state, navigation adapters | domain |
| **ui** | Compose Screens and design-system components | presentation state/callbacks |
| **core** | Shared contracts, pure business utilities, platform-neutral types | nothing or lower-level core |
| **platform** | Firebase, Android services, billing, crypto, Telegram adapters | domain/core contracts |

## Dependency flow

```
Compose UI -> Decompose Component -> domain
data -> domain
platform -> domain/core contracts
core (standalone unless explicitly layered)
```

## Forbidden imports

| From | To | Allowed? |
|------|----|----------|
| Decompose Component | UseCase / Repository interface | YES |
| Decompose Component | DAO, Entity, DataSource, Firebase/Room adapter | NO |
| UseCase | Repository interface | YES |
| UseCase | another UseCase | YES (sparingly) |
| Repository impl | DAO, Mapper, API | YES |
| Repository impl | another Repository | NO (use UseCase for orchestration) |
| Compose Screen | Component state/callbacks | YES |
| Compose Screen | Koin, Repository, DAO, UseCase | NO |
| domain/ | data.*, presentation.*, android.* (except Parcelable), room.* | NO |
| data/ | presentation.*, ui.* | NO |
| android/core/designsystem | shared/feature/* domain types | Prefer NO; use UI models unless explicitly shared by ADR |

## Rules

- Repository implementations live in data; interfaces in domain.
- Mapper chain: Entity (Room) <-> Mapper <-> Domain Model. Keep mapping in data layer.
- Entity classes stay in data layer — always map to domain models before exposing.
- Orchestration of multiple repositories belongs in use cases or explicitly named orchestration modules, not Compose screens.
- DI composition root / Koin modules are the only places where layer boundaries cross for construction.
- Consult PROJECT-CONTEXT.md for project-specific module structure and DI approach.

## Cross-module / cross-feature boundaries

Текущий проект разбит на KMP feature modules: `shared/feature/<slug>/{domain,data}` и `android/feature/<slug>/presentation`.

| From | To | Allowed? | How |
|------|----|----------|-----|
| `shared/feature/A` | `shared/feature/B` direct import | Avoid / ADR required | Разрешать только когда зависимость product-level и зафиксирована в ADR |
| `android/feature/A/presentation` | `android/feature/B/presentation` direct import | **NO by default** — Exception: Decompose ChildStack Compose rendering (see note below) | Общие UI contracts выносить в `android/core` или domain/core; ChildStack rendering — ADR required |
| feature | `shared/core/*` / `android/core/*` | YES | Общая инфраструктура и типы |
| `core/*` | `shared/feature/*` | **NO** | Core не знает о product features |

**Note: ChildStack Compose rendering exception.** В Decompose архитектуре parent screen хостирует child `@Composable` screen functions sibling features. Этот паттерн разрешён **только** при соблюдении всех условий:
1. Направление строго одностороннее (parent → child). Reverse direction = blocker всегда.
2. Импортируется только `@Composable` screen function — NOT component classes, NOT use cases, NOT repositories, NOT internal types.
3. Exception задокументирован в ADR фичи (пример: ADR-QS-17 для quizzes-screen → lesson-runner).
4. Bidirectional sibling presentation import = blocker всегда, без исключений.

Precedent: `AppShellScreen.kt:53-56` — `app-shell/presentation` импортирует `HomeQuestsScreen`, `MyQuestsScreen`, `QuizzesScreen` из sibling features. Established project pattern.

Bidirectional feature coupling (feature-A импортирует feature-B И feature-B импортирует feature-A) — **всегда blocker**, независимо от механизма. Эта связь идёт через core/domain contract или отдельный orchestration layer, не через прямые imports в обе стороны.

Reflection-based cross-feature calls (например call-handler loaded by class name) обязаны быть задокументированы в ADR с обоснованием.

## Avoid

- No transport details (HTTP codes, JSON keys, Room annotations) leaking into domain.
- No Android framework classes in domain models or use cases (см. `domain-models.md` для полного списка).
- No bypassing repository interface by calling DAO/DataSource directly from Decompose Component or Compose Screen.
- No circular dependencies between layers.
- No direct import между feature-модулями без ADR (см. cross-module таблицу выше).

## Review check (grep-паттерны для architect-reviewer)

```bash
# 1. Core imports feature code (should be empty)
rg -n "^import .*\.shared\.feature\." shared/core android/core platform -g "*.kt"

# 2. Presentation imports data/persistence internals directly
rg -n "^import .*\.(data|persistence).*(Dao|Entity|DataSource|Mapper|Firebase|Room)" android/**/presentation/src/main -g "*.kt"

# 3. Compose screens resolve Koin directly
rg -n "getKoin\(|koinInject\(|inject<" android/**/presentation/src/main/**/ui -g "*.kt"

# 4. Data layer imports presentation/ui
rg -n "^import .*\.(presentation|ui)\." shared/**/data/src/commonMain platform -g "*.kt"
```

Non-empty output in changed files → blocker. Non-empty output in untouched existing files → report as existing debt unless phase explicitly touches that boundary.

Bidirectional feature coupling → blocker независимо от механизма, требует refactor через core interface или single-direction reflection с ADR.
