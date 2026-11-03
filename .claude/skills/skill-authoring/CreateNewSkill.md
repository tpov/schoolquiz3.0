---
name: skill-authoring
description: Создание, ревью, миграция и улучшение Agent Skills / SKILL.md для Claude Code, Gemini CLI, Cursor, Codex и других agent tools. Use this whenever the user asks to create or improve a skill, mentions SKILL.md, .claude/skills, references/assets/scripts, trigger behavior, hooks, plugin packaging, or wants to turn a reusable workflow into a skill.
argument-hint: [описание нового скилла или путь к существующему]
---

# Skill Authoring

Этот skill проектирует skills по content design, а не только по формату.
Он сам обязан следовать тем же правилам: выбрать pattern, использовать progressive disclosure, добавить gotchas и пройти self-review перед финализацией.

## Сначала определи режим задачи

Load `references/design-patterns.md` before drafting or reviewing.

Выбери один primary pattern:
- Domain Knowledge: когда ценность скила — в конкретных примерах кода, RIGHT/WRONG паттернах и чеклистах. Не требует Gotchas, Inversion или выбора паттерна.
- Tool Wrapper: когда skill учит библиотеке, CLI, SDK, design system или внутренним conventions.
- Generator: когда нужен предсказуемый output по template.
- Reviewer: когда skill оценивает код, документы или конфигурацию по rubric/checklist.
- Inversion: когда без требований нельзя безопасно строить решение.
- Pipeline: когда есть строгие шаги, checkpoints и запрет на skip.

Добавляй secondary patterns только если они реально уменьшают failure modes.
Хороший дефолт для skill authoring: `Inversion + Pipeline`, а `Reviewer` использовать как финальный self-check.

## Workflow

### 1. Scope

Сначала пойми, нужен ли вообще skill:
- if the instructions should apply project-wide and almost always, prefer `.claude/rules/`
- if the instructions are optional, task-specific, or reusable as a slash workflow, use a skill
- project-level skill path: `.claude/skills/<name>/`
- user-level skill path: `~/.claude/skills/<name>/`
- plugin/marketplace: только если пользователь явно просит распространяемый пакет

Для нового skill по умолчанию используй folder-based layout с `SKILL.md`.
Если в репо есть legacy flat files, не мигрируй их массово без явной просьбы.

### 2. Invert Before You Generate

Не начинай писать skill, пока не понятны:
- задача и ценность skill
- когда skill должен triggerиться
- тип входов и ожидаемый output
- нужны ли `references/`, `assets/`, `scripts/`, `hooks`, config или persistent data
- требуется ли manual-only invocation из-за side effects

Если часть ответа уже есть в conversation или repo, не переспрашивай.
Если критично не хватает контекста, задавай только минимальный набор вопросов.

### 3. Choose the Shape

При создании нового skill load `assets/skill-template.md`.

При выборе структуры:
- always-on standards, coding conventions и file-path-scoped guidance держи в `.claude/rules/`, не в skill
- держи `SKILL.md` коротким и procedural
- длинные правила, rubrics, API details и examples выноси в `references/`
- templates and sample outputs храни в `assets/`
- детерминированные повторяемые операции выноси в `scripts/`
- hooks добавляй только когда они полезны именно на время активного skill
- persistent state не храни в самой папке skill, если возможны upgrades; используй stable location such as `${CLAUDE_PLUGIN_DATA}`

If you need frontmatter or runtime options, load `references/runtime-options.md`.

### 4. Write the Skill

При написании:
- Делай `description` model-facing: это список ситуаций, когда skill надо вызвать, а не human summary.
- Не превращай always-on project standards в skill только ради единого формата; для них есть `.claude/rules/`.
- Используй естественные фразы пользователя, включая косвенные формулировки.
- Не повторяй очевидное; добавляй только то, что реально меняет поведение модели.
- Prefer guardrails and reasoning over brittle micromanagement.
- Обязательно добавляй `## Gotchas` с реальными failure modes, footguns и undertrigger risks.
- Если нужен setup, научи skill обнаруживать missing config и спрашивать пользователя.
- Если skill делает опасные действия, по умолчанию делай manual-only и проси явное подтверждение на checkpoints.

### 5. Review Before Finalizing

Load `references/review-checklist.md`.

Перед завершением проверь:
- primary pattern выбран явно
- description покрывает реальные trigger phrases
- `SKILL.md` не раздут и не дублирует references
- есть gotchas, setup logic и validation path
- `scripts/assets/references/hooks` добавлены по делу, а не ради "полной структуры"
- skill не загоняет модель в ненужный railroading
- сам этот skill obeys the same checklist

### 6. Return a Complete Result

Когда создаёшь или обновляешь skill, отдай:
- primary pattern и secondary patterns с кратким why
- список созданных или изменённых files
- короткое объяснение, почему понадобились `references`, `assets`, `scripts`, `hooks` или config
- 2-3 realistic test prompts
- assumptions, которые ещё стоит проверить на реальных задачах

## Gotchas

- Плохой `description` почти всегда undertriggers, даже если сам `SKILL.md` хороший.
- Skill, который смешивает несколько patterns без явной причины, становится хрупким.
- `SKILL.md`, набитый справкой и примерами, хуже чем lean body + targeted references.
- Skill, который на самом деле должен быть always-on rule, undertriggers и усложняет runtime-модель.
- Не превращай instructions в rigid ceremony. Если правило не universal, объясни why and when to bend it.
- `scripts/` нужны, когда logic должна быть deterministic or repeatedly reconstructed; otherwise let the model stay flexible.
- Side-effect skills without gates are dangerous. If the skill deploys, deletes, posts, bills, or mutates shared state, add approval checkpoints and consider manual-only invocation.
- Не храни важные mutable данные прямо внутри skill directory, если skill может обновляться или переустанавливаться.

## Pattern Hints

- "Here are the RIGHT and WRONG code examples" -> Domain Knowledge
- "Teach the agent our library / CLI / framework" -> Tool Wrapper
- "Always produce the same document shape" -> Generator
- "Score or audit against rules" -> Reviewer
- "Interview me first" -> Inversion
- "Do this in strict phases with gates" -> Pipeline

Patterns compose, but composition must stay legible.
