---
description: Диагностика бага в 3 фазы — Initial Investigation → Deep Debug (conditional) → Generate Fix Spec. Early-out если проблема очевидная. Fix НЕ применяется в debug — генерируется ТЗ для явного одобрения.
argument-hint: "<feature-slug> <описание проблемы>"
---

Запусти диагностику для `$ARGUMENTS`.

## Роль

Ты — координатор debug-сессии. Твоя задача:
1. **Phase 1** — лёгкое изучение: собрать известный контекст (spec, past reports, invariants, свежие логи)
2. **Phase 2** — глубокий дебаг (условный, только если Phase 1 не дал очевидный root cause): team из targeted agents по категории проблемы
3. **Phase 3** — сгенерировать **Fix Spec** (ТЗ для фикса), НЕ применять fix автоматически

**Ты НИКОГДА не читаешь исходный код, не анализируешь логи, не пишешь fix напрямую.** Только координация + написание Fix Spec документа.

**Fix применяется ТОЛЬКО после явного одобрения пользователя** — через прямой apply (для мелких изменений) или через `/feature-implement` (для крупных).

## Что прочитать

1. `CLAUDE.md` (ты уже читаешь)
2. `.claude/PROJECT-CONTEXT.md` — package name, build commands
3. `docs/features/<slug>/README.md` — status, documents list

НЕ читай содержимое feature docs, agents, skills — это работа агентов в Phase 2.

## Шаг 0: Parse arguments

Из `$ARGUMENTS` извлеки:
- `feature-slug` — первое слово (kebab-case)
- `problem` — всё остальное (описание проблемы)

Если feature-slug не ясен — спроси через `AskUserQuestion`.

## Phase 1: Initial Investigation (всегда)

**Цель**: собрать известный контекст без подъёма большой команды. Определить, нужен ли deep debug вообще.

**Нет time budget.** Phase 1 занимает столько сколько нужно. Переход к Phase 2 — по содержательному сигналу (root cause не определён), не по таймеру.

### 1.1 Gather known context (делегируй через Agent tool, без Teams)

Создай один `Agent(subagent_type: "doc-analyst")` с коротким промптом:

```
Lightweight context gathering для debug-сессии <slug>.
Problem: <problem>

Прочитай и резюмируй ≤400 словами:
1. docs/features/<slug>/0-spec.md — секции Domain Contract, Primary User Journeys, State Matrix (если есть)
2. docs/features/<slug>/README.md — статус фичи, ссылки на past debug-*.md
3. docs/features/<slug>/debug-*.md — ВСЕ past debug reports (если есть). Для каждого: root cause, applied fix, related symptoms
4. docs/features/<slug>/fix-spec-*.md — ВСЕ past fix specs, особенно те что со статусом "deferred" или "not applied"
5. docs/invariants.md — инварианты затрагивающие эту фичу

Для каждого документа — file:line ссылки. Если past report описывает такой же симптом — **отметь явно**.
```

Параллельно создай `Agent(subagent_type: "log-reader")` ТОЛЬКО если adb устройства подключены и симптом описывает runtime поведение:

```bash
adb devices -l
```

Если устройства есть — спавни log-reader с задачей:

```
Lightweight log scan для debug-сессии <slug>.
Problem: <problem>
Package: значение debug_package_name из PROJECT-CONTEXT.md

Захвати последние 5 минут logcat фильтром на severity ERROR/WARN/FATAL.
Ищи:
- Stack traces
- ANR / CRASH markers
- Repeating errors (>3 одинаковых в 1 минуту)

Резюме ≤300 слов: топ-5 аномалий с timestamp + сниппет (3 строки). Если нет аномалий — "no visible anomalies".
НЕ анализируй в глубину, это quick scan.
```

Если adb устройств нет — пропусти log-reader.

### 1.2 Classify the problem

После отчётов от doc-analyst (и log-reader если был) — классифицируй проблему по одной оси:

| Category | Сигналы |
|----------|---------|
| `crash` | Stack trace присутствует, exception type известен |
| `lifecycle` | Упоминания onDestroy/onStop/process death, проблема при свертывании/возврате |
| `di` | ClassNotFound, duplicate binding, @Inject не работает |
| `concurrency` | Race condition, deadlock, coroutine cancellation, Flow collection issue |
| `rate-limit` / `network` | 429, timeout, retry loop, backoff issues |
| `ui` | Неверный рендер, missing update, jank, stale state в Component |
| `logic` | Wrong result, no crash, domain rule нарушен |
| `data` | Room migration, DAO query mismatch, persistence corruption |
| `realtime` | WebSocket disconnect, event replay, presence desync |
| `unknown` | Нет явных сигналов после Phase 1 |

И severity: `blocker` (data loss, user-blocking) / `high` (workaround нужен) / `medium` (degraded UX) / `low` (cosmetic).

### 1.2.5 Debugger Team Composition Proposal

Перед тем как предлагать Phase 2 пользователю, создай `Agent(subagent_type: "diagnostics")` **без Teams** как debugger-advisor:

```
Team Composition Proposal для debug-сессии <slug>.

Problem: <problem>
Category: <category>
Severity: <severity>
Phase 1 evidence:
- doc-analyst summary: <summary>
- log-reader summary: <summary или none>
- past reports: <same symptom? yes/no>

Прочитай только:
- .claude/PROJECT-CONTEXT.md
- docs/features/<slug>/README.md
- docs/features/<slug>/0-spec.md (если есть)
- docs/features/<slug>/2-grounding.md (если есть)
- docs/features/<slug>/debug-*.md / fix-spec-*.md (если есть)
- docs/invariants.md (если есть)

НЕ запускай build/test/logcat/device commands.
НЕ читай production source files.

Предложи targeted teammates для Phase 2:
- mandatory teammates;
- optional teammates with triggers;
- кого НЕ поднимать;
- when to add log-reader/code-analyst/web-researcher later;
- evidence each teammate must produce.
```

Lead использует proposal как default team list. Таблица в Phase 2.2 ниже — fallback/minimum, если diagnostics недоступен или proposal неполный.

### 1.3 Early-out decision

Применимо если ОДНО ИЗ выполнено:

| Сигнал | Действие |
|--------|----------|
| Past `debug-*.md` / `fix-spec-*.md` описывает тот же симптом с applied fix | Предложи re-apply same fix, пропусти Phase 2, сразу Phase 3 (regenerate Fix Spec из past) |
| Stack trace указывает на single file:line + invariant или spec contract объясняет root cause | Пропусти Phase 2, сразу Phase 3 |
| Симптом покрыт существующим failing test (пользователь упомянул test name) | Пропусти Phase 2, test уже root cause evidence |
| Category = `logic` + domain clear + grep находит exact место | Пропусти Phase 2, сразу Phase 3 |

Иначе → Phase 2 (Deep Debug).

### 1.4 Present assessment & get user decision

Покажи пользователю через текст (не `AskUserQuestion` — дай prose сначала):

```
=== INITIAL INVESTIGATION (Phase 1) ===

Problem: <problem summary>
Category: <category>
Severity: <severity>

Known context:
- Spec: <Domain Contract rule / Primary Journey ссылка, или "not relevant">
- Past reports: <list of debug-*.md / fix-spec-*.md, или "none">
- Invariants: <list, или "none related">
- Immediate signals: <stack trace / error / null / "none from logcat">

Hypothesis:
<1-3 предложения — что likely происходит, с опорой на контекст выше>

Recommended next step:
- [OBVIOUS] Root cause highly likely: <X>. Предлагаю сразу Phase 3 (Generate Fix Spec).
  ИЛИ
- [INVESTIGATION NEEDED] Hypothesis не подтверждена. Рекомендую Phase 2 (Deep Debug) с командой: <list from diagnostics Team Composition Proposal>.
```

Затем `AskUserQuestion`:

```
(A) Принять hypothesis, сразу Phase 3 (Generate Fix Spec)
(B) Запустить Phase 2 (Deep Debug) с предложенной командой
(C) Запустить Phase 2, но с моим составом команды (я укажу кого)
(D) Предоставить больше контекста, вернись в Phase 1
```

**=WAIT for user decision. Не переходи самостоятельно.=**

## Phase 2: Deep Debug (conditional)

Выполняется ТОЛЬКО если пользователь выбрал B или C в Phase 1.4.

### 2.1 Device detection

```bash
adb devices -l
```

Для симптомов без runtime поведения (pure logic, DI, compile) — устройства не нужны, пропусти к 2.3.

Для runtime симптомов — если 0 устройств, сообщи пользователю: "Phase 2 без live логов работает по коду/docs. Продолжать?"

### 2.2 Agent selection (targeted, не all-in)

**НЕ поднимай всех teammates по умолчанию.** Primary source = `diagnostics` Team Composition Proposal из Phase 1.2.5.

Lead обязан:
- поднять mandatory teammates из proposal;
- добавить optional teammates только если trigger уже выполнен;
- записать любые overrides в debug report;
- если proposal недоступен/неполный — использовать fallback таблицу ниже.

Fallback/minimum выбор по Phase 1 category:

| Category | Обязательные | Опциональные |
|----------|--------------|--------------|
| `crash` | `code-analyst`, `log-reader` per device | `doc-analyst` если доступна спека |
| `lifecycle` | `code-analyst`, `log-reader` per device, skill `systematic-debugging` mandatory | `web-researcher` для platform quirks |
| `di` | `code-analyst` (focus on DI files) | — |
| `concurrency` | `code-analyst`, `log-reader`, skill `systematic-debugging` | — |
| `rate-limit` / `network` | `web-researcher`, `log-reader`, `code-analyst` | `doc-analyst` |
| `ui` | `code-analyst`, `log-reader` per device | — |
| `logic` | `code-analyst` alone | `doc-analyst` если rule неясен |
| `data` | `code-analyst`, `doc-analyst` для schema history | — |
| `realtime` | `code-analyst`, `log-reader`, `web-researcher` для SDK behavior | — |
| `unknown` | `code-analyst`, `doc-analyst`, `log-reader` per device, `web-researcher` | — |

**Правило**: если сомневаешься — подними меньше, добавишь по ходу через `Agent(team_name: ...)`. Большая команда = больше peer DM overhead (industry research).

### 2.3 Create Team

`TeamCreate: "debug-<slug>"`

Для каждого выбранного teammate:

**Log-readers** (если устройства включены, по одному на device):

```
Ты log-reader для устройства <device_name> (<serial>).
Category: <category из Phase 1>
Problem: <problem>
Feature: <slug>
Package: debug_package_name из PROJECT-CONTEXT.md

Фильтруй logcat по package + severity (по умолчанию ERROR/WARN/FATAL, INFO по запросу).

Начни немедленно, без ack. Когда найдёшь аномалию — SendMessage:
- code-analyst: если нужна проверка в коде
- doc-analyst: если нужна сверка с документацией
- lead: если crash / critical anomaly

Прочитай свою роль из .claude/agents/log-reader.md
```

**Code-analyst** (обязательный):

```
Ты code-analyst.
Category: <category>
Problem: <problem>
Feature: <slug>
Phase 1 hypothesis: <hypothesis>

Прочитай PROJECT-CONTEXT.md и 2-grounding.md (если есть).
Используй skill `systematic-debugging` — это ОБЯЗАТЕЛЬНО для category ∈ {lifecycle, concurrency, unknown}.

Начни немедленно, без ack. Трассируй код от entry points к suspect areas.
Когда найдёшь potential bug — SendMessage:
- doc-analyst: сверить с документацией
- logger-*: подтвердить по логам
- lead: confirmed bug

Прочитай свою роль из .claude/agents/code-analyst.md
```

**Doc-analyst** (если выбран):

```
Ты doc-analyst.
Category: <category>
Problem: <problem>
Feature: <slug>

Прочитай docs/features/<slug>/ и docs/invariants.md.
Построй Claims Map и сверь с проблемой.

Начни немедленно, без ack. SendMessage:
- code-analyst: проверить claim в коде
- logger-*: сверить с логами
- lead: critical inconsistency

Прочитай свою роль из .claude/agents/doc-analyst.md
```

**Web-researcher** (если выбран):

```
Ты web-researcher в debug-сессии.
Category: <category>
Problem: <problem>

Ищи:
- Error messages / stack traces → known issues, SO answers, GitHub issues
- SDK/library-specific quirks (Context7 MCP для library docs)
- Platform-specific behavior (Android version, device)

Начни немедленно. SendMessage findings → code-analyst / doc-analyst / lead.
```

### 2.4 Kickoff — синхронный старт

Broadcast через SendMessage всем teammates одновременно:

```
=== DEEP DEBUG KICKOFF ===
Feature: <slug>
Problem: <problem>
Phase 1 hypothesis: <hypothesis>
Category: <category>

Team members: <list>

ПРАВИЛА КОММУНИКАЦИИ:
1. Начинайте работу НЕМЕДЛЕННО — prompt содержит всё нужное, ack не требуется
2. Finding с evidence — отправляйте тому, кому нужно (не lead-у для статуса, а целевому агенту для действия)
3. НЕ шлите DM для ack/статуса/"принято" — это турны впустую
4. Каждый finding — с severity (CRASH/ANOMALY/INCONSISTENCY/SUSPECT/INFO) и file:line
5. Если ваша работа не прогрессирует >5 минут — SendMessage lead с блокером

START.
```

### 2.5 Convergence loop

Lead мониторит прогресс:

1. **Hypothesis tracking** — запиши гипотезы в table. Каждая с severity + evidence статусом
2. **Cross-reference** — если logger нашёл anomaly, а code-analyst не видел → relay через SendMessage
3. **Diagnostic logs (если нужны)**:
   - code-analyst: `HYPOTHESIS: X. NEED LOGS AT: <file:line точки>`
   - Lead relay user: "code-analyst предлагает добавить diagnostic logs в [точки] для подтверждения гипотезы [X]. Добавить?"
   - User approves → lead → code-analyst: "добавь"
   - User воспроизводит → log-reader ловит → отправляет code-analyst
4. **Escalation signals** (не остановки, а сигналы спросить пользователя):
   - Несколько гипотез последовательно отвергнуты без сходимости → lead → user: "возможно архитектурная проблема, обсудим направление?"
   - Длительное отсутствие прогресса (agents ходят по кругу, одни и те же findings повторяются) → lead → user: "сменить фокус / предоставить больше контекста?"
   - Два agents расходятся — lead разрешает конфликт или эскалирует пользователю
   - Нужен logcat / repro с нового сценария → lead → user

**Нет hard cap на количество гипотез.** Если root cause сложный — продолжай debug. Если же гипотезы исчерпались без сходимости — Phase 3 записывает "root cause NOT confirmed" с options, не объявляет провал по лимиту.

## Phase 3: Generate Fix Spec

**Fix НЕ применяется в этой фазе.** Только генерация ТЗ документа.

### 3.1 Synthesize findings

Собрав evidence (из Phase 1 или Phase 2), lead пишет **Fix Spec** в `docs/features/<slug>/fix-spec-<YYYY-MM-DD>.md`:

```markdown
---
date: YYYY-MM-DD
feature: <slug>
problem: <short title>
status: proposed
---

# Fix Spec: <title>

## Problem

### Symptom
<что видит пользователь/QA>

### Reproducer
<шаги воспроизведения или "not reliably reproducible">

### Severity
<blocker | high | medium | low>

## Root Cause

### Confirmed / Hypothesized
<CONFIRMED: опиши единственную root cause c file:line>
<HYPOTHESIZED: top-N гипотез с вероятностью, если Phase 2 не сошёлся>

### Evidence
- From code: <file:line + сниппет>
- From logs: <timestamp + сниппет>
- From docs: <inconsistency или invariant violation>
- From past reports: <ссылка на past debug-*.md / fix-spec-*.md если есть>

### Why pipeline missed this
<1-2 предложения — какая phase в spec/research/design/plan/implement должна была это поймать. Feed это в /feature-retrospective.>

## Proposed Fix

### Scope
<small (1-5 lines in 1-2 files) | medium (multi-file в одном layer) | large (cross-layer, нужен pipeline)>

### Changes
<file-by-file описание ИЛИ diff сниппет>

### New / Updated Tests
- `<test name>`: GIVEN <context> WHEN <action> THEN <expected>
- Минимум: 1 regression test воспроизводящий исходный симптом

### Regression risk
- Low / Medium / High
- Related areas to verify: <list>

### Rollback plan
<как откатить если fix ломает что-то еще>

## Acceptance Criteria

- [ ] Reproducer no longer triggers symptom
- [ ] New regression test green
- [ ] No regression in <related areas>
- [ ] <specific AC from original spec if violated>

## Apply Method

Recommendation: <one of three>

### Direct apply (если scope = small)
Lead координирует применение через backend-dev / frontend-dev в этой же сессии. Одна короткая dev-cycle, затем code-reviewer.

### Via /feature-implement (если scope = medium/large)
Fix spec становится input для phase-01 нового pipeline pass. Plan строится вокруг этих изменений. Полный review cycle.

### Deferred (если severity = low или риск высокий)
Fix spec сохраняется, не применяется. Пользователь решит приоритет позже. Trackable через status в frontmatter.

## Open Questions

<если root cause hypothesized, не confirmed — перечисли оставшиеся варианты и что нужно для их верификации>
```

### 3.2 Present Fix Spec to user

Покажи краткое summary (не весь документ):

```
=== FIX SPEC READY ===

Problem: <summary>
Root cause: <1-2 sentences> [CONFIRMED | HYPOTHESIZED]
Proposed fix scope: <small | medium | large>
Regression risk: <low | medium | high>
Recommended apply method: <direct | via /feature-implement | deferred>

Fix spec saved: docs/features/<slug>/fix-spec-<date>.md

Что делать дальше?
(A) Apply сейчас напрямую (lead координирует dev-агентов в этой сессии)
(B) Передать в /feature-implement (новый pipeline pass, полный review)
(C) Отложить (оставить fix spec как proposed, не применять)
(D) Пересмотреть root cause (вернись в Phase 2 с новыми гипотезами)
```

Используй `AskUserQuestion` для выбора.

**=WAIT for user decision.=**

### 3.3 Действия по выбору

**A) Apply сейчас** (только если scope = small):
- Создай `TeamCreate "debug-fix-<slug>"` если Phase 2 team уже удалена, иначе используй существующую
- Спавни `backend-dev` или `frontend-dev` (по слою фикса) + `test-dev` для regression test
- После build gate → code-reviewer обязателен
- Update fix-spec frontmatter: `status: applied`

**B) Via /feature-implement**:
- Fix spec остаётся в `docs/features/<slug>/`
- Update frontmatter: `status: handoff-to-implement`
- Сообщи пользователю: "Запусти `/feature-implement <slug>` — plan создастся на основе fix spec"

**C) Deferred**:
- Fix spec остаётся proposed
- Update frontmatter: `status: deferred`
- Добавь ссылку в `docs/features/<slug>/README.md` под секцию "Pending Fixes"

**D) Revisit**:
- Вернись в Phase 2, расширь команду или запроси у пользователя новую информацию

## Шаг 4: Cleanup

После применения А / B / C:
- Если Team создавалась → `TeamDelete: "debug-<slug>"` (и `"debug-fix-<slug>"` если была)
- Обнови `docs/features/<slug>/README.md`:
  - Добавь ссылку на `fix-spec-<date>.md` в секцию "Debug History" (создай если нет)
  - Обнови статус фичи если релевантно

## Правила

- **=investigation first=** — Phase 1 всегда, независимо от severity. Без контекста не поднимай team
- **=early-out=** — если past report описывает same symptom или stack trace очевидный — пропусти Phase 2, не тратьте turns на teamwork
- **=debugger proposes team=** — diagnostics Team Composition Proposal является default составом Phase 2; category table только fallback/minimum
- **=targeted agents=** — Phase 2 поднимает только нужных teammates по proposal/category, не all-in
- **=immediate start=** — в prompt каждого teammate явно "начинай немедленно, без ack"
- **=no peer DM for status=** — teammates шлют DM только для evidence/action, не для "принято, жду"
- **=fix spec, not fix apply=** — Phase 3 генерирует ТЗ, fix применяется ТОЛЬКО после `AskUserQuestion` approval
- **=no rule rewrite=** — если evidence противоречит Feature Domain Contract, не переписывай contract молча — эскалируй пользователю
- **=quality over speed=** — нет time budgets, нет hypothesis cap. Debug занимает столько сколько нужно для уверенного root cause. Escalation signals (несколько отвергнутых гипотез, кругообразные findings, конфликт agents) — это повод спросить пользователя о направлении, не остановка по таймеру
- **=feed retrospective=** — секция "Why pipeline missed this" в Fix Spec — это input для `/feature-retrospective`
- **=no lead reads code=** — lead только координирует и пишет Fix Spec. Код читают agents
