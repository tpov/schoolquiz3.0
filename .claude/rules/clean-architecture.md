# Clean Architecture — Android

## Layer boundaries

| Layer | Responsibility | Depends on |
|-------|---------------|------------|
| **domain** | Models, repository interfaces, use cases. Pure Kotlin, no Android deps | nothing (or core) |
| **data** | Room DB, DAO, entities, mappers, repository implementations, network | domain |
| **presentation** | ViewModels, managers, orchestration | domain |
| **ui** | Screens (Compose/Fragment), navigation, adapters | presentation, domain |
| **core** (if exists) | Pure business logic, policies, shared types | nothing |

## Dependency flow

```
ui -> presentation -> domain
data -> domain
core (standalone)
```

## Forbidden imports

| From | To | Allowed? |
|------|----|----------|
| ViewModel | UseCase / Repository interface | YES |
| ViewModel | DAO, Entity, Mapper | NO |
| UseCase | Repository interface | YES |
| UseCase | another UseCase | YES (sparingly) |
| Repository impl | DAO, Mapper, API | YES |
| Repository impl | another Repository | NO (use UseCase for orchestration) |
| Screen/Fragment | ViewModel | YES |
| Screen/Fragment | Repository, DAO | NO |
| domain/ | data.*, presentation.*, android.* (except Parcelable), room.* | NO |
| data/ | presentation.*, ui.* | NO |

## Rules

- Repository implementations live in data; interfaces in domain.
- Mapper chain: Entity (Room) <-> Mapper <-> Domain Model. Keep mapping in data layer.
- Entity classes stay in data layer — always map to domain models before exposing.
- Orchestration of multiple repositories belongs in use cases, not ViewModels.
- DI composition root is the only place where all layer boundaries cross.
- Consult PROJECT-CONTEXT.md for project-specific module structure and DI approach.

## Cross-module / cross-feature boundaries

Когда проект разбит на feature-модули (`feature-auth`, `feature-calls`, `feature-voip`, `feature-tcp`) или feature-пакеты внутри :app:

| From | To | Allowed? | How |
|------|----|----------|-----|
| `feature-A/` | `feature-B/` (прямой import) | **NO** | Запрещён direct import между feature-модулями |
| `feature-A/` | `core/`, `shared/`, `common/` | YES | Общая инфраструктура и типы |
| `feature-A/` | `feature-B/` через published interface в core | YES | Interface в `core/`, имплементации в своих features |
| `feature-A/` | `feature-B/` через reflection / service locator | OK if ADR | Только если зафиксировано в `03-decisions.md` как by-design pattern |

Bidirectional feature coupling (feature-A импортирует feature-B И feature-B импортирует feature-A) — **всегда blocker**, независимо от механизма. Эта связь идёт через core или reflection, не через прямые imports в обе стороны.

Reflection-based cross-feature calls (например call-handler loaded by class name) обязаны быть задокументированы в ADR с обоснованием.

## Avoid

- No transport details (HTTP codes, JSON keys, Room annotations) leaking into domain.
- No Android framework classes in domain models or use cases (см. `domain-models.md` для полного списка).
- No bypassing repository interface by calling DAO directly from ViewModel.
- No circular dependencies between layers.
- No direct import между feature-модулями (см. cross-module таблицу выше).

## Review check (grep-паттерны для architect-reviewer)

```bash
# 1. Feature-A импортирует Feature-B напрямую (bidirectional coupling detection)
# Для каждой пары feature-пакетов (A, B) в проекте — подставь реальные имена:
grep -rE "^import .*\.feature\.<other_feature>\." <current_feature>/ --include="*.kt"

# 2. Layer boundary — ViewModel импортирует DAO / Entity / Mapper напрямую
grep -rnE "^import .*\.(data\.(local|dao|entity|mapper)|room\.)" <presentation_path>/ --include="*.kt"

# 3. Screen/Fragment импортирует Repository / DAO напрямую (должно через ViewModel)
grep -rnE "^import .*\.(data\.repository|data\.local\.dao)" <ui_path>/ --include="*.kt"

# 4. Data layer импортирует presentation/ui (обратное направление зависимости)
grep -rnE "^import .*\.(presentation|ui)\." <data_path>/ --include="*.kt"
```

Любой non-empty output → blocker.

Bidirectional feature coupling → blocker независимо от механизма, требует refactor через core interface или single-direction reflection с ADR.
