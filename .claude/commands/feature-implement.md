---
description: Реализовать фичу, оркестрируя dev/review/test-агентов через quality gates.
argument-hint: "<feature-slug>"
---

Реализуй фичу `$ARGUMENTS`.

## Роль

Ты диспетчер mob programming-команды. Ты НИКОГДА не пишешь код. Ты:
1. Читаешь план
2. Создаёшь Teams с devs и reviewers
3. Назначаешь фазы (по одной за раз)
4. Запускаешь build/tests
5. Координируешь review
6. Принимаешь решения по quality gates

**Delegate Mode:** Ты ограничен coordination-only — spawning, messaging, task management. Ты НЕ МОЖЕШЬ писать код, редактировать production files, или принимать High/Blocked architectural decisions за пользователя. Low/Medium process decisions (phase ordering внутри approved graph, retry routing, reviewer re-check routing, tier escalation по evidence) можно принимать автономно, но записывай их в Run Ledger. Если обнаружен architectural mismatch (agent хочет удалить/скрыть функционал, сменить паттерн, пропустить модуль) — STOP и спроси пользователя.

## Шаг 0: Подготовка

Прочитай:
1. `docs/features/<slug>/0-spec.md` (если есть)
2. `docs/features/<slug>/README.md`
3. `docs/features/<slug>/plan/README.md`

**Lazy-loading:** НЕ читай содержимое phase files заранее. Читай ТОЛЬКО `plan/README.md` для общей картины. Содержимое конкретной фазы читается ТОЛЬКО при переходе к ней: сначала `plan/phase-NN/overview.md`, затем только нужные role-файлы. Каждая фаза — self-contained единица; implementer получает ровно 1 role file per assignment.

Построй граф зависимостей:

| Type | Condition | Execution |
|------|-----------|-----------|
| Independent | Нет общих файлов | Parallel |
| Dependent | Phase-B зависит от Phase-A | Sequential |

### 0.5 Run Ledger (обязательно для автономности)

Создай/обнови директорию:

```text
docs/features/<slug>/run/
```

Веди два артефакта:

- `pipeline-state.json` — текущий resumable state: active phase, completed phases, blocked phases, spawned teams, last green command, open blockers, next action.
- `run.jsonl` — append-only event log: timestamp, event type, phase, agent, decision/finding/command, evidence path.

Минимальный `pipeline-state.json`:

```json
{
  "feature": "<slug>",
  "status": "implementing",
  "activePhase": null,
  "completedPhases": [],
  "blockedPhases": [],
  "lastGreenCommand": null,
  "openBlockers": [],
  "nextAction": "create team"
}
```

Обновляй ledger после:
- старта/завершения фазы;
- build/test pass/fail;
- HIGH/BLOCKER finding;
- autonomous Low/Medium process decision;
- user approval/defer decision;
- handoff/resume point.

Если session/context оборвался — первым делом прочитай `pipeline-state.json` и продолжай с `nextAction`, не рестартуя pipeline с нуля.

### 0.6 Debugger Team Composition Preflight

Перед `TeamCreate` запусти `diagnostics` как read-only debugger-advisor **без `team_name`**. Это исключение из правила "все devs/reviewers только через Teams": advisor не реализует, не review-ит и не участвует в fix loop, он только предлагает состав команды.

Prompt:

```text
Team Composition Preflight для implementation feature <slug>.

Прочитай только:
- .claude/PROJECT-CONTEXT.md
- docs/invariants.md (если есть)
- docs/features/<slug>/0-spec.md (если есть)
- docs/features/<slug>/2-grounding.md (если есть)
- docs/features/<slug>/plan/README.md
- docs/features/<slug>/plan/phase-*/overview.md

НЕ читай role files backend.md/frontend.md/tests.md.
НЕ запускай build/test/logcat/device commands.
НЕ предлагай code changes.

Верни Team Composition Proposal:
- mandatory teammates per phase;
- conditional teammates per trigger;
- which teammates NOT to spawn;
- scaling recommendations;
- debug hooks: какие failure signals route-ить в diagnostics/log-reader/code-analyst;
- device/backend prerequisites.

Use available agents: frontend-dev, backend-dev, firebase-dev, test-dev, integration-tester,
code-reviewer, architect-reviewer, security-reviewer, completeness-reviewer,
concurrency-reviewer, diagnostics, code-analyst, log-reader, web-researcher.
```

Lead обязан:
- принять proposal как default;
- проверить hard rules ниже (security-reviewer обязателен; test-dev обязателен для production code; scaffold ownership не нарушать);
- записать proposal summary и любые overrides в `run/run.jsonl`;
- если proposal говорит `diagnostics` или `log-reader` нужен в phase team, поднять их как teammates с конкретным trigger/scope.

## Шаг 1: Создать Teams

**ОБЯЗАТЕЛЬНАЯ последовательность** — нарушение = ошибка:
1. `TeamCreate` → создать команду
2. `Agent` с `team_name` → поднять **ВСЕХ** teammates из Debugger Team Composition Proposal (devs + reviewers + conditional diagnostics/log readers)
3. `TaskCreate` + `TaskUpdate` (blockedBy) → создать задачи с зависимостями
4. `SendMessage` → назначить первые фазы

**ЗАПРЕЩЕНО** использовать Agent tool без `team_name`. Все агенты — только как teammates в Teams.

TeamCreate: `"feature-<slug>-impl"`

Подними teammates по необходимости фичи:

**Devs:**
- `frontend-dev` — Android presentation (Decompose Components, Compose screens, navigation wiring)
- `backend-dev` — KMP domain/data, shared/core/data, Room/DAO/mappers/Koin scaffold при необходимости
- `firebase-dev` — `platform/firebase`, Firestore rules/scripts/cloud functions — только если фича затрагивает
- `test-dev` — JVM unit тесты по всем модулям (TDD-style, параллельно с dev)
- `integration-tester` — instrumented/integration тесты для lifecycle, multi-layer, Room DAO (conditional)

**Reviewers (spawn вместе с devs при `TeamCreate`, idle до build gate):**
- `code-reviewer`
- `architect-reviewer`
- `security-reviewer` — полноправный участник, не опциональный
- `completeness-reviewer`
- `concurrency-reviewer` — только для фаз с тегом `concurrency-review` в phase file (тег ставит planner)

**Debuggers / analysts (по proposal или failure trigger):**
- `diagnostics` — debugger teammate для build/test/runtime failures, DI/migration/lifecycle/concurrency risk, или когда previous phase имела repeated failures. Не пишет код, только root-cause analysis + evidence.
- `code-analyst` — deep code trace для confirmed/likely bug, когда нужен call chain и file:line root cause.
- `log-reader` — по одному на connected device для runtime/lifecycle/crash/realtime symptoms.
- `web-researcher` — только если failure зависит от external SDK/platform behavior.

**Важно:** все reviewers спавнятся одновременно с devs при `TeamCreate`, не после build gate. Их task-и в TaskList blockedBy build_task — они сидят idle пока lead не пометит build completed, затем lead высылает assignment SendMessage. Это делает команду полноценной visibility-wise и позволяет autonomous loop (reviewer↔coder peer DM) работать без дополнительного spawn overhead.

Onboarding prompt для idle reviewer (короткий):
```
=== TEAM ONBOARD ===
Ты <reviewer-type> в команде feature-<slug>-impl.

Текущий status: phase-NN в реализации, build gate ещё не пройден. Твоя задача `Phase-NN: review-<type>` — blocked. НЕ начинай review пока не получишь SendMessage assignment от lead-а с "Начни review НЕМЕДЛЕННО" + `Build Status: PASSED (commit <sha-or-phase-ref>)`.

Пока idle. Когда assignment — autonomous loop с implementer-ами напрямую (findings/fix/re-check через SendMessage EVIDENCE). Lead получает только финальный PASS или ERROR (escalation: architectural mismatch / repeated blocker / reviewer disagreement).

Без подтверждений.
```

Не поднимай firebase-dev если фича не затрагивает Firebase. Не поднимай frontend-dev если фича только backend. Concurrency-reviewer спавнится только если хотя бы одна фаза имеет тег `concurrency-review` или diagnostics proposal объясняет concurrency risk. Diagnostics/log-reader/code-analyst не обязательны всегда: поднимай их по proposal или при failure trigger.

### Scaffold File Ownership (обязательно)

Следующие файлы меняет ТОЛЬКО `backend-dev` (или, если фаза чисто frontend, то `frontend-dev` — но тогда зафиксировано в phase file overview):

- `build.gradle.kts` (root + app + любой module)
- `libs.versions.toml`
- `settings.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`
- `AndroidManifest.xml` (root entries; per-feature components могут менять подответственные devs)
- `gradle.properties`

Если `test-dev` или другой teammate требует изменений scaffold (добавить dependency, plugin, repository) — **SendMessage lead-у** с конкретным запросом. Lead обновляет phase file и делегирует backend-dev-у. Параллельное редактирование scaffold = merge conflict.

### Parallel Phases via Hierarchical Delegation (если independent)

`TeamCreate` поддерживает одну team на session. Для параллельной работы над несколькими **независимыми** фазами (нет общих файлов, нет sequential dependencies):

1. Master lead создаёт master team `feature-<slug>-impl`
2. Master lead спавнит **phase-lead** агентов (`subagent_type: "general-purpose"`) БЕЗ `team_name` — они работают как child-сессии в контексте master
3. Каждый phase-lead — автономный lead своей фазы. В своём prompt получает:
   - Path к `plan/phase-NN/overview.md`
   - Instruction: "ты lead для phase-N, следуй инструкциям `.claude/commands/feature-implement.md` Шагов 1-2.4 для твоей фазы"
   - Instruction: создать суб-team `TeamCreate: "feature-<slug>-phase-NN"` и спавнить там devs/reviewers
4. Phase-lead отчитывается master lead через SendMessage (child → parent, Agent tool возвращает результат)
5. Master lead координирует cross-phase review после завершения всех phase-lead

Это hierarchical pattern — industry best practice для multi-agent systems (Addy Osmani, Anthropic multi-agent blog). Master context не фрагментируется, phase-lead имеет свою под-команду.

**Когда НЕ использовать**: если фазы sequential (phase-B зависит от phase-A) — обычный single-team flow без phase-lead.

### Agent Scaling (масштабирование при необходимости)

Если фаза содержит большой scope, lead ДОЛЖЕН поднять дополнительных агентов того же типа. Не экономь на агентах — качество важнее токенов.

**Когда масштабировать:**

| Condition | Action |
|-----------|--------|
| Фаза меняет >5 production files в разных пакетах | 2 dev агента (разделить по пакетам) |
| Фаза требует >8 тест-сценариев | 2 test-dev агента (разделить по модулям/классам) |
| Фаза затрагивает и UI и data layer одновременно | frontend-dev + backend-dev параллельно |
| Фаза включает lifecycle/realtime/concurrency логику | test-dev (JVM) + integration-tester (instrumented) параллельно |
| Фаза имеет высокий риск build/test/runtime failures или repeated failure из previous phase | diagnostics teammate как debugger-on-call |
| Cross-phase review: >10 файлов изменено за все фазы | 2 code-reviewer агента (разделить по модулям) |

**Как масштабировать:**
Дополнительные агенты поднимаются как teammates с суффиксом: `test-dev-2`, `backend-dev-2`, `code-reviewer-2`. Каждый получает СВОЙ scope (подмножество файлов/сценариев). Lead распределяет работу равномерно.

```
Пример: фаза с 12 тест-сценариями
- test-dev-1: сценарии 1-6 (happy path + queue logic)
- test-dev-2: сценарии 7-12 (edge cases + concurrency)
```

### Integration Tester Activation

Подними `integration-tester` если фича затрагивает ЛЮБОЕ из:
- Lifecycle-зависимую логику (Component lifecycle, Activity process death, restore)
- Multi-layer flow (Component → UseCase → Repository → DAO)
- Room DAO queries с boundary values
- WebSocket/realtime event chains
- Concurrency (mutex, parallel coroutines, Flow collect)

Integration-tester работает ПОСЛЕ production phase + test-dev, получает:
```
=== INTEGRATION TEST ASSIGNMENT ===
Feature: <slug>, Phase: <N>
Changed production files: <list>
Unit tests written by test-dev: <list>

Напиши instrumented/integration тесты:
- Multi-layer scenarios из 02-behavior.md
- DAO boundary tests с реальной Room in-memory DB
- Lifecycle edge cases из 2-grounding.md
```

## Шаг 2: Work Loop (для каждой фазы)

**Walking Skeleton integration mode — Variant Y (phase-01)**: если `0-spec.md` содержит `Feature Domain Contract` ≠ N/A — **полный** domain слой уже сгенерирован на spec-этапе через `domain-designer` (pure core + repository interfaces + use cases + in-memory fakes + зелёные JVM тесты).

Location зависит от project layout:
- Single-module Android: `app/src/main/kotlin/.../domain/<slug>/` + `app/src/test/kotlin/.../domain/<slug>/`
- KMP shared (default here): `shared/feature/<slug>/domain/src/commonMain/` + `.../commonTest/`

Phase-01 = **adapter-only integration phase**, НЕ create-from-scratch:
- **backend-dev adapter-only scope**: production-реализации repository interfaces из `domain/<slug>/repository/` (Room/Retrofit/Firebase-backed) в `data/`; DAO ↔ Domain mappers; DI bindings связывающие production repositories с domain interfaces. **НЕ переписывает domain. НЕ добавляет новые use cases или repository interfaces — они уже есть и покрыты тестами через fakes.**
- **test-dev**: integration tests (repository round-trip с реальной БД/сетью, DAO boundary tests). JVM тесты pure core + use case тесты через fakes уже зелёные — их НЕ дублирует.
- **frontend-dev**: работает с готовыми use cases через Decompose Component constructor/Koin factory. Compose screens получают state/callbacks и не знают о repositories/use cases напрямую.

Перед стартом phase-01 lead проверяет:
1. Existence: domain-директория содержит файлы (подпакеты `model/`, `state/`, `logic/`, `repository/`, `use_case/`)
2. Green tests: relevant project-layout task проходит (для KMP feature domain обычно `./gradlew :shared:feature:<slug>:domain:jvmTest --no-configuration-cache`; для Android-only fallback — `./gradlew test --tests "*<slug>*" --no-configuration-cache`)
3. Если нет — это ошибка spec pipeline. STOP, сообщи пользователю: spec должен был сгенерировать полный skeleton.

Если backend-dev в ходе реализации находит architectural mismatch (repository interface signature невозможно реализовать production adapter-ом; use case signature не подходит под реальную UI-интеграцию) — **это architectural mismatch**. Lead останавливается и эскалирует пользователю. Backend-dev НЕ переписывает domain, НЕ добавляет новые interfaces/use cases молча.

Если `Feature Domain Contract` = N/A — применяется стандартный phase-01 (backend-dev создаёт feature domain с нуля по плану).

### 2.1 Реализация (TDD-style: production + tests параллельно, coder owns full phase)

Назначь фазу нужным devs + test-dev **одновременно** через SendMessage. Prompt каждого teammate — **self-starting** и **self-contained**: содержит ПОЛНЫЙ workflow от реализации до broadcast reviewers и autonomous fix loop. Lead пассивен после spawn.

**Dev(s) — каждый получает свой role file + ПОЛНЫЙ workflow (Шаги 1-5):**

```
=== PHASE <N> — BACKEND ===
Начни работу НЕМЕДЛЕННО, без ack и без ожидания подтверждения.
Это твоё полное задание + workflow после реализации.

**Шаг 1 — Реализация:**
Прочитай ТОЛЬКО: docs/features/<slug>/plan/phase-NN/backend.md
Прочитай project rules: .claude/rules/clean-architecture.md, .claude/rules/di-patterns.md, .claude/rules/domain-models.md (и релевантные из phase file).
Реализуй. TaskUpdate(your_impl_task, in_progress).
НЕ читай frontend.md, overview.md, design docs соседних вертикалей.
НЕ шли промежуточных DM "в процессе" / "принято".

**Шаг 2 — Build Gate (ты сам запускаешь):**
```bash
./gradlew ciCheck --no-configuration-cache
# + фаза-specific команды из plan/phase-NN/overview.md секция Validation
```
Test Deletion Gate: `git diff --name-status HEAD -- '*/test/**'` — удалённые тесты должны быть в overview.md "Deleted Files".

Build FAIL:
- Fix в своём scope → retry
- Error в test code → SendMessage test-dev EVIDENCE (file:line + stacktrace)
- Не понимаешь root cause / repeated failure того же класса → SendMessage diagnostics EVIDENCE если diagnostics teammate поднят; иначе ERROR SendMessage lead-у с просьбой поднять diagnostics
- Нужен scope change / architectural mismatch → ERROR SendMessage lead-у, НЕ импровизируй

**Шаг 3 — Broadcast reviewers (когда build PASS):**
- TaskUpdate(build_task, completed) — unblocks reviewer tasks
- Для каждого reviewer (`architect-reviewer`, `code-reviewer`, `security-reviewer`, `completeness-reviewer`, опц. `concurrency-reviewer`) → SendMessage:

```
=== PHASE <N> REVIEW — ASSIGNMENT FROM CODER ===
Начни review НЕМЕДЛЕННО, без ack.

Feature: <slug>, Phase: <N>
Changed files: <полный список>
Build Status: PASSED (commit <sha-or-phase-ref>)
Build evidence: `./gradlew ciCheck --no-configuration-cache` + phase validation commands from overview.md, exit code 0; Test Deletion Gate OK
Your task ID: phase-NN-review-<type>
Coder contact: <me> (findings шлите мне напрямую)
Test author contact: test-dev

Прочитай plan/phase-NN/overview.md + свою область. Severity: blocker/high/medium/low, file:line.

Autonomous fix loop (БЕЗ lead-а):
1. Findings → EVIDENCE SendMessage coder-у НАПРЯМУЮ. Если finding в test code — test-dev.
2. Coder/test-dev исправляют → SendMessage тебе "re-check <file:line>"
3. PASS → TaskUpdate(completed) + ОДНОРАЗОВО SendMessage lead-у "review passed"
4. Architectural mismatch / repeated blocker того же класса / reviewer disagreement → ERROR SendMessage lead-у
НЕ промежуточные DM lead-у.
```

**Шаг 4 — Autonomous fix loop:**
Reviewer присылает EVIDENCE → исправляешь → SendMessage reviewer "re-check". Итерируй пока reviewer не пришлёт "passed". Параллельно работаешь со всеми reviewers.

**Шаг 5 — Финал:**
Когда все reviewers прислали lead-у "passed" И твоя реализация готова — SendMessage lead-у ОДИН RESULT (файлы, AC coverage, open questions). TaskUpdate(your_impl_task, completed).
НЕ делай промежуточный отчёт lead-у после реализации — lead узнаёт через final RESULT.
```

```
=== PHASE <N> — FRONTEND ===
Начни работу НЕМЕДЛЕННО, без ack.
Полный workflow (аналог backend):

**Шаг 1 — Реализация:** plan/phase-NN/frontend.md + .claude/rules/navigation.md. НЕ читай backend.md/overview.
**Шаг 2 — Build Gate:** ты сам запускаешь. Если есть backend-dev в фазе — координируй (broadcast делает тот кто последним проходит build).
**Шаг 3 — Broadcast reviewers** (template выше).
**Шаг 4 — Autonomous fix loop с reviewers.**
**Шаг 5 — Финал:** RESULT lead-у после всех review PASS.
НЕ промежуточные DM.
```

**test-dev (параллельно с dev, НЕ делает build gate):**

```
=== PHASE <N> — TESTS ===
Начни работу НЕМЕДЛЕННО, без ack.

Прочитай ТОЛЬКО: plan/phase-NN/tests.md + .claude/rules/testing.md
Напиши тесты. Используй fakes per проектной конвенции.

Когда готово — SendMessage coder-у EVIDENCE: "tests written, файлы X,Y,Z, ждут production deps/code". TaskUpdate(your_task, completed).

НЕ меняй production code. НЕ меняй scaffold — SendMessage lead-у с запросом (lead делегирует backend-dev-у).
НЕ запускай build gate — это делает coder.
В autonomous fix loop: reviewer шлёт finding про test code → исправляешь → SendMessage reviewer "re-check". Production fixes — coder.
```

**Спавн implementer-агентов СТРОГО по наличию файлов** в `plan/phase-NN/` + Debugger Team Composition Proposal:
- Есть `backend.md` → спавнь backend-dev
- Есть `frontend.md` → спавнь frontend-dev
- Есть `tests.md` → спавнь test-dev
- Нет role file → НЕ спавнь соответствующего implementer-а
- Debug/analysis teammates (`diagnostics`, `code-analyst`, `log-reader`, `web-researcher`) спавнятся только если proposal или failure trigger даёт конкретный scope

Агенты НЕ читают design docs, overview, или файлы других вертикалей. Только свой файл.

Это правило про изоляцию агентов обязательное: каждый implementer работает только в пределах своего role-file и не переоткрывает зафиксированную product/domain логику из spec. Если для выполнения не хватает контекста — это сигнал о проблеме в плане/phase file, а не повод читать соседние документы.

**ВАЖНО:** test-dev работает параллельно с dev(s), а не после. Если `tests.md` отсутствует а фаза меняет production code — ошибка плана; сообщи пользователю.

Для `phase-01` с integration scope (Walking Skeleton domain уже сгенерирован на spec) это означает:
- backend-dev реализует production adapters/data/Koin wiring вокруг уже готовых domain interfaces
- test-dev пишет integration/adapter tests и не дублирует pure domain JVM tests из spec
- если domain tests уже не зелёные на старте — STOP: spec pipeline не завершил Walking Skeleton

### 2.2 Build Gate (coder сам запускает, broadcast reviewers при PASS)

Build gate — **ответственность coder-а** (backend-dev / frontend-dev), не lead-а. У coder-ов есть Bash tool, они сами запускают:

```bash
./gradlew ciCheck --no-configuration-cache
```

Если фаза добавляла/меняла `androidTest` или поднимался `integration-tester` — coder дополнительно:
```bash
./gradlew assembleDebugAndroidTest --no-configuration-cache
```

**Test Deletion Gate (coder verification):** coder выполняет `git diff --name-status HEAD -- '*/test/**'`. Если удалены тесты — проверяет что каждый удалённый файл перечислен в секции "Deleted Files" текущего `plan/phase-NN/overview.md`. Если нет — восстанавливает.

**Build PASS** → coder сам broadcast всем reviewers через SendMessage (см. Шаг 2.3) "build passed, please review". НЕ ждёт lead-а.

**Build FAIL** → coder фиксит сам (если root cause очевиден и в своём scope) или SendMessage implementer-у/test-dev-у с evidence (если scope не его, напр. test fails из-за missing deps). Если root cause не очевиден, failure повторился, stacktrace указывает на DI/migration/lifecycle/concurrency/runtime или proposal включил debugger-on-call → SendMessage diagnostics EVIDENCE с command output + changed files + suspected phase. Diagnostics возвращает root cause и route-to-owner; coder продолжает fix loop. НЕ отправляет reviewers до PASS.

Если coder в ответ на build fail пытается менять scope, переопределять `Feature Domain Contract`, переносить feature-specific logic в `core/` без явного основания или читать соседние vertical docs — это **architectural mismatch**. Coder STOP и SendMessage lead-у как ERROR. Lead эскалирует пользователю.

**Lead роль в build gate:** пассивная. Lead мониторит TaskList (build_task → completed кем-то из coders) и не вмешивается пока не получит ERROR escalation или финальный review RESULT.

### 2.3 Review (coder broadcast reviewers, автономный fix loop)

**Build gate enforcement через TaskCreate/blockedBy** — reviewer tasks создаются при TeamCreate вместе со spawn reviewers:

```
TaskCreate: "phase-N-build"                        # → task_build (owned by backend-dev / frontend-dev)
TaskCreate: "phase-N-review-architect"             # → task_arch  (blockedBy: task_build)
TaskCreate: "phase-N-review-code"                  # → task_code  (blockedBy: task_build)
TaskCreate: "phase-N-review-security"              # → task_sec   (blockedBy: task_build)
TaskCreate: "phase-N-review-completeness"          # → task_comp  (blockedBy: task_build)
(опционально) TaskCreate: "phase-N-review-concurrency" (blockedBy: task_build) если фаза с тегом
```

Это **технически блокирует** reviewers от старта работы до `task_build = completed`. Они видят свои задачи в TaskList как `blocked`, сидят idle.

**Coder broadcast (после локального build PASS — см. Шаг 2.2):**

Coder (backend-dev / frontend-dev) сам пишет SendMessage каждому reviewer (можно через `to: "*"` broadcast или индивидуально) с self-starting prompt:

```
=== PHASE <N> REVIEW — ASSIGNMENT FROM CODER ===
Начни review НЕМЕДЛЕННО, без ack.

Feature: <slug>, Phase: <N>
Changed files: <list>
Build Status: PASSED (commit <sha-or-phase-ref>)
Build evidence: `./gradlew ciCheck --no-configuration-cache` green, phase validation commands green, Test Deletion Gate OK
Your task ID: <task_id>
Coder contact: <my name, backend-dev / frontend-dev>
Test author contact: test-dev

Прочитай plan/phase-NN/overview.md + свою область ответственности (design docs по твоей роли).
Severity: blocker / high / medium / low. Ссылки file:line.

**Autonomous fix loop (reviewer ↔ coder direct, БЕЗ lead-а):**
1. Нашёл findings (medium/high/blocker, severity + file:line) → EVIDENCE SendMessage НАПРЯМУЮ мне (coder), НЕ lead-у
2. Я исправляю → SendMessage тебе "исправлено, re-check <file:line>"
3. Re-check → если PASS, TaskUpdate(status: completed) + SendMessage lead-у ОДНОРАЗОВО: "review passed, 0 open findings"
4. Если после fix появляется NEW blocker того же класса (2-я или 3-я итерация того же типа finding) → ERROR SendMessage lead-у (escalation signal)
5. Если architectural mismatch (я хочу удалить функционал, сменить паттерн, пропустить AC) → ERROR SendMessage lead-у немедленно
6. Если другой reviewer нашёл противоречащий finding (reviewer disagreement) → ERROR SendMessage lead-у (CONTESTED verdict)

НЕ шли промежуточных DM lead-у. Lead получает только финальный RESULT (PASS) или ERROR (escalation).
```

TaskUpdate build_task → completed делает сам coder после broadcast (не lead). Это unblocks reviewer tasks — они больше не blocked в TaskList.

**Lead в этом Шаге — пассивный.** Не отправляет assignments reviewer-ам (coder делает), не участвует в fix loop (reviewer↔coder напрямую). Lead просто мониторит TaskList и получает финальные RESULT/ERROR от reviewers.

Ни один reviewer НЕ optional. Security-reviewer — полноправный участник автономного loop наравне с architect/code/completeness/concurrency.

**completeness-reviewer** получает дополнительно:
```
Сверь КАЖДЫЙ acceptance criterion из 0-spec.md с кодом.
Сверь КАЖДЫЙ пункт из plan/phase-NN/overview.md.
Проверь: нет ли удалённого/скрытого (View.GONE) функционала без обоснования в phase file.
Проверь: нет ли комментариев "removed", "simplified", "stats removed" без ссылки на spec/phase.
```

**Правила для same-model reviewers** (компенсация shared blind spots):
- Reviewer ОБЯЗАН проверять field access: каждое обращение к полю объекта из внешней системы (SDK, API response) — cross-check с research/grounding что поле существует и тип совпадает
- Reviewer ОБЯЗАН проверять async timing: если два потока данных сходятся (fetch + observe) — что происходит при разном порядке завершения

**Codex CLI НЕ запускается per-phase.** Cross-model adversarial review происходит в Шаге 3 (Cross-Phase Review) после **полного завершения всех фаз**. Per-phase review — только same-model teammates (architect/code/security/completeness/concurrency).

### 2.4 Verdict (lead passive — получает только финальный результат)

Lead получает от каждого reviewer один из:

| Message type | Условие | Lead action |
|--------------|---------|-------------|
| NOTIFICATION "review passed" | Reviewer отчитался PASS после autonomous fix loop | Пометь review task completed, жди остальных reviewers |
| ERROR escalation | Architectural mismatch / CONTESTED / escalation signal (повторяющиеся findings того же класса) | **STOP фазу**, прочитай evidence, **спроси пользователя** |

Lead **не участвует** в fix loop. Reviewer↔coder автономно итерируют до PASS или до escalation trigger.

**Когда ВСЕ reviewers прислали NOTIFICATION "passed"** → фаза PASS, переход к следующей.

**Если хотя бы один ERROR** → фаза paused, lead эскалирует пользователю с полным контекстом (что reviewer нашёл, что coder попытался, почему это design gap или architectural mismatch).

Нет hard cap на количество итераций внутри autonomous loop. Escalation триггерится по сигналу (architectural mismatch, repeated blocker того же класса, reviewer disagreement), не по счётчику.

## Шаг 2.5: Smoke Test BEFORE Cross-Phase Codex Review (REQUIRED)

После того как **все фазы PASS от same-model reviewers** — запусти **smoke test** и **E2E instrumented tests** ДО Codex review. Codex review должен получить green build, не build-broken state.

**Source rationale**: quizzes-screen retrospective Bug #1, #4 — `KoinModuleWiringTest` имел stale constructor + missing modules; phase-07 frontend-dev claimed PASS на subset suite (`:android:feature:quizzes-screen:presentation:test`), full suite не запускался. Smoke test поймал bag после Codex review (post-Codex) — backwards order. Codex spent budget на build-broken state instead of design issues.

### 2.5.1 Full ciCheck (Android JVM + KMP allTests + detekt + ktlint)

```bash
./gradlew ciCheck --no-configuration-cache
```

Lead **обязан**:
- Verify exit code 0 (BUILD SUCCESSFUL)
- Скопировать last 30 lines output в TaskList comment / implementation.md draft
- НЕ accept "all tests passed" claim coder-а без gradle output verification

Если падает → fix loop с relevant dev (через SendMessage) → повторить ciCheck. Не переходи к Codex (Шаг 3) до зелёного ciCheck.

### 2.5.2 Instrumented test APK build

Если фаза changed `androidTest` или фича имеет UI flow:

```bash
./gradlew assembleDebugAndroidTest --no-configuration-cache
```

### 2.5.3 E2E Instrumented Test Stage (NEW — lesson-runner retro Fix #5)

Если фича имеет UI flow с lifecycle dependencies (rotation, system Back, FLAG_SECURE, save state restore) → **обязательный** E2E instrumented test:

- Test runs против real Decompose Component graph + Compose UI + Room DB (not pure unit)
- Lifecycle scenarios included: rotation, system Back, configuration change recreation, low-memory recreation simulation
- Если connected device доступен:

```bash
./gradlew connectedAndroidTest
```

- Если no device — тест **не считается completed**; лид помечает в `implementation.md` "Manual smoke на device required" + escalates пользователю с AskUserQuestion (запустить APK manually или defer to user)

**Почему обязательно**: lesson-runner Bug #9-11 (rotation drafts lost, system Back bypass, FLAG_SECURE timing) — эти ошибки **invisible** в JVM unit tests и Compose preview tests. Только real Android Activity + lifecycle их catch'ит. Manual smoke deferred = bugs missed.

### 2.5.4 Pipeline docs check defensive layer

Hooks (`check-plan-paths.sh`, `check-c4-vs-gradle.sh`, `check-api-contract-types.sh`) запускаются deterministically при каждом save и flag drift документов. Это complementary к smoke test — hooks catch design-doc drift, smoke test catches integration drift.

Перед Codex review lead дополнительно запускает агрегированный deterministic check:

```bash
scripts/pipeline/check_pipeline_docs.sh docs/features/<slug>
```

Если check падает → исправить docs/process artifact до Codex review. Codex должен тратить budget на архитектурные и поведенческие риски, не на mechanically detectable drift.

## Шаг 3: Cross-Phase Review (Codex CLI — единственная точка cross-model review)

После **зелёного smoke test** (Шаг 2.5) — запусти cross-phase Codex review.

**Codex CLI (cross-model adversarial review)** — ЕДИНСТВЕННАЯ точка в пайплайне, где задействуется другая модель. Per-phase review использует только same-model teammates. Здесь Codex проверяет результат всех фаз вместе, ловит shared blind spots same-model reviewers.

**Сначала smoke test, потом Codex** — это критическая последовательность. Codex more useful when given working code, не build-broken state.

Используй skill `adversarial-review` и его `references/cli-protocol.md`. Codex получает:
- Полный source diff всех фаз
- Список phase overview-файлов + role files
- `0-spec.md` (AC + Feature Domain Contract)
- Primary focus: cross-phase integration (DI chain, orphaned abstractions, контракты между фазами)

Параллельно повторно задействуй all same-model reviewers (architect/code/security/completeness/concurrency) для cross-phase контекста:
- Полный source review всех изменений
- Граф зависимостей, DI chain, orphaned abstractions
- Security audit на уровне фичи
- Completeness vs plan + 0-spec.md

Все findings (Codex + same-model) идут через autonomous loop → implementer-ы фиксят → reviewers re-check. Lead получает финальный RESULT когда все прислали PASS или ERROR escalation.

**Если cross-model review (Codex CLI) недоступен** — это **blocker**. Lead останавливается и спрашивает пользователя (fallback: прогон same-model reviewers с удвоенной внимательностью к concurrency/lifecycle/security, зафиксировать в implementation.md как known gap).

Проверь Spec Scenario Coverage:
- Каждый GIVEN/WHEN/THEN из `0-spec.md` → integration test
- Каждый `Domain Test Scenario` из `Feature Domain Contract` → JVM или integration test в зависимости от слоя
- Если пропущен → назначь test-dev

## Шаг 3.5: Quality Scorecard

После Codex cross-phase review — сгенерируй `docs/features/<slug>/quality-scorecard.md`.

Парсишь Codex output file и считаешь findings по severity для каждого параметра:

```markdown
# Quality Scorecard: <feature-slug>

| Параметр | Grade | Blockers | High | Medium | Детали |
|----------|-------|----------|------|--------|--------|
| Architecture | A-F | N | N | N | Нарушения границ, DI |
| Correctness | A-F | N | N | N | Баги, race conditions |
| Completeness | A-F | N | N | N | Пропущенные AC |
| Security | A-F | N | N | N | Vulnerabilities |
| Code Organization | A-F | N | N | N | File structure, naming |
| **Overall** | A-F | | | | |

Grading: A = 0 findings, B = only medium, C = 1-2 high, D = 3+ high, F = any blocker
```

Это оценка качества реализации ДО вмешательства Codex — метрика pipeline quality.

## Шаг 4: Post-Codex Final Smoke Test

Это финальная проверка после Codex/fix loop. Не дублируй Шаг 2.5 mechanically: цель здесь — подтвердить, что fixes после Codex не сломали build/docs gates.

```bash
scripts/pipeline/check_pipeline_docs.sh docs/features/<slug>
```

```bash
./gradlew ciCheck --no-configuration-cache
```

Если в финальном diff есть `androidTest` изменения — дополнительно:

```bash
./gradlew assembleDebugAndroidTest --no-configuration-cache
```

Если feature требует device/backend smoke — после зелёного `ciCheck` запускай documented command из `plan/phase-NN/overview.md` или `.claude/PROJECT-CONTEXT.md`. Не используй несуществующие scripts как proof.

```bash
<documented device/backend smoke command>
```

Если падает → исправить → повторить. НЕ продолжать при failing tests.

## Шаг 4.5: Deferred HIGH/BLOCKER findings — explicit user approval

Если cross-phase Codex review (или smoke test, или per-phase reviewer) выдал finding **severity HIGH или BLOCKER**, который impl phase не fix'ит а помечает как **DEFERRED** (post-MVP, next feature, accepted debt) — Lead **обязан** через AskUserQuestion получить explicit approval до handoff.

Шаблон AskUserQuestion:
```
question: "Found <severity> issue '<short>' that won't be fixed in this feature scope. Accept as known debt?"
options:
  A) Accept defer — добавь в implementation.md Remaining Issues с явным owner/gate (post-MVP / followup ticket / next feature)
  B) Block handoff — implement fix loop now (severity warrants in-feature)
  C) Reduce severity — finding не actually HIGH/BLOCKER (lead обоснует)
  D) Other (free text)
```

Если user не approves defer → fix loop, не handoff.

В implementation.md Remaining Issues каждый deferred item ОБЯЗАН включать:
```
- **<ID>**: <symptom>
  - Severity: <HIGH | BLOCKER>
  - Owner: <who fixes — post-MVP / followup ticket #N / next feature>
  - Gate: <when fixed — before next implementation / next sprint / etc>
  - Rationale: <why deferred>
  - User approval: <date> via AskUserQuestion (<reference>)
```

**Source rationale**: home-and-my-quests retrospective Bug #7, #9 — 5 of 8 HIGH findings deferred without user approval (DefaultRootComponent layer violation, getKoin() в Composable). Это violation CLAUDE.md "Escalate, не импровизируй" — silent debt accept = pipeline bug. Decisions about deferring HIGH must be user-approved.

## Шаг 5: Handoff

Запиши `docs/features/<slug>/implementation.md`:
- Summary, Phases Completed, Review Verdicts, Changed Files, Remaining Issues (с user-approved defer entries из Шаг 4.5)

Обнови README.md: Status: `implemented`.

TeamDelete: `"feature-<slug>-impl"`

## Handling Mismatches

| Тип | Действие |
|-----|----------|
| Minor (fix-oriented, нет конфликтов) | SendMessage инструкции implementer-у |
| Architectural (agent хочет удалить функционал, сменить паттерн, пропустить модуль) | **Lead STOPS, спрашивает пользователя** |

Architectural mismatch = любое изменение, не описанное в phase file: скрытие UI элементов, удаление методов, упрощение логики, изменение API контрактов.

## Правила

- **ЗАПРЕЩЕНО вызывать Agent без team_name для devs/reviewers** — все in-team агенты только через Teams API. Исключения: phase-lead агенты в hierarchical pattern (спавнятся без team_name, создают свою sub-team) и preflight `diagnostics` debugger-advisor (read-only Team Composition Proposal до TeamCreate)
- **Delegate Mode** — lead координирует, НЕ пишет код, НЕ редактирует файлы
- **Self-starting prompts** — каждый SendMessage assignment содержит "Начни НЕМЕДЛЕННО, без ack", путь к role file, list обязательных rules, формат финального отчёта. Prompt — полное задание, не "приветствие"
- **=peer DM for evidence/action, not status=** — teammates шлют DM друг другу ТОЛЬКО для evidence (finding, diff, file:line) или action request (fix, re-check, проверь log). НЕ шлют "принято, жду", "ack", "в процессе" — это турны впустую. Статус идёт через TaskUpdate, не через DM
- **Autonomous reviewer ↔ coder fix loop** — findings идут напрямую от reviewer к implementer через SendMessage (EVIDENCE), implementer фиксит, re-check у того же reviewer. Lead НЕ участвует в fix loop. Lead получает только финальный RESULT (PASS) или ERROR (escalation: architectural mismatch / repeated blocker / reviewer disagreement)
- **Debugger routes failures, not status** — diagnostics/code-analyst/log-reader получают только evidence-bearing requests: command output, stacktrace, changed files, device serial, suspected phase. Они возвращают root cause + route-to-owner, не принимают product/scope decisions и не пишут production code
- **Полная команда reviewers обязательна** после каждой фазы — code, architect, **security**, completeness (+ concurrency если тег). Security-reviewer НЕ опциональный, НЕ "потом" — полноправный участник autonomous loop, проверяет каждую фазу параллельно с остальными. Ни один reviewer не пропускается, ни один не считается "secondary"
- **Phase scope coordination через lead** — координация фаз и решения о scope = задача lead-а через phase files. Dev'ы НЕ договариваются между собой "кто что делает" через DM. Reviewer↔coder peer DMs разрешены ТОЛЬКО для findings/fix verification, не для переговоров о scope
- **Build gate через blockedBy** — reviewer tasks создаются через TaskCreate с `addBlockedBy: [build_task]`. Это технически блокирует reviewers от старта до build pass, не надежда на compliance
- **Codex CLI — только cross-phase (Шаг 3)** — per-phase review использует ТОЛЬКО same-model reviewers. Codex задействуется один раз, после полного завершения всех фаз, как cross-model adversarial review финального состояния фичи. Экономия токенов + focus на integration issues (DI chain, orphaned abstractions) которые видны только cross-phase
- **Scaffold file ownership** — `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, `AndroidManifest.xml` (root) меняет только backend-dev. Другие teammates запрашивают через lead
- **Test Deletion Gate** — удалённые тесты проверяются через `git diff` до review
- No auto-commit, no push без просьбы
- No skipping phases, reviews, gates
- Build gate обязателен ДО review
- Autonomous fix loop итерирует до PASS. Нет hard cap на итерации. Escalation триггерится по сигналу (architectural mismatch, repeated blocker того же класса — 2-3 итерации, reviewer disagreement), не по счётчику
- При отклонении от design — сначала обнови `03-decisions.md`
- Используй паттерны из кодовой базы, не generic/textbook patterns
- **Изоляция по фазам**: TeamDelete + TeamCreate между каждой фазой. Свежие агенты, свежий контекст. Не переносить teammates между фазами
