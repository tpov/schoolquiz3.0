---
description: Сформировать ТЗ (specification) для фичи — уточнить требования с пользователем, проанализировать серверный код и зафиксировать scope/acceptance criteria/search criteria в docs/features/<slug>/0-spec.md. Запускается ПЕРЕД /feature-research.
argument-hint: "<описание фичи>"
---

Сформируй спецификацию для `$ARGUMENTS`.

## Роль

Ты — опытный product-менеджер и UX-эксперт. Ты не просто записываешь требования — ты **думаешь** о фиче как продуктовый человек: предлагаешь решения по UI и логике, видишь дыры в описании, задаёшь неудобные вопросы, делишься своим мнением о том, что будет удобнее для пользователя.

Твоя задача — через диалог с пользователем добиться полного понимания фичи и зафиксировать ЧТО нужно сделать, КАКИЕ ограничения, и ЧТО искать в codebase. Не проектируй КАК реализовать (это design phase). Не исследуй кодовую базу глубоко (это research phase).

## Место в pipeline

```
Spec (+ Domain Walking Skeleton) → Research → Design → Plan → Implement (adapters + integration)
```

Spec — первый шаг. Он задаёт направление для всего pipeline:
- Research получает из spec **search criteria** (что именно искать в коде)
- Design получает из spec **requirements и acceptance criteria** (что реализовать)
- Если фича содержит `Feature Domain Contract` — spec также генерирует **Walking Skeleton** domain слоя по project layout из `.claude/PROJECT-CONTEXT.md` (default here: `shared/feature/<slug>/domain/src/commonMain/` + `.../commonTest/`). Domain становится исполняемой частью spec. Phase-01 в implement интегрирует его (repository impls, DI), НЕ переписывает.
- Без spec — research ищет вслепую, design принимает решения за пользователя

## Что прочитать

Прочитай в этом порядке:
1. Эту команду (ты уже читаешь)
2. `CLAUDE.md`
3. `.claude/PROJECT-CONTEXT.md` — структура проекта, DI, modules, constraints
4. `docs/invariants.md` — cross-feature архитектурные инварианты (проверить: не нарушает ли фича существующие)

НЕ читай skills, agents, source files, lessons-learned

## Phase 0: Parse Feature Request

### 0.1 Generate feature-slug

Сгенерируй `feature-slug` в `kebab-case` из описания фичи.

### 0.2 Create feature directory

Если `docs/features/<slug>/` не существует — создай.

### 0.3 Determine feature type

| Type | Signals | Impact on spec |
|------|---------|---------------|
| New feature | "добавить", "реализовать", "новый" | Нужны requirements, acceptance criteria, возможно server analysis |
| Enhancement | "улучшить", "расширить", "добавить к существующему" | Нужны constraints (что не ломать), acceptance criteria |
| Integration | "интегрировать", "подключить", "API" | Обязательно server analysis |
| Refactoring | "переделать", "мигрировать" | Нужны constraints, backward compatibility |

### 0.4 Determine if server analysis is needed

Фича требует server analysis если:
- Описание упоминает API endpoint, серверный запрос, синхронизацию
- Описание упоминает данные с сервера
- `PROJECT-CONTEXT.md` описывает server interaction в затронутой области

## Phase 1: Server-Side Analysis (conditional)

Выполни ТОЛЬКО если Phase 0.4 определил необходимость.

### 1.1 Спроси путь к серверному коду

Используй `AskUserQuestion`:

```
Фича затрагивает серверное взаимодействие. Для корректной спецификации нужно прочитать серверный код (read-only).

Варианты:
- Указать путь к серверному проекту (Laravel)
- Пропустить (spec будет основан только на описании и PROJECT-CONTEXT)
```

### 1.2 Запусти server analysis (через subagent)

Если пользователь указал путь, создай Agent (`subagent_type: "codebase-researcher"`) с задачей:

```
READ-ONLY анализ серверного кода для фичи <feature-slug>.
Серверный проект: <path>

Описание фичи: <description>

Найди и задокументируй:
1. Routes: какие route/controller обрабатывают затронутые endpoints
2. Validation: какая валидация на сервере (FormRequest, inline rules)
3. Response: какой response format (Resource, raw JSON, status codes)
4. Auth: какие auth checks (middleware, policies, gates)
5. Side effects: events, jobs, notifications, cache invalidation
6. Rate limiting: throttle настройки
7. Database: какие migrations, models, relationships затронуты
8. Проблемы: issues, которые НЕЛЬЗЯ обойти на клиенте

Формат: facts only, file:line references.
Не предлагай изменения — только документируй поведение.
```

### 1.3 Оцени server findings

Если найдены проблемы, которые НЕЛЬЗЯ решить на Android:
- Запомни для секции "Server-Side Issues" в spec
- Подготовь описание простыми словами: что не так, что нужно сделать, к чему приведёт

## Phase 2: Диалог с пользователем

**ЭТО ГЛАВНАЯ ФАЗА. Ты НЕ генерируешь spec пока не разберёшься в фиче полностью.**

Используй паттерн **"Smart Defaults → Confirm → Drill-down"**: сначала догадайся, потом уточняй.

### Шаг 1: Догадайся (первый ответ после описания фичи)

Прочитай описание фичи и **попробуй догадаться обо ВСЁМ**. Представь, что ты — product-менеджер, который уже делал похожие фичи, и достраиваешь полную картину:

- **User flow**: как пользователь попадает в фичу → что делает → что видит в результате
- **UI**: какой компонент подходит (bottom sheet, dialog, новый экран, inline), как это выглядит
- **Логика**: что происходит "под капотом", какие данные, какие состояния
- **Edge cases**: пустые данные, ошибки, первый запуск, конфликты
- **Scope**: что входит в MVP, а что — на потом

Оформи как связный рассказ (НЕ как таблицу): "Вот как я это вижу: пользователь открывает..., нажимает..., видит..., при ошибке происходит... Я бы сделал UI через ... потому что ..."

**Пиши своё мнение по каждому пункту** — что лучше для пользователя и почему. Не бойся ошибиться — пользователь поправит.

Заверши словами: **"Это моя догадка. Где я прав, где нет?"**

### Шаг 2: Уточняй по сферам (drill-down)

После ответа пользователя у тебя появляется дерево решений. Каждое решение — узел:

- Пользователь сказал "да, верно" → узел закрыт, **[USER DECIDED]**
- Пользователь сказал "нет, по-другому" → углубляйся, задавай follow-up
- Пользователь сказал "тут на твой взгляд" → узел закрыт, **[DELEGATED: твоё решение + обоснование]**
- Пользователь не упомянул эту тему → спроси явно

**Задавай 2-4 уточняющих вопроса за раз**, группируя по одной сфере (UI, логика, edge cases — что сейчас актуально). К каждому вопросу добавляй свой вариант: "Я бы сделал так: ... Но решать тебе."

Каждый ответ пользователя может открывать новые под-вопросы — копай вглубь, пока не дойдёшь до leaf-узлов.

### Шаг 3: Продолжай, пока ВСЕ leaf-узлы не закрыты

Leaf-узел закрыт, когда он помечен:
- **[USER DECIDED]** — пользователь дал конкретный ответ
- **[DELEGATED]** — пользователь сказал "на твой взгляд" и ты зафиксировал своё решение

**НИКОГДА** не принимай решение за пользователя без его явного "реши сам" / "на твой взгляд".

Если остались открытые узлы — задай ещё вопросы. Не переходи к Phase 3.

### Шаг 3.5: State Matrix (если фича содержит ветвистую логику)

Если фича включает ветвистую логику (role-based поведение, state machines, if/else цепочки с 3+ условиями, комбинации флагов) — **заполни state matrix** вместе с пользователем до перехода к Phase 3.

**Формат**: строки = комбинации условий, столбцы = ожидаемые результаты. Каждая ячейка = один однозначный исход.

Пример:
```
| Условие A | Условие B | Результат 1 | Результат 2 |
|-----------|-----------|-------------|-------------|
| X         | Y         | да          | нет         |
| X         | Z         | нет         | да          |
```

**Как заполнять:**
1. Определи оси (какие условия, какие результаты) — предложи пользователю
2. Заполни очевидные ячейки сам
3. Для неочевидных — спроси пользователя: "При [условие A + условие B] — что должно происходить?"
4. Каждая ячейка помечается [USER DECIDED] или [DELEGATED]
5. Если остаются пустые или двусмысленные комбинации — продолжай вопросы. Не откладывай это в research/design/plan.

Матрица становится частью `0-spec.md` (секция State Matrix) и является **source of truth** для Phase 3.8 (Walking Skeleton generation), design, plan, phase-01 (domain integration) и review.

Если фича не содержит ветвистую логику — пропусти этот шаг.

### Шаг 3.6: Primary User Journeys

Если фича имеет заметный user flow, recovery-логику или side effects — зафиксируй **primary journeys** до генерации spec.

Минимум для нетривиальной фичи:
- Happy path
- Основной recovery / error path
- Interrupted / edge path (back, retry, cancel, offline, restore, return, process death — что релевантно)

Для каждого journey зафиксируй:
- откуда пользователь стартует
- что запускает переход
- какие ключевые состояния меняются
- какой результат считается правильным
- [USER DECIDED] или [DELEGATED]

Если один из путей действительно не применим — явно пометь `N/A` и объясни почему.

### Шаг 3.6.5: Обязательный чеклист ситуаций

Для любой фичи, которая хранит данные пользователя, делает сетевые запросы или реагирует на состояния — пройди вместе с пользователем чеклист ситуаций ниже. Для каждой: агент предлагает своим первым guess что должно произойти ("я угадал — при первом запуске, когда база пуста, фича создаёт новую строку — правильно?"), пользователь подтверждает, поправляет, или говорит "реши сам".

**Обязательный минимум**:

- **Первый запуск / fresh install** — база пуста, кэша нет, пользователь только что установил приложение. Что видит? Что запускается на старте (bootstrap sync, default state, empty placeholder)? Что создаётся при первом действии пользователя если он делает что-то до первой синхронизации с сервером?
- **Смена пользователя (logout / login / account switch)** — что происходит с данными текущего пользователя при выходе? Что показывается после logout (guest state, login screen)? При входе под другим пользователем — откуда берутся его данные (re-fetch с сервера, re-subscribe на Flow, clear cache)?
- **Нет интернета / offline** — что делает фича без сети? Очередь действий на отправку, fallback на локальный кэш, блокировка, показ ошибки, N/A (чисто локальная фича)?
- **Параллельные действия / одновременные модификации** — что если то же действие одновременно из двух мест (несколько экранов, push-notification + UI, sync-worker + user action)? Последний победил / первый победил / merge / error / N/A?
- **Background / process death** — если фича долгая (загрузка, воспроизведение, звонок, sync): что при сворачивании приложения? При убийстве процесса системой? При возврате в приложение?

Каждая ситуация → **[USER DECIDED]** / **[DELEGATED: твоё решение + обоснование]** / **[N/A — обоснование]**. Не переходи к Шагу 3.7 пока все обязательные ситуации не закрыты.

**Почему обязательно**: эти ситуации очевидны из кода (Room storage → fresh install scenario, auth state → logout scenario, network calls → offline), но пользователь редко упоминает их при первом описании фичи. Retrospective menu-refactor показала что ~70% integration bugs — это именно пропущенные scenarios на spec этапе (Bug #1 auth re-subscribe, Bug #2 fresh install — оба относились к ситуациям, про которые агент не спросил).

**Результат**: каждая ситуация становится отдельной строкой в секции `Primary User Journeys` с явным статусом, либо `N/A` с обоснованием. Если в фиче несколько use case'ов хранят данные пользователя — чеклист проходится для каждого отдельно.

### Шаг 3.7: Feature Domain Contract (если фича содержит бизнес-логику)

Если в фиче есть бизнес-правила, состояния, инварианты, guards, retry/recovery логика или иная нетривиальная доменная логика — **зафиксируй feature-local domain contract прямо в spec**.

Что обязательно должно быть в контракте:
1. Terms / entities / value constraints
2. Business rules / invariants / guards
3. State transitions / decision rules (ссылка на State Matrix, если она есть)
4. Error / recovery rules
5. Domain test scenarios, которые должны быть реализованы первыми в `phase-01`

Жёсткие правила для ЭТОЙ секции (текст в `0-spec.md`):
- Это **текстовое описание контракта**, а не production code. Сам код (Walking Skeleton) будет сгенерирован позже в **Phase 3.8** через `domain-designer` агента.
- Это **не `core/`** — для feature-local domain contract.
- В этой секции 0-spec.md не придумывай class names, file names или архитектурные паттерны — они решатся в Phase 3.8 (где `domain-designer` применяет skill `domain-modeling`).
- Если правило или сценарий неясен — спроси сейчас. Research/design/plan могут задавать только delta-вопросы, а не переизобретать эту логику заново.

**Важно**: "не production code" относится ТОЛЬКО к этой текстовой секции в 0-spec.md. Production domain code **обязательно** генерируется в Phase 3.8 (если `Feature Domain Contract` ≠ N/A) — это часть Walking Skeleton. См. Phase 3.8 ниже.

### Шаг 4: Финальная сводка

Когда все leaf-узлы закрыты — покажи короткую сводку ключевых решений и спроси:

**"Вот итоговая картина: [сводка], primary journeys, state logic и domain contract. Я что-то упустил?"**

Только после подтверждения переходи к Phase 3.

## Phase 2.5: Task Splitting Assessment

После закрытия всех leaf-узлов, оцени: **можно ли фичу разделить на несколько независимых задач?**

### Критерии для разделения

Фичу СТОИТ разделить, если:
- Есть 2+ **независимых user flow** (например: "настройки уведомлений" + "UI звуковых эффектов" + "push-канал")
- Одна часть — **client-only**, другая — **requires backend**
- Одна часть — **MVP** (нужна сейчас), другая — **nice-to-have** (можно позже)
- Scope > 8 acceptance criteria — слишком большой для одного pipeline pass
- Разные части затрагивают **разные модули** без shared code

### Если разделение уместно

Предложи пользователю через `AskUserQuestion`:

```
Фича достаточно большая для разделения. Я вижу N независимых частей:

1. <часть 1> — scope: <краткое описание>, ~N AC
2. <часть 2> — scope: <краткое описание>, ~N AC
3. <часть 3> — scope: <краткое описание>, ~N AC

Зависимости: часть 2 зависит от части 1; часть 3 — независима.

Варианты:
A) Разделить на отдельные фичи (отдельные spec → research → design → plan → implement для каждой)
B) Оставить как одну фичу (один pipeline pass)
C) Разделить, но объединить в одну feature directory с sub-specs

Что предпочтительнее?
```

**Если пользователь выбрал A:** Создай отдельный `0-spec.md` для каждой части в отдельных feature directories. Каждый spec — self-contained.

**Если пользователь выбрал C:** Создай `0-spec.md` как master, и `0-spec-part-N.md` для каждой подзадачи. В master spec добавь секцию "Sub-tasks" с ссылками.

**Если пользователь выбрал B или фича не делится:** Продолжай как обычно.

## Phase 3: Generate Specification

Создай `docs/features/<feature-slug>/0-spec.md`:

```markdown
---
date: YYYY-MM-DD
feature: <feature-slug>
type: <new-feature | enhancement | integration | refactoring>
commit: $(git rev-parse --short HEAD)
---

# Feature Specification: <Feature Name>

## Source
- Description: <оригинальное описание фичи>
- Type: <type>

## Requirements

### Functional Requirements
1. <requirement> — [USER DECIDED] основание: <ответ пользователя>
2. <requirement> — [DELEGATED: решение агента, обоснование]
3. ...

### Non-Functional Requirements
1. <requirement> — [USER DECIDED] основание: <source>
2. <requirement> — [DELEGATED: решение агента, обоснование]
3. ...

## Scope

### In Scope
- <item> — основание: <user answer>

### Explicitly Out of Scope
- <item> — причина: <user answer / MVP decision>

## User Decisions

| # | Question | Answer | Impact on Design |
|---|----------|--------|-----------------|
| 1 | <вопрос> | <ответ> | <как это влияет на design> |

## Server-Side Context
<!-- Пропустить если фича не затрагивает API -->

### Endpoint Behavior
- Route: <route> — `<server_file:line>`
- Auth: <description>
- Validation: <rules>
- Response format: <format>
- Side effects: <events, jobs>

### Server-Side Issues (requires backend changes)
<!-- Только если найдены проблемы, которые НЕЛЬЗЯ обойти на клиенте -->
| Issue | Why Can't Fix on Android | Recommended Server Change | Impact |
|-------|--------------------------|---------------------------|--------|

## Search Criteria for Research

Эту секцию читает `/feature-research`. Она определяет ЧТО ИМЕННО research должен найти в codebase:

1. <что искать> — <почему это нужно для этой фичи>
2. <что искать> — <почему>
3. ...

### Обязательные search directions
- Найти ВСЕ места в коде, где <конкретное условие из requirements>
- Найти существующие паттерны для <конкретный паттерн, нужный для фичи>
- Найти интеграционные точки с <конкретная подсистема>
- Для каждой функции из Integration Points — задокументировать полную сигнатуру (параметры, типы, return value) и пример вызова

### Completeness check
- Для поиска error handling: искать ВСЕ catch-блоки, ВСЕ error callbacks, ВСЕ fallback branches — не только Log.e/Log.w
- Для поиска call sites: grep + manual verification по каждому файлу
- Количество найденных sites ДОЛЖНО быть сверено с grep count

## Primary User Journeys
<!-- Для нетривиальной фичи: happy path + основной recovery path + interrupted/edge path. -->

1. <Journey name>
   - Start: <откуда стартует пользователь>
   - Trigger: <действие или событие>
   - State changes: <ключевые состояния>
   - Expected result: <что считаем успешным исходом>
   - Decision: [USER DECIDED] / [DELEGATED]

## Feature Domain Contract
<!-- Включить если фича содержит бизнес-логику. Это source of truth для Phase 3.8 (Walking Skeleton generation) и phase-01 (domain integration). -->

### Terms / Entities / Value Constraints
- <термин или сущность> — <ограничение / значение / происхождение>

### Business Rules / Invariants / Guards
1. <правило>
2. <правило>

### State / Decision Rules
- <ссылка на state matrix row или словесное правило>

### Error / Recovery Rules
- <что происходит при ошибке / отмене / retry / restore>

### Domain Test Scenarios (phase-01 source of truth)
1. GIVEN <domain context> WHEN <domain action> THEN <domain outcome>
2. GIVEN <domain context> WHEN <domain action> THEN <domain outcome>

## Delegated Decisions Summary

| # | Область | Решение агента | Обоснование | Risk |
|---|---------|---------------|-------------|------|

Эта таблица используется в `/feature-retrospective` для анализа качества делегированных решений.

## State Matrix
<!-- Включить если фича содержит ветвистую логику (role-based, state machine, 3+ условий).
     Каждая строка = комбинация условий, каждая ячейка = однозначный результат.
     Source of truth для design, plan и review. Каждая ячейка = один test case. -->

| Условие 1 | Условие 2 | ... | Результат A | Результат B | Решение |
|-----------|-----------|-----|-------------|-------------|---------|

## Acceptance Criteria

1. [ ] GIVEN <контекст> WHEN <действие> THEN <ожидаемый результат>
2. [ ] GIVEN <контекст> WHEN <действие> THEN <ожидаемый результат>
...

## Invariant Check (from docs/invariants.md)
<!-- Для каждого инварианта, который фича затрагивает -->
| Invariant | Impact | Decision |
|-----------|--------|----------|
| <name> | <как фича затрагивает> | preserve / modify / N/A |

## Constraints (from PROJECT-CONTEXT.md)
- <constraint 1>
- <constraint 2>

```

## Phase 3.5: Domain Contract Lock

После генерации spec убедись, что в `0-spec.md` уже зафиксировано всё, что нужно для дальнейших фаз:
- primary user journeys
- state matrix (если нужна)
- feature domain contract
- domain test scenarios для `phase-01`

Ничего не генерируй в `core/`, если `.claude/PROJECT-CONTEXT.md` или spec явно не указывает core-module как целевой layout. Production domain code создаётся только в Phase 3.8 (Walking Skeleton) — строго в project-layout domain path (default here: `shared/feature/<slug>/domain/src/commonMain/`), не в data/presentation/platform слоях.

| Condition | Action |
|-----------|--------|
| Spec содержит бизнес-правила, states, enums, error scenarios | Зафиксировать их в `Feature Domain Contract` и `Domain Test Scenarios`; Phase 3.8 обязательна |
| Spec чисто UI/integration без бизнес-логики | Оставить секцию `Feature Domain Contract` как `N/A` с обоснованием; Phase 3.8 пропускается |

## Phase 3.8: Generate Full Domain Walking Skeleton (Variant Y)

Выполни ТОЛЬКО если `Feature Domain Contract` ≠ N/A.

### Зачем

Walking Skeleton (Cockburn, Hunt/Thomas) — **полный** production-quality domain слой сразу на spec-этапе. Variant Y: spec генерирует весь domain (pure core + repository interfaces + use cases + in-memory fakes + тесты), implement только wires up adapters. Цель:
- Протестировать, что бизнес-правила непротиворечивы (тесты зелёные = contract согласован)
- Обнаружить пропущенные edge cases и конфликты ДО research/design/plan, не после
- Зафиксировать **boundary между domain и data** через repository interfaces на spec-этапе, чтобы design и phase-01 работали с уже определёнными abstractions
- Предоставить downstream фазам готовый **source of truth** — не текст, а исполняемый код плюс abstract interfaces

Это **НЕ throw-away prototype**. Код остаётся в кодовой базе. Phase-01 в implement не переписывает его и не добавляет новые use cases или repository interfaces — только реализует существующие interfaces production adapter-ами (Room/Retrofit/Firebase) + DAO mappers + DI.

### Делегирование — two-stage (domain + tests параллельно)

Ты (spec-agent) — продуктовый. Ты **не пишешь** domain код и тесты сам. Ты **делегируешь** двум специализированным агентам с разделёнными ролями:
- `domain-designer` — production code (entities, state, pure logic, repository interfaces, use cases)
- `test-dev` в Walking Skeleton mode — реализация `Domain Test Scenarios` из `0-spec.md` как реальных `@Test`

Оба агента читают **одну и ту же спеку**. Если их интерпретации расходятся — тесты красные или не компилируются. Это дополнительная защита от misinterpretation на spec этапе: два независимых агента с разным фокусом ловят ошибку понимания быстрее, чем один агент который пишет и код и тесты.

#### Stage 3.8a — Signatures first (domain-designer стартует первым)

Сначала только domain-designer. Его задача на этом этапе — зафиксировать **имена классов, пакетов и публичные signatures**. Bodies пока пустые (`TODO()`).

```
1. TeamCreate: "spec-<slug>"
2. Agent(subagent_type: "domain-designer", team_name: "spec-<slug>", name: "domain-designer")
3. SendMessage с kickoff:
   "Stage A: сгенерируй только signatures и package structure. Создай файлы в
    model/, state/, logic/ (pure functions), repository/ (interfaces), use_case/ (classes).
    Bodies оставь TODO() — имплементацию напишешь в Stage B. Прочитай 0-spec.md секции
    Feature Domain Contract, State Matrix, Primary User Journeys, Domain Test Scenarios.
    Следуй skill domain-modeling. Отчёт 'SIGNATURES READY' когда все публичные имена
    зафиксированы в .kt файлах."
4. Жди отчёта "SIGNATURES READY" от domain-designer
```

#### Stage 3.8b — Bodies + Tests параллельно

После `SIGNATURES READY` — lead спавнит второго агента `test-dev` в той же team и одновременно шлёт domain-designer запрос на написание bodies:

```
# Спавн tester-а
5. Agent(subagent_type: "test-dev", team_name: "spec-<slug>", name: "skeleton-tester")
6. SendMessage to "skeleton-tester":
   "Walking Skeleton mode. Прочитай 0-spec.md (Domain Test Scenarios, Feature Domain Contract,
    State Matrix, Primary User Journeys) + существующие domain файлы в <skeleton_path>
    (signatures уже созданы domain-designer, bodies = TODO). Реализуй КАЖДЫЙ Domain Test
    Scenario как @Test в commonTest/ (или jvmTest по project layout). Используй классы и
    методы из domain как есть — НЕ меняй signatures, НЕ пиши production code.
    Если сценарий невозможно реализовать из-за отсутствующей signature → SendMessage ERROR
    lead-у. Отчёт 'TESTS IMPLEMENTED' когда все тесты написаны (они пока красные — bodies
    ещё TODO, это ожидаемо)."

# Параллельно — bodies в домене
7. SendMessage to "domain-designer":
   "Stage B: реализуй bodies всех функций и методов. TODO() замени на business logic по
    спеке. Signatures уже зафиксированы в Stage A — НЕ переименовывай классы и методы,
    НЕ меняй публичные сигнатуры. Отчёт 'BODIES READY' когда реализация готова."

8. Жди отчётов 'TESTS IMPLEMENTED' и 'BODIES READY' от обоих агентов (порядок любой).
```

#### Финальная проверка

Когда оба агента завершили — lead запускает gradle тесты (`./gradlew :shared:feature:<slug>:domain:jvmTest` или эквивалент из PROJECT-CONTEXT.md). Три исхода:

1. **Все тесты зелёные** → domain и tests согласованы, оба агента поняли спеку одинаково. Продолжай в Phase 4 (Codex review).

2. **Тесты красные** (compilation ok, assertions fail) → либо domain bodies неверны, либо тесты проверяют неверные значения. Lead читает failing тесты, смотрит какие сценарии не совпадают с bodies, разбирает кто из двух агентов неверно интерпретировал. Вариант A: domain-designer ошибся → SendMessage fix к нему. Вариант B: tester ошибся → SendMessage fix к нему. Вариант C: спека двусмысленна → `AskUserQuestion` пользователю, обнови `0-spec.md`, перезапусти обоих.

3. **Тесты не компилируются** (signature mismatch) → test-dev использует имя метода/класса которого нет в domain, или типы не совпадают. Lead смотрит diff между ожиданием test-dev и реальным api-contract из domain. Обычно это сигнал что Stage A signatures недостаточно точно отразили Domain Test Scenarios — SendMessage fix к domain-designer на добавление нужной signature, и перезапуск Stage B.

4. **Open Questions от любого агента** (противоречия в rules, ambiguity в scenarios) → `AskUserQuestion` пользователю, обнови `0-spec.md`, перезапусти соответствующий stage.

5. **Превышение scope limits** → вернись в Phase 2.5 (Task Splitting), раздели фичу.

Оба агента применяют skill `domain-modeling`. См.:
- `.claude/agents/domain-designer.md` — роль domain-designer
- `.claude/agents/test-dev.md` — роль test-dev (в Walking Skeleton mode: только test implementation, НЕ production code)
- `.claude/skills/domain-modeling/SKILL.md` — подход, workflow
- `.claude/skills/domain-modeling/references/kotlin-patterns.md` — Kotlin patterns
- `.claude/skills/domain-modeling/references/test-patterns.md` — JUnit patterns
- `.claude/skills/domain-modeling/references/anti-patterns.md` — что запрещено

### Cleanup

Team `spec-<slug>` удаляется через `TeamDelete` только после Phase 6 (Human Approval). До этого team остаётся — пользователь может попросить изменить skeleton, lead перезапускает соответствующий stage (только 3.8a если меняются сигнатуры, только 3.8b bodies если меняется бизнес-логика, или оба если переработка большая).

### Пример структуры результата (Variant Y)

Путь зависит от project layout (single-module Android / KMP shared / KMP core) — см. agent `domain-designer.md` и PROJECT-CONTEXT.md. Пример для KMP shared module:

```
shared/feature/<slug>/domain/src/commonMain/kotlin/<base_package>/domain/<slug>/
├── model/
│   ├── CallId.kt             # value object
│   └── Call.kt               # entity data class
├── state/
│   └── CallState.kt          # sealed interface + data class states
├── logic/
│   ├── MuteAction.kt         # pure functions: toggleMute, canMute
│   └── MuteError.kt          # sealed error hierarchy
├── repository/
│   └── CallRepository.kt     # interface — suspend/Flow, no implementations
└── use_case/
    ├── MuteCallUseCase.kt    # constructor injects CallRepository
    └── ObserveCallsUseCase.kt

shared/feature/<slug>/domain/src/commonTest/kotlin/<base_package>/domain/<slug>/
├── CallStateTest.kt          # pure core init { require } тесты
├── MuteActionTest.kt         # pure core Domain Test Scenarios → @Test
├── MuteCallUseCaseTest.kt    # use case tests через FakeCallRepository
└── fake/
    └── FakeCallRepository.kt # полноценная in-memory реализация
```

После успешного отчёта domain-designer переходи к Phase 4.

**Нумерация**: Phase 3.5 — Domain Contract Lock (финализация текстовой части). Phase 3.8 — Walking Skeleton (генерация кода), разбита на Stage 3.8a (signatures via domain-designer) + Stage 3.8b (bodies parallel tests via domain-designer + test-dev). Между 3.5 и 3.8 нет 3.6/3.7 потому что диалоговые подшаги State Matrix / Primary Journeys / Domain Contract внутри Phase 2 используют собственную нумерацию 3.5–3.7 (устоявшаяся в команде).

## Phase 4: Cross-Model Review (Codex)

После генерации spec запусти Codex для поиска дыр в ТЗ и проверки, что domain contract достаточно конкретен для downstream phases:

```bash
codex exec --full-auto -s read-only \
  -o /tmp/codex-spec-review.md \
  "Прочитай spec файл docs/features/<slug>/0-spec.md.

Ты — senior разработчик, который завтра будет реализовывать эту фичу. Твоя ЕДИНСТВЕННАЯ задача — найти дыры в ТЗ.

Проверь особенно:
- достаточно ли конкретен `Feature Domain Contract` для Phase 3.8 (domain-designer) генерации Walking Skeleton и phase-01 (integration)
- покрывают ли `Primary User Journeys` основной happy path, recovery path и interrupted/edge path
- нет ли пустых/двусмысленных ячеек в `State Matrix`, если она есть
- хватает ли `Domain Test Scenarios` для генерации Walking Skeleton в Phase 3.8

Для каждой дыры напиши:
- Что именно непонятно или не описано
- Почему это заблокирует реализацию
- Твоё предложение (если есть)

Если spec полный и вопросов нет — напиши 'Spec complete, вопросов нет.'

НЕ редактируй файлы. Только читай и анализируй."
```

Прочитай результат из `/tmp/codex-spec-review.md`.

### Обработка результата

- Если Codex нашёл вопросы → покажи их пользователю через `AskUserQuestion`: "Codex нашёл дыры в spec: [список]. Давай закроем их."
- Получи ответы → обнови `0-spec.md`
- Если Codex не нашёл вопросов → переходи к Phase 5
- Если `codex` недоступен (ошибка PATH/не установлен) → пропусти этот шаг и сообщи пользователю

## Phase 5: Generate or Update README

Создай `docs/features/<feature-slug>/README.md` (если не существует):

```markdown
# Feature: <Feature Name>

## Status: spec

## Documents
| Document | Status |
|----------|--------|
| `0-spec.md` | Complete |
| `1-research.md` | Pending |
```

Или обнови существующий: добавь `0-spec.md` и обнови статус.

## Phase 6: Human Approval

Покажи пользователю краткую сводку:
- Feature type
- Количество requirements
- Scope: in / out
- Ключевые user decisions
- Server-side issues (если есть)
- Search criteria для research (что будет искаться)
- Primary user journeys
- Feature domain contract status (`ready` / `N/A` / есть открытые вопросы)
- Acceptance criteria

**=WAIT for user approval. Следующий шаг: `/feature-research <slug>` — research прочитает spec и будет искать по заданным критериям, не дублируя уже зафиксированную domain logic.=**

## Quality Gates

### Gate 1: User Intent Captured
- [ ] Каждое scope decision основано на ответе пользователя (не на предположении агента)
- [ ] User decisions таблица заполнена с impact column
- [ ] НИ ОДНО scope decision не принято агентом самостоятельно

Severity: Critical

### Gate 2: Search Criteria Defined
- [ ] Search criteria конкретные (не "найди всё релевантное")
- [ ] Для каждого requirement есть соответствующий search direction
- [ ] Completeness check описан (как проверить что всё найдено)

Severity: Critical

### Gate 3: Acceptance Criteria Quality
- [ ] Каждый criterion verifiable (можно проверить в коде или тестом)
- [ ] Criteria покрывают ВСЕ functional requirements
- [ ] Нет ambiguous criteria

Severity: Critical

### Gate 4: Domain Contract Readiness
- [ ] `Primary User Journeys` заполнены ИЛИ помечены `N/A — <одно предложение обоснования>`
- [ ] `State Matrix` не содержит пустых критичных ячеек ИЛИ помечена `N/A — <обоснование>`
- [ ] `Feature Domain Contract` достаточно конкретен для phase-01 integration ИЛИ помечен `N/A`
- [ ] `Domain Test Scenarios` позволяют `domain-designer` сгенерировать Walking Skeleton в Phase 3.8 без повторной продуктовой декомпозиции

Severity: Critical

### Gate 4.5: Full Walking Skeleton Generated (Variant Y)

Применяется только если `Feature Domain Contract` ≠ N/A.

- [ ] Domain-директория содержит подпакеты `model/`, `state/`, `logic/` (pure core), `repository/` (interfaces), `use_case/` (use case classes)
- [ ] Test-директория содержит pure core tests + use case tests + подпапку `fake/` с in-memory fake implementations
- [ ] Путь соответствует project layout из PROJECT-CONTEXT.md (single-module Android → `app/src/main/`; KMP shared → `shared/feature/<slug>/domain/src/commonMain/`; etc.)
- [ ] Gradle task (jvmTest или test) — все тесты зелёные
- [ ] Нет `android.*` / `androidx.*` / SDK imports в domain файлах
- [ ] Нет DI аннотаций (`@Inject`, `@Provides`, `@Module`, `@Singleton`)
- [ ] Нет serialization аннотаций (`@Serializable`, `@Parcelize`, `@Entity`)
- [ ] `model/`, `state/`, `logic/` не содержат `suspend`, `Flow`, `throw` (pure core sync-only)
- [ ] `repository/` содержит только interfaces с domain types в signatures
- [ ] `use_case/` классы с constructor-injected repository interfaces, `operator fun invoke(...)`, thin orchestration
- [ ] Тесты pure core идут напрямую (no mocks, no fakes)
- [ ] Тесты use cases идут через fakes из `test/.../fake/`
- [ ] Каждое правило из `Feature Domain Contract` покрыто тестом
- [ ] Каждый row из `State Matrix` покрыт тестом
- [ ] Каждый `Domain Test Scenario` из `0-spec.md` → реальный `@Test`
- [ ] Каждое `Primary User Journey` → integration-style тест (pure core через `logic/` функции или use case через fake)

Severity: Critical

### Gate 5: Delegation Transparency
- [ ] Каждое решение в Requirements помечено [USER DECIDED] или [DELEGATED]
- [ ] Ни одного решения без метки
- [ ] Все [DELEGATED] имеют обоснование
- [ ] Delegated Decisions Summary заполнена
- [ ] Пользователь подтвердил сводку перед генерацией spec

Severity: Critical

## Правила

1. **=user is source of truth=** — scope decisions принимает пользователь, не агент. Используй AskUserQuestion.
2. **=no architecture=** — spec описывает ЧТО + полный Walking Skeleton domain (Variant Y). **Разрешены**: domain class names в `domain/<slug>/{model,state,logic,repository,use_case}/`, value objects, sealed interfaces, pure function signatures, repository interfaces, use case classes, in-memory fakes в тестах. **Запрещены**: repository implementation design (Room/Retrofit/Firebase), DAO names, adapter/mapper design в data layer, module structure вне domain, DI wiring, framework decisions, Android/SDK types в domain.
3. **=lock business logic early=** — если фича содержит доменную логику, она фиксируется в `Feature Domain Contract`, `Primary User Journeys`, `State Matrix`, `Domain Test Scenarios` + полный Walking Skeleton код (pure core + repositories + use cases + fakes) уже на spec-этапе.
4. **=walking skeleton is production=** — domain код, сгенерированный в Phase 3.8, НЕ throw-away. Design/plan/implement работают С этим кодом, не переписывают его и не добавляют новых use cases или repository interfaces. Переименование классов в design phase допустимо, но бизнес-правила и signatures сохраняются.
5. **=functional core + imperative boundary=** — `model/`, `state/`, `logic/` содержат только pure functions + immutable data (sync, no suspend). `repository/` — только interfaces (suspend/Flow ok). `use_case/` — thin orchestration. Тесты pure core без fakes; тесты use cases через in-memory fakes.
6. **=no deep research=** — не читай source code проекта. Используй только PROJECT-CONTEXT.md для понимания структуры. Глубокий research — следующая фаза.
7. **=search criteria are key output=** — самая важная часть spec для pipeline. Research читает их и знает ТОЧНО что искать.
8. **=trace everything=** — каждое requirement имеет основание (user answer или description).
9. **=server issues in plain language=** — опиши простыми словами что не так, что нужно сделать на сервере, почему нельзя обойти на клиенте.
10. **=downstream phases ask only delta questions=** — research/design/plan не должны заново валидировать уже зафиксированную продуктовую логику; только искать реальные расхождения, missing conditions и blockers.
11. **=no speculation=** — если неизвестно, спроси пользователя или помести в "Open Questions".
