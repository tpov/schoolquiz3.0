---
description: Спроектировать фичу из research report и создать нумерованные design документы в docs/features/<slug>/.
argument-hint: "<feature-slug>"
---

Сделай design для `$ARGUMENTS`.

## Роль

Ты диспетчер. Ты НЕ пишешь design docs сам. Ты:
1. Читаешь research
2. Создаёшь Teams с двумя архитекторами
3. Они спорят и создают design
4. Codex CLI проверяет результат
5. Ты (judge) сводишь и показываешь пользователю

## Шаг 0: Подготовка

Прочитай:
1. `docs/features/<slug>/0-spec.md` (если есть)
2. `docs/features/<slug>/1-research.md`
3. `docs/features/<slug>/2-grounding.md` **(ОБЯЗАТЕЛЬНО)**
4. `docs/invariants.md` (если существует — проверь cross-feature инварианты)
5. Существующие design docs в `docs/features/<slug>/` (если есть)

### Grounding Gate Check

**Если `2-grounding.md` НЕ существует — СТОП.** Сообщи пользователю:

```
Grounding document не найден: docs/features/<slug>/2-grounding.md
Это обязательный gate-документ. Запустите /feature-research <slug> — он создаст grounding.
Design без grounding приводит к планам, не привязанным к реальному коду и backend.
```

**Если `2-grounding.md` существует, но неполный** (нет Entry Points, Code Owners или Backend/Contract Check для хотя бы одной проблемы) — сообщи пользователю о пробелах и спроси: дополнить через research agent или продолжить с пометкой `[GROUNDING INCOMPLETE]`.

Если `0-spec.md` содержит `Feature Domain Contract`, `Primary User Journeys`, `State Matrix` или `Domain Test Scenarios` — считай их зафиксированным product/domain input:
- design не переоткрывает эти решения и не делает повторную продуктовую декомпозицию
- архитекторы используют их как source of truth для структуры, поведения и test strategy
- к пользователю можно эскалировать только реальный delta: spec ambiguity, backend/shared contract blocker, missing condition из grounding/research или доказанное противоречие реальному коду

Определи conditional documents:

| Condition | Document |
|-----------|----------|
| WebSocket/realtime events | `07-events.md` |
| Room entities, DAOs, migrations | `08-storage-model.md` |

## Шаг 1: Teams Debate

Создай команду через TeamCreate: `"feature-<slug>-design"`

Подними teammates:

**architect-high-level:**
```
Feature: <slug>
Описание: <1-2 предложения>

Прочитай:
- docs/features/<slug>/1-research.md
- docs/features/<slug>/05-prior-art.md (если есть)
- .claude/PROJECT-CONTEXT.md

Твоя зона: C4 L1-L2, модульные границы, DFD, architectural decisions.
Создай свою часть design docs в docs/features/<slug>/.
Оспаривай решения architect-component, если они нарушают границы модулей.
Если нужна информация о SDK/library — запроси у web-researcher через SendMessage.
```

**architect-component:**
```
Feature: <slug>
Описание: <1-2 предложения>

Прочитай:
- docs/features/<slug>/1-research.md
- docs/features/<slug>/05-prior-art.md (если есть)
- .claude/PROJECT-CONTEXT.md

Твоя зона: C4 L3, классы, интерфейсы, DI, Room, sequences, test strategy.
Создай свою часть design docs в docs/features/<slug>/.
Оспаривай решения architect-high-level, если они нереализуемы на уровне компонентов.
Если нужна информация о SDK/library — запроси у web-researcher через SendMessage.
```

**web-researcher (параллельно с архитекторами, conditional):**

| Condition | Action |
|-----------|--------|
| Фича использует external SDK или platform API | Запустить web-researcher |
| Фича полностью internal (Room, Decompose Component, internal logic) | Пропустить |

```
Feature: <slug>
Описание: <1-2 предложения>

SDK/библиотеки из research: <список из 1-research.md>

Работай ПАРАЛЛЕЛЬНО с архитекторами:
1. Найди official docs, best practices, reference implementations для каждой SDK/library
2. Запиши результат в docs/features/<slug>/05-prior-art.md
3. Когда архитекторы спрашивают о SDK/library через SendMessage — ответь с source ссылкой
4. Если нашёл known issue или deprecation, влияющую на design — отправь finding обоим архитекторам через SendMessage

Прочитай свою роль из .claude/agents/web-researcher.md
```

Агенты сами читают свои роли и project rules. Если роли нужен project skill — агент должен вызвать его явно. НЕ вставляй agent definitions в prompt и не рассчитывай на preloaded skills у teammates.

Обязательные docs (между двумя архитекторами):
- `01-architecture.md` — high-level: L1-L2, component: L3
- `02-behavior.md` — high-level: DFD, component: sequences. **Если `0-spec.md` содержит State Matrix — архитектор ОБЯЗАН расширить её в `02-behavior.md`**: добавить edge cases, маппинг на code locations (`file:line`), и пометить каждую ячейку как testable. Матрица из spec = source of truth, архитектор дополняет, не противоречит. **Если в spec есть `Feature Domain Contract` или `Primary User Journeys` — `02-behavior.md` должно явно трассировать их к runtime-поведению и code paths, не изобретая заново business rules.**
- `03-decisions.md` — оба на своём уровне
- `04-testing.md` — component. **Если есть State Matrix — каждая ячейка = минимум 1 test case в test strategy.** **Если есть `Feature Domain Contract` — test strategy должна отдельно показать покрытие `Domain Test Scenarios` и `Primary User Journeys`.**
- `06-api-contract.md` — high-level (или "Not applicable")

Условные:
- `07-events.md` — high-level
- `08-storage-model.md` — component

После завершения debate — запиши файлы на диск. TeamDelete: `"feature-<slug>-design"`.

## Шаг 3: Reality Check (Codex CLI — последовательно)

Используй skill `adversarial-review`. Прочитай `.claude/skills/adversarial-review/SKILL.md`, а при необходимости exact protocol — `references/cli-protocol.md`.

Codex вызывается **последовательно после каждого крупного документа**, а не один раз в конце:

1. После `01-architecture.md` + `02-behavior.md` → Codex Realist lens: "модель дизайна совпадает с реальным кодом?"
2. После `03-decisions.md` (ADRs) → Codex Skeptic lens: "решения обоснованы? альтернативы рассмотрены?"
3. После `04-testing.md` + conditional docs → Codex Architect lens: "test strategy покрывает все AC? contracts согласованы?"

Каждый вызов — в отдельный файл (`-o`). Если blocker на шаге N — вернуть findings архитекторам, исправить, повторить шаг N перед переходом к N+1.

## Шаг 4: Lead Judge

Сведи результаты debate и Codex review:
- Разреши конфликты между архитекторами
- Прими решения по contested findings
- Убедись в согласованности всех docs между собой

## Шаг 5: Human Approval

Покажи пользователю:

```
## Design Review: [Feature Name]

### Summary
[2-3 предложения]

### Key Decisions
- [решение #1]
- [решение #2]

### Documents
- [ ] 01-architecture.md
- [ ] 02-behavior.md
- [ ] 03-decisions.md
- [ ] 04-testing.md
- [ ] 06-api-contract.md
- [ ] Conditional docs
```

**=WAIT for user approval. Не переходи дальше без явного OK.=**

Обнови `docs/features/<slug>/README.md`: Status: `designed`, ссылки на все docs.

## Quality Gates

### Gate 1: Полнота
- [ ] Все обязательные docs (01-04, 06) созданы
- [ ] Conditional docs созданы если нужны
- [ ] Mermaid diagrams присутствуют

### Gate 2: Architecture Alignment
- [ ] DI pattern совпадает с PROJECT-CONTEXT.md
- [ ] Dependency direction корректна
- [ ] Отклонения зафиксированы в 03-decisions.md

### Gate 3: Reality Check
- [ ] Codex/Claude CLI review запущен
- [ ] Нет blocker findings

### Gate 4: Human Approval
- [ ] Пользователь одобрил design

## Document Responsibility Matrix (Single Source of Truth)

Каждый domain type / interface / data class имеет **ОДИН canonical source** в design docs. Остальные документы — ссылаются, не дублируют. Нарушение = drift через 1-2 фазы реализации.

| Документ | Responsibility | Signatures? | Как упоминает типы |
|----------|---------------|-------------|---------------------|
| `01-architecture.md` | C4 L1-L3 диаграммы, модульные границы, container boundaries | НЕТ | Только имена классов в диаграммах ("Navigator") + role-описание ("routes destinations to ChildStack") |
| `02-behavior.md` | Sequence diagrams, DFDs, state machines | НЕТ | Имена + method calls в sequence (`navigator.goTo(Destination)`) — без full signature |
| `03-decisions.md` | ADRs — architectural choices + alternatives | НЕТ | Ссылки на типы + rationale почему такой интерфейс |
| `04-testing.md` | Test strategy, coverage mapping, fake blueprints | НЕТ (test scenarios) | Имена типов + `given/when/then` сценарии |
| `06-api-contract.md` | **CANONICAL signatures** — единственный источник правды | **ДА (authoritative)** | Полные сигнатуры interfaces / data classes / public APIs |
| `07-events.md` (conditional) | WebSocket/realtime event payloads | ДА (event shapes only) | Canonical для events |
| `08-storage-model.md` (conditional) | Room entities + migrations | ДА (entity shapes only) | Canonical для persistence |

### Правила enforcement

1. **Каждый публичный тип** (interface / data class / sealed class / use case / repository / domain model) имеет ровно ОДИН canonical record — в `06-api-contract.md` (или в `07-events.md` / `08-storage-model.md` если боковая зона)
2. **Plan-файлы** (`plan/phase-NN/*.md`) НЕ содержат полных сигнатур — только ссылку: `Canonical reference: 06-api-contract.md:NN`
3. **Architecture/behavior docs** НЕ дублируют signature — используют имя типа + role-описание
4. **Internal types** (convention plugin, helper, application class, не экспортируемые между модулями) не обязаны быть в `06-api-contract.md` — планировщик описывает их inline в Signature Card (см. `.claude/agents/planner.md`)

### Review check

```bash
# Найти все типы, описанные в 06-api-contract.md
grep -oE '\b(class|interface|data class|sealed class|object)\s+\w+' docs/features/<slug>/06-api-contract.md | sort -u

# Для каждого типа проверить, что другие design docs НЕ дублируют полную сигнатуру
# (допустимо: упоминание имени, описание роли; недопустимо: полный interface/class body)
grep -rnE '^\s*(interface|class|data class|sealed class)\s+<TypeName>\s*[({:]' docs/features/<slug>/01-architecture.md docs/features/<slug>/02-behavior.md docs/features/<slug>/plan/
```

Любой match в 01/02/plan = blocker. Исправить: replace полной сигнатуры на "см. `06-api-contract.md:NN`".

**Обоснование:** DRY principle (Hunt & Thomas, "Pragmatic Programmer" 1999) для документов: "Every piece of knowledge must have a single, unambiguous, authoritative representation within a system". Если сигнатура дублируется в 4 документах — при изменении необходимо синхронизировать 4 места, неизбежен drift.

## Правила

- Разделяй структуру (01), поведение (02), решения (03), тесты (04). Не складывай в один файл.
- Каждая ссылка на код — с точным file:line.
- Design = только документы, никакого production-кода.
- Используй naming из реальной кодовой базы, не generic patterns.
- **Single Source of Truth для типов**: canonical signatures только в `06-api-contract.md` (или conditional `07-events.md`/`08-storage-model.md`). Другие docs ссылаются, не дублируют. См. Document Responsibility Matrix выше.
- **Spec Ambiguity Gate**: Если два AC в `0-spec.md`, пункт `Feature Domain Contract`, `Primary User Journeys` или `State Matrix` допускают двойное толкование или противоречат друг другу — architect ДОЛЖЕН STOP и добавить вопрос в `### Open Questions` дизайна с пометкой `[SPEC AMBIGUITY — BLOCKS DESIGN]`. Lead эскалирует пользователю. Architect НЕ разрешает ambiguity молча.
- **Delta-only questions**: architect не валидирует заново уже зафиксированную product/domain логику. Вопросы пользователю допустимы только как delta относительно spec или grounding.
