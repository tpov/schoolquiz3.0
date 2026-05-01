---
description: Разбить design на phase files внутри docs/features/<slug>/plan/ и провести review плана.
argument-hint: "<feature-slug>"
---

Сделай реалистичный план для `$ARGUMENTS`.

## Роль

Ты диспетчер. Ты НЕ пишешь план сам. Ты:
1. Читаешь design docs
2. Запускаешь planner
3. Codex CLI ревьюит план
4. Показываешь результат пользователю

## Шаг 0: Подготовка

Прочитай:
1. `docs/features/<slug>/0-spec.md` (если есть)
2. `docs/features/<slug>/1-research.md`
3. `docs/features/<slug>/2-grounding.md` **(ОБЯЗАТЕЛЬНО)**
4. Design docs: `01-architecture.md`, `02-behavior.md`, `03-decisions.md`, `04-testing.md`, `06-api-contract.md`
5. Conditional docs `07-events.md`, `08-storage-model.md` (если есть)

### Grounding Gate Check

**Если `2-grounding.md` НЕ существует — СТОП.** Plan без grounding — главная причина планов, не привязанных к реальному коду.

**Для каждой проблемы из grounding проверь заполненность:**
- [ ] Entry Points — где сценарий стартует
- [ ] Code Owners — кто владеет логикой
- [ ] Backend/Contract Check — что реально поддерживает backend
- [ ] Validation — как проверим фикс

Если хотя бы одно поле пустое — план ещё сырой. Сообщи пользователю о пробелах.

Выбери стратегию порядка фаз:
- **Bottom-up** (default): [Domain from spec] → UseCase orchestration → Adapter → Controller → UI
- **Adapter-first**: Adapter → UseCase → Controller → UI
- **Vertical slice**: (UseCase + Adapter) per endpoint → UI

**Walking Skeleton integration**: если `0-spec.md` содержит `Feature Domain Contract` ≠ N/A — domain слой уже сгенерирован на spec-этапе по project layout из `.claude/PROJECT-CONTEXT.md`. Для этого репозитория default: `shared/feature/<slug>/domain/src/commonMain/` + `.../commonTest/` с зелёными KMP/JVM тестами.

Phase-01 в этом случае = **integration phase**, НЕ create-from-scratch:
- backend-dev добавляет repository implementations, DAO-domain mappers, DI bindings, UseCase orchestration классы (если нужна координация multiple repos)
- test-dev добавляет integration tests (repository round-trip, DAO boundary) — JVM тесты для pure domain УЖЕ зелёные из spec
- frontend-dev работает с готовыми domain types (state classes, value objects, enums)

Domain классы НЕ переписываются. Если grounding или design указывают на блокер в existing skeleton — STOP, сообщи пользователю (это сигнал, что spec skeleton был неверным, нужен re-spec).

Если `Feature Domain Contract` = N/A (фича чисто UI/integration) — применяй стандартную стратегию без Walking Skeleton integration.

## Шаг 1: Planner

Запусти Agent(planner):

```
Feature: <slug>
Описание: <1-2 предложения>
Стратегия: <bottom-up / adapter-first / vertical slice>

Прочитай design docs в docs/features/<slug>/ и разбей на фазы.

Каждая фаза — директория в docs/features/<slug>/plan/ с файлами per role:
- plan/phase-01/overview.md, backend.md, tests.md, (frontend.md если нужен)
- overview: Goal, Scope, Layer, Dependencies, AC, Traceability, Review Tags
- backend.md / frontend.md: конкретные файлы, изменения, сигнатуры для dev
- tests.md: сценарии, fakes, edge cases для test-dev
- frontend.md создаётся ТОЛЬКО если фаза затрагивает UI/presentation

Если в spec есть `Feature Domain Contract` ≠ N/A, planner ОБЯЗАН:
- phase-01 = **integrate existing domain** из project-layout domain path (default here: `shared/feature/<slug>/domain/src/commonMain/`), НЕ create-from-scratch
- в `phase-01/backend.md` явно указать: "Domain классы из spec phase — NOT modify. Only wrap in repository/adapter/DI."
- в `phase-01/backend.md` перечислить конкретные интеграционные артефакты: repository impls, mappers, DI bindings, UseCase orchestration классы
- в `phase-01/tests.md` перечислить integration tests (не unit tests для pure domain — они уже зелёные)
- в Traceability ссылаться на existing domain файлы из spec + `Feature Domain Contract`, `Primary User Journeys`, `State Matrix`, `Domain Test Scenarios`
- не отправлять feature-specific business logic в отдельный `core/` flow
- если grounding показывает необходимость modify domain — это blocker, план НЕ создаётся, эскалация пользователю

Спавни sub-planner агентов параллельно для каждой вертикали.

Также создай plan/README.md с обзором, таблицей фаз и file map.
```

Агент сам прочитает свою роль и project rules. Если ему нужен project skill — он должен вызвать его явно.

### Phase File Contract

Каждый `plan/phase-NN/overview.md` должен содержать:

```markdown
## Phase N: [Name]

### Goal
[1-2 предложения]

### Scope
[Что реализует эта фаза]

### Layer
domain | useCase | adapter | controller | ui

### Dependencies
phases_ref: [phase-01] or none

### Role Inputs
- backend: `backend.md` or `none`
- frontend: `frontend.md` or `none`
- tests: `tests.md` or `none`

### Traceability
| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|----------------|-------------|------------|
| Problem 1: ... | FileA.kt | method1(), method2() | API supports X, no Y | Change Z in FileA | Test: given/when/then |

### New Files
- `path/to/NewFile.kt` — description

### Modified Files
- `path/to/ExistingFile.kt` — what changes

### Deleted Files
- `none` or exact files allowed to delete in this phase

### Cross-Phase Dependencies (menu-refactor / quizzes-screen retro fix)

**Consumed from previous phases** (types/Flows/Channels/DI bindings):
- <Type / Flow / Channel> from phase-NN: consumer contract <single-consumer / multicast / state / event>

**Provided for next phases**:
- <Type / Flow / Channel>: что фаза создаёт + кто consumer

**Temporary stubs** (TODO markers):
- <file>:<line> — TODO "Phase NN cleanup": <что должно быть удалено / replaced>

**Required cleanup in this phase** (from earlier temp stubs):
- phase-NN TODO @ <file>:<line> — action: <remove / replace>

### DI Bindings (menu-refactor retro fix — required if phase wires Koin)

**Provided in this phase** (single/factory):
- <Type> via <Module>: dependencies = [<Type2>, <Type3>]

**Required from earlier phases**:
- <Type2> (expected в Phase-NN)

**Koin Verify status**:
- [ ] All `get()` / constructor deps have binding в этой фазе или earlier phases
- [ ] Unit test для `koinApplication { modules(...) }.checkModules()` if applicable

### Acceptance Criteria
- [ ] [Критерий этой фазы]

### Tests Required
- `test_name`: given X, when Y, then Z
- `test_edge_case`: given boundary, when action, then expected
- Для `phase-01` (integration mode если Walking Skeleton сгенерирован на spec, см. выше): тесты — integration (repository round-trip, DAO boundary, multi-layer). Pure domain JVM тесты уже зелёные из spec, их не дублируют

### Validation
| # | Command | Expected |
|---|---------|----------|
| 1 | `./gradlew ciCheck --no-configuration-cache` | canonical local gate passes |
| 2 | phase-specific command from `overview.md` if needed | device/backend smoke passes |
```

### Traceability Gate Rule

**Нельзя создавать phase file без заполненной Traceability таблицы.** Каждая строка должна ссылаться на конкретную проблему из `2-grounding.md`. Если фаза не связана ни с одной проблемой из grounding — это красный флаг: либо фаза лишняя, либо grounding неполный.

## Шаг 2: Plan Review (Codex CLI)

Используй skill `adversarial-review`. Прочитай `.claude/skills/adversarial-review/SKILL.md`, а при необходимости exact protocol — `references/cli-protocol.md`.

**ОБЯЗАТЕЛЬНО** передай Codex CLI ссылку на **Plan Review Lens** — `.claude/skills/adversarial-review/references/plan-review-lens.md`. Этот lens ловит класс проблем "plan = готовый код вместо ТЗ", который обычный sequencing/deps review пропускает.

1. Проверь наличие CLI (`codex` или `claude`)
2. Запусти review плана с **двумя lenses**:
   - **Sequencing lens**: все design docs покрыты фазами? Dependencies корректны? Validation commands реалистичны? README синхронизирован с phase files?
   - **Plan-as-ТЗ lens** (см. `references/plan-review-lens.md`): нет fenced ```kotlin/```java блоков? Каждый new file имеет Signature Card? Canonical references указаны? Tests Required — scenarios, не JUnit код? Complex фазы имеют Options Considered?
3. Если blocker в любом lens — исправь и повтори

**Defence-in-depth**: hook `.claude/hooks/check-plan-no-code.sh` блокирует save plan-файлов с fenced кодом на уровне файловой системы. Review lens — дополнительная проверка на уровне содержания (canonical refs, Signature Card полнота, Options для complex).

## Шаг 3: Human Approval

Покажи пользователю:

```
## Code Plan: [Feature Name]

### Strategy
[Bottom-up / Adapter-first / Vertical slice]

### Phases
1. [Phase 1] — [layer]
2. [Phase 2] — [layer]
...

### File Map
New: N files, Modified: M files

### Status
Design: approved
Plan: N phase files
```

**=WAIT for user approval.=**

Обнови `docs/features/<slug>/README.md`: Status: `planned`.

## Правила

- Каждый `plan/phase-NN/` — self-contained пакет фазы: lead читает `overview.md`, workers читают только свои role-файлы
- `plan/README.md` должен быть lead-dashboard: `Phase | Goal | Depends on | Role Inputs | Validation`
- Exact file references с path и description
- **Plan = ТЗ (Signature Card формат), не implementation.** Fenced блоки ```kotlin/```java/```kt/```groovy запрещены в plan-файлах и блокируются hook'ом `.claude/hooks/check-plan-no-code.sh` (exit 2). Формат задачи — Signature Card (см. `.claude/agents/planner.md`): файл + сигнатура в inline backticks + вход/выход/edge cases + canonical reference в `06-api-contract.md` (для публичных типов). Для internal types (convention plugins, helpers) допустима inline-сигнатура без api-contract entry, но НЕ полный класс
- Canonical signatures публичных типов живут ТОЛЬКО в `06-api-contract.md` (см. `feature-design.md` Document Responsibility Matrix) — plan-файлы ссылаются, не копируют
- Validation commands из реального проекта (см. CLAUDE.md)
- Каждая фаза, меняющая production code, ОБЯЗАНА содержать секцию "Tests Required"
- Каждая фаза ОБЯЗАНА содержать Traceability таблицу со ссылкой на grounding
- **Complex фазы** (3+ модулей / новый architectural pattern / FSM / REQUIRES tag) ОБЯЗАНЫ содержать секцию `### Options Considered` с минимум 2 вариантами + recommended + rationale + rejected trade-offs (см. `planner.md`)
- План не может пропустить acceptance criterion из `0-spec.md` — completeness-reviewer проверит покрытие
