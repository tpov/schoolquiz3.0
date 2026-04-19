---
description: Провести ретроспективу пайплайна после реализации фичи — проанализировать ошибки, предложить и применить исправления в инструкциях.
argument-hint: "<feature-slug>"
---

Проведи ретроспективу пайплайна для `$ARGUMENTS`.

# Ретроспектива пайплайна — самоулучшающийся workflow

Вы — инженер по качеству пайплайна. Ваша задача — проанализировать, что пошло не так во время разработки фичи, и исправить инструкции пайплайна так, чтобы такой класс ошибок автоматически ловился в будущих задачах.

**Ключевой принцип:** Любой баг, найденный после реализации, — это сбой пайплайна, а не единичная ошибка. Исправляйте систему, а не симптом.

## Режим: Delegation

**Ведущий ретроспективы ДЕЛЕГИРУЕТ сбор evidence subagent-ам.** Задача ведущего:
1. Разбить сбор evidence на параллельные задачи (Фаза 0)
2. Создать subagent через Agent tool для чтения артефактов и инструкций
3. Исследовать root cause через WebSearch до предложения исправлений (Фаза 1.4)
4. Синтезировать findings в retrospective report

**Self-check**: Если вы собираетесь напрямую читать >5 файлов — ОСТАНОВИТЕСЬ. Сначала создайте subagent.
Subagent работают на Sonnet, стоят дешевле и дают независимый анализ.

## Фаза 0: Сбор evidence

### Режим оркестрации — ОБЯЗАТЕЛЬНО Teams API

Вы ДОЛЖНЫ использовать Teams API (Mode A) для координации всех worker:
1. `TeamCreate` с именем `"feature-<slug>-retro"`
2. Создайте teammates через `Agent` tool с параметром `team_name`: два экземпляра `codebase-researcher` (artifact-reader + instruction-reader)
3. Используйте `TaskCreate`/`TaskUpdate` для трекинга задач
4. Используйте `SendMessage`, чтобы назначить задачи на чтение
5. После завершения retrospective выполните `TeamDelete`

Все teammates используют Sonnet (`model: sonnet` во frontmatter агента).

**Fallback: Mode B — Agent tool** допустим ТОЛЬКО если `TeamCreate` возвращает ошибку:
Создавайте subagent через Agent tool с `subagent_type: "Explore"` как описано ниже.

### 0.1 Прочитайте pipeline artifacts (через subagent)

Создайте Agent (`subagent_type: "Explore"`), который прочитает и кратко опишет ВСЕ feature artifacts:

```
Прочитай и кратко опиши эти файлы для retrospective пайплайна:
1. docs/features/<feature-slug>/1-research.md
2. docs/features/<feature-slug>/01-architecture.md
3. docs/features/<feature-slug>/02-behavior.md
4. docs/features/<feature-slug>/03-decisions.md
5. docs/features/<feature-slug>/04-testing.md
6. Conditional docs (06-api-contract.md, 07-events.md, 08-storage-model.md), если они существуют
7. Все docs/features/<feature-slug>/plan/phase-NN/ (`overview.md` + role files)
8. docs/features/<feature-slug>/implementation.md, если он существует
9. docs/features/<feature-slug>/README.md

Для каждого документа сообщи:
- Какие ключевые утверждения и предположения в нем сделаны
- Какие acceptance criteria определены
- Что было явно выведено ЗА scope
- Какие caveat или open question зафиксированы
```

### 0.2 Прочитайте pipeline instructions (через subagent, параллельно с 0.1)

Создайте ВТОРОЙ Agent (`subagent_type: "Explore"`) параллельно для чтения pipeline instructions:

```
Прочитай и кратко опиши ВСЕ файлы с pipeline instructions для retrospective:
1. CLAUDE.md — правила работы, правила коммитов
2. .claude/PROJECT-CONTEXT.md — ограничения проекта
3. Все файлы в .claude/commands/ — правила делегирования, quality gates, структура фаз
4. Все files в `.claude/rules/` и `.claude/skills/adversarial-review/` — project standards и review protocol
5. Все файлы в .claude/agents/ — роли агентов и их возможности

Для каждого файла сообщи:
- Требования к делегированию (кто должен создавать кого)
- Quality gates и механизмы их enforcement
- Правила, ограничивающие реализацию
- Любые gaps: что НЕ покрыто инструкциями
```

Ведущий читает напрямую только `CLAUDE.md` и `.claude/PROJECT-CONTEXT.md` (для контекста). Все остальное делегируется.

### 0.3 Соберите feedback после реализации

Если feedback передан в аргументах команды, используйте его напрямую.

Иначе попросите пользователя (через `AskUserQuestion`) предоставить:

```
Пожалуйста, пришлите что-то из следующего:
1. Описания багов — что сломалось после реализации?
2. Error log или stack trace
3. Feedback от пользователя/QA о сломанном поведении
4. Ваши наблюдения о том, что пайплайн пропустил
5. Любые fix commit или hotfix plan, которые уже подготовлены

Вставьте все релевантное — я проанализирую это целиком.
```

**=WAIT for user response. Do NOT proceed without evidence.=**

## Фаза 1: Анализ root cause

Для каждого сообщенного бага/инцидента двигайтесь назад по этапам пайплайна:

### 1.1 Аудит по стадиям

Для каждого бага заполните таблицу:

| Stage | File(s) Examined | Verdict | Первопричина на этой стадии |
|-------|-----------------|---------|--------------------------|
| Research | `1-research.md` | CORRECT / INCOMPLETE / WRONG | Был ли найден релевантный факт? |
| Design | docs `01-04` | CORRECT / INCOMPLETE / WRONG | Смоделировал ли дизайн реальность корректно? |
| Plan | `plan/phase-NN/overview.md` + role files | CORRECT / INCOMPLETE / WRONG | Реализует ли план дизайн без потерь? |
| Implement | actual code | CORRECT / INCOMPLETE / WRONG | Совпадает ли код с планом? |
| Review | verdict из `implementation.md` | CAUGHT / MISSED | Нашли ли ревьюеры эту проблему? |

### 1.2 Определите самую раннюю точку отказа

Для каждого бага определите:

- **Injection point**: на какой стадии ошибка была ВНЕСЕНА? (например, дизайн предположил симметрию, которой нет)
- **Propagation path**: какие стадии ПРОНЕСЛИ ошибку дальше, не поймав ее?
- **Detection gap**: какой review/gate ДОЛЖЕН был это поймать, но не поймал?

### 1.3 Классифицируйте паттерн отказа

Отнесите каждую первопричину к одному из паттернов:

| Pattern | Description | Example |
|---------|-------------|---------|
| **Modeling Error** | Модель дизайна не совпадает с реальным поведением кода | Симметричная dedup-логика для асимметричных transport |
| **Missing Side-Effect Inventory** | Дизайн блокирует путь, не перечислив, что теряется | WS заблокирован → потерялась запись в БД |
| **Commit-Before-Action** | Состояние фиксируется до операции, которая может упасть | register() до showNotification() |
| **Assumption Not Verified** | Неявное предположение никогда не было проверено по коду | "replay=0 значит события всегда доставляются" |
| **Test Validates Wrong Spec** | Тесты проходят, но подтверждают неверный дизайн | Тесты подтверждают, что dedup работает, но сама dedup-логика ошибочна |
| **Incomplete Research** | Релевантный код/поведение не были найдены | Пропущен side-effect у code path |
| **Review Blind Spot** | Все ревьюеры разделяют одну и ту же модель → один и тот же blind spot | Ревьюер на той же модели не ловит собственную design-ошибку |
| **Plan Faithfulness** | План безошибочно реализует плохой дизайн | Верный перевод неверной спецификации |
| **Integration Gap** | Компоненты работают по отдельности, но ломаются вместе | Два transport работают порознь, но при совместной работе возникает race condition |
| **Lifecycle Mismatch** | Код предполагает неверное состояние Android lifecycle | Activity в STOPPED, когда событие эмитится с replay=0 |
| **Lead Role Violation** | Lead выполняет работу сам вместо делегирования агентам | Lead читает source-файлы и пишет код напрямую, не создавая subagent. Причина: модель по умолчанию стремится решать задачу сама; текстовые инструкции это не перебивают; нужна детерминированная enforcement-механика (Delegate Mode, hooks) |
| **Delegated Decision Error** | Агент принял решение за пользователя (пользователь сказал "реши сам"), решение оказалось неверным | Агент выбрал формат хранения данных, который не подходит под реальные объёмы. Проверяй Delegated Decisions Summary в `0-spec.md` |

### 1.4 Исследуйте root cause (WebSearch)

Прежде чем предлагать исправления, используйте `WebSearch`, чтобы понять, ПОЧЕМУ возникает каждый паттерн отказа и что рекомендует индустрия:

**Для process-failure** (делегирование не соблюдено, review пропущен):
- Search: "LLM agent delegation problem model ignores instructions"
- Search: "claude code subagent delegation best practices"
- Search: "multi-agent orchestration failure patterns"

**Для technical-failure** (неверные предположения, пропущенная интеграция):
- Ищите по конкретной технологии: например, `laravel reverb pusher-java-client origin header websocket`
- Ищите по паттерну отказа: например, `protocol compatible drop-in replacement pitfalls`

**Для pipeline/methodology-failure**:
- Search: "software development pipeline retrospective automation"
- Search: "AI code review blind spots same model"

Интегрируйте findings из web search в:
1. Анализ root cause (ПОЧЕМУ этот паттерн отказа возникает системно)
2. Предложения по исправлению (что реально работает, а не просто хорошо звучит)
3. Lessons learned (знания индустрии, выходящие за рамки этого проекта)

**Ключевой вывод из research**: Одних текстовых инструкций недостаточно, чтобы принудить LLM к поведению.
Модель по умолчанию стремится сделать работу сама. Эффективные исправления используют **детерминированное enforcement**
(Delegate Mode, hooks, tool restrictions), а не дополнительные абзацы инструкций.

## Фаза 2: Генерация retrospective report

Создайте файл `docs/features/<feature-slug>/retrospective.md`:

```markdown
# Pipeline Retrospective: <feature-slug>

## Date
<current date>

## Summary
<2-3 предложения: что пошло не так и почему пайплайн это не поймал>

## Bugs Analyzed

### Bug #N: <title>
- **Symptom**: <что увидел пользователь/QA>
- **Root cause**: <техническое объяснение>
- **Injection point**: <stage name> — <specific file:section>
- **Propagation**: <какие стадии протащили ошибку дальше, не поймав ее>
- **Detection gap**: <какой gate должен был поймать это>
- **Failure pattern**: <имя паттерна из классификации>

## Stage Performance

| Stage | Grade | Notes |
|-------|-------|-------|
| Research | A/B/C/D/F | <что было сделано хорошо, что пропущено> |
| Design | A/B/C/D/F | <что было сделано хорошо, что пропущено> |
| Plan | A/B/C/D/F | <что было сделано хорошо, что пропущено> |
| Реализация | A/B/C/D/F | <что было сделано хорошо, что пропущено> |
| Review | A/B/C/D/F | <что было сделано хорошо, что пропущено> |

## Pipeline Fixes Required

### Fix #N: <title>
- **Target file**: <.claude/commands/X.md or .claude/skills/<skill>/SKILL.md or .claude/skills/<skill>/references/X.md or .claude/agents/X.md>
- **What to add/change**: <конкретное изменение инструкции>
- **Why**: <какой failure pattern это предотвращает>
- **Prevents recurrence of**: Bug #N

## Lessons Learned
<ключевые выводы, применимые шире этой конкретной фичи>
```

## Фаза 2.5: Research best practices (WebSearch)

Прежде чем предлагать исправления, изучите industry best practices по найденным паттернам отказа.

### Обязательные запросы (Sonnet subagent):

1. **Instruction design**: `CLAUDE.md best practices reusable rules vs task-specific fixes`
2. **Enforcement mechanism**: `claude code hooks vs rules deterministic enforcement`
3. **По каждому паттерну отказа**: `LLM agent <failure-pattern-name> prevention best practices`

### Framework выбора инструмента по данным research:

| Failure type | Instrument | Why |
|-------------|-----------|-----|
| Должно выполняться каждый раз (compile, lint) | Hook (PostToolUse/Stop) | 100% enforcement |
| Project-specific knowledge (build chain) | PROJECT-CONTEXT.md | Загружается в каждой сессии |
| Универсальное правило принятия решений | CLAUDE.md working rule | Коротко, <20 правил всего |
| Domain-specific pattern | skill references in `.claude/skills/*/references/*.md` | Агенты читают по необходимости |
| Сложный review (model-vs-reality) | Codex через CLI в команде | Динамический prompt, context-specific |

Интегрируйте findings в предложения исправлений из Фазы 3. Для каждого предлагаемого исправления нужно явно указать,
какой instrument выбран и ПОЧЕМУ (со ссылкой на research).

## Фаза 3: Предложение исправлений в instructions

Для каждого найденного failure pattern предложите конкретное исправление pipeline instructions. Исправления ДОЛЖНЫ быть:

1. **Specific** — точный файл, точная секция, точный текст для добавления/изменения
2. **Automated** — исправление становится частью пайплайна, а не пунктом ручного чек-листа
3. **Scoped** — без переусложнения; исправляйте класс бага, а не все гипотетические баги
4. **Non-breaking** — не удаляйте существующие рабочие проверки; только дополняйте их

### Категории исправлений

| Category | Target Files | Examples |
|----------|-------------|---------|
| **Research gaps** | `feature-research.md`, `codebase-researcher.md` | Добавить обязательный research-вопрос о side-effect каждого code path |
| **Design modeling errors** | `feature-design.md`, `design-architect.md`, `.claude/skills/adversarial-review/*` | Добавить Reality Check gate, Realist lens |
| **Review blind spots** | `.claude/skills/adversarial-review/*`, reviewer agents | Добавить cross-model execution, новый lens |
| **Plan gaps** | `feature-plan.md`, `planner.md` | Добавить шаг валидации design-assumption |
| **Testing gaps** | `.claude/rules/testing.md`, агент `test-dev.md` | Добавить категорию теста для пропущенного сценария |
| **Agent definition gaps** | `.claude/agents/*.md` | Добавить инструкцию в role definition агента |
| **Skill reference gaps** | `.claude/skills/*/references/*.md` | Добавить паттерн/конвенцию в reference doc |
| **Delegation enforcement** | `CLAUDE.md`, `.claude/commands/*.md` | Рекомендовать Delegate Mode, hooks, упрощенные prompt. Не добавляйте больше текстовых инструкций — модель умеет их рационализировать в обход (context paradox) |

### Представление исправлений пользователю

Для каждого предлагаемого исправления покажите:

```
## Proposed Fix #N: <title>

**File**: `.claude/<path>`
**Section**: <section name or line range>

**Current instruction** (if modifying):
> <quote existing text>

**Proposed change**:
> <new text>

**Rationale**: Prevents <failure pattern> by <mechanism>.
```

**=WAIT for user approval before applying any fixes.=**

## Фаза 4: Применение одобренных исправлений

После одобрения пользователя (всех или части исправлений):

1. Примените каждое одобренное исправление через Edit tool
2. После всех правок проверьте согласованность:
   - Нет дублирующихся инструкций между файлами
   - Нет противоречий с существующими инструкциями
   - Перекрестные ссылки между файлами корректны
3. Обновите retrospective report, добавив статус `Applied` для каждого исправления

### Чек-лист верификации

- [ ] Каждое исправление нацелено на конкретный файл и секцию
- [ ] Ни одна существующая рабочая инструкция не удалена
- [ ] Новые инструкции согласованы с методологией пайплайна
- [ ] Перекрестные ссылки между commands/skills/agents корректны
- [ ] Retrospective report завершен в `docs/features/<slug>/retrospective.md`

## Фаза 4.5: Обновление cross-feature инвариантов

Если ретроспектива выявила нарушение архитектурного инварианта (source-of-truth, data ownership, protocol contract), которое:
- Затрагивает БОЛЕЕ одной фичи
- Будет актуально для БУДУЩИХ фич

→ Добавьте/обновите запись в `docs/invariants.md`:

```markdown
## <Название инварианта>

- **Invariant**: <что ДОЛЖНО быть true всегда>
- **Source/Trigger/Constraint**: <как это работает>
- **Owner**: <кто владеет логикой (file.kt)>
- **Added**: <дата>, из retrospective <feature-slug> (Bug #N).
```

Если инвариант уже существует и был нарушен — обновите его с пометкой: `**Updated**: <дата>, нарушен в <feature-slug>, уточнение: <что добавлено>`.

## Фаза 5: Обновление lessons learned

Добавьте ключевые lessons в `docs/features/lessons-learned.md` (создайте файл, если его нет). Этот файл — архив для человека, лиды pipeline его НЕ читают.

Формат одной записи:
```markdown
### <date> — <feature-slug>: <one-line lesson>
- **Pattern**: <имя failure pattern>
- **Lesson**: <что future research/design должны проверять>
- **Example**: <конкретный пример из этой фичи>
```

Добавляйте только **обобщаемые** lessons — не детали конкретной фичи, а паттерны, которые могут повториться.

## Фаза 6: Обновление README

Обновите `docs/features/<feature-slug>/README.md`:

- Добавьте `retrospective.md` в список документов
- Добавьте retrospective status и дату
- Отметьте примененные pipeline fixes

## Quality Gates

### Gate 1: Полнота evidence

- [ ] Прочитаны все pipeline artifacts (research, design, plan, implementation)
- [ ] Прочитаны все pipeline instructions (commands, skills, agents)
- [ ] Собран feedback/bugs от пользователя
- [ ] Каждый баг протрассирован через все 5 стадий

Severity: `Critical`

### Gate 2: Глубина root cause

- [ ] Для каждого бага найдена injection point, а не только симптом
- [ ] Каждый баг отнесен к failure pattern
- [ ] Задокументирован propagation path (почему поздние стадии его не поймали)
- [ ] Выявлен detection gap (какой gate должен был сработать)

Severity: `Critical`

### Gate 3: Качество исправлений

- [ ] Каждое исправление нацелено на конкретный `file:section`
- [ ] Каждое исправление можно автоматизировать (это часть пайплайна, а не ручной пункт)
- [ ] Каждое исправление предотвращает класс бага, а не только текущий инцидент
- [ ] Исправления не противоречат существующим инструкциям
- [ ] До применения получено одобрение пользователя

Severity: `Critical`

## Правила

1. **=evidence first=** — никогда не предлагайте исправления без трассировки root cause через все стадии
2. **=fix the system=** — каждое исправление должно менять файл pipeline instructions, а не просто описывать проблему
3. **=one fix per pattern=** — не добавляйте 5 перекрывающихся проверок на один и тот же failure pattern
4. **=preserve working parts=** — явно отмечайте, что каждая стадия сделала ХОРОШО, а не только ошибки
5. **=user approval=** — ждите одобрения перед редактированием любого instruction file
6. **=no speculation=** — анализируйте только баги, реально сообщенные пользователем, не придумывайте гипотетические проблемы
7. **=concrete references=** — каждое finding должно ссылаться на точный `file:section` как в artifacts, так и в instructions
8. **=non-breaking changes=** — новые gates/check добавляются поверх существующих; не перестраивайте рабочие фазы пайплайна
9. **=scope to class=** — исправляйте failure pattern достаточно широко, чтобы ловить похожие баги, но без чрезмерного обобщения
10. **=retrospective report=** — всегда создавайте `retrospective.md`, даже если изменения в instructions не потребовались
