# Pipeline Retrospective: app-shell-menu

## Date
2026-04-19

## Summary

Фича app-shell-menu полностью реализована (all 7 phases PASS, quality-scorecard A-), но методологический feedback пользователя (feedback.txt) вскрыл 3 системных сбоя пайплайна:

1. **Plan files содержат готовый код вместо ТЗ** — plan/phase-01/backend.md = 783 строки, из них ~72% Kotlin code blocks. Прямое нарушение `feature-plan.md:188` ("Только phase files, никакого кода")
2. **Document Duplication** — один интерфейс (Navigator) описан 5 раз: 0-spec.md → 01-architecture.md → 06-api-contract.md → plan/phase-01/backend.md → domain/Navigator.kt
3. **No Options Pattern** — plan содержит ровно один вариант реализации; нет trade-off таблиц, нет "несколько вариантов → review приоритизирует", как просит пользователь

Quality-scorecard показал A- (0 blockers, 0 high), но цена этого результата — дублирование информации и методологическое размывание ролей документов. Same-model reviewers и Codex CLI review не поймали ни один из этих паттернов, т.к. проверяли content/correctness, а не pipeline faithfulness.

---

## Bugs Analyzed

### Bug #1: Plan содержит готовый Kotlin код вместо ТЗ

- **Symptom**: plan/phase-01/backend.md = 783 строки, 22 `\`\`\`kotlin` блока с полными классами (package imports, class bodies, all methods). Примеры: `AndroidComposeLibraryConventionPlugin` (строки 20–50), `AndroidComposeApplicationConventionPlugin` (54–79). В plan/phase-04/frontend.md — 7 готовых Kotlin блоков с полным `DefaultRootComponent`. Общий объём plan: 2713 строк за 7 фаз
- **Root cause**: `planner` LLM-агент видит design docs (06-api-contract.md уже содержит сигнатуры), копирует их и расширяет до полных классов. Инструкция `planner.md:39` говорит "сигнатуры", но это abstract и легко интерпретируется как "полный готовый класс". Правило `feature-plan.md:188` ("Только phase files, никакого кода") legal, но находится в самом конце списка "Правила" и не имеет enforcement
- **Injection point**: Plan stage — `planner` агент (.claude/agents/planner.md)
- **Propagation**: Codex CLI plan review (3 rounds: REJECT → CONTESTED → PASS) проверял sequencing, dependencies, validation commands — но не проверял "есть ли код в plan файлах". `feature-implement.md` никак не реагирует на готовый код в plan — backend-dev и frontend-dev просто копируют
- **Detection gap**: (1) нет grep-check на `\`\`\`kotlin` в plan files при save, (2) нет review lens "Plan as ТЗ", (3) нет примера корректно оформленной задачи в planner.md без кода
- **Failure pattern**: **Plan Faithfulness** (план выполняет design корректно, но не в роли task description) + **Model Default Behavior** (LLM склонен копировать готовое) + **Missing Deterministic Enforcement** (текстовое правило игнорируется агентом)

### Bug #2: Document Duplication — Navigator описан 5 раз

- **Symptom**: один интерфейс Navigator упоминается в 5 разных документах:
  - `0-spec.md:23–40` — описание "interface with goTo(Destination)"
  - `01-architecture.md:110–140` — Component diagram + rationale
  - `06-api-contract.md:40–50` — сигнатура + responsibility
  - `plan/phase-01/backend.md:220–235` — готовый Kotlin файл
  - `shared/feature/app-shell/domain/navigation/Navigator.kt` — реальный код
- **Root cause**: (1) нет правила "single source of truth для типа/интерфейса", (2) 01-architecture.md и 06-api-contract.md имеют пересекающиеся responsibility — оба описывают интерфейсы, но с разной detail, (3) `feature-design.md` не фиксирует, какой документ — canonical для типа, какой — reference
- **Injection point**: Design stage — нечёткий контракт между `01-architecture.md`, `02-behavior.md`, `06-api-contract.md` (вся система может иметь дубли)
- **Propagation**: plan копирует из api-contract, implementation копирует из plan. Если сигнатура меняется — надо менять в 4 местах, контент может разойтись
- **Detection gap**: нет grep-проверки "название типа встречается > 2 раз в docs/features/<slug>/" (1 в canonical doc + 1 в code)
- **Failure pattern**: **Document Duplication** (новый паттерн, не зафиксирован в классификации ретроспективы) — violates DRY для документации (Hunt & Thomas: "Every piece of knowledge must have a single, unambiguous, authoritative representation within a system")

### Bug #3: Plan не содержит альтернатив для приоритизации через review

- **Symptom**: plan содержит ровно ОДИН вариант реализации. Нет trade-off таблиц, нет Option A/B/C comparison, нет "rationale" секций с отвергнутыми альтернативами. User отмечает: "несколько вариантов, а потом с каждым ревью он убирал или приоритизировал пункты — что-бы можно было легко переписывать если проблема реально проявится"
- **Root cause**: `feature-plan.md` описывает plan как "разбиение design на phases" — чисто секвенцирование. `planner.md` не содержит концепции "options pattern" — каждая фаза получает одно решение. ADR-подобные документы (`03-decisions.md`) существуют, но работают на уровне design decisions (больших), не phase-level tactical choices
- **Injection point**: **Methodology gap** — инструкции не требуют альтернатив в plan, не дают формат
- **Propagation**: все существующие фичи в проекте не имеют options в plan
- **Detection gap**: никакой — это не bug, это **missing feature** в методологии пайплайна
- **Failure pattern**: **Methodology Gap** — industry best practice (ADR "Alternatives Considered" секция, Nygard 2011) не применена к phase-level planning

---

## Stage Performance

| Stage | Grade | Notes |
|-------|-------|-------|
| Research | A | 1-research.md всеобъемлющий: 7 параллельных агентов, 9 search criteria, 7 open questions. 2-grounding.md покрывает все problems |
| Design | B | 01-architecture.md (C4 L1-L3 + Mermaid) и 06-api-contract.md (canonical signatures) дублируют друг друга по responsibility. 03-decisions.md — отличный ADR trail. 08-storage-model.md полезный. Но boundary между architecture и api-contract размыт |
| Plan | C | Структура (overview + role files), Traceability, Pattern Invariants — хорошие. Но plan файлы содержат 22+ `\`\`\`kotlin` блока на фазу — нарушение `feature-plan.md:188`. Codex CLI plan review (3 rounds) не поймал методологическое нарушение |
| Implement | A | Все 7 фаз PASS. Walking Skeleton integration выполнен корректно (adapter-only, без переписывания domain). Autonomous fix loop работал — 3 round Codex поймали integration bugs (Firebase wiring, InstanceKeeper) |
| Review | B | Codex CLI эффективен для correctness (поймал Firebase runtime gap, InstanceKeeper leak, firestore.rules wrong field names). НО: не поймал методологические сбои (Plan = код, Document Duplication). Review lens оптимизирован под content, не под pipeline faithfulness |

---

## Pipeline Fixes Required

### Fix #1: Hook на save — блокировать save plan files с `\`\`\`kotlin` блоками

- **Target file**: `.claude/settings.json` hooks + `.claude/commands/feature-plan.md`
- **What to add**: PostToolUse hook (matcher: Write, path glob `docs/features/*/plan/**/*.md`) — grep -qE '^\s*\`\`\`kotlin' → warning или exit 2 для блока
- **Why**: Research (Pixelmojo 2026): "Hooks inject deterministic processing — execute the same way every time. Unlike prompt instructions which Claude may interpret flexibly." Правило `feature-plan.md:188` уже существует, но не enforced — text rules LLM рационализирует
- **Prevents recurrence of**: Bug #1

### Fix #2: Signature Card формат вместо полного кода в plan

- **Target file**: `.claude/agents/planner.md` (добавить в секцию "Формат вывода" + дать concrete example)
- **What to add**: Явный шаблон "Signature Card" в planner.md — обязательный формат для каждого New File в backend.md / frontend.md:
  ```markdown
  ## Задача: <action> <ClassName>
  
  - **Файл:** `path/to/File.kt`
  - **Тип:** <class | interface | object | fun>
  - **Сигнатура:** <canonical signature — одна строка>
  - **Вход:** <parameters + meaning>
  - **Выход / Поведение:**
    - <step 1>
    - <step 2>
  - **Edge cases:**
    - <case 1>
  - **Depends on:** <existing types / APIs>
  - **Canonical reference:** <06-api-contract.md:NN | 01-architecture.md:NN>
  - **Rationale:** <why this approach>
  ```
  И явное правило: "Не копируй полный Kotlin файл. Укажи сигнатуру + поведение. Canonical signatures живут в 06-api-contract.md"
- **Why**: Research (DRY, Hunt & Thomas): spec = WHAT, plan = HOW (task description). Research (LangChain plan-and-execute 2026): "planner's job is strategic, executor handles tactical"
- **Prevents recurrence of**: Bug #1

### Fix #3: Single Source of Truth для типов в design docs

- **Target file**: `.claude/commands/feature-design.md` (добавить правило в секцию про document boundaries)
- **What to add**: явная таблица responsibility между design docs:
  | Document | Responsibility | Signatures? |
  |----------|----------------|-------------|
  | 01-architecture.md | Component diagrams (C4 L1-L3), high-level interactions, module boundaries | НЕТ (только имена классов, не сигнатуры) |
  | 02-behavior.md | Sequence diagrams, state machines, DFDs | НЕТ (reference to types, not define) |
  | 03-decisions.md | ADRs — architectural choices + alternatives | НЕТ |
  | 06-api-contract.md | **Canonical signatures** — единственный источник правды для interfaces, data classes, public APIs | ДА (authoritative) |
  | plan/phase-NN/*.md | Tasks — reference to signatures via "canonical reference: 06-api-contract.md:NN" | НЕТ (only refs) |
  
  И правило: "Каждый interface/data class имеет ровно ОДНУ canonical signature в 06-api-contract.md. Другие docs — reference."
- **Why**: DRY for documents (Fowler, Pragmatic Programmer). Predict cost: если сигнатура меняется в 4 местах, drift неизбежен
- **Prevents recurrence of**: Bug #2

### Fix #4: Options Pattern в plan для complex фаз (opt-in)

- **Target file**: `.claude/commands/feature-plan.md` + `.claude/agents/planner.md`
- **What to add**: новая секция в overview.md для фаз с tag `complex` (проставляется planner-ом при spawn'e):
  ```markdown
  ### Options Considered
  
  | Critère | Option A | Option B | Option C |
  |---------|----------|----------|----------|
  | Complexity | low | medium | high |
  | Flexibility | limited | good | max |
  | Test cost | 1d | 2d | 5d |
  | Refactor cost if wrong | small | medium | large |
  
  **Recommended**: Option B
  
  **Rationale**: <why>
  
  **Rejected A**: <trade-off>
  
  **Rejected C**: <trade-off>
  ```
  Trigger для complex: фаза затрагивает > 3 модулей, или содержит новый architectural pattern, или реализует FSM
- **Why**: User request. Research (ADR best practice, Nygard 2011): "The most valuable part of an ADR is the rejected alternatives and the reasoning behind the rejection"
- **Prevents recurrence of**: Bug #3

### Fix #5: Plan Review Lens для Codex CLI

- **Target file**: новый `.claude/skills/adversarial-review/references/plan-review-lens.md` + обновление `.claude/commands/feature-plan.md` (Шаг 2)
- **What to add**: Checklist для Codex CLI при plan review:
  ```markdown
  ## Plan Review Lens: "Plan as ТЗ, не implementation"
  
  Для каждого plan/phase-NN/*.md:
  - [ ] Есть ли \`\`\`kotlin блоки? Если да — blocker, требует replace на Signature Card
  - [ ] Каждая New Files entry имеет Signature Card формат (см. planner.md)?
  - [ ] Каждая сигнатура имеет "canonical reference: 06-api-contract.md:NN"?
  - [ ] Tests Required — это scenarios (given/when/then), не JUnit код?
  - [ ] Pattern Invariants — ссылаются на существующий code patterns, не новый code?
  - [ ] Если фаза имеет tag complex — есть Options Considered таблица?
  ```
- **Why**: Catches bug class, которые `feature-plan.md:188` legal правило пропустило. Research: "Reviewer agents are an especially good fit because they benefit from a clear checklist"
- **Prevents recurrence of**: Bug #1 + Bug #2 + Bug #3 (defence-in-depth)

### Fix #6: Правило "0-spec.md — zero Kotlin fenced blocks"

- **Target file**: `.claude/commands/feature-spec.md` (Phase 3.7 "text contract lock")
- **What to add**: явный grep-check в конце Phase 3.7:
  ```bash
  grep -c '^\s*```kotlin' docs/features/<slug>/0-spec.md
  # Expected: 0
  ```
  И правило: "Feature Domain Contract в 0-spec.md — ТОЛЬКО текстовые описания (terminology, правила, scenarios GIVEN/WHEN/THEN). Inline-упоминания типа `Role` допустимы, но НЕ Kotlin code blocks. Сигнатуры живут в canonical form в Phase 3.8 (domain/*.kt), НЕ в spec"
- **Why**: Текущий 0-spec.md имеет 0 Kotlin fenced blocks (artifact-reader подтвердил), это OK — но user perceives inline `enum class Role { ... }` как дубликат с domain/. Делаем explicit
- **Prevents recurrence of**: partial spec↔code дублирование восприятия

---

## Lessons Learned

1. **Plan-and-Execute separation принципиален** (LangChain plan-and-execute 2026): planner = strategic "what/why", executor = tactical "how". Нарушение этого разделения = LLM planner пишет готовый код, а executor не имеет свободы адаптации
2. **DRY применим к документам, не только к коду** (Hunt & Thomas, Pragmatic Programmer): каждая piece of knowledge имеет ОДНО authoritative representation. Duplication между spec/architecture/api-contract/plan — классическое DRY violation, ведущее к drift
3. **Deterministic enforcement > text instructions**: research (Anthropic 2026, Pixelmojo) показывает, что hooks + grep — 100% enforcement, text rules LLM рационализирует. Правило `feature-plan.md:188` игнорировалось planner-ом 7 фаз подряд
4. **Alternatives являются first-class artifact** (Nygard 2011, ADR best practice): "The most valuable part of an ADR is the rejected alternatives." Это применимо не только к architecture decisions, но и к phase-level tactical choices
5. **Review blind spots shared models**: Codex CLI эффективен для correctness bugs (integration, wiring, race conditions — поймал 3 critical issues), но не ловит methodological issues, т.к. Codex делит те же pipeline assumptions с автором plan. Cross-model НЕ защищает от methodology drift — нужен explicit review lens

---

## Applied Fixes

Дата применения: 2026-04-19. Пользователь одобрил 5 из 6 предложенных fixes (Fix #1 skip — проблемы нет, защита на будущее не нужна сейчас). Применена Вариант B (гибрид) для Signature Card: публичные типы ссылаются на `06-api-contract.md`, internal — краткая inline-сигнатура без api-contract entry.

| # | Fix | Status | Файлы |
|---|-----|--------|-------|
| 1 | grep 0 kotlin в 0-spec.md | **SKIPPED** | — (no current issue) |
| 2 | Signature Card в planner.md (Variant B гибрид) | **APPLIED** | `.claude/agents/planner.md` (Формат вывода + Правила) |
| 3 | Hook блокирует kotlin/java/kt/groovy blocks в plan files | **APPLIED** | `.claude/hooks/check-plan-no-code.sh` (новый, chmod +x), `.claude/settings.json` (регистрация в PostToolUse) |
| 4 | Options Pattern для complex фаз | **APPLIED** | `.claude/agents/planner.md` (Options Considered шаблон + trigger criteria), `.claude/commands/feature-plan.md` (Правила) |
| 5 | Document Responsibility Matrix (SoT) | **APPLIED** | `.claude/commands/feature-design.md` (новая секция + grep-check + Правило SoT) |
| 6 | Plan Review Lens для Codex CLI | **APPLIED** | `.claude/skills/adversarial-review/references/plan-review-lens.md` (новый), `.claude/commands/feature-plan.md` (Шаг 2 — обязательная ссылка на lens) |

### Defence-in-depth layers

Три уровня защиты против Bug #1 (plan = готовый код):

1. **Формат (planner.md)** — agent знает, что писать Signature Card, не готовый класс
2. **Enforcement (hook)** — файловая система блокирует save с fenced `\`\`\`kotlin` (exit 2 + сообщение со ссылками на правила)
3. **Review (plan-review-lens)** — Codex CLI проверяет содержание Signature Card (canonical refs, полноту полей, Options для complex)

Любой из трёх уровней самостоятельно ловит bug. Text rule `feature-plan.md:188` усилено до 3 слоёв.

### Verification

- Hook script: `bash -n check-plan-no-code.sh` → SYNTAX OK
- Settings.json: JSON парсится, hook зарегистрирован в PostToolUse для Edit|Write
- Signature Card + Options templates: добавлены в planner.md Формат вывода + Правила
- Document Responsibility Matrix: добавлена в feature-design.md с grep-check командой
- Plan Review Lens: файл создан, ссылка добавлена в feature-plan.md Шаг 2
