---
description: Исследовать фичу, запустить codebase-researcher агентов и собрать factual report в docs/features/<slug>/1-research.md.
argument-hint: "<описание фичи>"
---

Исследуй фичу `$ARGUMENTS`.

## Роль

Ты диспетчер. Ты НЕ читаешь исходный код, НЕ исследуешь кодовую базу сам. Ты:
1. Читаешь спеку
2. Запускаешь агентов
3. Синтезируешь их результаты

## Шаг 0: Подготовка

1. Сгенерируй `feature-slug` в `kebab-case`.
2. Прочитай `docs/features/<slug>/0-spec.md`. Если нет — работай по описанию из аргументов.
3. Создай `docs/features/<slug>/` если не существует.

| Condition | Action |
|-----------|--------|
| `1-research.md` не существует | Полный research |
| Существует, но спека изменилась | Обнови research |
| Существует и спека та же | Delta-check, обнови README |

## Шаг 0.5: Вопросы по спеке (только delta)

После прочтения `0-spec.md` оцени: достаточно ли информации для запуска research-агентов **без повторной продуктовой декомпозиции**?

`0-spec.md` уже фиксирует product-решения, state logic, journeys и feature domain contract. Research НЕ должен заново валидировать эту логику с пользователем.

Если видишь дыры — **спроси пользователя** через `AskUserQuestion`, но только если это реальный delta:
- Неясные или противоречивые requirements, которые блокируют поиск
- Search criteria, которые слишком расплывчаты для конкретного исследования
- В `Feature Domain Contract`, `Primary User Journeys` или `State Matrix` не хватает деталей именно для codebase/back-end verification
- Research нашёл в реальном коде условия, ограничения или contract mismatch, которых нет в spec

К каждому вопросу пиши своё предположение: "Я понял это как ... — верно?" и явно объясняй, **какой delta** ты пытаешься закрыть.

После ответов пользователя — **обнови `0-spec.md`**: дополни requirements, search criteria, domain contract или scope. Помечай добавленное как `[ADDED IN RESEARCH: причина]`.

Если спека ясная — переходи дальше без вопросов.

## Шаг 0.6: Shared core / infrastructure scan (если применимо)

Если фича реально затрагивает shared infrastructure в `core/` или зависит от существующих shared contracts — для каждого релевантного пакета в `core/` создай отдельного Agent(codebase-researcher):

```
Прочитай все файлы в core/<package>/:
- README.md (если есть) — контракты, инварианты
- *Policy.kt, *Rule.kt — shared правила
- Shared types, value objects, infrastructure helpers

Извлеки: ограничения, инварианты, точки интеграции и reusable shared logic.
```

Важно:
- Не трактуй `core/` как место для feature-specific business logic
- Не запускай этот шаг только потому, что в проекте существует `core/`
- Если логика feature-specific и должна жить в feature-local `domain/*` — исследуй её через spec/research/design, а не через отдельный core flow

Если фича не затрагивает shared infrastructure/core — пропусти этот шаг.

## Шаг 0.7: Проверка инвариантов

Прочитай `docs/invariants.md` (если существует). Для каждого инварианта оцени: затрагивает ли фича owner или source этого инварианта? Если да — добавь в промт соответствующему research-агенту задание: "Проверь как текущая реализация <invariant owner> обеспечивает <invariant>, и как планируемые изменения могут это нарушить."

## Шаг 0.8: Cross-Feature Dependency Scan

Фича может вступать в связь с другими feature-модулями проекта. Эту связь **обязательно документировать на research-этапе** — иначе design/plan принимают решения не зная о существующих cross-feature contracts, и реализация добавляет coupling, которое будет блокером позже.

Этот scan делает **отдельный** `Agent(codebase-researcher)` параллельно с основными research-агентами из Шага 1. Lead НЕ читает код сам.

Промт для cross-feature scanner:

```
Feature: <slug>
Описание: <1 предложение>

Задача: полная карта cross-feature dependencies для этой фичи.

Шаги:
1. Определи feature-пакеты/модули в проекте (grep для package паттернов, ls feature-директорий)
2. Для каждого feature-пакета найди: импортирует ли он другие feature-пакеты
3. Для каждого cross-feature import задокументируй:
   - Direction: A → B или bidirectional
   - Mechanism: direct import / reflection (Class.forName) / shared interface в core / event bus / broadcast / service binder
   - File:line где происходит coupling
   - Документирован ли паттерн в docs/features/<each>/03-decisions.md (ADR)?
4. Специально проверь затрагиваемую фичу <slug>: 
   - Какие OTHER фичи она будет импортировать (прямо или через reflection) если реализована по spec?
   - Какие OTHER фичи могут импортировать её?
5. Если фича использует external SDK/library — определи какие ещё фичи используют тот же SDK (чтобы design мог унифицировать адаптеры, а не дублировать)

Формат вывода:
- Cross-feature dependency graph (markdown table)
- Bidirectional coupling (если есть) — явно помечен как risk
- Undocumented reflection calls (если есть) — явно помечены
- Shared SDK usage across features

Читай роль из .claude/agents/codebase-researcher.md
```

### Web-researcher дополнение для cross-feature SDK

Если cross-feature scanner нашёл shared SDK usage (например несколько фич используют LiveKit, Retrofit клиент, Firebase) — промт web-researcher-у дополняется:

```
Дополнительно: используй Context7 MCP (mcp__context7__resolve-library-id + mcp__context7__get-library-docs) 
для каждой shared SDK, найденной cross-feature scanner-ом. Задокументируй:
- Официальный recommended pattern интеграции (single instance vs per-feature)
- Threading model SDK
- Lifecycle ownership (кто создаёт/уничтожает)

Цель: design-фаза должна решать интеграцию на основе official docs, не по догадкам.
```

### Синтез в 1-research.md

Findings cross-feature scanner-а и web-researcher-а включаются в `1-research.md` отдельной секцией:

```markdown
## Cross-Feature Interactions

### Dependency Graph
| Feature A | → | Feature B | Mechanism | File:line | Documented in ADR? |
|-----------|---|-----------|-----------|-----------|---------------------|
| voip | → | tcp | direct import SocketService | voip/VoipCallHandler.kt:42 | NO — risk |
| tcp | → | voip | reflection | tcp/SocketService.kt:88 | YES (ADR-003) |

### Bidirectional Coupling Risks
<список пар с bidirectional связью, требующих attention в design>

### Shared SDK Across Features
| SDK | Used by | Recommended pattern (from Context7) | Current integration |
|-----|---------|-------------------------------------|---------------------|
| LiveKit | voip, calls | single Room instance per process | per-feature instance — refactor candidate |

### Undocumented Patterns (blockers для design)
<reflection calls или cross-imports без ADR — должны быть задокументированы или исправлены до plan phase>
```

### Broadcast findings команде design-фазы

После завершения research (перед `/feature-design`) lead **обязательно** делает broadcast через SendMessage всем архитекторам, которые будут работать в design-фазе:

```
Cross-feature dependency summary для <slug>:
- <slug> импортирует: <list>
- <slug> используется: <list>
- Bidirectional risks: <list>
- Shared SDK: <list>
- Undocumented patterns: <list — blockers>

Полные детали — в 1-research.md секция "Cross-Feature Interactions".
Учитывайте эти dependencies при design. Новые cross-feature связи без документирования в 03-decisions.md = blocker.
```

## Шаг 1: Запуск агентов

Из `0-spec.md` возьми **Search Criteria for Research**. Каждый criterion = 1 Agent(codebase-researcher).

Сколько criteria — столько агентов. **Без ограничения на количество.** Запускай все параллельно. Если criterion широкий (затрагивает >3 подсистем) — разбей на 2+ агентов. Модель НЕ должна самоограничиваться числом параллельных агентов.

Промт каждому агенту — короткий:

```
Feature: <slug>
Описание: <1 предложение>

Исследуй: <конкретный search criterion>

Entry points:
- <путь 1>
- <путь 2>

Что искать:
- <пункт 1>
- <пункт 2>
```

Агент сам прочитает свою роль из `.claude/agents/codebase-researcher.md` и нужные skill references.


НЕ вставляй в промт:
- Содержимое agent definition
- Содержимое skill references
- Полные тексты файлов

### Web-researcher (параллельно с codebase-researchers)

Если фича затрагивает external SDK, library или platform API — запусти `web-researcher` как отдельного Agent ПАРАЛЛЕЛЬНО с codebase-researchers:

```
Feature: <slug>
Описание: <1 предложение>

SDK/библиотеки из спеки: <список из 0-spec.md>

Задачи:
1. Найди official documentation для каждой SDK/библиотеки
2. Проверь: существуют ли поля/методы/классы, на которые ссылается спека
3. Найди known issues для используемых версий
4. Platform-specific quirks если фича затрагивает device-specific поведение

Прочитай свою роль из .claude/agents/web-researcher.md
```

| Condition | Action |
|-----------|--------|
| Spec упоминает external SDK (VideoSDK, Location, Camera, etc.) | Запустить web-researcher |
| Spec использует platform API (Notification, Foreground Service, PiP) | Запустить web-researcher |
| Spec — полностью internal (Room, ViewModel, internal logic) | Пропустить web-researcher |

Web-researcher findings включаются в synthesis (Шаг 2) и используются для верификации в grounding (Шаг 3).

## Шаг 1.5: State Matrix / Domain Contract Validation (если `0-spec.md` содержит State Matrix или Feature Domain Contract)

После получения findings от codebase-researchers — сверь реальный код с матрицей и domain contract из spec:

1. **Пропущенные условия**: Нашёл ли research условия/флаги/состояния в коде, которых НЕТ в матрице? (например: spec описывает Role=ADMIN/CLIENT, а в коде есть ещё Role=MODERATOR; spec не учитывает screen share state; код проверяет `isOffline` которого нет в матрице)
2. **Несостыковки**: Совпадает ли ожидаемое поведение в матрице с тем, что реально делает код? Если ячейка матрицы говорит "SDK call = NO", а в коде SDK вызывается — зафиксируй.
3. **Пропущенные комбинации**: Есть ли в коде ветки для комбинаций условий, которых нет в матрице? (code path который матрица не описывает)
4. **Domain contract mismatch**: Есть ли в коде, API, shared contracts или SDK ограничения, которые противоречат `Feature Domain Contract`, `Primary User Journeys` или `Domain Test Scenarios`?

Результат оформи как секцию в `1-research.md`:

```markdown
## State Matrix Validation

### Пропущенные условия (предложить пользователю добавить в матрицу)
- <условие> — найдено в <file:line>, в матрице отсутствует

### Несостыковки (матрица vs код)
- Матрица строка N: ожидает <X>, код делает <Y> — <file:line>

### Непокрытые комбинации
- <комбинация условий> — в коде есть ветка <file:line>, в матрице нет строки

### Domain Contract Mismatches
- <правило/journey/scenario из spec> — конфликтует с <код/API/shared contract> в <file:line>
```

Если нашлись пропуски/несостыковки — **спроси пользователя** через `AskUserQuestion`, но не переоткрывай весь spec. Формат вопроса: "Research нашёл delta относительно spec: [список]. Обновить `0-spec.md`?" После ответа — обнови только релевантные секции (`State Matrix`, `Primary User Journeys`, `Feature Domain Contract`, `Domain Test Scenarios`).

## Шаг 2: Синтез

После завершения всех агентов:
1. Объедини находки, разреши противоречия
2. Если есть пробелы — один дополнительный раунд агентов (максимум)
3. Определи conditional documents: нужны ли `07-events.md`, `08-storage-model.md`
4. Явно отметь, готов ли `Feature Domain Contract` к phase-01 реализации или какие delta-вопросы остались открыты

## Шаг 3: Grounding Gate (ОБЯЗАТЕЛЬНО)

После синтеза research findings — создай `docs/features/<slug>/2-grounding.md`.

**Grounding — это gate-документ.** Без него переход к design/plan ЗАПРЕЩЁН. Research отвечает "что есть в коде". Grounding отвечает "что сломается, если мы это изменим, и что реально возможно".

Для каждой проблемы/изменения из spec создай **grounding-карточку**:

```markdown
# Grounding

## Problem 1: <короткое название>

### Symptom
Что ломается / что нужно изменить — простыми словами.

### Repro (для багфиксов)
1. Шаг
2. Шаг
3. Фактический результат
4. Ожидаемый результат

### Entry Points (EXHAUSTIVE)
- Где сценарий стартует (file:line)
- Где ещё может стартовать (альтернативные пути: return/restore, notification tap, deep link, PiP)
- Кто вызывает entry point (caller chain)
- Если entry points не найдены exhaustively — пометить `[ENTRY POINTS INCOMPLETE]` и запустить доп. agent

### Code Owners
- Класс / файл, владеющий логикой (file:line)
- Кто ещё затрагивается (file:line)

### Flow Trace
- Откуда приходит событие → через что проходит → где меняется state → где принимается финальное решение
- Формат: `FileA:line → FileB:line → FileC:line`

### Backend / Contract Check
- Что поддерживает REST API (endpoints, response format)
- Что поддерживает WebSocket (channels, events, payload)
- Что поддерживает push payload
- Чего сейчас НЕТ (и нужен ли backend change)

### Constraints
- Lifecycle: какие Android lifecycle states затрагиваются
- In-memory state: что теряется при process death
- DB/Storage: schema constraints, migration needs
- Offline/Online: поведение при отсутствии сети

### Code Path Divergence (если spec требует "одинаковое поведение" для нескольких сценариев)
- Какие code paths реально используются для каждого сценария (file:line)
- Если paths разные — перечислить расхождения
- Если spec предполагает один path а реально их несколько — пометить как risk

### Fix Shape (минимально реализуемое решение)
- Client-only fix: <описание> ИЛИ
- Requires backend: <описание что нужно>
- Follow-up: <что можно отложить>

### Validation
- Ручной сценарий проверки
- Какие тесты подтвердят фикс
- Критерий success
```

**Правила grounding:**
- **Independent Verification Protocol**: Для КАЖДОГО утверждения из `1-research.md` — ОТКРОЙ исходный файл через Read tool и ПРОЧИТАЙ реальный код. НЕ копируй research findings. Если research говорит "method X resets Y" — открой method X и проверь сам. Output format для каждого claim: `[VERIFIED: прочитал <file:line>, подтверждаю: <что реально делает код>]` или `[CONTRADICTS: research говорит <X>, код показывает <Y>, file:line]`. Если ЛЮБОЙ claim помечен [CONTRADICTS] — это blocker: design не может начаться пока claim не исправлен в research.
- Если для проблемы нельзя заполнить Entry Points + Code Owners + Flow Trace — запусти дополнительного Agent(codebase-researcher) для трассировки.
- Если Backend/Contract Check показывает что backend не поддерживает нужное — явно пометь: `[REQUIRES BACKEND CHANGE]`.
- Для каждого предположения о backend (формат ответа, поддержка поля, WebSocket event) — ссылка на source: server code file:line, или API doc, или `[ASSUMPTION — NOT VERIFIED]`.
- Прочитай `docs/invariants.md` (если существует) и проверь: не нарушает ли фича существующие cross-feature инварианты. Если нарушает — добавь секцию `### Invariant Conflicts`.

## Шаг 4: Записать результат

Собери единый report в `docs/features/<slug>/1-research.md`:

```markdown
---
date: YYYY-MM-DD
researcher: Claude
commit: <git rev-parse --short HEAD>
branch: <git branch --show-current>
---

# Research: [Feature Name]

## Summary
[2-3 абзаца: что существует, что релевантно, ключевые паттерны]

## Architecture Overview
[Архитектура затронутых модулей — file:line]

## Existing Patterns
[Паттерны для переиспользования — file:line]

## Integration Points
[Внешние зависимости, API, events]

## Detailed Findings

### 1. [Component/Area]
- **Location**: `path/File.kt:line`
- **Description**: что делает
- **Dependencies**: file:line
- **Data flow**: вход -> обработка -> выход

## Conditional Documents Needed
[Какие design docs кроме 01-04 нужны и почему]

## Constraints
[Ограничения, найденные в коде]

## Open Questions
[Что требует design-решений]
```

Создай/обнови `docs/features/<slug>/README.md`:
- Feature, Request, Status: `research`, Documents: `1-research.md`, `2-grounding.md`

## Правила

- Каждое утверждение в report — с `file:line` ссылкой
- Только факты. Никаких design proposals, критики, рекомендаций
- Не путай `domain/model/` и `data/local/entity/` — разные слои
- Агент не должен получать больше 6 entry points на task
- `2-grounding.md` ОБЯЗАТЕЛЕН. Research без grounding = неполный research. Не переходи к summary/approval пока grounding не записан на диск.
