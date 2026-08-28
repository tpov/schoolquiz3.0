# Build-spec: Kent-воркфлоу «Quiz Feature Pipeline»

Миграция Claude-Code-пайплайна квиз-проекта (7 команд `feature-*`, 21 субагент, 8 скилов, 11 rules, 6 hooks) в новый Kent-воркфлоу + новый Kent-проект для `/Volumes/EXTERNAL/schoolquiz3.0`.

Источники:
- Квизовая редакция: `/private/tmp/claude-501/-Users-tpov-tg-watch-userbot/1672033a-247c-4679-8d37-c1d0b621863e/scratchpad/quiz-claude/.claude` (приоритетный источник структуры).
- Структурный референс: Skillify Feature Pipeline (`notes/kent-graph-playbook.md`) — используем его паттерны (стадия = диспетчер → фан-аут → join → synth → crossmodel review → verdict), но при расхождениях копируем квизовые команды.

Итоговые параметры графа: **80 нод, 132 ребра, 9 join-нод (у каждой ровно 1 исходящее ребро), 6 approval-рёбер, 5 judge-рёбер continue_session**.

---

## 1. Обзор: спайн стадий и отличия от Skillify

### 1.1 Спайн

```
backlog (start) ──[intake, param feature]──> spec_intake  (единственный выход start-ноды)
SPEC: spec_intake (product-manager, Phase 0 + ТРИАЖ: новая фича или баг на готовой?)
     ├─ to_debug (баг-репорт на готовой фиче) ─────────────────────────────→ DEBUG (standalone-диагностика)
     ├─ server_needed → spec_server (codebase-researcher, READ-ONLY серверная реальность ДО диалога)
     │                    → server_done → spec
     └─ no_server ─────────────────────────────────────────────────────────→ spec
  spec (product-manager, Phase 2 диалог + Phase 3 запись 0-spec.md + Phase 3.5 Domain Contract Lock)
     ├─ Contract ≠ N/A → WALKING SKELETON: skeleton_sig (domain-designer, Stage A)
     │     ──fan-out──> {skeleton_bodies (domain-designer), skeleton_tests (test-dev)}
     │     → skeleton_join → skeleton_gate (lead, gradle)
     │         advance → spec_review | rerun → skeleton_sig | respec → spec
     └─ Contract = N/A → spec_review (crossmodel gpt-5.4) ──judge──> spec_verdict
           approved [APPROVAL] → research | needs_changes → spec
RESEARCH: research (lead-диспетчер)
  ──fan-out──> {research_codebase, research_core, research_crossfeat, research_web}
  → research_join → research_synth (lead: синтез + State-Matrix-validation + 2-grounding.md)
      review → research_review ──judge──> research_verdict | rerun → research
      research_verdict: approved → design | needs_changes → research
DESIGN: design (lead) ──fan-out──> {design_arch_high, design_arch_comp, design_web}
  → design_join → design_synth (design-architect: свод + детерминированные гейты-хуки)
      review → design_review (3 линзы) ──judge──> design_verdict | rerun → design
      design_verdict: approved [APPROVAL] → plan | needs_changes → design
PLAN: plan (lead) — ДВА исхода: plan_team → plan_planner (planner) | respec → spec (F5: skeleton-блокер/re-spec)
  plan_planner → plan_gate (lead: check-plan-no-code + check-plan-paths + check_pipeline_docs)
      pass → plan_review (crossmodel, Sequencing + Plan-as-ТЗ; same-model plan_reviewer СНЯТ — D5)
             ──judge──> plan_verdict
      fail → plan_planner
      plan_verdict: approved [APPROVAL] → impl_preflight | needs_changes → plan
IMPLEMENT: impl_preflight (diagnostics, Team Composition) → implement (lead, Delegate Mode)
  ──fan-out 'dev'──> {dev_backend, dev_frontend, dev_firebase, dev_test, dev_integration}
  → dev_join → build_gate (lead: ciCheck + Test-Deletion Gate)
      build_failed → implement
      review ──fan-out──> {review_code, review_architect, review_security,
                           review_completeness, review_concurrency}   (5 same-model; cross-model СНЯТ — D4)
      → review_join ──judge (continue_session, контекст implement)──> verdict (lead)
          needs_changes → implement | next_phase → implement | phases_done → impl_smoke
  implement ──debug──> debug (живая диагностика посреди реализации)
CROSS-PHASE: impl_smoke (lead: smoke-гейты + E2E + docs-check) — ТРИ исхода:
      smoke_failed → implement
      debug → debug (D3: финальный E2E/UI выявил рантайм-баг → живая диагностика)
      crossphase ──fan-out──> {cp_crossmodel, cp_code, cp_architect, cp_security, cp_completeness}
  → cp_join → cp_verdict (lead: scorecard + final smoke + deferred findings)
      fix → implement | handoff [APPROVAL] → retrospective
DEBUG (входы: spec_intake·to_debug | implement·debug | impl_smoke·debug):
  debug (lead, Phase 1) ──fan-out──> {dbg_doc, dbg_log_scan} → dbg_p1_join
  → dbg_advisor (diagnostics, Team Composition Proposal) → dbg_decision (lead, A/B/C/D)
      fixspec → dbg_fixspec | deep ──fan-out──> {dbg_code, dbg_log, dbg_doc2, dbg_web}
      moreinfo → debug
      deep-team → dbg_join → dbg_synth (lead, convergence)
          fixspec → dbg_fixspec | rerun → dbg_decision
  dbg_fixspec (lead, Fix Spec + выбор A/B/C/D, терминальный выбор по контексту запуска):
      apply_direct [APPROVAL] → dbg_fix_dev (coder) → dbg_fix_review (code-reviewer)
          passed_done → done | passed_impl → implement | fix → dbg_fix_dev
      to_plan → dbg_plan (planner: fix-spec → план) → to_impl → implement   (D3: ОСНОВНОЙ путь, заменил handoff)
      defer_done → done | defer_impl → implement | revisit → dbg_decision
RETROSPECTIVE: retrospective (lead, evidence-гейт)
  ──fan-out──> {retro_artifacts, retro_instructions} → retro_join
  → retro_synth (lead: root cause, 12 failure patterns, proposed fixes)
      apply [APPROVAL] → retro_apply (lead) → done | no_fixes → done
done (terminal)
```

### 1.2 Чем квизовая редакция отличается от Skillify-структуры (и что меняется в графе)

| # | Отличие квизовых команд | Изменение в графе относительно Skillify |
|---|---|---|
| 1 | **Нет стадии `rules` (rules-generator)** — вместо генерации core-правил квиз генерирует **Walking Skeleton (Variant Y)** прямо на spec-этапе (`feature-spec.md` Phase 3.8: domain-designer Stage A signatures → Stage B bodies ∥ test-dev tests → gradle-гейт с 5 исходами). | Нода `rules` удалена; вместо неё подграф из 5 нод `skeleton_sig → {skeleton_bodies, skeleton_tests} → skeleton_join → skeleton_gate` между `spec` (Phase 3.5 Domain Contract Lock) и `spec_review`, с петлями `rerun` (перезапуск стейджей) и `respec` (scope overflow → назад в spec / Task Splitting). Новая роль `domain-designer`. |
| 2 | **Research богаче**: обязательный **cross-feature dependency scanner** (Шаг 0.8) — отдельный параллельный агент; core-scan; grounding с Independent Verification Protocol и BLOCKER→RESOLVED-гейтом. | В фан-аут research добавлена 4-я нода `research_crossfeat`; промпт `research_synth` несёт grounding-карточки, `[VERIFIED]/[CONTRADICTS]`, BLOCKER-резолюции и broadcast-сводку для design (передаётся через файл `1-research.md`, секция Cross-Feature Interactions). |
| 3 | **Design-гейты детерминированные**: Gates 5–8 (rg REQUIRES VERIFY, module direction audit, ADR↔api-contract round-trip, Multi-Path State Machine) + hooks `check-c4-vs-gradle.sh`, `check-api-contract-types.sh`. | В Kent PostToolUse-хуков нет → все детерминированные проверки зашиты в промпт `design_synth` (запуск bash/hook-скриптов вручную) — до crossmodel-ревью. |
| 4 | **Plan**: hook-блокеры `check-plan-no-code.sh` (exit 2), `check-plan-paths.sh` + агрегатор `scripts/pipeline/check_pipeline_docs.sh`; ревью Codex с обязательной линзой **plan-review-lens** («Plan = ТЗ, Signature Card»); структура плана per-role: `plan/phase-NN/{overview,backend,tests[,frontend]}.md`. | Добавлена нода `plan_gate` (lead) между planner и plan_review с петлёй `fail → plan_planner`. Same-model `plan_reviewer` **снят** (D5): `plan_gate --pass--> plan_review` напрямую; его детерминированные проверки (сигнатуры vs research, Cross-Phase/DI closure, AC-покрытие) перенесены в промпт `plan_review` (cross-model), который несёт оба lens (Sequencing + Plan-as-ТЗ). Плюс F5: у `plan` появился 2-й исход `respec → spec` (skeleton-блокер / нужно менять domain). |
| 5 | **Implement**: preflight-advisor `diagnostics` (Team Composition Proposal), Run Ledger (`run/pipeline-state.json` + `run.jsonl`), coder-owned build gate, per-phase петля, затем отдельные шаги 2.5–4.5: smoke → cross-phase review (Codex ∥ same-model) → scorecard → final smoke → deferred-approval. | Добавлены ноды `impl_preflight` (между plan_verdict и implement) и **cross-phase подграф** `impl_smoke → {cp_*}×5 → cp_join → cp_verdict` (в Skillify его не было — verdict сразу шёл в retrospective). Per-phase review = **5 same-model** ревьюеров (`review_crossmodel` **снят** — D4: Codex per-phase НЕ запускается, cross-model только на cross-phase `cp_crossmodel`). `verdict` получает 3 исхода: needs_changes / next_phase / phases_done (петля по фазам явная). |
| 6 | **Debug трёхфазный** (`feature-debug.md`): Phase 1 (doc-analyst + log-reader quick scan, всегда) → diagnostics-advisor → user-решение A/B/C/D → conditional Phase 2 (deep team) → Phase 3 Fix Spec (fix НЕ применяется без approve; исходы: direct apply / plan+implement / defer / revisit). | Вместо одного фан-аута Skillify — 16 нод: Phase-1-подграф, нода решения `dbg_decision`, deep-фан-аут, `dbg_fixspec`, планировщик фикса `dbg_plan` (D3, роль planner) и мини-подграф прямого фикса `dbg_fix_dev → dbg_fix_review`. **Несколько входов** в `debug` (D3): `spec_intake·to_debug` (standalone на готовой фиче — триаж интейка распознаёт баг-репорт), `implement·debug` (живая диагностика), `impl_smoke·debug` (рантайм-баг из финального E2E). `dbg_fixspec` перемаршрутизирован: `to_plan → dbg_plan → implement` (ОСНОВНОЙ путь, заменил handoff), `apply_direct → dbg_fix_dev` (small 1-5 строк), `defer`/`passed` — **выбор по контексту** (done если standalone, implement если посреди реализации). |
| 7 | **Retrospective** — самоулучшение: обязательный evidence-гейт (WAIT feedback), 12 failure patterns, WebSearch, framework выбора инструмента, **применение правок инструкций только после approve**. | Добавлены evidence-гейт в промпт `retrospective`, approval-ребро `apply` и нода `retro_apply`. |
| 8 | Skillify «Codex» = crossmodel-reviewer нода. Квиз зовёт **внешний CLI codex exec** (skill adversarial-review: «отказ CLI = стоп»). | Внешний CLI не переносится: cross-model точки = agent-ноды роли `crossmodel-reviewer` на **gpt-5.4** (другая модель, чем спайн gpt-5.5) — функциональный эквивалент. Гейт «CLI недоступен = blocker» становится неактуален. |
| 9 | Квизовые **AskUserQuestion/WAIT-точки** разбросаны по всем командам. | WAIT = (а) `kent ask_question` внутри нод (диалог spec, выбор A/B/C/D в debug, evidence в retro), (б) requires-approval на 6 стыковых рёбрах. |
| 10 | GLM-sidecar (`glm_query.py`, ZAI) у codebase-researcher / code-analyst / log-reader. | Оставлен как **опциональный** bash-вызов в промптах (fail-open); если ключа нет — пропустить и отметить в отчёте (отклонение от «стоп», см. §6). |
| 11 | **Pipeline tier** (Light/Medium/Heavy/Critical): в источнике маршрутизирует граф (Light пропускает research/design/plan, Medium — design). | **D1 — осознанное отклонение**: tier фиксируется нодой `spec_intake` в `0-spec.md` как **advisory** метаданные/приоритет, но граф по тиру **НЕ маршрутизируется** — полный пайплайн (skeleton→research→design→plan→implement→cross-phase→retrospective) проходят ВСЕ фичи. Проще/безопаснее для v1; stage-skipping можно добавить позже. Промпты `spec_intake` и `spec` сохраняют выбор тира, помечая его advisory. |

### 1.3 Конвенции графа

- Модели: спайн (lead, синтезы, вердикты, диспетчеры) = gpt-5.5/xhigh; работники = gpt-5.5/high; crossmodel-ревьюеры = **gpt-5.4/xhigh**; модель/усилие фиксируются в `display_name` суффиксом (как в Skillify) и в config.toml ролей.
- `completion_mode` не указываем ни у одной ноды (дефолт), `output_fields` не объявляем: обмен данными — через файлы `docs/features/<slug>/…` и комментарии задачи (паттерн Skillify).
- Промпт ноды = постоянная роль («кто ты всегда»), промпт ребра = задание на переход («что сделать сейчас»); большие стадийные промпты — на рёбрах вердиктов (как в Skillify).
- Все петли: `new_session`, `immediate_source`, approval=0; edge-промпт петли всегда говорит, где лежат findings.
- Join-ноды: ровно 1 исходящее ребро (грабля №1). Все фан-ауты — из agent-нод.
- Скилы/rules НЕ инлайнятся целиком: промпты содержат инструкции «прочитай файл `.claude/rules/...md` / `.claude/skills/.../SKILL.md`» (файлы восстановлены в рабочее дерево, §5); управляющие формулировки (Iron Law, Delegate Mode, вердикт-логика PASS/CONTESTED/REJECT и т.п.) продублированы в промптах дословно, т.к. агент Kent не увидит исходные команды.
- Строить с нуля одним прогоном (CLI не умеет удалять) — порядок команд в §7.

---

## 2. Каталог НОД (80)

Формат: `key` — display_name · kind · subagent_role · completion_mode (везде default) · полный prompt_template. У start/join/terminal промпт пуст.

### 2.1 SPEC + Walking Skeleton (11 нод)

#### `backlog` — Backlog · start · (пусто)

#### `spec_intake` — Spec Intake (product-manager) · product-manager · gpt-5.5/high
```text
Интейк и триаж задачи — БЕЗ диалога и БЕЗ записи 0-spec.md (это Phase 2 ноды spec). Прочитай AGENTS.md, .claude/PROJECT-CONTEXT.md, docs/invariants.md; НЕ читай skills, agents и исходники. ТРИАЖ (первым делом): если описание задачи Kent — это БАГ-РЕПОРТ / запрос диагностики на УЖЕ существующей фиче (симптом/краш/ошибка в готовом коде, а не новая фича/доработка/интеграция/рефакторинг) → transition to_debug (нода debug — standalone-диагностика; в задании передай feature-slug и описание проблемы), дальше по Phase 0 НЕ иди. Иначе — это новая фича/изменение: выполни Phase 0. Из описания задачи Kent: (1) сгенерируй feature-slug (kebab-case); (2) создай docs/features/<slug>/ (если нет) и заготовку 0-spec.md с пустыми секциями Server-Side Context / Server-Side Issues (заполнит server analysis); (3) определи feature type (new/enhancement/integration/refactoring); (4) выбери pipeline tier (Light/Medium/Heavy/Critical) — это ADVISORY метаданные/приоритет: фиксируется в 0-spec.md, но граф по тиру НЕ маршрутизируется, полный пайплайн проходят ВСЕ фичи (осознанное отклонение от источника, где Light пропускает research/design/plan); при сомнении между двумя — более высокий, причина в Decision Ledger; Critical = auth/privacy/security/payment/irreversible migration/data loss/external cost; (5) реши, нужен ли server analysis (integration/API/фича затрагивает серверное взаимодействие → нужен). Не веди диалог и не фиксируй требования. Исходы: баг-репорт на готовой фиче → transition to_debug (standalone-диагностика); нужен серверный анализ → transition server_needed (нода spec_server, READ-ONLY серверная реальность ДО диалога); не нужен → transition no_server (сразу к продуктовому диалогу spec).
```

#### `spec_server` — Server Analysis (codebase-researcher) · codebase-researcher · gpt-5.5/high
```text
READ-ONLY анализ серверного кода для фичи — ДО продуктового диалога (порядок как в источнике: server → dialog → write). Если заготовка 0-spec.md / описание задачи указывает, что фича затрагивает серверное взаимодействие, и путь к серверному проекту известен (спроси пользователя через kent ask_question: указать путь или пропустить) — задокументируй: routes, validation, response, auth, side effects (events/jobs/notifications/cache), rate limiting, database (migrations/models/relationships), проблемы, которые НЕЛЬЗЯ обойти на клиенте. Только факты с file:line, никаких предложений изменений. Впиши результат в 0-spec.md секции Server-Side Context и Server-Side Issues (issue / почему нельзя починить на клиенте / recommended server change / impact — простыми словами), чтобы продуктовый диалог видел серверные ограничения. Если серверной части нет — пройди насквозь без изменений. Единственный исход: transition server_done (передать серверную реальность в диалог spec).
```

#### `spec` — Spec (Dialog + Write) · product-manager · gpt-5.5/xhigh
```text
ТЗ фичи, Phase 2 (диалог) + Phase 3 (запись) + Phase 3.5 (Domain Contract Lock). Ты — опытный product-менеджер и UX-эксперт: не просто записываешь требования, а думаешь о фиче как продуктовый человек — предлагаешь решения по UI и логике, видишь дыры, задаёшь неудобные вопросы. Фиксируешь ЧТО нужно сделать, КАКИЕ ограничения и ЧТО искать в codebase. НЕ проектируй КАК реализовать (design phase), НЕ исследуй кодовую базу глубоко (research phase). feature-slug, docs/features/<slug>/, feature type и pipeline tier уже определены нодой spec_intake (Phase 0); tier — ADVISORY (метаданные/приоритет в 0-spec.md, граф по тиру НЕ маршрутизируется, полный пайплайн для всех фич): сохрани его, при необходимости уточни. Прочитай AGENTS.md, .claude/PROJECT-CONTEXT.md, docs/invariants.md и — если spec_server уже заполнил секции Server-Side Context / Server-Side Issues — учитывай серверную реальность в диалоге (клиент не обходит серверные ограничения); НЕ читай skills, agents и исходники.
Phase 2 (ГЛАВНАЯ — ты НЕ генерируешь spec, пока не разберёшься в фиче полностью): диалог с пользователем через kent ask_question по паттерну Smart Defaults → Confirm → Drill-down. Шаг 1: построй полную догадку (user flow, UI, логика, edge cases, scope MVP) и закончи фразой «Это моя догадка. Где я прав, где нет?». Шаг 2: 2-4 уточняющих вопроса за раз, к каждому своё мнение («Я бы сделал так: ... Но решать тебе»). Autonomy Policy: Low/Medium risk — решай сам + Decision Ledger (rationale + rollback); High/Blocked — ТОЛЬКО пользователь; НИКОГДА не принимай решение за пользователя без явного «реши сам». Каждый leaf-узел закрывается как [USER DECIDED] или [DELEGATED: решение + обоснование]; открытые узлы остались → ещё вопросы, дальше не идти. Условные подшаги: State Matrix (ветвистая логика; каждая ячейка = один test case, пустые/двусмысленные ячейки закрывай вопросами, «не откладывай в research/design/plan»); Primary User Journeys (happy + recovery + interrupted path, либо явный N/A); ОБЯЗАТЕЛЬНЫЙ чеклист 5 ситуаций для фич с данными/сетью/состояниями (первый запуск; смена пользователя; offline; параллельные действия; background/process death — каждая [USER DECIDED]/[DELEGATED]/[N/A—обоснование]); Cross-cutting ADR re-validation (grep по docs/features/*/03-decisions.md и docs/decisions/, секция «Cross-Cutting ADR Impact»); Feature Domain Contract (текстовый: terms/rules/state transitions/error recovery/Domain Test Scenarios в GIVEN/WHEN/THEN; неясное правило → спроси СЕЙЧАС). Phase 2.5: если фича делится (2+ независимых flow, >8 AC, разные модули) — спроси пользователя: A) разделить на отдельные фичи, B) оставить одной, C) master + sub-specs.
Финальная сводка + подтверждение пользователя («Вот итоговая картина... Я что-то упустил?») — только после этого Phase 3: запиши docs/features/<slug>/0-spec.md по шаблону (frontmatter date/feature/type/commit; секции: Source c pipeline tier (advisory) + reason, Requirements с метками [USER DECIDED]/[DELEGATED], Scope In/Out, User Decisions, Decision Ledger, Assumption Ledger (High-risk assumption блокирует следующую фазу), Server-Side Context (из spec_server, если был), Search Criteria for Research (самая важная часть spec: конкретные критерии, обязательные directions, completeness check «все catch-блоки/error callbacks/fallback branches, счёт сверить с grep»), Primary User Journeys, Feature Domain Contract, Delegated Decisions Summary, State Matrix, Acceptance Criteria (GIVEN/WHEN/THEN чекбоксы), Invariant Check, Constraints). Phase 3.5 Domain Contract Lock: есть бизнес-логика → Contract обязателен и Walking Skeleton будет сгенерирован дальше; чисто UI/integration → Contract = N/A с обоснованием. Создай/обнови docs/features/<slug>/README.md (Status: spec). Ничего не генерируй в core/. Правила: risk-based autonomy; no architecture (домен-имена разрешены только для будущего domain/<slug>/{model,state,logic,repository,use_case}); lock business logic early; no deep research; trace everything; no speculation. Исходы (Phase 3.5): Feature Domain Contract ≠ N/A → transition skeleton (нода skeleton_sig, генерация Walking Skeleton); Contract = N/A → transition review (нода spec_review, сразу cross-model ревью).
```

#### `skeleton_sig` — Skeleton Stage A: Signatures (domain-designer) · domain-designer · gpt-5.5/high
```text
Walking Skeleton (Variant Y), Stage A: сгенерируй ТОЛЬКО signatures и package structure будущего domain-слоя. Прочитай 0-spec.md (секции Feature Domain Contract, State Matrix, Primary User Journeys, Domain Test Scenarios), .claude/PROJECT-CONTEXT.md (base_package, project layout; дефолт KMP: shared/feature/<slug>/domain/src/commonMain/ + commonTest/), затем скилл: .claude/skills/domain-modeling/SKILL.md и references/kotlin-patterns.md. ВАЖНО: SKILL.md (Variant Y) главнее anti-patterns.md — repository interfaces, use cases, suspend/Flow в repository/use_case и fakes в тестах РАЗРЕШЕНЫ; запреты references действуют только для pure core (model/state/logic). Создай файлы в model/, state/, logic/ (pure functions), repository/ (interfaces), use_case/ (classes); bodies оставь TODO() — имплементация в Stage B. Пиши ТОЛЬКО в domain-директории фичи; scaffold (gradle/manifest) не трогай. Нет hard limits на количество файлов — преждевременное упрощение хуже избыточности; подозрительно большой объём → отметь в отчёте («возможно нужен Task Splitting»). UPDATE-режим (повторный проход через respec / spec_needs_changes): если shared/feature/<slug>/domain/ уже существует — НЕ пересоздавай слой с нуля: сохрани валидные сигнатуры, меняй ТОЛЬКО то, что затронуто обновлением 0-spec.md (новые/изменённые термины, правила, состояния, сценарии), diff сигнатур (added/changed/removed) вынеси в отчёт. Заверши отчётом «SIGNATURES READY» со списком публичных имён.
```

#### `skeleton_bodies` — Skeleton Stage B: Bodies (domain-designer) · domain-designer · gpt-5.5/high
```text
Walking Skeleton Stage B: реализуй bodies всех функций и методов — TODO() замени на business logic строго по 0-spec.md. Signatures зафиксированы в Stage A — НЕ переименовывай классы и методы, НЕ меняй публичные сигнатуры. Прочитай .claude/skills/domain-modeling/references/kotlin-patterns.md и anti-patterns.md (pre-commit чеклист: без android.*/androidx.*, DI/serialization-аннотаций, throw в pure functions (только Result.failure), side effects, mutable global state; suspend/Flow — только в repository/use_case). Если два правила Contract несовместимы или сценарий двусмысленен — STOP, зафиксируй противоречие в отчёте (Open Questions), не выбирай молча. Отчёт «BODIES READY».
```

#### `skeleton_tests` — Skeleton Tests (test-dev, WS mode) · test-dev · gpt-5.5/high
```text
Walking Skeleton mode. Прочитай 0-spec.md (Domain Test Scenarios, Feature Domain Contract, State Matrix, Primary User Journeys) + существующие domain-файлы фичи + .claude/skills/domain-modeling/references/test-patterns.md + .claude/rules/testing.md. Реализуй КАЖДЫЙ Domain Test Scenario как @Test в commonTest/ (или jvmTest по layout): каждый сценарий → один @Test, каждая строка State Matrix → @Test, каждый Journey → минимум один @Test; pure-core тесты без mocks/fakes; use-case тесты через полноценные in-memory fakes в test/.../fake/. Используй классы и методы из domain как есть — НЕ меняй signatures, НЕ пиши production code. Если сценарий нельзя реализовать из-за отсутствующей signature — зафиксируй ERROR в отчёте. Тесты могут быть красными (bodies пишутся параллельно — это ожидаемо). Отчёт «TESTS IMPLEMENTED» со списком тестов.
```

#### `skeleton_join` — Skeleton Join · join · (пусто)

#### `skeleton_gate` — Skeleton Gate (Lead) · lead · gpt-5.5/xhigh
```text
Гейт Walking Skeleton. Запусти ./gradlew :shared:feature:<slug>:domain:jvmTest --no-configuration-cache (или эквивалент из PROJECT-CONTEXT.md; fallback ./gradlew test --tests "*<slug>*" --no-configuration-cache). Прогони pre-commit grep-чеклист из .claude/skills/domain-modeling/references/anti-patterns.md (import android.*, @Inject, throw, var top-level, Log./println, mockk — только для pure core). Сверь coverage: каждое правило Contract, каждая строка State Matrix, каждый Domain Test Scenario, каждый Journey покрыты тестом. Исходы: (1) все тесты зелёные и coverage полон → advance (cross-model review); (2) тесты красные или не компилируются (assertion fail — виноват domain-designer или tester; signature mismatch — Stage A неточен) → rerun с точным указанием, какой stage и что исправить; (3) спека двусмысленна / open questions от агентов / scope overflow → respec: спроси пользователя через kent ask_question, что уточнить, и верни фичу в spec (обновить 0-spec.md, при overflow — Phase 2.5 Task Splitting). Walking skeleton — это PRODUCTION код, не throw-away: phase-01 стадии implement его не переписывает.
```

#### `spec_review` — Spec Cross-Model Review · crossmodel-reviewer · gpt-5.4/xhigh
```text
Ты cross-model рецензент (gpt-5.4, другая модель, чем спайн) — senior разработчик, который завтра будет реализовывать эту фичу. Твоя ЕДИНСТВЕННАЯ задача — найти дыры в ТЗ. Прочитай docs/features/<slug>/0-spec.md. Проверь особенно: конкретность Feature Domain Contract для skeleton и phase-01; покрытие Primary User Journeys (happy/recovery/interrupted); отсутствие пустых/двусмысленных ячеек State Matrix; достаточность Domain Test Scenarios; measurability Acceptance Criteria; полноту Search Criteria. Если skeleton сгенерирован — выборочно сверь тесты с Domain Test Scenarios. Для каждой дыры: что непонятно, почему заблокирует реализацию, предложение. Запиши отчёт в docs/features/<slug>/reviews/0-spec.review.md (severity blocker/high/medium + file:section). Если spec полный — напиши «Spec complete, вопросов нет». НЕ редактируй другие файлы.
```

#### `spec_verdict` — Spec Verdict (Lead) · lead · gpt-5.5/xhigh
```text
Прочитай docs/features/<slug>/reviews/0-spec.review.md. Нет blocker и high → approved (переход к research, ждёт подтверждения пользователя). Есть blocker/high → needs_changes: передай findings команде spec (закрыть дыры с пользователем, обновить 0-spec.md и при необходимости skeleton).
```

### 2.2 RESEARCH (9 нод)

#### `research` — Research · lead · gpt-5.5/xhigh
```text
Исследование: ты диспетчер, сам код НЕ читаешь и НЕ исследуешь. Ты: читаешь спеку → запускаешь команду исследователей (fan-out) → потом синтезируешь их результаты. Прочитай docs/features/<slug>/0-spec.md (нет файла — работай по описанию задачи) и docs/invariants.md. Шаг 0.5 (delta-only): 0-spec.md уже фиксирует product-решения — НЕ перевалидируй их; спроси пользователя через kent ask_question ТОЛЬКО при реальном delta (search criteria слишком расплывчаты, requirements противоречивы, не хватает деталей для codebase-верификации); к каждому вопросу — предположение «Я понял это как ... — верно?»; ответы вноси в 0-spec.md с пометкой [ADDED IN RESEARCH: причина]. Для каждого инварианта из docs/invariants.md, который фича затрагивает, включи в задание исследователям: «Проверь, как текущая реализация обеспечивает <invariant> и как планируемые изменения могут это нарушить». Затем выбери transition research_team (fan-out: criteria-исследователь, core-scan, cross-feature scanner, web-researcher). Правила: промпт агентам короткий (feature, описание, criterion, entry points ≤6, что искать); только факты с file:line, никаких рекомендаций.
```

#### `research_codebase` — Criteria Researcher (codebase-researcher) · codebase-researcher · gpt-5.5/high
```text
Прочитай свою роль-дисциплину: только факты с file:line; сомневаешься — читай больше файлов, никогда не угадывай. Возьми из 0-spec.md секцию Search Criteria for Research и отработай КАЖДЫЙ criterion (по очереди, широкий criterion дели на подзадачи). 4 обязательных скана: Impact Scan (для каждого изменяемого entity/DAO — все consumers и их предположения, таблица Consumer/Assumption/Impact/Severity); Duplicate Logic Scan (для каждого создаваемого компонента — существующие аналоги; трогаем core/ → прочитай .claude/skills/core-module/references/conventions.md); Entry Points (ВСЕ: intents, public methods, callbacks, restore/return, deep links, notification taps — с caller chain; пропуск альтернативных entry points каскадирует до implementation); верификация ссылок/путей (полная цепочка base+relative). Completeness: все catch-блоки/error callbacks/fallback branches, счёт сверить с grep count. Для каждой функции из Integration Points — полная сигнатура + пример вызова (file:line). Опционально: один GLM breadth-pass (python3 .claude/skills/glm/scripts/glm_query.py --profile research --json --prompt "<compact evidence packet>"); упал/нет ключа — продолжай без GLM; каждую подсказку проверь по коду, GLM — не доказательство. Отчёт: Findings/Components/Data flow/Constraints/Risks/Open Questions (несоответствие spec↔код — фиксируй, не продолжай на best guess).
```

#### `research_core` — Core Scan (codebase-researcher) · codebase-researcher · gpt-5.5/high
```text
Shared core scan (conditional). Если фича реально затрагивает shared infrastructure в core/ или зависит от существующих shared contracts — для каждого релевантного пакета core/<package> прочитай README.md (контракты, инварианты), *Policy.kt, *Rule.kt, shared types/helpers; извлеки ограничения, инварианты, точки интеграции и reusable shared logic; сверь с .claude/skills/core-module/references/conventions.md. НЕ запускай скан только потому, что core/ существует; feature-specific логика живёт в feature-local domain/* — не в core. Если фича core не затрагивает — пройди насквозь с пометкой «core scan: N/A». Факты с file:line.
```

#### `research_crossfeat` — Cross-Feature Scanner (codebase-researcher) · codebase-researcher · gpt-5.5/high
```text
Полная карта cross-feature dependencies для фичи (обязательный шаг: без неё design/plan примут решения, не зная о существующих cross-feature contracts). Шаги: 1) определи feature-пакеты/модули (grep по package-паттернам, ls feature-директорий); 2) для каждого — импортирует ли он другие feature-пакеты; 3) для каждого cross-feature import: direction (A→B/bidirectional), mechanism (direct import / reflection Class.forName / shared interface в core / event bus / broadcast / service binder), file:line, задокументирован ли в docs/features/<each>/03-decisions.md (ADR); 4) специально для нашей фичи: какие OTHER фичи она будет импортировать по spec и кто может импортировать её; 5) если используется external SDK — какие ещё фичи используют тот же SDK. Вывод: dependency graph (markdown table), bidirectional coupling (явно как risk), undocumented reflection calls (явно), shared SDK usage. Undocumented паттерны = blockers для design.
```

#### `research_web` — Web Researcher · web-researcher · gpt-5.5/high
```text
Условный web-research. Если 0-spec.md упоминает external SDK или platform API (Notification, Foreground Service, PiP и т.п.) — найди official documentation каждой SDK/библиотеки (Context7 MCP первым: mcp__context7__resolve-library-id → get-library-docs; иначе WebSearch/WebFetch), проверь существование полей/методов/классов из спеки, собери known issues версий и platform quirks. Для shared SDK, найденных cross-feature scanner-ом: если 1-research.md уже существует — используй его Shared SDK findings; иначе fallback на 0-spec.md (research_web идёт в ТОМ ЖЕ параллельном фан-ауте, что и cross-feature scanner, поэтому на его вывод рассчитывать нельзя — принятое ограничение, см. §6). Для найденного SDK: recommended pattern интеграции (single instance vs per-feature), threading model, lifecycle ownership — design должен решать по official docs, не по догадкам. Каждый факт с source URL; не найдено — [NOT FOUND]; расхождение docs↔код — [DISCREPANCY]. Фича полностью internal — пройди насквозь («web research: N/A»).
```

#### `research_join` — Research Team Join · join · (пусто)

#### `research_synth` — Research Synthesis + Grounding · lead · gpt-5.5/xhigh
```text
Ты лид-судья research. 1) Синтез: объедини находки команды в docs/features/<slug>/1-research.md (frontmatter date/researcher/commit/branch из git; секции Summary, Architecture Overview, Existing Patterns, Integration Points, Detailed Findings (file:line), Cross-Feature Interactions (Dependency Graph таблица, Bidirectional Coupling Risks, Shared SDK Across Features, Undocumented Patterns — blockers для design), State Matrix Validation (пропущенные условия / несостыковки матрица-vs-код / непокрытые комбинации / Domain Contract Mismatches — при delta спроси пользователя через kent ask_question «Research нашёл delta относительно spec: [...]. Обновить 0-spec.md?» и обнови только релевантные секции), Conditional Documents Needed (нужны ли 07-events.md / 08-storage-model.md), Constraints, Open Questions). Только факты, без рекомендаций. 2) Grounding Gate (ОБЯЗАТЕЛЕН — без 2-grounding.md переход к design ЗАПРЕЩЁН): создай docs/features/<slug>/2-grounding.md — на каждую проблему grounding-карточка: Symptom, Repro, Entry Points (EXHAUSTIVE, иначе [ENTRY POINTS INCOMPLETE]), Code Owners, Flow Trace (FileA:line → FileB:line → FileC:line), Backend/Contract Check ([REQUIRES BACKEND CHANGE] при пробеле), Constraints (lifecycle/in-memory/DB/offline), Code Path Divergence, Fix Shape, Validation. Independent Verification Protocol: для КАЖДОГО утверждения из 1-research.md ОТКРОЙ исходный файл и прочитай реальный код сам (это единственное место, где lead читает код); формат [VERIFIED: file:line, что реально делает код] или [CONTRADICTS: research говорит X, код показывает Y, file:line]. Любой [CONTRADICTS] = blocker. Каждый BLOCKER-finding резолвится до design: amendment в 0-spec.md / ADR будет в 03-decisions.md / accepted risk с явным approve пользователя (kent ask_question); фиксация «### Status: BLOCKER → RESOLVED (date)». Не завершай стадию, пока grounding не записан на диск. Обнови README (Status: research). РЕШЕНИЕ: grounding полон и все claims verified → transition review (cross-model проверка claims по коду; совпадает с именем transition ребра); пробелы/[CONTRADICTS]/[ENTRY POINTS INCOMPLETE] → transition rerun (пере-поднять команду по недостающим местам; максимум один полный доп. раунд, дальше — точечные направления).
```

#### `research_review` — Research Cross-Model Review · crossmodel-reviewer · gpt-5.4/xhigh
```text
Ты cross-model рецензент (gpt-5.4). Прочитай 1-research.md и 2-grounding.md, открой реальный код и проверь claims сам. Любой CONTRADICTS = blocker. Проверь: exhaustiveness Entry Points, полноту Cross-Feature Interactions, резолюции всех BLOCKER. Запиши отчёт в docs/features/<slug>/reviews/1-research.review.md (findings, severity, file:line). PASS если нет blocker. Research не редактируй.
```

#### `research_verdict` — Research Verdict (Lead) · lead · gpt-5.5/xhigh
```text
Прочитай docs/features/<slug>/reviews/1-research.review.md и проверь, что все BLOCKER из 2-grounding.md имеют Status: RESOLVED. Нет blocker → approved (переход к design). Иначе needs_changes.
```

### 2.3 DESIGN (8 нод)

#### `design` — Design · lead · gpt-5.5/xhigh
```text
Проектирование: ты диспетчер, design docs сам НЕ пишешь. Grounding Gate: если docs/features/<slug>/2-grounding.md НЕ существует — СТОП, сообщи пользователю «Grounding не найден, нужен research» и выбери rerun-путь через вердикты (не продолжай). Если grounding неполный (нет Entry Points / Code Owners / Backend-Contract Check хотя бы для одной проблемы) — спроси пользователя через kent ask_question: до-research или продолжить с пометкой [GROUNDING INCOMPLETE]. Прочитай 0-spec.md, 1-research.md (включая Cross-Feature Interactions — передай сводку архитекторам в edge-заданиях: что фича импортирует, кто её использует, bidirectional risks, shared SDK, undocumented patterns = blockers; новые cross-feature связи без документирования в 03-decisions.md = blocker), 2-grounding.md, docs/invariants.md. Feature Domain Contract / Journeys / State Matrix / Domain Test Scenarios из spec — зафиксированный input: design их НЕ переоткрывает; эскалация пользователю только по реальному delta. Определи conditional docs: WebSocket/realtime → 07-events.md; Room entities/DAO/migrations → 08-storage-model.md. Затем transition design_team (fan-out: два архитектора-спорщика + conditional web-researcher). Spec Ambiguity Gate: двойное толкование в spec → [SPEC AMBIGUITY — BLOCKS DESIGN] в Open Questions, эскалируй пользователю, не разрешай молча.
```

#### `design_arch_high` — Architect High-Level · architect-high-level · gpt-5.5/high
```text
Ты high-level архитектор (C4 L1-L2). Прочитай .claude/PROJECT-CONTEXT.md, .claude/rules/clean-architecture.md, docs/features/<slug>/1-research.md, 2-grounding.md, 05-prior-art.md (если есть). Твоя зона: модульные границы, направление зависимостей, DFD, architectural decisions. НЕ спускайся до классов и интерфейсов. Пиши свою часть в docs/features/<slug>/: 01-architecture.md (C4 L1-L2, module dependency graph, mermaid), 02-behavior.md (DFD), 03-decisions.md (module-level ADRs — каждый ADR ОБЯЗАН содержать «Alternatives Considered» с минимум 1 отвергнутым вариантом), 06-api-contract.md (canonical signatures — ЕДИНСТВЕННЫЙ источник правды для публичных типов; либо «Not applicable»), условно 07-events.md. Не выдумывай модули, которых нет в research; имена — из реальной кодовой базы; каждая ссылка на код с точным file:line; production-код не писать. В своей части отчёта явно оспорь решения component-архитектора, если они нарушают модульные границы.
```

#### `design_arch_comp` — Architect Component · architect-component · gpt-5.5/high
```text
Ты component-level архитектор (C4 L3). Прочитай .claude/PROJECT-CONTEXT.md, .claude/rules/{clean-architecture,di-patterns,domain-models,navigation,room-database,use-cases}.md, 1-research.md, 2-grounding.md, 05-prior-art.md (если есть). Твоя зона: классы, интерфейсы, DI (Koin по PROJECT-CONTEXT), Room, sequences, test strategy. Модульные границы НЕ определяешь. Пиши свою часть: 01-architecture.md (L3, class diagrams, DI wiring), 02-behavior.md (sequences, error flows; если в 0-spec.md есть State Matrix — ОБЯЗАН расширить её в 02-behavior.md: edge cases, маппинг на file:line, каждая ячейка testable; Contract/Journeys трассируй к runtime-поведению, не переизобретая rules), 03-decisions.md (component ADRs с Alternatives Considered), 04-testing.md (каждая ячейка State Matrix = минимум 1 test case; отдельно покрытие Domain Test Scenarios и Journeys), условно 08-storage-model.md. Decision ссылается на функцию → проверь сигнатуру в 1-research.md, отсутствует — пометь «REQUIRES: verify signature before implementation». Если Walking Skeleton существует (shared/feature/<slug>/domain/) — design работает С ним: переименования допустимы, business rules и signatures сохраняются. Явно оспаривай high-level архитектора, если его границы ломают DI или lifecycle. Production-код не писать.
```

#### `design_web` — Web Researcher (prior-art) · web-researcher · gpt-5.5/high
```text
Условный prior-art research для архитекторов. Если фича использует external SDK/platform API: найди official docs, best practices, reference implementations (Context7 first), запиши в docs/features/<slug>/05-prior-art.md; known issues/deprecations, влияющие на design, пометь явно. Каждый факт с source. Фича полностью internal — пройди насквозь («prior-art: N/A»).
```

#### `design_join` — Design Team Join · join · (пусто)

#### `design_synth` — Design Synthesis + Gates · design-architect · gpt-5.5/xhigh
```text
Ты лид-судья дизайна (design-architect). 1) Сведи позиции архитекторов в согласованные design-доки 01-04, 06 (+07/08): разреши конфликты, contested-решения зафиксируй в 03-decisions.md; Document Responsibility Matrix (SSoT): каждый публичный тип имеет РОВНО ОДИН canonical record в 06-api-contract.md (07/08 для событий/persistence); 01/02/03/04 ссылаются, полные сигнатуры не дублируют. 2) Прогони детерминированные гейты (bash): Gate 5 «no hopeful gates»: rg -nE "REQUIRES?\s+VERIFY|UNRESOLVED|TBD\b" по 01,02,03,04,06 — любой match = blocker (resolve через verified docs/код или эскалируй пользователю как Spec Ambiguity); Gate 6 Module Direction Audit: core/* не импортирует feature/*, feature/A не импортирует feature/B напрямую (исключение — Decompose ChildStack rendering, документировано в ADR), designsystem-компоненты не принимают feature domain types; Gate 7 ADR↔api-contract round-trip (инлайн-проверка, без внешнего скрипта): извлеки список типов из docs/features/<slug>/03-decisions.md и для КАЖДОГО запусти `rg -n "<TypeName>" docs/features/<slug>/06-api-contract.md` — тип упомянут в ADR, но не имеет canonical signature в 06-api-contract.md = blocker (детерминированная проверка вместо grep-скрипта; канон-описание см. .claude/commands/feature-design.md); Gate 8 Multi-Path State Machine (conditional: multiple entry points с разными stack shapes → секция в 02-behavior.md со stack shape per path и таблицей operations across paths); хук-скрипты .claude/hooks/check-c4-vs-gradle.sh и .claude/hooks/check-api-contract-types.sh запусти вручную для 01-architecture.md и 06-api-contract.md (это warnings: реши сам «planned vs drift», зафиксируй решение). 3) Проверь Gate 1 (полнота: 01-04, 06 + conditional, mermaid присутствуют) и Gate 2 (DI/dependency direction по PROJECT-CONTEXT, отклонения в 03-decisions.md). РЕШЕНИЕ: дизайн полон, согласован, гейты зелёные → review (cross-model reality check); архитекторы не сошлись / гейты красные → rerun (пересоздать команду с findings). Design = только документы, никакого production-кода.
```

#### `design_review` — Design Cross-Model Review · crossmodel-reviewer · gpt-5.4/xhigh
```text
Ты cross-model рецензент (gpt-5.4). Прогони по design-докам 3 линзы последовательно: Realist («модель дизайна совпадает с реальным кодом?» — сверь module graph в 01-architecture.md против реальных build.gradle.kts модулей; сверь signature snippets в 06-api-contract.md против существующих *.kt и Walking Skeleton в shared/feature/<slug>/domain/ — любое расхождение blocker), Skeptic по 03-decisions.md («решения обоснованы? альтернативы рассмотрены?»), Architect по 04-testing.md + conditional docs («test strategy покрывает все AC? contracts согласованы?»). ОБЯЗАТЕЛЬНО открывай реальный код — не суди только по докам. Запиши отчёт в docs/features/<slug>/reviews/design.review.md (по линзам, severity, doc:section + file:line). Finding без точного evidence — не actionable. blocker = REJECT. Design-доки не редактируй.
```

#### `design_verdict` — Design Verdict (Lead Judge) · lead · gpt-5.5/xhigh
```text
Прочитай docs/features/<slug>/reviews/design.review.md. Вердикт-логика: нет high/blocker → PASS → approved (обнови README: Status designed; переход к plan ждёт подтверждения пользователя); high с расхождением ревью — CONTESTED — реши сам; blocker или консенсусный high → REJECT → needs_changes (вернуть findings архитекторам, пересоздать команду).
```

### 2.4 PLAN (5 нод)

#### `plan` — Plan · lead · gpt-5.5/xhigh
```text
План: ты диспетчер, план сам НЕ пишешь. Grounding Gate: 2-grounding.md отсутствует — СТОП, сообщи пользователю (plan без grounding — главная причина планов, не привязанных к реальному коду); для каждой проблемы grounding проверь заполненность Entry Points / Code Owners / Backend-Contract Check / Validation — пустое поле → сообщи пользователю о пробелах. Прочитай 0-spec.md, 1-research.md, 2-grounding.md, design-доки 01-04, 06 (+07/08). Выбери стратегию порядка фаз: bottom-up (default: Domain from spec → UseCase → Adapter → Controller → UI) / adapter-first / vertical slice. Ветка Walking Skeleton: если Feature Domain Contract ≠ N/A — domain уже сгенерирован на spec-этапе; phase-01 = integration phase, НЕ create-from-scratch (repository impls, DAO-domain mappers, DI bindings, integration tests; domain классы НЕ переписываются). У тебя ДВА исхода: (1) блокер в existing skeleton / требуется менять domain (нужно переписать сигнатуры или бизнес-правила) → STOP, transition respec (назад в spec на re-spec скелета, эскалация пользователю); (2) иначе transition plan_team → planner.
```

#### `plan_planner` — Planner · planner · gpt-5.5/high
```text
Ты планировщик реализации. Прочитай .claude/PROJECT-CONTEXT.md, 2-grounding.md (ОБЯЗАТЕЛЬНО — source of truth для traceability), design-доки 01-04, 06 (+07/08). Разбей дизайн на фазы: каждая фаза — директория docs/features/<slug>/plan/phase-NN/ (zero-padded) с файлами per role: overview.md (Goal, Scope, Layer domain|useCase|adapter|controller|ui, Dependencies, Role Inputs, Review Tags — security-review always, concurrency-review если фаза трогает coroutines/Flow/shared mutable state/lifecycle, Diagnostics Hints (expected failure signals, debugger triggers, device/backend prerequisites), State Matrix Coverage, Domain Contract Coverage, Traceability-таблица (Problem from grounding | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation — каждая строка ссылается на проблему из 2-grounding.md; фаза без привязки = красный флаг; Code Owner/Entry Points из grounding, не выдумываются), New/Modified/Deleted Files (Deleted = точный список или none), Cross-Phase Dependencies (Consumed с consumer contract, Provided, Temporary stubs с TODO "Phase NN cleanup", Required cleanup), DI Bindings (Provided/Required/Koin Verify чекбоксы), Acceptance Criteria, Tests Required (test_name: given X, when Y, then Z; минимум happy+edge на каждый public method; для phase-01 в integration-mode — только integration-тесты, pure domain уже зелёный), Pattern Invariants, Validation-таблица — строка 1 всегда ./gradlew ciCheck --no-configuration-cache); backend.md / frontend.md — Signature Card на каждый new file (файл, тип, сигнатура одной строкой в inline backticks, вход, поведение/выход, edge cases, depends on, Canonical reference → 06-api-contract.md:NN для публичных типов, rationale); tests.md — сценарии given/when/then, fakes blueprints, edge cases. frontend.md создаётся ТОЛЬКО если фаза затрагивает UI/presentation — lead спавнит агентов СТРОГО по наличию файлов. ЗАПРЕЩЕНО: fenced ```kotlin/```kt/```java/```groovy блоки в plan-файлах (Plan = ТЗ, не implementation; разрешены ```bash и ```markdown). Canonical signatures публичных типов живут ТОЛЬКО в 06-api-contract.md — план ссылается, не копирует. Complex-фазы (3+ модулей / новый architectural pattern / FSM / REQUIRES tag) ОБЯЗАНЫ содержать Options Considered (минимум 2 варианта + recommended + rationale + rejected trade-offs). REQUIRES-пометка из design = blocker: верифицировать или DEFERRED с обоснованием. Walking Skeleton: Contract ≠ N/A → phase-01 adapter-only integration, «Domain классы из spec phase — NOT modify»; нужен modify domain → blocker, план НЕ создаётся, эскалация. Stateful field reset: новые StateFlow/maps/flags в long-lived компоненте → AC «сбрасываются при re-init». Exhaustive scope: для каждого нового UI-компонента/guard — grep ВСЕ аналогичные call sites, лишнее → «Out of phase scope: [...] — REQUIRES separate phase». Также создай plan/README.md — lead-dashboard: Phase | Goal | Depends on | Role Inputs | Validation + File Map. План не может пропустить ни один acceptance criterion из 0-spec.md.
```

#### `plan_gate` — Plan Deterministic Gate (Lead) · lead · gpt-5.5/xhigh
```text
Детерминированный гейт плана (замена PostToolUse-хуков). Запусти bash: (1) bash .claude/hooks/check-plan-no-code.sh недоступен как hook — выполни его проверку напрямую: grep -RnE '^[[:space:]]*```(kotlin|kt|java|groovy)\b' docs/features/<slug>/plan/ — любой match = FAIL («PLAN CONTAINS READY-TO-COPY CODE: Plan = ТЗ, Signature Card формат; canonical signatures живут в 06-api-contract.md»); (2) проверку путей check-plan-paths.sh: для каждого пути из секций New/Modified/Deleted Files — Modified/Deleted обязан существовать, для New обязан существовать parent dir (или его создание включено в план); удобно запустить сам скрипт: echo '{"tool_input":{"file_path":"<plan-file>"}}' | bash .claude/hooks/check-plan-paths.sh для каждого plan-файла; (3) scripts/pipeline/check_pipeline_docs.sh docs/features/<slug> (если скрипт существует в репо; нет — отметь и пропусти). Любой FAIL → transition fail (назад в planner с полным текстом ошибок). Всё зелёное (warnings только по legacy, не относящемуся к текущему плану) → transition pass.
```

#### `plan_review` — Plan Cross-Model Review · crossmodel-reviewer · gpt-5.4/xhigh
```text
Ты cross-model рецензент плана (gpt-5.4) — ЕДИНСТВЕННОЕ ревью плана (same-model plan-reviewer снят по источнику: planner → cross-model, без промежуточного same-model ревьюера). Два lens: (1) Sequencing: все design-доки покрыты фазами? dependencies корректны? validation-команды реалистичны (из AGENTS.md/PROJECT-CONTEXT, не выдуманные)? README синхронизирован? Плюс перенятые из снятого plan-reviewer детерминированные проверки: для каждого вызова функции в phase file сигнатура совпадает с 1-research.md (не задокументирована — открой source; несовпадение = blocker); Cross-Phase Dependencies closure (все Consumed имеют matching Provided ранее; Temporary stubs имеют Required cleanup); DI Bindings closure (каждое Required имеет Provided ранее, иначе orphaned binding); покрытие всех Acceptance Criteria из 0-spec.md фазами; zero-padded naming; согласованность README dashboard с phase files. (2) Plan-as-ТЗ (плановый lens против «plan = готовый код»): нет fenced kotlin/java/groovy блоков (grep -nE '^[[:space:]]*```(kotlin|kt|java|groovy)\b' plan/phase-*/*.md, 0 matches); каждый New File имеет полную Signature Card (путь, тип, inline-сигнатура, вход, поведение, edge cases, canonical reference или «internal», rationale) — полный класс вместо карточки = blocker; публичный тип не в 06-api-contract.md = blocker; Tests Required — scenarios given/when/then, JUnit-код в tests.md = blocker; Options Considered для complex-фаз (минимум 2 варианта; отсутствие при complex = blocker); Pattern Invariants с file:line-референсом, прозой; redundancy: ни один тип не описан полной сигнатурой в >1 документе. Вердикт PASS / CONTESTED (medium, применимо с trade-off docs) / REJECT (≥1 blocker, findings: файл+строка, цитата, пункт чеклиста, fix). Отчёт в docs/features/<slug>/reviews/plan.review.md. Plan-файлы не редактируй. Если ты оспариваешь recommended-вариант из Options Considered — пометь это отдельно: это сигнал lead-у эскалировать пользователю.
```

#### `plan_verdict` — Plan Verdict (Lead) · lead · gpt-5.5/xhigh
```text
Прочитай docs/features/<slug>/reviews/plan.review.md. Нет blocker → approved (обнови README: Status planned; переход к implement ждёт подтверждения пользователя; если crossmodel оспорил recommended option — сначала спроси пользователя через kent ask_question). Иначе needs_changes.
```

### 2.5 IMPLEMENT + per-phase review (16 нод)

#### `impl_preflight` — Team Composition Preflight (diagnostics) · diagnostics · gpt-5.5/high
```text
Team Composition Preflight для implementation. Режим advisor: НЕ запускай build/test/logcat/device-команды, НЕ предлагай code changes. Прочитай ТОЛЬКО: .claude/PROJECT-CONTEXT.md, docs/invariants.md, 0-spec.md, 2-grounding.md, plan/README.md, plan/phase-*/overview.md (НЕ читай role-файлы backend/frontend/tests.md). Верни Team Composition Proposal по шаблону: Mandatory Teammates per phase (пул ролей: frontend-dev, backend-dev, firebase-dev, test-dev, integration-tester, code-reviewer, architect-reviewer, security-reviewer, completeness-reviewer, concurrency-reviewer, diagnostics, code-analyst, log-reader, web-researcher); Conditional Teammates с триггерами; Do Not Spawn; Scaling; Debug Hooks (какие failure signals куда route-ить, какое evidence обязательно); Device/backend prerequisites; Confidence High/Medium/Low. Hard rules: security-reviewer обязателен; test-dev обязателен для production code. Lead может override, но обязан записать это в Run Ledger.
```

#### `implement` — Implement (Lead, Delegate Mode) · lead · gpt-5.5/xhigh
```text
Диспетчер реализации в Delegate Mode: ты ограничен coordination-only — код НЕ пишешь, production files НЕ редактируешь, High/Blocked architectural decisions за пользователя НЕ принимаешь. Low/Medium process-решения (порядок фаз внутри approved-графа, retry routing, re-check routing) принимай автономно, но записывай в Run Ledger. Architectural mismatch (кто-то хочет удалить/скрыть функционал, сменить паттерн, пропустить модуль/AC; = любое изменение, не описанное в phase file) — STOP и спроси пользователя через kent ask_question. Шаг 0: прочитай 0-spec.md, README, plan/README.md; содержимое phase-файлов — ЛЕНИВО (только overview.md текущей фазы при переходе к ней); построй граф зависимостей фаз (independent → допускается перекрытие, dependent → sequential; в Kent фазы идут последовательно через петлю verdict → implement). Шаг 0.5 Run Ledger: создай/обнови docs/features/<slug>/run/pipeline-state.json (feature, status, activePhase, completedPhases, blockedPhases, lastGreenCommand, openBlockers, nextAction) и append-only run/run.jsonl; при возобновлении сессии первым делом читай pipeline-state.json и продолжай с nextAction, не рестартуя пайплайн. Прими Team Composition Proposal (из preflight или комментариев задачи) как default. Walking Skeleton preflight для phase-01 (если Contract ≠ N/A): проверь подпакеты model/state/logic/repository/use_case и зелёный ./gradlew :shared:feature:<slug>:domain:jvmTest --no-configuration-cache; нет — это ошибка spec pipeline, STOP и сообщи пользователю; phase-01 = adapter-only integration. Для КАЖДОЙ фазы выбери transition dev — fan-out работников; в edge-заданиях укажи номер фазы и scope; работники спавнятся СТРОГО по наличию role-файлов (нет frontend.md → задание frontend-dev «N/A, пройди насквозь»); tests.md отсутствует при изменении production code = ошибка плана → сообщи пользователю. Правила: no auto-commit/push; no skipping phases/reviews/gates; scaffold ownership у backend-dev; отклонение от design → сначала обнови 03-decisions.md; повторяющиеся findings одного класса (2-3 итерации) → эскалация пользователю, не тихая остановка. Для живой диагностики бага — transition debug.
```

#### `dev_backend` — Backend Dev · backend-dev · gpt-5.5/high
```text
=== PHASE — BACKEND ===. Начни работу НЕМЕДЛЕННО. Прочитай ТОЛЬКО docs/features/<slug>/plan/phase-NN/backend.md (номер фазы — в задании) + project rules: .claude/rules/clean-architecture.md, di-patterns.md, domain-models.md, kotlin-conventions.md (типы data class/sealed interface/object/enum/operator invoke, verb-first/is*/has*/can*/should* нейминг, observe*/*Flow для потоков, no !! вне тестов → requireNotNull, Result<T>/Flow<T> на границах, лимиты файл<600/функция<50/nesting≤3/params≤5, structured concurrency без GlobalScope) (+ перечисленные в phase file; трогаешь core/ → .claude/skills/core-module/references/conventions.md). НЕ читай frontend.md, overview.md, design-доки соседних вертикалей — нехватка контекста = сигнал о проблеме плана (Open Question), а не повод читать чужое. Реализуй scope фазы в domain/data (repository impls, DAO, mappers, Koin bindings). Walking Skeleton: если domain сгенерирован на spec-этапе — ты НЕ пишешь новые use cases/repository interfaces, только реализуешь существующие интерфейсы production-адаптерами и wires up DI; interface нереализуем → это architectural mismatch: STOP, Open Questions, НЕ переписывай domain молча. Scaffold файлы (build.gradle.kts, libs.versions.toml, settings.gradle.kts, gradle.properties, gradle/wrapper/*, root AndroidManifest.xml) — твоя эксклюзивная зона. Сам прогони Build Gate: ./gradlew ciCheck --no-configuration-cache + phase-specific команды из Validation; Test Deletion Gate: git diff --name-status HEAD -- '*/test/**' — удалённые тесты должны быть в Deleted Files overview.md, иначе восстанови. Нет фазового backend-scope (нет backend.md) — пройди насквозь «backend: N/A». Отчёт: изменённые файлы, что реализовано, валидация, follow-up, Open Questions (несоответствие = зафиксировать, не угадывать). В fix-режиме: исправляй ТОЛЬКО указанные замечания со ссылкой severity+file:line, без рефакторинга вокруг.
```

#### `dev_frontend` — Frontend Dev · frontend-dev · gpt-5.5/high
```text
=== PHASE — FRONTEND ===. Начни НЕМЕДЛЕННО. Прочитай ТОЛЬКО plan/phase-NN/frontend.md + .claude/rules/navigation.md, kotlin-conventions.md (verb-first/is*/has* нейминг, no !! → requireNotNull, лимиты файл<600/функция<50/params≤5, structured concurrency без GlobalScope, НЕТ JSON-парсинга в Compose Screen/Decompose Component/ViewModel/Activity) (+указанные в phase file; работа с Compose UI/темами → при необходимости референсы .claude/skills/material-3/references/ по Decision Tree: theming → theming-and-dynamic-color.md, компоненты → component-catalog.md; anti-patterns MD3: не хардкодить цвета, tonal pairing парами, shape-токены). Реализуй presentation-scope: Decompose Components, Compose screens, navigation wiring, Koin factories. Бизнес-логика — в use case/domain; Component координирует state, Screen только рендерит; НЕ переводи Decompose на ViewModel и не добавляй Koin resolution в Screen без явного ADR. Contract/Journeys/State Matrix из spec — зафиксированы, не переопределяй на UI-слое; delta → Open Questions. Scaffold не трогай (владение backend-dev). Если в фазе есть backend-dev — Build Gate прогоняет тот, кто заканчивает последним; в чисто-frontend фазе прогони сам (ciCheck + Test Deletion Gate). Нет frontend.md у фазы — пройди насквозь «frontend: N/A». Отчёт: файлы/реализовано/валидация/follow-up/Open Questions. Fix-режим: только указанные замечания.
```

#### `dev_firebase` — Firebase Dev · firebase-dev · gpt-5.5/high
```text
=== PHASE — FIREBASE ===. Начни НЕМЕДЛЕННО. Только если фича/фаза затрагивает Firebase (auth, sync, backup, cloud functions, Firestore rules) — реализуй по назначенному phase-файлу и design/ADR; пути из PROJECT-CONTEXT.md. НИКОГДА не удаляй тестовые файлы вне Deleted Files. Не затрагивает — пройди насквозь «firebase: N/A». Отчёт: файлы/реализовано/валидация/follow-up. Fix-режим: только указанные замечания.
```

#### `dev_test` — Test Dev (TDD) · test-dev · gpt-5.5/high
```text
=== PHASE — TESTS ===. Начни НЕМЕДЛЕННО. Работаешь ПАРАЛЛЕЛЬНО с coder-ами. Прочитай ТОЛЬКО plan/phase-NN/tests.md + .claude/rules/testing.md. Пиши JVM unit-тесты по ОЖИДАЕМЫМ интерфейсам из design (даже если production ещё не готов); используй fakes по проектной конвенции (JUnit4+MockK+coroutines-test, no Turbine). Spec Scenario Coverage: каждый GIVEN/WHEN/THEN из 0-spec.md → соответствующий тест: fun `when <action> given <context> then <result>`. Walking Skeleton: pure-domain тесты уже зелёные — НЕ дублируй; для phase-01 только integration-тесты. Production-код НЕ меняй (нет seam — сообщи, не правь); scaffold НЕ трогай (запросы к backend-dev через Open Questions/отчёт). НИКОГДА не удаляй тест-файлы вне Deleted Files. Обязательная таблица покрытия | # | Spec / Domain Scenario | Test file:method | Status (Written / NOT COVERED) |. Отчёт: тест-файлы, таблица, gaps. Fix-режим: только замечания по test code.
```

#### `dev_integration` — Integration Tester · integration-tester · gpt-5.5/high
```text
Интеграционный тестер (conditional). Активируйся, только если фаза затрагивает: lifecycle-зависимую логику (process death, restore), multi-layer flow (Component → UseCase → Repository → DAO), Room DAO boundary values, WebSocket/realtime chains, concurrency — иначе пройди насквозь «integration tests: N/A». Прочитай phase-файл, 04-testing.md, 02-behavior.md (State Transitions & Edge Cases), 2-grounding.md (Constraints, Flow Trace), 0-spec.md (GIVEN/WHEN/THEN), .claude/rules/testing.md; учитывай unit-тесты test-dev — не дублируй покрытие. Пиши instrumented/integration тесты: AndroidJUnit4 + Room.inMemoryDatabaseBuilder().allowMainThreadQueries(); DAO boundary (negative ID, 0, MAX_LONG, empty table) для КАЖДОГО query; multi-layer сценарии; lifecycle edge cases; concurrency через StandardTestDispatcher + advanceTimeBy(). НЕ используй Hilt. Имя теста `given boundary when action then expected`, один тест = один сценарий. НИКОГДА не удаляй тест-файлы. Отчёт: файлы, таблица scenario → test → result, использованные fakes, непокрытое и почему.
```

#### `dev_join` — Dev Join · join · (пусто)

#### `build_gate` — Build & Test Gate (Lead) · lead · gpt-5.5/xhigh
```text
Гейт фазы до ревью. Верифицируй сам (не верь заявлению «tests passed» без вывода gradle): ./gradlew ciCheck --no-configuration-cache — exit code 0, последние 30 строк вывода сохрани в комментарий задачи; если фаза меняла androidTest — ./gradlew assembleDebugAndroidTest --no-configuration-cache. Test Deletion Gate: git diff --name-status HEAD -- '*/test/**' — каждый удалённый тест-файл обязан быть в Deleted Files текущего overview.md, иначе reject. Если фаза трогала scripts/qa_lib/*.sh — прогони bash -n и source-цепочку (аналог check-qa-sourcing.sh). Падает → transition build_failed (назад в implement на исправление с полным текстом ошибок; неочевидный root cause — implement поднимет debug-ветку). Зелёное → transition review (параллельное ревью 5 same-model ревьюеров: code/architect/security/completeness/concurrency — per-phase cross-model НЕ запускается, cross-model только на cross-phase; в задание каждому войдёт строка «Build Status: PASSED (commit <sha>)» — ревьюер обязан отказаться работать без неё, per agent-communication.md).
```

#### `review_code` — Code Review · code-reviewer · gpt-5.5/high
```text
Code review фазы. Проверь в задании строку «Build Status: PASSED (commit ...)»; отсутствует или не PASSED → ERROR «Build status not confirmed in assignment, refusing to start review» и не начинай (сам build не запускай — ты не build agent). Прочитай plan/phase-NN/overview.md, 0-spec.md, .claude/PROJECT-CONTEXT.md, .claude/rules/; сверь diff фазы. Ищи: корректность, регрессии, lifecycle-баги, misuse API, пропущенную валидацию, отсутствие тестов рискованного поведения. Обязательные same-model проверки: field access (каждое поле внешнего SDK/API-ответа существует и тип совпадает — cross-check с research/grounding); async timing (два сходящихся потока — что при разном порядке завершения). Severity blocker (crash/потеря данных/сломанный контракт) / high / medium / low; каждый finding: Location file:line, Problem, почему важно, минимальное исправление. Секции: Замечания / Отсутствующие тесты / Оставшийся риск / Open Questions. Код не редактируй. Findings — в комментарии задачи.
```

#### `review_architect` — Architecture Review · architect-reviewer · gpt-5.5/high
```text
Architecture review фазы. Требуй «Build Status: PASSED» в задании (иначе ERROR, не начинай). Прочитай 01-architecture.md, 03-decisions.md, phase overview, .claude/rules/{clean-architecture,di-patterns,domain-models,lifecycle,navigation,auth-scoped-flow,kotlin-conventions}.md. ОБЯЗАТЕЛЬНО запусти все grep-проверки до вердикта (verdict без grep results = incomplete review): (1) domain purity: rg '^import (android|androidx)\.' + SDK-типы + DI-аннотации в shared/**/domain/src/commonMain — match в changed files = blocker; (2) presentation boundary: импорты Dao|Entity|DataSource|Mapper|Firebase|Room и getKoin(|koinInject(|inject< в presentation/ui; (3) cross-module: core не импортирует feature; feature-to-feature (bidirectional = blocker); (4) lifecycle: business actions в onDestroy без if (isFinishing && !isChangingConfigurations) = blocker; (5) Koin sanity: duplicate binding / Hilt-Dagger аннотации = blocker; (6) ADR-vs-code fidelity: извлеки из 03-decisions.md все code-level constraints и grep-ни каждый в changed code — паттерн отсутствует = HIGH (Modeling Error / Plan Faithfulness) с шаблоном ADR/Constraint/Code Evidence/Impact; (7) module direction (designsystem не принимает feature-типы); (8) auth-scoped-flow: RepositoryImpl с observe* без currentUidFlow().flatMapLatest, currentUid() вне flatMap, emptyFlow() при null uid — match в changed = blocker. Existing untouched matches = existing debt, не blocker. В отчёте — Reporting Checklist: какие greps запущены, что с matches, что clean, причина пропуска. Ревью относительно ТЕКУЩЕГО утверждённого дизайна. Код не редактируй; findings в комментарии задачи.
```

#### `review_security` — Security Review · security-reviewer · gpt-5.5/high
```text
Security review фазы (ты полноправный участник, не опциональный). Требуй «Build Status: PASSED» (иначе ERROR). Проверь diff: auth tokens, intents, exported components (без защит = blocker), content-provider утечки, WebView/addJavascriptInterface, spoofing/deep-link параметры без валидации, хранение credentials, чувствительный logging в production, certificate pinning, доверие к realtime payload, утечки состояния через новый persistence/transport. Severity: blocker (утечка credential/незащищённая exported surface/критическая утечка данных) / high (spoofing risk, missing auth/validation) / medium (слабое hardening) / low. Реальные attack surface выше теоретических. Секции: Замечания (Location file:line, Problem, вектор атаки, минимальная мера) / Отсутствующие проверки / Оставшийся риск. Код не редактируй; findings в комментарии задачи.
```

#### `review_completeness` — Completeness Review · completeness-reviewer · gpt-5.5/high
```text
Completeness review фазы. Требуй «Build Status: PASSED» (иначе ERROR). Прочитай plan/phase-NN/overview.md, 01/02/03-доки, 0-spec.md; сверь с кодом. Проверки: каждый файл из scope плана существует и соответствует; каждый acceptance criterion фазы имеет подтверждение в коде или тестах; каждое поведение из 02-behavior.md реализовано; каждое решение 03-decisions.md соблюдено или явно обновлено; Spec→Plan Coverage (AC без реализующей фазы = blocker|missing-ac); Domain Contract Coverage (правило/сценарий без кода+теста = blocker|missing-behavior); State Matrix Coverage (ячейка без кода и теста = blocker; код есть, теста нет = high|missing-test); Design→Plan Fidelity (сужение абстрактного требования = blocker|design-deviation с перечнем пропущенного). Проверь: нет ли удалённого/скрытого функционала (View.GONE, removed, simplified) без обоснования в phase file. Формат: severity | type (missing-file/missing-behavior/missing-ac/design-deviation/partial-implementation) | source | location | требуемое действие; + чек-лист полноты, Residual gaps, Open Questions. «Implementation looks complete» без доказательств — запрещено. Не принимай молча упрощённые реализации. Findings в комментарии задачи.
```

#### `review_concurrency` — Concurrency Review · concurrency-reviewer · gpt-5.5/high
```text
Concurrency review (conditional: только если у фазы тег concurrency-review в overview.md или фаза трогает coroutines/Flow/shared mutable state/lifecycle callbacks/mutex — иначе пройди насквозь «concurrency review: N/A»). Требуй «Build Status: PASSED» (иначе ERROR). Прочитай .claude/rules/kotlin-conventions.md. Для КАЖДОГО изменённого файла с async-кодом чеклист: Async Timing (два сходящихся потока; default до завершения async; cached vs fresh); Race Conditions (shared mutable state, idempotency/guards, read-modify-write); Coroutine Scope Lifecycle (cancel propagation, self-cancellation, SupervisorScope vs coroutineScope); State Lifecycle (reset при re-init, очистка прошлой сессии, process death); Flow Collection (SharedFlow replay=0 и late collectors, initial value StateFlow, collect при STOPPED). ДЕТЕРМИНИРОВАННЫЙ grep-гейт из kotlin-conventions.md «### Review check» (обязателен для фаз, меняющих event streams): запусти `rg -n "Channel.*(receiveAsFlow|consumeAsFlow)" --type kt`; для каждого совпавшего поля посчитай collect-sites (`.collect|collectLatest|collectIndexed` по имени того же поля) — >1 collector на одном Channel = blocker (nondeterministic event split: один-consumer-контракт нарушен, события расщепляются; несколько observer'ов → нужен SharedFlow). Плюс проверь правила kotlin-conventions: no `!!` вне тестов (→ requireNotNull с описанием) = high; GlobalScope вместо lifecycle-aware/structured-concurrency scope = blocker; blocking-вызовы на Main thread; НЕТ JSON-парсинга в Compose Screen/Decompose Component/Fragment/ViewModel/Activity = blocker; лимиты размеров (файл >1000 строк / функция >100 / nesting >3 / params >7 = must-fix) и нейминг (verb-first, is*/has*/can*/should* для boolean, observe*/*Flow для потоков) в затронутых файлах. Для каждого finding — КОНКРЕТНЫЙ сценарий (порядок событий), не абстрактная race condition; не предлагай mutex, если решается порядком вызовов/immutable data. Severity + category + location + scenario + минимальный fix; Open Questions (что требует runtime/device). Findings в комментарии задачи.
```

#### `review_join` — Review Join · join · (пусто)

#### `verdict` — Phase Verdict (Lead) · lead · gpt-5.5/xhigh
```text
Судья ревью фазы (lead judgment, в контексте implement-сессии). Прочитай findings всех 5 same-model ревьюеров (code/architect/security/completeness/concurrency) из комментариев задачи (per-phase cross-model ревью нет — cross-model только на cross-phase). Вердикт-логика: нет high/blocker → PASS; high с расхождением ревьюеров → CONTESTED — реши сам; blocker или консенсусный high → REJECT. Architectural mismatch / повторный blocker того же класса (2-3 итерации) / reviewer disagreement — СТОП, спроси пользователя через kent ask_question с полным контекстом. Обнови Run Ledger (completedPhases, nextAction). Исходы: REJECT → needs_changes (fix loop в implement: адресные замечания с severity+file:line нужным devs); PASS и остались фазы → next_phase (implement, следующая фаза; между фазами — свежие сессии работников); PASS и все фазы завершены → phases_done (smoke-гейт перед cross-phase review).
```

### 2.6 CROSS-PHASE (8 нод)

#### `impl_smoke` — Smoke Gate (Lead) · lead · gpt-5.5/xhigh
```text
Smoke-гейт перед cross-phase review (Codex-эквивалент должен получить green build, не build-broken state). Выполни: (1) полный ./gradlew ciCheck --no-configuration-cache — verify exit code 0, последние 30 строк вывода в комментарий задачи; НЕ принимай «all tests passed» на слово — только вывод gradle. (2) Если менялся androidTest или у фичи UI flow: ./gradlew assembleDebugAndroidTest --no-configuration-cache. (3) E2E instrumented (UI flow с lifecycle: rotation, system Back, FLAG_SECURE, restore): при подключённом устройстве ./gradlew connectedAndroidTest; устройства нет — тест НЕ считается completed: пометь в implementation.md «Manual smoke на device required» и спроси пользователя через kent ask_question (запустить вручную / defer). (4) scripts/pipeline/check_pipeline_docs.sh docs/features/<slug> (если есть в репо; падает → это исправимо на уровне docs — исправь артефакты сам ТОЛЬКО в части docs-drift, код не трогай). Исходы: (a) любой красный гейт по коду/тестам (сборка/детерминированный fail) → transition smoke_failed (назад в implement на fix loop с полным текстом ошибок; Codex-ревью на красном билде запрещено); (b) финальный реалистичный E2E/UI-тест выявил РАНТАЙМ-баг, которому нужна диагностика (не простое падение сборки, а неочевидное поведение runtime/lifecycle) → transition debug (живая диагностика Phase 1; param problem = slug и описание симптома); (c) всё зелёное → transition crossphase (fan-out cross-model + same-model cross-phase ревьюеры).
```

#### `cp_crossmodel` — Cross-Phase Cross-Model Review · crossmodel-reviewer · gpt-5.4/xhigh
```text
Ты cross-model рецензент (gpt-5.4) — ЕДИНСТВЕННАЯ точка пайплайна, где весь diff смотрит другая модель (ловит shared blind spots same-model ревьюеров). Вход: полный source diff всех фаз (git diff), plan/phase-*/overview.md + role-файлы, 0-spec.md (AC + Feature Domain Contract). Primary focus: cross-phase integration — DI chain целиком, orphaned abstractions, контракты между фазами, race conditions на стыках. Spec Scenario Coverage: каждый GIVEN/WHEN/THEN из 0-spec.md имеет integration test; каждый Domain Test Scenario — JVM или integration test; пропуски перечисли явно. Отчёт в docs/features/<slug>/reviews/implement.review.md (findings + severity blocker/high/medium, file:line). Код не редактируй.
```

#### `cp_code` — Cross-Phase Code Review · code-reviewer · gpt-5.5/high
```text
Cross-phase code review: полный source diff всех фаз (не одной). Фокус: корректность на стыках фаз, регрессии, error handling, field access внешних SDK, async timing сходящихся потоков. Severity + file:line; findings в комментарии задачи. Код не редактируй.
```

#### `cp_architect` — Cross-Phase Architecture Review · architect-reviewer · gpt-5.5/high
```text
Cross-phase architecture review: граф зависимостей всей фичи, DI chain от composition root, orphaned abstractions, соответствие 01-architecture.md/03-decisions.md итоговому коду. Запусти обязательные grep-проверки (domain purity, boundaries, cross-module, lifecycle, Koin, ADR-vs-code, module direction, auth-scoped-flow) по ИТОГОВОМУ diff. Reporting Checklist обязателен. Findings в комментарии задачи.
```

#### `cp_security` — Cross-Phase Security Review · security-reviewer · gpt-5.5/high
```text
Security audit на уровне всей фичи (итоговый diff): сквозные trust boundaries, суммарная exported surface, токены/credentials, logging, deep links, realtime contracts. Severity + file:line; findings в комментарии задачи.
```

#### `cp_completeness` — Cross-Phase Completeness Review · completeness-reviewer · gpt-5.5/high
```text
Финальный completeness: сверь КАЖДЫЙ acceptance criterion из 0-spec.md и КАЖДЫЙ пункт всех plan/phase-NN с итоговым кодом; Spec→Plan Coverage, Domain Contract Coverage, State Matrix Coverage, Design→Plan Fidelity — по всей фиче. Удалённый/скрытый функционал без обоснования = blocker. Findings (severity|type|source|location|action) в комментарии задачи.
```

#### `cp_join` — Cross-Phase Join · join · (пусто)

#### `cp_verdict` — Cross-Phase Verdict + Handoff (Lead) · lead · gpt-5.5/xhigh
```text
Финальный судья фичи. 1) Прочитай reviews/implement.review.md + findings same-model ревьюеров. 2) Quality Scorecard: сгенерируй docs/features/<slug>/quality-scorecard.md — таблица Architecture/Correctness/Completeness/Security/Code Organization × Grade A-F, Blockers/High/Medium (A = 0 findings, B = only medium, C = 1-2 high, D = 3+ high, F = any blocker) + Overall; это метрика качества ДО cross-model вмешательства. 3) Есть blocker/high, требующие фикса → transition fix (адресный fix loop в implement: какие findings, какие файлы, каким devs). 4) Иначе Post-Codex Final Smoke: scripts/pipeline/check_pipeline_docs.sh, ./gradlew ciCheck --no-configuration-cache, при androidTest-изменениях assembleDebugAndroidTest, при необходимости documented device smoke из overview.md/PROJECT-CONTEXT.md (не выдумывай несуществующие скрипты); красное → transition fix. 5) Deferred HIGH/BLOCKER: каждый finding, который помечается DEFERRED, требует явного approve пользователя через kent ask_question («Found <severity> issue ... Accept as known debt?» A: accept defer — в implementation.md Remaining Issues с owner/gate/rationale/датой approve; B: block handoff — fix loop; C: reduce severity с обоснованием); без approve — fix, не handoff. 6) Handoff: запиши docs/features/<slug>/implementation.md (Summary, Phases Completed, Review Verdicts, Changed Files, Remaining Issues), обнови README (Status: implemented), transition handoff (ретроспектива; ребро ждёт подтверждения пользователя). No auto-commit/push.
```

### 2.7 DEBUG (16 нод)

#### `debug` — Debug Phase 1 (Lead) · lead · gpt-5.5/xhigh
```text
Живая диагностика бага: ты координатор, НИКОГДА не читаешь исходный код, не анализируешь логи и не пишешь fix — только координация и Fix Spec. Fix применяется ТОЛЬКО после явного одобрения пользователя. Определи КОНТЕКСТ запуска (важно для терминального выбора фикса дальше по подграфу): standalone на готовой фиче (вход spec_intake --to_debug-->), живая диагностика посреди реализации (вход implement --debug-->) или рантайм-баг из финального E2E/UI-теста (вход impl_smoke --debug-->); зафиксируй его (Run Ledger run/pipeline-state.json, если есть). Из задания извлеки feature-slug (первое слово) и описание проблемы; slug неясен — спроси через kent ask_question. Прочитай ТОЛЬКО AGENTS.md, .claude/PROJECT-CONTEXT.md (debug_package_name, build commands), docs/features/<slug>/README.md; НЕ читай feature-доки/скилы — это работа агентов. Проверь adb devices -l (есть ли устройства). Phase 1 всегда выполняется, time budget-а нет. Выбери transition phase1 — fan-out: doc-analyst (контекст по докам) + log-reader (quick scan; в задании передай package и укажи, что при отсутствии adb-устройств или не-runtime симптоме он проходит насквозь).
```

#### `dbg_doc` — P1 Doc Context (doc-analyst) · doc-analyst · gpt-5.5/high
```text
Lightweight context gathering для debug-сессии. Прочитай и резюмируй ≤400 словами: 0-spec.md (Domain Contract, Journeys, State Matrix), README (статус, ссылки на past debug-*.md), ВСЕ docs/features/<slug>/debug-*.md (для каждого: root cause, applied fix, related symptoms), ВСЕ fix-spec-*.md (особенно deferred/not applied), docs/invariants.md. Для каждого документа — file:line. Если past report описывает ТАКОЙ ЖЕ симптом — отметь явно (это триггер early-out). Только документы, НЕ исходный код.
```

#### `dbg_log_scan` — P1 Log Quick Scan (log-reader) · log-reader · gpt-5.5/high
```text
Lightweight log scan. Только если есть подключённые adb-устройства (adb devices -l) И симптом runtime — иначе пройди насквозь «log scan: N/A». Захвати последние 5 минут logcat (фильтр package + severity ERROR/WARN/FATAL): stack traces, ANR/CRASH markers, repeating errors (>3 одинаковых в минуту). Резюме ≤300 слов: топ-5 аномалий с timestamp + 3-строчный сниппет; аномалий нет — «no visible anomalies». НЕ анализируй в глубину — это quick scan.
```

#### `dbg_p1_join` — Debug P1 Join · join · (пусто)

#### `dbg_advisor` — Debugger Team Advisor (diagnostics) · diagnostics · gpt-5.5/high
```text
Team Composition Proposal для debug-сессии (режим advisor). Вход: problem, суммарные findings Phase 1 (из комментариев задачи). Прочитай только: .claude/PROJECT-CONTEXT.md, README фичи, 0-spec.md, 2-grounding.md, debug-*.md / fix-spec-*.md, docs/invariants.md. НЕ запускай build/test/logcat, НЕ читай production source. Классифицируй проблему (crash/lifecycle/di/concurrency/rate-limit/network/ui/logic/data/realtime/unknown) и severity (blocker/high/medium/low). Предложи targeted-команду Phase 2: mandatory teammates, optional с триггерами, кого НЕ поднимать, когда добавить log-reader/code-analyst/web-researcher, какое evidence обязан дать каждый; Confidence. Fallback-минимум по категориям: crash → code-analyst+log-reader; lifecycle/concurrency → code-analyst+log-reader+skill systematic-debugging mandatory; di/logic → code-analyst; data → code-analyst+doc-analyst; network/rate-limit/realtime → +web-researcher; unknown → все четыре. «Если сомневаешься — подними меньше».
```

#### `dbg_decision` — Debug Decision (Lead) · lead · gpt-5.5/xhigh
```text
Точка решения. Сведи Phase 1 в assessment-блок пользователю: Problem/Category/Severity; Known context (spec, past reports, invariants, immediate signals); Hypothesis (1-3 предложения); Recommended next step ([OBVIOUS] → сразу Fix Spec / [INVESTIGATION NEEDED] → Deep Debug с командой из proposal). Early-out (пропустить Phase 2): past report тот же симптом с applied fix; stack trace указывает single file:line + invariant объясняет root cause; симптом покрыт существующим failing test; category=logic + grep находит точное место. Затем ОБЯЗАТЕЛЬНО спроси пользователя через kent ask_question: (A) принять hypothesis → сразу Fix Spec; (B) Deep Debug с предложенной командой; (C) Deep Debug с составом от пользователя; (D) дать больше контекста, вернуться в Phase 1. WAIT — не переходи самостоятельно. Исходы: A → transition fixspec; B/C → transition deep (fan-out команды; при C передай в задания состав пользователя, ненужным — «пройди насквозь»); D → transition moreinfo (назад в debug). При runtime-симптоме и 0 устройств перед deep уточни: «Phase 2 без live логов работает по коду/докам. Продолжать?».
```

#### `dbg_code` — Deep Debug Code Analyst · code-analyst · gpt-5.5/high
```text
Ты code-analyst debug-команды. Вход: category, problem, hypothesis Phase 1. Прочитай .claude/PROJECT-CONTEXT.md, .claude/rules/clean-architecture.md, 2-grounding.md (entry points, code owners). ОБЯЗАТЕЛЬНО примени протокол .claude/skills/systematic-debugging/SKILL.md (для category ∈ {lifecycle, concurrency, unknown} — строго): Iron Law «NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST»; Phase 1 Root Cause Investigation (читать stack traces целиком, воспроизведение, недавние изменения, диагностическое логирование на границах компонентов, трассировка data flow назад — references/root-cause-tracing.md); Phase 2 Pattern Analysis (найти работающий аналог, читать целиком); Phase 3 — ОДНА гипотеза, минимальная проверка, одна переменная; Phase 4 — сначала failing test, потом один фикс; счётчик: <3 неудачных фикса → назад в Phase 1, ≥3 → STOP и вопрос об архитектуре (эскалация lead-у). Трассируй код от entry points: цепочка EntryPoint.kt:line → ... → SuspectPoint.kt:line; на каждом шаге Null/Concurrency/Lifecycle/State/Boundary; чеклист паттернов (shared mutable state, collect vs collectLatest, SharedFlow replay=0, SQL id<=:max при negative IDs, mapper nullable, reconnect без re-subscribe...). Опционально ОДИН GLM-вызов (python3 .claude/skills/glm/scripts/glm_query.py --profile debug --json --prompt "<trace map + findings>"); упал/нет ключа — продолжай; GLM = hypothesis input, не evidence. Валидация гипотез: ./gradlew test --tests "*ClassName*" --no-configuration-cache. Вывод — Code Analysis Report: Trace Map; Confirmed Bugs (BUG-N: location file:line, category, root cause, call chain, evidence, severity, proposed fix как diff); Suspect Areas (SUSPECT-N + что нужно от log-reader/doc-analyst); Code Health Notes. НЕ исправляй код — только диагностика и diff-предложения.
```

#### `dbg_log` — Deep Debug Log Reader · log-reader · gpt-5.5/high
```text
Ты log-reader debug-команды (по одному устройству; несколько устройств — обработай каждое по очереди, findings помечай устройством). Нет adb-устройств — пройди насквозь «live logs: N/A». Package = debug_package_name из PROJECT-CONTEXT.md. adb -s <serial> get-state; logcat -c; adb -s <serial> logcat --pid=$(adb -s <serial> shell pidof -s <package>) -v threadtime (fallback *:W). Ищи: FATAL EXCEPTION/AndroidRuntime, ANR in, exceptions по package, WebSocket events, HTTP (OkHttp, коды, timeouts), SQLiteException/MIGRATION, lifecycle timing, custom tags из default_log_tags. Формат finding: [LOGGER <device>] <CRASH|ANOMALY|TIMING|INFO>: описание + timestamp/thread/tag/context 2-3 строки/correlation; при crash — полный stacktrace без сокращений. Приложение перезапустилось — переподключи logcat к новому PID. Опционально ОДИН fail-open GLM-вызов для breadth-паттернов в логах (python3 .claude/skills/glm/scripts/glm_query.py --profile debug --json --prompt "<compact log evidence packet>"; как в dbg_code/research_codebase — GLM sidecar исторически жил на log-reader); упал/нет ключа ZAI_API_KEY/GLM_API_KEY — продолжай без GLM и отметь в отчёте; GLM = hypothesis input, не evidence; секреты в prompt не отправляй. Только логи: код не читаешь, приложение не модифицируешь.
```

#### `dbg_doc2` — Deep Debug Doc Analyst · doc-analyst · gpt-5.5/high
```text
Ты doc-analyst debug-команды. НЕ доверяй документации по умолчанию: она может быть верной (баг в коде) или неверной (код прав, доки врут). Прочитай ВСЕ доки фичи по порядку: 0-spec.md, 1-research.md, 2-grounding.md, 01-04, 06/07/08, plan/phase-*/, implementation.md, retrospective.md, docs/invariants.md. Построй Claims Map (| # | Document | Claim | Type behavior/contract/requirement/constraint/invariant/assumption | Verified? |), сверь каждый claim с проблемой (CONFIRMED/CONTRADICTED/UNVERIFIED/N/A); проверь внутреннюю согласованность (spec AC ↔ design ↔ plan ↔ implementation; grounding assumptions; invariants). Вывод — Documentation Analysis Report: Claims Map; Inconsistencies (INC-N: source doc:line, claim, reality, severity, who is wrong: documentation/code/both/unclear, impact); Cross-Document Contradictions; Missing Coverage; Recommendations. Исходный код НЕ читаешь (это code-analyst). Каждое несоответствие — с точной ссылкой document:line.
```

#### `dbg_web` — Deep Debug Web Researcher · web-researcher · gpt-5.5/high
```text
Ты web-researcher debug-команды (conditional: только если категория/симптом связаны с external SDK/library/platform — иначе пройди насквозь «web: N/A»). Ищи: error messages/stack traces → known issues, SO, GitHub issues; SDK-quirks через Context7 MCP; platform-specific behavior (Android version, vendor). Каждый факт с source URL; не найдено — [NOT FOUND]; расхождение docs↔наблюдаемому — [DISCREPANCY]. Приоритет: official docs > GitHub issues > SO > blogs.
```

#### `dbg_join` — Deep Debug Join · join · (пусто)

#### `dbg_synth` — Debug Synthesis (Lead) · lead · gpt-5.5/xhigh
```text
Ты лид-судья debug. Сведи findings команды (Code Analysis Report, логи, Claims Map, web) в root cause: hypothesis tracking (таблица гипотез с evidence-статусом), cross-reference расхождений. Нет hard cap на гипотезы и время; escalation-сигналы (несколько гипотез отвергнуто без сходимости; агенты по кругу; противоречие агентов; нужен repro/логи с нового сценария) — повод спросить пользователя через kent ask_question о направлении, не остановка. Если для подтверждения нужны diagnostic logs в коде — спроси пользователя («code-analyst предлагает добавить логи в [точки] для гипотезы [X]. Добавить?»), это единственное разрешённое изменение кода в debug. Если evidence противоречит Feature Domain Contract — НЕ переписывай contract молча, эскалируй. РЕШЕНИЕ: root cause найден (или гипотезы исчерпаны — тогда «root cause NOT confirmed» с вариантами) → transition fixspec; сходимости нет и нужен ещё раунд с новыми гипотезами/составом → transition rerun (назад к dbg_decision).
```

#### `dbg_fixspec` — Fix Spec (Lead) · lead · gpt-5.5/xhigh
```text
Phase 3: fix НЕ применяется — только ТЗ. Запиши docs/features/<slug>/fix-spec-<YYYY-MM-DD>.md: frontmatter (date, feature, problem, status: proposed); Problem (Symptom/Reproducer/Severity); Root Cause (CONFIRMED с file:line ИЛИ HYPOTHESIZED top-N с вероятностями; Evidence из code/logs/docs/past reports; «Why pipeline missed this» — какая стадия должна была поймать, feed в retrospective); Proposed Fix (Scope: small 1-5 строк в 1-2 файлах / medium multi-file один layer / large cross-layer; Changes file-by-file; New/Updated Tests GIVEN/WHEN/THEN — минимум 1 regression test, воспроизводящий симптом; Regression risk; Rollback plan); Acceptance Criteria; Apply Method (Direct apply — только small; Via feature-implement — medium/large; Deferred); Open Questions. Обнови README фичи (Debug History + Pending Fixes при defer). Покажи пользователю краткое summary (=== FIX SPEC READY ===: problem, root cause [CONFIRMED|HYPOTHESIZED], scope, risk, recommended method, путь файла) и спроси через kent ask_question: (A) Apply сейчас напрямую — ТОЛЬКО если scope=small (1-5 строк, 1-2 файла); (B) Спланировать фикс — превратить fix-spec в план и пройти полный per-phase цикл реализации (medium/large; status: handoff-to-implement) — ОСНОВНОЙ путь; (C) Отложить (status: deferred); (D) Пересмотреть root cause (назад в Phase 2). WAIT. Терминальный выбор фикса (done vs возврат в implement) делается по Run Ledger / контексту запуска debug: был ли debug standalone (из spec_intake·to_debug / impl_smoke на готовой фиче) или посреди реализации (из implement). Исходы: A → transition apply_direct (прямой small-фикс, ребро ждёт approve → dbg_fix_dev); B → transition to_plan (fix-spec → планировщик dbg_plan → implement, полный цикл); C → по контексту transition defer_done (standalone → done) ИЛИ defer_impl (посреди реализации → вернуться в implement с записью Pending Fix); D → transition revisit (назад в dbg_decision).
```

#### `dbg_plan` — Debug Fix Planner (planner) · planner · gpt-5.5/high
```text
Планировщик фикса: превращаешь одобренный fix-spec в исполняемый план (medium/large-фикс, полный per-phase цикл реализации). Прочитай docs/features/<slug>/fix-spec-<YYYY-MM-DD>.md + отчёты debug-команды из комментариев задачи (Code Analysis Report с Trace Map/Confirmed Bugs, логи log-reader, Claims Map doc-analyst) + .claude/PROJECT-CONTEXT.md + затронутые design/plan-доки (01-04, 06, plan/README.md, релевантные plan/phase-*/). Разбей фикс в план: ОДНА фаза docs/features/<slug>/plan/phase-NN/ (zero-padded, следующий свободный номер) или docs/features/<slug>/fix-plan.md, targeting ТОЛЬКО изменения из fix-spec (Changes file-by-file) + ОБЯЗАТЕЛЬНЫЙ regression test, воспроизводящий исходный симптом. Формат как у planner: overview.md (Goal, Scope, Traceability на root cause из fix-spec, New/Modified/Deleted Files, Acceptance Criteria из fix-spec, Tests Required given/when/then, Validation-таблица — строка 1 всегда ./gradlew ciCheck --no-configuration-cache); Signature Card на каждый НОВЫЙ файл (путь, тип, inline-сигнатура, вход, поведение, edge cases, canonical reference или «internal», rationale); ЗАПРЕЩЕНО fenced kotlin/kt/java/groovy в plan-файлах (Plan = ТЗ). Domain НЕ переписывается (Walking Skeleton сохраняется — сигнатуры/бизнес-правила не трогаем; нужно менять domain → это blocker плана, эскалируй, а не переписывай). Единственный исход: transition to_impl — реализация фикса по плану полным per-phase циклом implement (dev → build_gate → 5 ревьюеров → verdict → smoke → cross-phase).
```

#### `dbg_fix_dev` — Direct Fix Dev (coder) · coder · gpt-5.5/high
```text
Прямое применение small-фикса по fix-spec-<date>.md (пользователь одобрил). Реализуй ТОЛЬКО изменения из Fix Spec (1-5 строк, 1-2 файла) + regression test из секции New/Updated Tests. Прочитай релевантные .claude/rules/ (testing.md и kotlin-conventions.md обязательно). Прогони ./gradlew ciCheck --no-configuration-cache; Test Deletion Gate. Обнови frontmatter fix-spec: status: applied. Никакого рефакторинга вокруг; scope вырос за small → STOP, сообщи (нужен to_plan → dbg_plan → implement). После review терминальный выбор (завершить vs вернуться в implement) делается по Run Ledger / контексту запуска debug — это решает нода dbg_fix_review. Отчёт: файлы, тест, вывод валидации.
```

#### `dbg_fix_review` — Direct Fix Code Review · code-reviewer · gpt-5.5/high
```text
Обязательный code review прямого фикса (после build gate). Сверь diff с fix-spec-<date>.md: только заявленные изменения; regression test воспроизводит исходный симптом; нет побочного рефакторинга; AC Fix Spec выполнены. Severity + file:line. Терминальный выбор (done vs возврат в implement) делается по Run Ledger / контексту запуска debug: standalone (из spec_intake·to_debug / impl_smoke) или посреди реализации (из implement). Исходы: чисто и debug был standalone → transition passed_done (завершение: README фичи обновлён — Debug History); чисто и debug посреди реализации → transition passed_impl (вернуться в implement продолжать фазы); blocker/high → transition fix (адресные замечания обратно dev-у).
```

### 2.8 RETROSPECTIVE (6 нод) + terminal

#### `retrospective` — Retrospective (Lead) · lead · gpt-5.5/xhigh
```text
Ретроспектива пайплайна: ты инженер по качеству пайплайна. Ключевой принцип: любой баг, найденный после реализации, — сбой пайплайна, а не единичная ошибка; исправляй систему, а не симптом. Delegation: сам читаешь напрямую только AGENTS.md и .claude/PROJECT-CONTEXT.md; собираешься читать >5 файлов — остановись, это работа читателей. Evidence-гейт: если feedback (описания багов, error log/stack trace, наблюдения QA, fix-коммиты) не передан в задании — спроси пользователя через kent ask_question: «Пришлите: 1) описания багов; 2) error log или stack trace; 3) feedback QA; 4) наблюдения о том, что пайплайн пропустил; 5) готовые fix commit/hotfix plan». WAIT — без evidence не продолжать. Также подтяни секции «Why pipeline missed this» из fix-spec-*.md. Затем transition retro_team — fan-out двух читателей.
```

#### `retro_artifacts` — Artifacts Reader (codebase-researcher) · codebase-researcher · gpt-5.5/high
```text
Reader-1: прочитай ВСЕ артефакты фичи: 1-research.md, 2-grounding.md, 01-architecture.md, 02-behavior.md, 03-decisions.md, 04-testing.md, 06/07/08 (если есть), все plan/phase-NN/ (overview + role-файлы), implementation.md, quality-scorecard.md, reviews/*.review.md, README. По каждому документу: ключевые утверждения/предположения; acceptance criteria; что выведено за scope; caveats/open questions. Со ссылками file:section.
```

#### `retro_instructions` — Instructions Reader (codebase-researcher) · codebase-researcher · gpt-5.5/high
```text
Reader-2: прочитай ВСЕ pipeline-инструкции: AGENTS.md (CLAUDE.md в репо нет), .claude/PROJECT-CONTEXT.md, .claude/commands/* (референс-описания стадий), .claude/rules/*, .claude/skills/adversarial-review/* и .claude/skills/domain-modeling/*, .claude/agents/*. По каждому файлу: требования к делегированию; quality gates и их enforcement; правила, ограничивающие реализацию; gaps (что НЕ покрыто). Со ссылками file:section. Учитывай: в Kent-редакции управляющая логика живёт также в промптах нод/рёбер воркфлоу — отметь, если инструкция файла расходится с фактическим поведением пайплайна.
```

#### `retro_join` — Retro Join · join · (пусто)

#### `retro_synth` — Retro Synthesis (Lead) · lead · gpt-5.5/xhigh
```text
Анализ root cause. Для каждого сообщённого бага: аудит по стадиям (Research/Design/Plan/Implement — CORRECT/INCOMPLETE/WRONG; Review — CAUGHT/MISSED); injection point (стадия + file:section), propagation path, detection gap (какой review/gate ДОЛЖЕН был поймать — Review Blind Spot); классификация по 12 failure patterns (Modeling Error, Missing Side-Effect Inventory, Commit-Before-Action, Assumption Not Verified, Test Validates Wrong Spec, Incomplete Research, Review Blind Spot, Plan Faithfulness, Integration Gap, Lifecycle Mismatch, Lead Role Violation, Delegated Decision Error — для последнего проверь Delegated Decisions Summary в 0-spec.md). ОБЯЗАТЕЛЬНЫЙ WebSearch до предложения фиксов (delegation problems / технология конкретного бага / AI review blind spots). Ключевой вывод-аксиома: одних текстовых инструкций недостаточно — эффективные исправления используют детерминированное enforcement (гейт-ноды, скрипты, tool restrictions), а не дополнительные абзацы. Запиши docs/features/<slug>/retrospective.md (Date, Summary, Bugs Analyzed, Stage Performance A-F, Pipeline Fixes Required — target file: Kent-нода/ребро (правится через kent CLI, НЕ действует на запущенные раны), .claude/rules/*.md, .claude/skills/*/references/*.md, PROJECT-CONTEXT.md, AGENTS.md (CLAUDE.md в репо нет); What to change; Why — какой pattern предотвращает; Prevents recurrence of Bug #N; Lessons Learned). retrospective.md пишется ВСЕГДА, даже без фиксов. Каждый фикс: Specific (точный файл/нода, секция, текст), Automated, Scoped (класс бага), Non-breaking (не удалять рабочие проверки). Покажи пользователю каждый фикс в формате Proposed Fix #N (File/Section/Current/Proposed/Rationale). РЕШЕНИЕ: есть одобряемые фиксы → transition apply (ребро ждёт approve пользователя; частичное одобрение допустимо — согласуй список через kent ask_question ДО перехода); фиксы не требуются или пользователь всё отклонил → transition no_fixes.
```

#### `retro_apply` — Apply Approved Fixes (Lead) · lead · gpt-5.5/xhigh
```text
Примени ТОЛЬКО одобренные пользователем исправления: правки файлов (rules/skills/PROJECT-CONTEXT/AGENTS.md — CLAUDE.md в репо нет) — через Edit; правки промптов Kent-нод/рёбер — сформируй и выведи готовые команды kent workflow node/edge update (сам их НЕ запускай без явного подтверждения; напомни: правка графа не действует на уже запущенные раны). Consistency-check: нет дублей между файлами, нет противоречий, cross-references корректны, ни одна рабочая инструкция не удалена. Пометь фиксы Applied в retrospective.md. Условно: cross-feature инвариант → добавь/обнови запись в docs/invariants.md (Invariant/Source/Owner/Added из retrospective Bug #N). Допиши docs/features/lessons-learned.md (только обобщаемые уроки: date — slug: lesson / Pattern / Lesson / Example). Обнови README фичи (retrospective.md, статус, применённые фиксы).
```

#### `done` — Done · terminal · (пусто)

---

## 3. Каталог РЁБЕР (132)

Формат: `from --[transition / edge-key]--> to` · context mode · context-source · approval · params · prompt template. Дефолт (не указан) = `new_session` / `immediate_source` / approval=0 / без params. Рёбра-рапорты работников в join идут без промпта.

### 3.1 SPEC (17)

1. `backlog --[intake / backlog_intake]--> spec_intake` · params: `feature=«описание фичи от пользователя»`
   ```text
   Интейк фичи (Phase 0). Фича: {{feature}}. Сгенерируй slug, создай docs/features/<slug>/ + заготовку 0-spec.md, определи type / pipeline tier (advisory) / нужен ли server analysis. Без диалога и без записи требований.
   ```
2. `spec_intake --[server_needed / intake_server]--> spec_server` · desc: «нужен серверный анализ»
   ```text
   Фича затрагивает серверное взаимодействие. READ-ONLY серверный анализ ДО диалога: routes/validation/auth/side effects/rate limiting/db → секции Server-Side Context / Server-Side Issues заготовки 0-spec.md.
   ```
3. `spec_intake --[no_server / intake_spec]--> spec` · desc: «сервер не нужен»
   ```text
   Серверный анализ не нужен. Сразу к продуктовому диалогу: Phase 2 (Smart Defaults→Confirm→Drill-down) → Phase 3 (0-spec.md + README) → Phase 3.5 (Domain Contract Lock).
   ```
4. `spec_server --[server_done / server_to_spec]--> spec` · desc: «серверная реальность зафиксирована» (единственный исход)
   ```text
   Серверный контекст записан в заготовку 0-spec.md. Продуктовый диалог с учётом серверных ограничений (Phase 2) → запись 0-spec.md (Phase 3) → Domain Contract Lock (Phase 3.5).
   ```
5. `spec --[skeleton / spec_to_sig]--> skeleton_sig` · desc: «Contract ≠ N/A»
   ```text
   Feature Domain Contract ≠ N/A. Stage A: сгенерируй signatures и package structure domain-слоя по 0-spec.md. Отчёт «SIGNATURES READY».
   ```
6. `spec --[review / spec_to_review]--> spec_review` · desc: «Contract = N/A»
   ```text
   Feature Domain Contract = N/A (skeleton не нужен). Прогони cross-model ревью 0-spec.md.
   ```
7. `skeleton_sig --[bodies / sig_to_bodies]--> skeleton_bodies` · group «Bodies» (fan-out)
   ```text
   Stage B: реализуй bodies по зафиксированным сигнатурам. Отчёт «BODIES READY».
   ```
8. `skeleton_sig --[bodies / sig_to_tests]--> skeleton_tests` · group «Bodies»
   ```text
   Stage B (параллельно): реализуй каждый Domain Test Scenario как @Test по зафиксированным сигнатурам. Отчёт «TESTS IMPLEMENTED».
   ```
9. `skeleton_bodies --[built / sb_join]--> skeleton_join` · (пусто)
10. `skeleton_tests --[built / st_join]--> skeleton_join` · (пусто)
11. `skeleton_join --[gate / skel_join_gate]--> skeleton_gate`
   ```text
   Оба отчёта получены. Прогони gradle-гейт, anti-pattern grep-чеклист и coverage-проверку skeleton.
   ```
10. `skeleton_gate --[advance / skel_advance]--> spec_review` · desc: «тесты зелёные, coverage полон»
    ```text
    Walking Skeleton зелёный. Прогони cross-model ревью 0-spec.md (skeleton доступен для выборочной сверки).
    ```
11. `skeleton_gate --[rerun / skel_rerun]--> skeleton_sig` · desc: «красные тесты/не компилируется — перезапустить стейджи»
    ```text
    Гейт красный. В комментарии задачи — диагноз (кто ошибся: signatures/bodies/tests) и точные исправления. Перезапусти Stage A при неверных сигнатурах (иначе сохрани их) и Stage B.
    ```
12. `skeleton_gate --[respec / skel_respec]--> spec` · desc: «спека двусмысленна / scope overflow»
    ```text
    Spec требует доработки: см. вопросы/противоречия в комментарии задачи. Обнови 0-spec.md с пользователем (при scope overflow — Phase 2.5 Task Splitting), затем фича снова пойдёт по пайплайну.
    ```
13. `spec_review --[judge / spec_rev_verdict]--> spec_verdict` · **continue_session**
    ```text
    Review записан. Сведи вердикт по reviews/0-spec.review.md.
    ```
14. `spec_verdict --[approved / spec_approved]--> research` · **requires-approval** · desc: «нет blocker/high — пользователь одобряет ТЗ»
    ```text
    Spec одобрена пользователем. Исследование: ты диспетчер, сам код не читаешь. Прочитай 0-spec.md; delta-вопросы пользователю только при реальной необходимости; на каждый Search Criterion — задание criteria-исследователю, отдельно core-scan (условно), ОБЯЗАТЕЛЬНЫЙ cross-feature scanner и conditional web-researcher. Инварианты из docs/invariants.md — в задания. Затем fan-out research_team.
    ```
15. `spec_verdict --[needs_changes / spec_needs_changes]--> spec` · desc: «blocker/high в ТЗ»
    ```text
    Cross-model review нашёл проблемы в spec. Findings: docs/features/<slug>/reviews/0-spec.review.md. Закрой их с пользователем и обнови 0-spec.md (и skeleton, если затронут).
    ```

### 3.2 RESEARCH (14)

16. `research --[research_team / research_to_criteria]--> research_codebase` · group «Research Team»
    ```text
    Отработай ВСЕ Search Criteria из 0-spec.md: факты с file:line, 4 обязательных скана (Impact/Duplicate/Entry Points/пути), инвариантные проверки из задания диспетчера.
    ```
17. `research --[research_team / research_to_core]--> research_core` · group «Research Team»
    ```text
    Core-scan затронутых core/<package> (README, *Policy.kt, contracts); фича core не трогает — насквозь.
    ```
18. `research --[research_team / research_to_crossfeat]--> research_crossfeat` · group «Research Team»
    ```text
    Полная карта cross-feature dependencies фичи (graph, bidirectional risks, undocumented reflection, shared SDK).
    ```
19. `research --[research_team / research_to_web]--> research_web` · group «Research Team»
    ```text
    Conditional: external SDK/platform API из спеки — official docs, known issues, shared-SDK интеграционные паттерны. Internal-фича — насквозь.
    ```
20-23. `research_codebase|research_core|research_crossfeat|research_web --[found / rc_join|rcore_join|rcf_join|rweb_join]--> research_join` · (пусто)
24. `research_join --[synth / research_join_synth]--> research_synth`
    ```text
    Своди findings в 1-research.md (+Cross-Feature Interactions, State Matrix Validation) и создай gate-документ 2-grounding.md с Independent Verification Protocol. Реши: advance или rerun.
    ```
25. `research_synth --[review / research_synth_review]--> research_review` · desc: «grounding полон, claims verified»
    ```text
    Research + grounding готовы — cross-model проверка claims по реальному коду.
    ```
26. `research_synth --[rerun / research_synth_rerun]--> research` · desc: «пробелы/[CONTRADICTS]/[ENTRY POINTS INCOMPLETE]»
    ```text
    Команда не покрыла проблему: пере-подними исследователей по недостающим направлениям (список в комментарии задачи; это единственный полный доп. раунд) и дополни grounding.
    ```
27. `research_review --[judge / research_rev_verdict]--> research_verdict` · **continue_session**
    ```text
    Review записан. Сведи вердикт по reviews/1-research.review.md + статусы BLOCKER в 2-grounding.md.
    ```
28. `research_verdict --[approved / research_approved]--> design` · desc: «grounding подтверждён, все BLOCKER resolved»
    ```text
    Проектирование: ты диспетчер и lead judge, design docs сам не пишешь. СТОП, если нет 2-grounding.md. Прочитай 0-spec.md, 1-research.md, 2-grounding.md, docs/invariants.md; передай архитекторам cross-feature summary (импорты, риски, shared SDK, undocumented = blockers). Contract/Journeys/Matrix — зафиксированный input. Определи conditional docs (07/08). Затем fan-out design_team: два архитектора-спорщика + conditional web-researcher (prior-art).
    ```
29. `research_verdict --[needs_changes / research_needs_changes]--> research` · desc: «CONTRADICTS/blocker в grounding»
    ```text
    Cross-model review нашёл противоречия. См. reviews/1-research.review.md. Исправь claims в 1-research.md, при необходимости — доп. исследователи, обнови grounding.
    ```

### 3.3 DESIGN (12)

30. `design --[design_team / design_to_arch_high]--> design_arch_high` · group «Design Team»
    ```text
    Подними high-level архитектора: C4 L1-L2, границы, DFD по 1-research.md + 2-grounding.md + prior-art; оспаривай component-решения, ломающие границы.
    ```
31. `design --[design_team / design_to_arch_comp]--> design_arch_comp` · group «Design Team»
    ```text
    Подними component-архитектора параллельно: C4 L3, классы, DI, Room, sequences, test strategy; расширь State Matrix в 02-behavior.md; оспаривай нереализуемые границы.
    ```
32. `design --[design_team / design_to_web]--> design_web` · group «Design Team»
    ```text
    Conditional prior-art: official docs/best practices по SDK → 05-prior-art.md. Internal-фича — насквозь.
    ```
33-35. `design_arch_high|design_arch_comp|design_web --[designed / dah_join|dac_join|dweb_join]--> design_join` · (пусто)
36. `design_join --[synth / design_join_synth]--> design_synth`
    ```text
    Своди обе архитектурные позиции в согласованные design-доки; прогони Gates 5-8 и hook-скрипты; реши advance/rerun.
    ```
37. `design_synth --[review / design_synth_review]--> design_review` · desc: «дизайн полон, гейты зелёные»
    ```text
    Design-доки готовы — cross-model reality check тремя линзами (Realist обязателен) с открытием реального кода.
    ```
38. `design_synth --[rerun / design_synth_rerun]--> design` · desc: «архитекторы не сошлись / гейты красные»
    ```text
    Дизайн неполон или детерминированные гейты красные (см. комментарий задачи) — пересоздай команду архитекторов с findings.
    ```
39. `design_review --[judge / design_rev_verdict]--> design_verdict` · **continue_session**
    ```text
    Review записан. Сведи вердикт по reviews/design.review.md (PASS/CONTESTED/REJECT).
    ```
40. `design_verdict --[approved / design_approved]--> plan` · **requires-approval** · desc: «reality check пройден — пользователь одобряет дизайн»
    ```text
    Дизайн одобрен. План: ты диспетчер, план сам не пишешь. Grounding Gate (Entry Points/Code Owners/Backend-Contract/Validation заполнены). Выбери стратегию фаз (bottom-up default). Walking Skeleton: Contract ≠ N/A → phase-01 = integration, domain не переписывается (блокер skeleton → STOP, re-spec). Затем запусти planner.
    ```
41. `design_verdict --[needs_changes / design_needs_changes]--> design` · desc: «blocker в дизайне»
    ```text
    Cross-model review нашёл blocker. См. reviews/design.review.md. Верни findings архитекторам, пересоздай команду, исправь и повтори.
    ```

### 3.4 PLAN (8)

42. `plan --[plan_team / plan_to_planner]--> plan_planner` · desc: «домен ок — планируем» (1-й из 2 исходов plan)
    ```text
    Разбей design-доки на фазы plan/phase-NN/{overview,backend,tests[,frontend]}.md + plan/README.md. Стратегия и Walking-Skeleton-ограничения — в комментарии задачи. Plan = ТЗ (Signature Card), не код.
    ```
43. `plan --[respec / plan_respec]--> spec` · desc: «skeleton-блокер / требуется re-spec» (F5: блокер в existing skeleton или нужно менять domain → назад в spec)
    ```text
    Блокер в existing Walking Skeleton / требуется переписать сигнатуры или бизнес-правила domain → план построить нельзя. Эскалация: обнови 0-spec.md с пользователем (Feature Domain Contract / State Matrix), затем фича снова пойдёт по пайплайну (skeleton перегенерируется в UPDATE-режиме).
    ```
44. `plan_planner --[planned / planner_to_gate]--> plan_gate`
    ```text
    План записан. Прогони детерминированные проверки (no-code grep, пути, check_pipeline_docs).
    ```
45. `plan_gate --[pass / plangate_pass]--> plan_review` · desc: «все проверки зелёные» (D5: same-model plan_reviewer снят — сразу cross-model)
    ```text
    Детерминированные гейты зелёные. Cross-model ревью плана двумя lens (Sequencing + Plan-as-ТЗ) + перенятые из снятого plan-reviewer детерминированные проверки: сигнатуры vs research (mismatch = BLOCKER), Cross-Phase/DI closure, покрытие AC, README sync.
    ```
46. `plan_gate --[fail / plangate_fail]--> plan_planner` · desc: «fenced-код/битые пути/docs-check красный»
    ```text
    Гейт красный. Полный текст ошибок в комментарии задачи. Исправь plan-файлы (Signature Card вместо кода; существующие пути; согласованность docs) и повтори.
    ```
47. `plan_review --[judge / plan_rev_verdict]--> plan_verdict` · **continue_session**
    ```text
    Review записан. Сведи вердикт по reviews/plan.review.md.
    ```
48. `plan_verdict --[approved / plan_approved]--> impl_preflight` · **requires-approval** · desc: «план одобрен пользователем»
    ```text
    План одобрен. Прогони Team Composition Preflight: по plan/README.md и overview-файлам предложи состав команды реализации per phase (mandatory/conditional/do-not-spawn/scaling/debug hooks/prerequisites).
    ```
49. `plan_verdict --[needs_changes / plan_needs_changes]--> plan` · desc: «blocker в плане»
    ```text
    Cross-model review нашёл blocker. См. reviews/plan.review.md. Исправь phase-файлы и повтори.
    ```

### 3.5 IMPLEMENT + per-phase review (30)

50. `impl_preflight --[proposal / preflight_impl]--> implement`
    ```text
    Реализация (Delegate Mode): ты диспетчер — код НИКОГДА не пишешь, файлы не редактируешь, только координируешь. Прими Team Composition Proposal (в комментарии задачи) как default; security-reviewer и test-dev обязательны. Прочитай plan/README.md (phase-файлы — лениво); инициализируй Run Ledger (run/pipeline-state.json + run.jsonl); построй граф зависимостей фаз. Walking Skeleton preflight для phase-01. Назначай фазы по одной: fan-out dev (в edge-заданиях — номер фазы; агенты строго по наличию role-файлов). На каждую фазу: Build Gate ДО ревью, ревью ВСЕМИ обязательными ревьюерами, вердикт, fix-loop; architectural mismatch → STOP + вопрос пользователю. После всех фаз — smoke и cross-phase review. Начни с фазы 01.
    ```
51. `implement --[dev / impl_dev_backend]--> dev_backend` · group «Dev»
    ```text
    PHASE NN: прочитай plan/phase-NN/backend.md и реализуй backend-слой фазы; сам прогони Build Gate и Test Deletion Gate. Нет backend.md — насквозь.
    ```
52. `implement --[dev / impl_dev_frontend]--> dev_frontend` · group «Dev»
    ```text
    PHASE NN: реализуй frontend-часть по plan/phase-NN/frontend.md. Нет frontend.md — насквозь.
    ```
53. `implement --[dev / impl_dev_firebase]--> dev_firebase` · group «Dev»
    ```text
    PHASE NN: Firebase-часть фазы, если затрагивается. Иначе насквозь.
    ```
54. `implement --[dev / impl_dev_test]--> dev_test` · group «Dev»
    ```text
    PHASE NN (TDD, параллельно): тесты фазы по plan/phase-NN/tests.md + таблица покрытия spec-сценариев.
    ```
55. `implement --[dev / impl_dev_integration]--> dev_integration` · group «Dev»
    ```text
    PHASE NN: instrumented/integration тесты (DAO boundary, multi-layer, lifecycle) — если фаза попадает под триггеры активации. Иначе насквозь.
    ```
56. `implement --[debug / implement_debug]--> debug` · group «Debug» · params: `problem=«slug и описание проблемы»`
    ```text
    Живая диагностика бага: {{problem}}. Ты координатор: Phase 1 investigation → advisor → решение пользователя → conditional deep debug → Fix Spec. Fix без одобрения не применяй.
    ```
57-61. `dev_backend|dev_frontend|dev_firebase|dev_test|dev_integration --[built / db_join|df_join|dfb_join|dt_join|di_join]--> dev_join` · (пусто)
62. `dev_join --[gate / devjoin_gate]--> build_gate`
    ```text
    Все работники фазы отчитались. Верифицируй Build Gate сам (ciCheck exit 0 + 30 строк вывода + Test Deletion Gate) до допуска ревьюеров.
    ```
63. `build_gate --[build_failed / gate_fail]--> implement` · desc: «сборка/тесты/гейты упали»
    ```text
    Build Gate красный (полный вывод в комментарии задачи). Верни devs на исправление: адресные задания по scope ошибок; при неочевидном root cause подними debug-ветку.
    ```
64. `build_gate --[review / gate_rev_code]--> review_code` · group «Review»
    ```text
    Build Status: PASSED (commit <sha>). Code review фазы NN: diff, plan/phase-NN/overview.md, 0-spec.md. Findings в комментарии задачи.
    ```
65. `build_gate --[review / gate_rev_arch]--> review_architect` · group «Review»
    ```text
    Build Status: PASSED (commit <sha>). Architecture review фазы NN: все обязательные grep-проверки + ADR-vs-code audit.
    ```
66. `build_gate --[review / gate_rev_sec]--> review_security` · group «Review»
    ```text
    Build Status: PASSED (commit <sha>). Security review фазы NN.
    ```
67. `build_gate --[review / gate_rev_comp]--> review_completeness` · group «Review»
    ```text
    Build Status: PASSED (commit <sha>). Completeness review фазы NN: каждый AC и каждый пункт плана против кода.
    ```
68. `build_gate --[review / gate_rev_conc]--> review_concurrency` · group «Review» (5-й и последний same-model ревьюер; cross-model per-phase СНЯТ — D4)
    ```text
    Build Status: PASSED (commit <sha>). Concurrency review фазы NN — только при теге concurrency-review/async-изменениях, иначе насквозь.
    ```
    _(D4: ребро `gate_rev_crossmodel` build_gate→review_crossmodel удалено — per-phase cross-model не запускается; cross-model только на cross-phase `cp_crossmodel`.)_
69-73. `review_code|review_architect|review_security|review_completeness|review_concurrency --[reviewed / rc2_join|ra2_join|rs2_join|rcomp2_join|rconc_join]--> review_join` · (пусто)  _(ребро `rcm_join` review_crossmodel→review_join удалено — D4)_
74. `review_join --[judge / join_verdict]--> verdict` · **continue_session** · **context-source: node:implement**
    ```text
    Сведи findings всех ревьюеров фазы из комментариев задачи + cross-model отчёт и вынеси вердикт (PASS/CONTESTED/REJECT) в контексте всей реализации.
    ```
75. `verdict --[needs_changes / review_needs_changes]--> implement` · desc: «blocker/консенсусный high»
    ```text
    REJECT. Организуй fix loop: адресные замечания (severity + file:line) нужным devs, затем повторный Build Gate и re-review той же фазы. Повторный blocker того же класса — эскалация пользователю.
    ```
76. `verdict --[next_phase / review_next_phase]--> implement` · desc: «фаза PASS, остались фазы»
    ```text
    Фаза PASS. Обнови Run Ledger и переходи к следующей фазе по plan/README.md: свежий fan-out dev (свежие сессии работников — контекст между фазами не переносится).
    ```
77. `verdict --[phases_done / review_done]--> impl_smoke` · desc: «все фазы PASS»
    ```text
    Все фазы PASS. Прогони smoke-гейты перед cross-phase review (ciCheck, instrumented, E2E, docs-check).
    ```
78. `impl_smoke --[smoke_failed / smoke_fail]--> implement` · desc: «smoke/docs-гейт красный»
    ```text
    Smoke-гейт красный (вывод в комментарии задачи). Организуй fix loop с нужным dev, затем повтори smoke.
    ```
79. `impl_smoke --[debug / smoke_debug]--> debug` · group «Debug» · params: `problem=«slug и описание рантайм-симптома»` · desc: «финальный E2E/UI выявил рантайм-баг → живая диагностика» (D3: 3-й исход impl_smoke, наряду с smoke_failed/crossphase)
    ```text
    Финальный реалистичный E2E/UI-тест выявил РАНТАЙМ-баг (неочевидное поведение runtime/lifecycle, не простое падение сборки): {{problem}}. Живая диагностика Phase 1 → advisor → решение → conditional deep debug → Fix Spec. Fix без одобрения не применяй.
    ```

### 3.6 CROSS-PHASE (13)

81. `impl_smoke --[crossphase / smoke_cp_crossmodel]--> cp_crossmodel` · group «Crossphase»
    ```text
    Smoke зелёный. Cross-model ревью ВСЕГО diff фичи: cross-phase integration, DI chain, orphaned abstractions, Spec Scenario Coverage → reviews/implement.review.md.
    ```
82. `impl_smoke --[crossphase / smoke_cp_code]--> cp_code` · group «Crossphase»
    ```text
    Cross-phase code review полного diff.
    ```
83. `impl_smoke --[crossphase / smoke_cp_arch]--> cp_architect` · group «Crossphase»
    ```text
    Cross-phase architecture review полного diff + все grep-аудиты.
    ```
84. `impl_smoke --[crossphase / smoke_cp_sec]--> cp_security` · group «Crossphase»
    ```text
    Security audit на уровне фичи.
    ```
85. `impl_smoke --[crossphase / smoke_cp_comp]--> cp_completeness` · group «Crossphase»
    ```text
    Финальный completeness: все AC из 0-spec.md против итогового кода.
    ```
86-90. `cp_crossmodel|cp_code|cp_architect|cp_security|cp_completeness --[reviewed / cpcm_join|cpc_join|cpa_join|cps_join|cpcomp_join]--> cp_join` · (пусто)
91. `cp_join --[verdict / cpjoin_verdict]--> cp_verdict`
    ```text
    Сведи cross-phase findings, построй quality-scorecard.md, прогони final smoke, отработай deferred-approve и подготовь handoff.
    ```
92. `cp_verdict --[fix / cp_fix]--> implement` · desc: «blocker/high требуют фикса или final smoke красный»
    ```text
    Cross-phase findings требуют фикса (список с severity и file:line в комментарии задачи). Организуй fix loop и повтори smoke + cross-phase проверку затронутого.
    ```
93. `cp_verdict --[handoff / cp_handoff]--> retrospective` · **requires-approval** · desc: «implementation.md записан, deferred одобрены — пользователь подтверждает handoff»
    ```text
    Фича реализована (Status: implemented). Проведи ретроспективу пайплайна: собери evidence (feedback пользователя обязателен), протрассируй баги по стадиям, предложи правки пайплайна.
    ```

### 3.7 DEBUG (30)

Входы в agent-ноду `debug` — четыре входящих ребра (это ОК, входящие рёбра инвариантов не нарушают; см. §6): три ВНЕШНИХ точки входа `spec_intake·to_debug` (триаж интейка распознал баг-репорт; edge ниже), `implement·debug` (edge 56 §3.5), `impl_smoke·debug` (edge 79 §3.5) + внутренний loopback `dbg_decision·moreinfo` (edge 105). `spec_intake` (agent) при этом имеет 3 исходящих transition-группы (`server_needed`→spec_server, `no_server`→spec, `to_debug`→debug); start-нода `backlog` — РОВНО 1 выход (`intake`→spec_intake), что безопасно для Kent.

93a. `spec_intake --[to_debug / intake_debug]--> debug` · params: `problem=«slug и описание проблемы»` · desc: «триаж интейка: баг-репорт на готовой фиче → standalone-диагностика» (D3)
   ```text
   Триаж интейка определил, что это баг на существующей фиче, а не новая работа. Живая диагностика: {{problem}}. Ты координатор — Phase 1 → advisor → решение → conditional deep → Fix Spec. Контекст запуска = standalone (терминальный выбор фикса → done).
   ```
    ```text
    Standalone debug на готовой фиче: {{problem}}. Ты координатор: определи КОНТЕКСТ запуска = standalone (терминальный выбор фикса дальше по подграфу будет → done). Phase 1 investigation → advisor → решение пользователя → conditional deep debug → Fix Spec. Fix без одобрения не применяй.
    ```
94. `debug --[phase1 / debug_to_doc]--> dbg_doc` · group «Phase 1»
    ```text
    Lightweight контекст по докам фичи и past debug/fix-spec отчётам (≤400 слов, file:line, совпадение симптомов отметь явно).
    ```
95. `debug --[phase1 / debug_to_logscan]--> dbg_log_scan` · group «Phase 1»
    ```text
    Quick scan logcat последних 5 минут (только при adb-устройствах и runtime-симптоме; иначе насквозь).
    ```
96-97. `dbg_doc|dbg_log_scan --[found / dd1_join|dl1_join]--> dbg_p1_join` · (пусто)
98. `dbg_p1_join --[advise / p1_advisor]--> dbg_advisor`
    ```text
    Phase 1 собран. Классифицируй проблему и предложи Team Composition Proposal для Phase 2.
    ```
99. `dbg_advisor --[assess / advisor_decision]--> dbg_decision`
    ```text
    Proposal готов. Сформируй assessment для пользователя и спроси решение A/B/C/D (WAIT).
    ```
100. `dbg_decision --[fixspec / decision_fixspec]--> dbg_fixspec` · desc: «A / early-out: root cause очевиден»
    ```text
    Пользователь принял hypothesis (или early-out). Сгенерируй Fix Spec по Phase-1 evidence (root cause CONFIRMED или из past report).
    ```
101. `dbg_decision --[deep / deep_code]--> dbg_code` · group «Deep»
    ```text
    Deep Debug: category, problem, hypothesis — в комментарии задачи. Трассируй код по протоколу systematic-debugging.
    ```
102. `dbg_decision --[deep / deep_log]--> dbg_log` · group «Deep»
    ```text
    Deep Debug: live-мониторинг logcat по проблеме (нет устройств — насквозь).
    ```
103. `dbg_decision --[deep / deep_doc]--> dbg_doc2` · group «Deep»
    ```text
    Deep Debug: Claims Map по всем докам фичи и сверка с проблемой (если advisor включил doc-analyst; иначе насквозь).
    ```
104. `dbg_decision --[deep / deep_web]--> dbg_web` · group «Deep»
    ```text
    Deep Debug: known issues/SDK quirks по симптомам (если advisor включил web-researcher; иначе насквозь).
    ```
105. `dbg_decision --[moreinfo / decision_moreinfo]--> debug` · desc: «D: пользователь даст больше контекста»
    ```text
    Пользователь предоставил новый контекст (в комментарии задачи). Повтори Phase 1 с учётом новых данных.
    ```
106-109. `dbg_code|dbg_log|dbg_doc2|dbg_web --[found / dc2_join|dl2_join|dd2_join|dw2_join]--> dbg_join` · (пусто)
110. `dbg_join --[synth / dbgjoin_synth]--> dbg_synth`
    ```text
    Команда отчиталась. Своди findings в root cause (file:line, evidence, предлагаемый diff) через convergence-протокол.
    ```
111. `dbg_synth --[fixspec / synth_fixspec]--> dbg_fixspec` · desc: «root cause найден или гипотезы исчерпаны»
    ```text
    Синтез готов (root cause CONFIRMED либо NOT confirmed с вариантами). Сгенерируй Fix Spec и спроси пользователя о способе применения.
    ```
112. `dbg_synth --[rerun / synth_rerun]--> dbg_decision` · desc: «нужен новый раунд гипотез/состава»
    ```text
    Сходимости нет. Обнови assessment (отвергнутые гипотезы, новые кандидаты) и снова спроси пользователя о направлении/составе команды.
    ```
113. `dbg_fixspec --[apply_direct / fixspec_apply]--> dbg_fix_dev` · **requires-approval** · desc: «A: scope=small (1-5 строк), пользователь одобрил прямой фикс»
    ```text
    Пользователь одобрил прямое применение. Реализуй фикс строго по fix-spec-<date>.md + regression test; прогони ciCheck.
    ```
114. `dbg_fixspec --[to_plan / fixspec_to_plan]--> dbg_plan` · desc: «B: medium/large — спланировать фикс» (D3 ОСНОВНОЙ путь, заменил handoff)
    ```text
    Fix spec одобрен для планирования (status: handoff-to-implement). Преврати fix-spec-<date>.md в исполняемый план: одна phase-NN (или fix-plan.md), targeting изменения из fix-spec + ОБЯЗАТЕЛЬНЫЙ regression test. Domain НЕ переписывается.
    ```
115. `dbg_fixspec --[defer_done / fixspec_defer_done]--> done` · desc: «C-standalone: отложить, debug был standalone (status: deferred, README Pending Fixes)» · (пусто)
116. `dbg_fixspec --[defer_impl / fixspec_defer_impl]--> implement` · desc: «C-mid: отложить, debug посреди реализации → вернуться в implement» (выбор по контексту/Run Ledger)
    ```text
    Fix отложен (status: deferred, README Pending Fixes). debug шёл посреди реализации — вернись в implement продолжать фазы; отложенный фикс зафиксирован как Pending Fix в Run Ledger.
    ```
117. `dbg_fixspec --[revisit / fixspec_revisit]--> dbg_decision` · desc: «D: пересмотреть root cause»
    ```text
    Пользователь запросил пересмотр root cause. Сформируй новые гипотезы/расширь команду и снова спроси решение.
    ```
118. `dbg_plan --[to_impl / dbgplan_impl]--> implement` · desc: «план фикса готов — полный per-phase цикл реализации» (единственный исход dbg_plan, D3)
    ```text
    План фикса записан (phase-NN / fix-plan.md вокруг изменений fix-spec + regression test). Вход для нового цикла реализации: dev → build_gate → 5 same-model ревьюеров → verdict → smoke → cross-phase. Domain (Walking Skeleton) не трогать.
    ```
119. `dbg_fix_dev --[built / fixdev_review]--> dbg_fix_review`
    ```text
    Фикс применён, билд зелёный. Обязательный code review: diff против fix-spec, regression test, отсутствие лишнего. Терминальный выбор (done vs implement) — по Run Ledger/контексту запуска debug.
    ```
120. `dbg_fix_review --[passed_done / fixreview_passed_done]--> done` · desc: «фикс чист, debug был standalone → завершение (README Debug History)» · (пусто)
121. `dbg_fix_review --[passed_impl / fixreview_passed_impl]--> implement` · desc: «фикс чист, debug посреди реализации → вернуться в implement» (выбор по контексту/Run Ledger)
    ```text
    Прямой фикс чист. debug шёл посреди реализации — вернись в implement продолжать оставшиеся фазы (Run Ledger обновлён: fix applied).
    ```
122. `dbg_fix_review --[fix / fixreview_fix]--> dbg_fix_dev` · desc: «blocker/high в фиксе»
    ```text
    Замечания ревью (severity + file:line в комментарии задачи). Исправь только их и верни на re-check.
    ```

### 3.8 RETROSPECTIVE (8)

123. `retrospective --[retro_team / retro_to_artifacts]--> retro_artifacts` · group «Retro Team»
    ```text
    Reader-1: все артефакты фичи (research/design/plan/implementation/reviews/scorecard).
    ```
124. `retrospective --[retro_team / retro_to_instructions]--> retro_instructions` · group «Retro Team»
    ```text
    Reader-2: все pipeline-инструкции (AGENTS.md — CLAUDE.md в репо нет, PROJECT-CONTEXT, rules, skills, agents, commands-референсы).
    ```
125-126. `retro_artifacts|retro_instructions --[read / rart_join|rinstr_join]--> retro_join` · (пусто)
127. `retro_join --[synth / retrojoin_synth]--> retro_synth`
    ```text
    Оба читателя отчитались, evidence от пользователя собран. Протрассируй каждый баг по 5 стадиям, классифицируй по 12 паттернам, исследуй в интернете, запиши retrospective.md и предложи конкретные фиксы пайплайна.
    ```
128. `retro_synth --[apply / retro_apply_edge]--> retro_apply` · **requires-approval** · desc: «пользователь одобрил (часть) фиксов»
    ```text
    Примени ТОЛЬКО одобренные фиксы (список в комментарии задачи): файлы — через Edit, Kent-ноды/рёбра — подготовь команды kent CLI. Затем invariants/lessons-learned/README.
    ```
129. `retro_synth --[no_fixes / retro_done]--> done` · desc: «фиксы не требуются или отклонены; retrospective.md записан» · (пусто)
130. `retro_apply --[complete / retroapply_done]--> done` · (пусто)

---

## 4. Дополнения config.toml

Квизовые 21 агент против ростера: `architect-component, architect-high-level, architect-reviewer, backend-dev, code-analyst, code-reviewer, codebase-researcher, completeness-reviewer, concurrency-reviewer, diagnostics, doc-analyst, firebase-dev, frontend-dev, integration-tester, log-reader, plan-reviewer, planner, security-reviewer, test-dev, web-researcher` — **все уже есть в ростере**. Отсутствует ровно один: **`domain-designer`**. Также используются существующие роли `lead`, `design-architect`, `crossmodel-reviewer`, `product-manager`, `coder` (для прямого debug-фикса). `rules-generator` НЕ используется (квизовая редакция без core-rules стадии). После правок: **`plan-reviewer` больше НЕ используется** ни одной нодой (D5 — same-model ревью плана снято; роль остаётся в ростере, но dangling, как rules-generator); **`planner` используется дважды** — `plan_planner` (планирование фич) и новая нода `dbg_plan` (планировщик фикса, D3); роль planner уже в ростере, дополнять config.toml не нужно.

Добавить в config.toml:

```toml
[subagents.domain-designer]
description = "Генерирует полный domain-слой (Walking Skeleton, Variant Y) на spec-этапе: pure functional core + repository interfaces + use cases + in-memory fakes + JVM-тесты, все зелёные. Работает строго в domain-директории фичи (model/state/logic/repository/use_case), не трогает другие слои, scaffold и чужие модули. Spec — единственный источник правды: противоречия и двусмысленности эскалирует, не решает молча. Никаких android/androidx/SDK-типов, DI- и serialization-аннотаций, throw в pure functions (только Result), mocks (только полноценные fakes)."
model = "gpt-5.5"
thinking_level = "high"
```

Квиз-специфика остальных ролей (ADR-vs-code audit у architect-reviewer, auth-scoped-flow greps, Signature Card у planner, WS-mode у test-dev, advisor-режим diagnostics и т.д.) в описания ролей НЕ добавляется — по решению пользователя она зашита в промпты нод/рёбер (§2-3).

---

## 5. План размещения ассетов (skills / rules / hooks в рантайме)

Агенты Kent работают в рабочем дереве `/Volumes/EXTERNAL/schoolquiz3.0` и читают файлы через Read/Bash — все ассеты должны лежать в репо по стабильным путям.

**Действие:** скопировать извлечённую редакцию целиком:

```bash
rsync -a --exclude commands \
  '/private/tmp/claude-501/-Users-tpov-tg-watch-userbot/1672033a-247c-4679-8d37-c1d0b621863e/scratchpad/quiz-claude/.claude/' \
  '/Volumes/EXTERNAL/schoolquiz3.0/.claude/'
# commands/ скопировать тоже, но как РЕФЕРЕНС для retro_instructions (пайплайном не исполняются):
rsync -a \
  '/private/tmp/claude-501/-Users-tpov-tg-watch-userbot/1672033a-247c-4679-8d37-c1d0b621863e/scratchpad/quiz-claude/.claude/commands/' \
  '/Volumes/EXTERNAL/schoolquiz3.0/.claude/commands/'
```

Состав и кто на что ссылается (промпт → файл):

| Ассет | Путь в репо | Ссылающиеся ноды |
|---|---|---|
| rules: agent-communication, auth-scoped-flow, clean-architecture, di-patterns, domain-models, kotlin-conventions, lifecycle, navigation, room-database, testing, use-cases | `.claude/rules/*.md` | dev_backend, dev_frontend, dev_test, dev_integration, review_architect (+auth-scoped-flow greps), review_concurrency (kotlin-conventions Channel-правило), design_arch_comp, dbg_code, skeleton_tests, dbg_fix_dev, retro_instructions |
| skill domain-modeling (SKILL.md + references/{kotlin-patterns,test-patterns,anti-patterns}.md) | `.claude/skills/domain-modeling/` | skeleton_sig, skeleton_bodies, skeleton_tests, skeleton_gate |
| skill adversarial-review (SKILL.md + references/{cli-protocol,plan-review-lens}.md) | `.claude/skills/adversarial-review/` | promты crossmodel-нод несут логику линз/вердиктов инлайн; файлы остаются для retro_instructions и как канон (cli-protocol в Kent не исполняется — см. §6) |
| skill core-module (references/conventions.md) | `.claude/skills/core-module/` | research_codebase, research_core, dev_backend |
| skill systematic-debugging (SKILL.md + references/{root-cause-tracing,defense-in-depth,condition-based-waiting}.md) | `.claude/skills/systematic-debugging/` | dbg_code (обязателен для lifecycle/concurrency/unknown) |
| skill glm (SKILL.md + scripts/glm_query.py) | `.claude/skills/glm/` | research_codebase, dbg_code, dbg_log — опциональный sidecar; ключ ZAI_API_KEY/GLM_API_KEY в окружении Kent-раннера |
| skill material-3 (references/*) | `.claude/skills/material-3/` | dev_frontend (явные пути по Decision Tree — автотриггера в Kent нет) |
| skills android-reverse-engineering, skill-authoring | `.claude/skills/...` | пайплайном не используются; сохраняются для retro_instructions/ручных задач (пути `${CLAUDE_PLUGIN_ROOT}` при использовании переписать) |
| hooks: check-plan-no-code.sh, check-plan-paths.sh, check-qa-sourcing.sh, check-api-contract-types.sh, check-c4-vs-gradle.sh | `.claude/hooks/*.sh` | plan_gate (первые два, запуск вручную), build_gate (qa-sourcing: **inline-эквивалент** — скрипт check-qa-sourcing.sh как таковой НЕ исполняется, build_gate сам делает `bash -n` + source-цепочку по scripts/qa_lib/*.sh — F10), design_synth (последние два); `chmod +x` |
| context-timeline.py | `.claude/hooks/` | не переносится в графе (телеметрия) |
| PROJECT-CONTEXT.md | `.claude/PROJECT-CONTEXT.md` | почти все ноды (base_package, debug_package_name, канонические gradle-команды, glm-профили). ВНИМАНИЕ: файл писан под schoolquiz4.0 — сверить пути/команды с реальным деревом schoolquiz3.0 (open question §6) |
| agents/*.md (21 файл) | `.claude/agents/` | как референс: retro_instructions читает; промпты нод самодостаточны и на них НЕ ссылаются |
| scripts/pipeline/check_pipeline_docs.sh | `scripts/pipeline/` | plan_gate, impl_smoke, cp_verdict — если в schoolquiz3.0 скрипта нет, создать заглушку или пропускать (промпты это допускают) |

Ретроспектива пишет в `.claude/rules/**`, `.claude/skills/*/references/**`, `PROJECT-CONTEXT.md`, `AGENTS.md` (**CLAUDE.md в репо schoolquiz3.0 нет** — F2: во всех промптах, где источник говорил «прочитай/правь CLAUDE.md», цель заменена на `AGENTS.md` + `.claude/PROJECT-CONTEXT.md`), `docs/invariants.md`, `docs/features/lessons-learned.md` — пути стабильны, право записи у retro_apply.

---

## 6. Ограничения переноса (что в Kent невоспроизводимо 1:1) и компенсации

1. **PostToolUse-хуки (детерминированный enforcement на каждую запись файла)**. В Kent нет перехвата записи. Компенсация — трёхслойная, по философии «Deterministic enforcement > hope»: (а) запреты в промптах писателей (planner: «fenced kotlin запрещён»), (б) выделенные гейт-ноды, запускающие те же скрипты/greps (plan_gate, build_gate, design_synth, skeleton_gate), (в) петли fail-рёбер с полным stderr. Гейт срабатывает после записи, а не в момент — «окно» между записью и проверкой закрыто тем, что дальше по графу без гейта пройти нельзя.
2. **AskUserQuestion / «=WAIT=»**. Компенсация: `kent ask_question` внутри нод (диалог spec, delta-вопросы research, A/B/C/D debug, deferred-approve, evidence retro) + `--requires-approval` на 6 стыковых рёбрах (spec→research, design→plan, plan→implement, handoff→retrospective, apply-фикса, apply ретро-правок). Отличие: у approval-ребра пользователь подтверждает переход, но не редактирует выбор из 4 вариантов — многовариантные решения оформлены как ask_question + выбор transition самой нодой.
3. **`$ARGUMENTS`**. Компенсация: edge-params (`feature` на backlog→spec, `problem` на implement→debug) + описание задачи Kent; slug генерирует нода spec.
4. **Teams / SendMessage / TaskCreate(blockedBy) / автономный reviewer↔coder fix loop**. В Kent нет peer-messaging между живыми агентами. Компенсация: обмен — через файлы репо и комментарии задачи; blockedBy → топология графа (ревьюеры достижимы только через build_gate; строка «Build Status: PASSED» продублирована в edge-промптах и в промптах ревьюеров как отказ-гейт); автономный loop «reviewer→coder→re-check» → графовая петля verdict→implement→dev→build_gate→review (дороже по итерациям; эскалация по сигналу сохранена). Debate архитекторов теряет живой пинг-понг: каждый пишет свою позицию + возражения, сводит design_synth.
5. **Динамическое масштабирование** (N researchers = N criteria, log-reader per device, 2 test-dev, hierarchical phase-lead для параллельных фаз, sub-planner'ы). Kent-граф статичен: одна нода роли обрабатывает все свои единицы работы внутри сессии; параллельные независимые фазы выполняются последовательно. Компенсация — промпты явно требуют покрыть все критерии/устройства/вертикали по очереди.
6. **Внешний Codex CLI (adversarial-review: «codex exec, при отказе CLI — стоп»)**. Kent не имеет Anthropic/Codex CLI; кросс-модельность реализована ролью `crossmodel-reviewer` на gpt-5.4 против спайна gpt-5.5 — сохранён принцип «другая модель, изолированная сессия, findings-only». Гейт «CLI недоступен = blocker» не переносится (неактуален).
7. **GLM-sidecar**: скрипт переносится, но ключа в окружении может не быть. Отклонение: вместо «стоп при отсутствии ключа» — fail-open («пропусти и отметь в отчёте»), т.к. GLM — вспомогательный breadth-pass. Запрет отправки секретов сохранён в промптах через ссылку на SKILL.md.
8. **Sonnet-модели субагентов** (`model: sonnet` во всех 21 frontmatter). Kent = только OpenAI: работники gpt-5.5/high, crossmodel gpt-5.4/xhigh, fast-роль при желании gpt-5.4-mini. Сам смысл «same-model blind spots» сохраняется (спайн одной моделью, ревью — другой).
9. **Автотриггер скилов по description** (material-3 у frontend-dev). Компенсация: явные инструкции чтения референсов в промпте dev_frontend.
10. **context-timeline.py** (observability) — не переносится; у Kent своя видимость ранов.
11. **Правка графа = самоулучшение ретроспективы**: в CC ретро правила `.claude/commands/*.md`; в Kent целевые файлы фиксов — rules/skills/PROJECT-CONTEXT + промпты нод/рёбер через `kent workflow node|edge update` (retro_apply готовит команды, не исполняет без подтверждения). Грабля №2: правка графа не действует на уже запущенные раны — зафиксировано в промпте retro_synth.
12. **Resume после обрыва**: в CC — Run Ledger + продолжение сессии; в Kent сессии нод управляет сам Kent, Run Ledger сохранён как файловый артефакт (pipeline-state.json), промпт implement велит начинать с его чтения.
13. **`kent ask_question` в неинтерактивном ране**: WAIT-точки работают, только если ран Kent обслуживается с UI/оператором; при headless-запуске диалоговая spec-нода деградирует (open question).
14. **D1 — pipeline tier = метаданные, полный пайплайн всегда**: в источнике tier маршрутизирует граф (Light пропускает research/design/plan, Medium — design). Здесь tier (Light/Medium/Heavy/Critical) фиксируется нодой `spec_intake` в 0-spec.md как **advisory** метаданные/приоритет, но граф по тиру **НЕ маршрутизируется** — все фичи проходят полный пайплайн. Осознанное отклонение (проще/безопаснее для v1; stage-skipping можно добавить позже). Промпты `spec_intake` и `spec` сохраняют выбор тира, помечая advisory.
15. **D2 — SPEC: server-анализ ДО продуктового диалога**: порядок восстановлен по источнику (Phase 0 intake → Phase 1 server → Phase 2 dialog → Phase 3 write → Phase 3.5 Contract Lock). Новая нода `spec_intake` (product-manager) генерирует slug/type/tier/решение о server analysis без диалога и без записи 0-spec.md. `spec_server` (READ-ONLY) идёт ПЕРЕД диалогом и пишет Server-Side Context/Issues в заготовку. `spec` (Phase 2+3+3.5) ведёт диалог **с учётом серверных ограничений** и развязывает skeleton (Contract≠N/A) vs spec_review (Contract=N/A) — эта развязка перенесена со старого spec_server на `spec`. Диалог теперь видит серверную реальность до фиксации требований.
16. **D3 — «выбор по контексту» = agent-нода Kent сама выбирает transition**: у `dbg_fixspec` (defer_done→done | defer_impl→implement) и `dbg_fix_review` (passed_done→done | passed_impl→implement) по ДВА исходящих ребра с РАЗНЫМИ transition-именами; нода выбирает переход по Run Ledger / контексту запуска debug (standalone из spec_intake·to_debug или impl_smoke·debug → done; посреди реализации из implement·debug → implement). Это НЕ параллельный фан-аут (разные transition-группы, по одному ребру каждая) — легально для agent-ноды. `debug` имеет 3 внешних входящих ребра (spec_intake, implement, impl_smoke) + loopback moreinfo — входящие рёбра к одной ноде инвариантов не нарушают. Основной путь фикса: `dbg_fixspec --to_plan--> dbg_plan (planner) --to_impl--> implement` (заменил прямой handoff).
17. **backlog (start) — РОВНО 1 выход** (`intake`, param feature) → `spec_intake` (как в рабочем Skillify-графе). Standalone-диагностика бага доступна через ТРИАЖ agent-ноды `spec_intake`: если описание задачи — баг-репорт на готовой фиче, spec_intake уходит `to_debug`→debug; иначе выполняет Phase 0. Причина: `kent task create` НЕ даёт выбирать точку входа/первый transition (проверено по CLI) — задача всегда стартует со start-ноды и идёт по её единственному выходу; ветвление feature-vs-bug поэтому делает agent-нода spec_intake, а не start. Это исправляет флаг валидатора о ветвящейся start-ноде.
18. **D4 — per-phase cross-model review снят**: per-phase ревью = 5 same-model ревьюеров (code/architect/security/completeness/concurrency); нода `review_crossmodel` и рёбра `gate_rev_crossmodel`/`rcm_join` удалены. Cross-model остаётся ТОЛЬКО на cross-phase (`cp_crossmodel`). Соответствует источнику feature-implement.md: «Codex per-phase НЕ запускается — cross-model только на Шаге 3 после всех фаз».
19. **D5 — same-model plan_reviewer снят**: нода `plan_reviewer` и ребро `planreviewer_to_review` удалены; `plan_gate --pass--> plan_review` напрямую. Детерминированные проверки снятого ревьюера (сигнатуры vs research, Cross-Phase/DI closure, AC-покрытие, README sync) перенесены в промпт cross-model `plan_review`. Соответствует источнику feature-plan.md: planner → Codex(cross-model, 2 линзы) → verdict, без промежуточного same-model ревью.
20. **F2 — CLAUDE.md в репо schoolquiz3.0 отсутствует**: во ВСЕХ промптах, где источник говорил «прочитай/правь CLAUDE.md» (spec_intake, spec, spec_server, debug, retrospective, retro_instructions, retro_synth, retro_apply, dbg_fix_dev и др. + ребро retro_to_instructions), цель заменена на `AGENTS.md` + `.claude/PROJECT-CONTEXT.md`. Отмечено также в §5 (write-targets retro).
21. **F11 — research_web зависит от cross-feature scanner, но идёт в том же параллельном фан-ауте research_team**: на вывод cross-feature scanner (1-research.md ещё не создан на момент старта research_web) рассчитывать нельзя — принятое ограничение. Компенсация в промпте research_web: «если 1-research.md уже существует — используй его Shared SDK findings; иначе fallback на 0-spec.md». Точность shared-SDK-паттернов может пострадать до следующего раунда — приемлемо.
22. **F12 — потеря per-document гранулярности design_review**: источник (feature-design.md) гонял Codex по группам доков 01+02 / 03 / 04 с per-lens fix-петлёй по каждой группе; здесь 3 линзы (Realist/Skeptic/Architect) прогоняются в ОДНОЙ ноде `design_review`, а fix = rerun всей команды архитекторов через `design_synth --rerun--> design`. Грубее по адресности фиксов (нет отдельной петли на документ/линзу) — принято ради простоты графа.
23. **F13 — плейсхолдеры {{feature}}/{{problem}} в edge-промптах Kent НЕ подставляет**: в рабочем Skillify-графе нет ни одного {{...}} и ни одного заполненного param; фактическое значение задачи несёт описание задачи Kent (и edge-`--param` — как декларация ожидаемого входа). Плейсхолдеры оставлены как литерал-подсказки для оператора/агента, не как шаблонная подстановка.

---

## 7. Порядок команд kent CLI (исполняемый скрипт)

Подготовка: (1) добавить `[subagents.domain-designer]` в config.toml (§4; `planner` для dbg_plan уже в ростере — доп. правок не нужно); (2) развернуть ассеты (§5); (3) выгрузить промпты нод и рёбер из §2-3 в файлы `P=deploy/kent-prompts/` — по одному файлу на ноду `P/<node-key>.node.txt` и на ребро `P/<edge-key>.edge.txt` (текст = содержимое соответствующего ```text-блока; для нод/рёбер «(пусто)» файлы не нужны; относительно прошлой версии: **+spec_intake +dbg_plan** файлы промптов, **−review_crossmodel −plan_reviewer** файлы больше не нужны). Затем исполнить по порядку (create workflow → все ноды → все рёбра → validate → **project create (F3) → link**). CLI не умеет удалять — исполнять на чистой БД, при ошибке в середине НЕ пересоздавать ноды, продолжать с места ошибки.

```bash
set -euo pipefail
W="Quiz Feature Pipeline"
P=deploy/kent-prompts

kent workflow create --description "Claude Code feature pipeline (Quiz/schoolquiz): Spec+WalkingSkeleton-Research-Design-Plan-Implement-CrossPhase-Retrospective + Debug" "$W"

# ---------- НОДЫ (80): start → agent'ы по стадиям → join'ы → terminal ----------
kent workflow node add "$W" --key backlog --kind start --display-name "Backlog"

# SPEC
kent workflow node add "$W" --key spec_intake    --kind agent --agent product-manager      --display-name "Spec Intake · gpt-5.5/high"             --prompt "$(cat $P/spec_intake.node.txt)"
kent workflow node add "$W" --key spec_server    --kind agent --agent codebase-researcher  --display-name "Server Analysis · gpt-5.5/high"          --prompt "$(cat $P/spec_server.node.txt)"
kent workflow node add "$W" --key spec           --kind agent --agent product-manager      --display-name "Spec Dialog+Write · gpt-5.5/xhigh"      --prompt "$(cat $P/spec.node.txt)"
kent workflow node add "$W" --key skeleton_sig   --kind agent --agent domain-designer      --display-name "Skeleton A: Signatures · gpt-5.5/high"   --prompt "$(cat $P/skeleton_sig.node.txt)"
kent workflow node add "$W" --key skeleton_bodies --kind agent --agent domain-designer     --display-name "Skeleton B: Bodies · gpt-5.5/high"       --prompt "$(cat $P/skeleton_bodies.node.txt)"
kent workflow node add "$W" --key skeleton_tests --kind agent --agent test-dev             --display-name "Skeleton Tests · gpt-5.5/high"           --prompt "$(cat $P/skeleton_tests.node.txt)"
kent workflow node add "$W" --key skeleton_join  --kind join  --display-name "Skeleton Join"
kent workflow node add "$W" --key skeleton_gate  --kind agent --agent lead                 --display-name "Skeleton Gate · gpt-5.5/xhigh"           --prompt "$(cat $P/skeleton_gate.node.txt)"
kent workflow node add "$W" --key spec_review    --kind agent --agent crossmodel-reviewer  --display-name "Spec Cross-Model Review · gpt-5.4/xhigh" --prompt "$(cat $P/spec_review.node.txt)"
kent workflow node add "$W" --key spec_verdict   --kind agent --agent lead                 --display-name "Spec Verdict · gpt-5.5/xhigh"            --prompt "$(cat $P/spec_verdict.node.txt)"

# RESEARCH
kent workflow node add "$W" --key research           --kind agent --agent lead                --display-name "Research · gpt-5.5/xhigh"                    --prompt "$(cat $P/research.node.txt)"
kent workflow node add "$W" --key research_codebase  --kind agent --agent codebase-researcher --display-name "Criteria Researcher · gpt-5.5/high"          --prompt "$(cat $P/research_codebase.node.txt)"
kent workflow node add "$W" --key research_core      --kind agent --agent codebase-researcher --display-name "Core Scan · gpt-5.5/high"                    --prompt "$(cat $P/research_core.node.txt)"
kent workflow node add "$W" --key research_crossfeat --kind agent --agent codebase-researcher --display-name "Cross-Feature Scanner · gpt-5.5/high"        --prompt "$(cat $P/research_crossfeat.node.txt)"
kent workflow node add "$W" --key research_web       --kind agent --agent web-researcher      --display-name "Web Researcher · gpt-5.5/high"               --prompt "$(cat $P/research_web.node.txt)"
kent workflow node add "$W" --key research_join      --kind join  --display-name "Research Team Join"
kent workflow node add "$W" --key research_synth     --kind agent --agent lead                --display-name "Research Synthesis+Grounding · gpt-5.5/xhigh" --prompt "$(cat $P/research_synth.node.txt)"
kent workflow node add "$W" --key research_review    --kind agent --agent crossmodel-reviewer --display-name "Research Cross-Model Review · gpt-5.4/xhigh" --prompt "$(cat $P/research_review.node.txt)"
kent workflow node add "$W" --key research_verdict   --kind agent --agent lead                --display-name "Research Verdict · gpt-5.5/xhigh"            --prompt "$(cat $P/research_verdict.node.txt)"

# DESIGN
kent workflow node add "$W" --key design           --kind agent --agent lead                 --display-name "Design · gpt-5.5/xhigh"                    --prompt "$(cat $P/design.node.txt)"
kent workflow node add "$W" --key design_arch_high --kind agent --agent architect-high-level --display-name "Architect High-Level · gpt-5.5/high"       --prompt "$(cat $P/design_arch_high.node.txt)"
kent workflow node add "$W" --key design_arch_comp --kind agent --agent architect-component  --display-name "Architect Component · gpt-5.5/high"        --prompt "$(cat $P/design_arch_comp.node.txt)"
kent workflow node add "$W" --key design_web       --kind agent --agent web-researcher       --display-name "Prior-Art Researcher · gpt-5.5/high"       --prompt "$(cat $P/design_web.node.txt)"
kent workflow node add "$W" --key design_join      --kind join  --display-name "Design Team Join"
kent workflow node add "$W" --key design_synth     --kind agent --agent design-architect     --display-name "Design Synthesis+Gates · gpt-5.5/xhigh"    --prompt "$(cat $P/design_synth.node.txt)"
kent workflow node add "$W" --key design_review    --kind agent --agent crossmodel-reviewer  --display-name "Design Cross-Model Review · gpt-5.4/xhigh" --prompt "$(cat $P/design_review.node.txt)"
kent workflow node add "$W" --key design_verdict   --kind agent --agent lead                 --display-name "Design Verdict · gpt-5.5/xhigh"            --prompt "$(cat $P/design_verdict.node.txt)"

# PLAN
kent workflow node add "$W" --key plan          --kind agent --agent lead                --display-name "Plan · gpt-5.5/xhigh"                    --prompt "$(cat $P/plan.node.txt)"
kent workflow node add "$W" --key plan_planner  --kind agent --agent planner             --display-name "Planner · gpt-5.5/high"                  --prompt "$(cat $P/plan_planner.node.txt)"
kent workflow node add "$W" --key plan_gate     --kind agent --agent lead                --display-name "Plan Deterministic Gate · gpt-5.5/xhigh" --prompt "$(cat $P/plan_gate.node.txt)"
# (D5) нода plan_reviewer СНЯТА — plan_gate --pass--> plan_review напрямую
kent workflow node add "$W" --key plan_review   --kind agent --agent crossmodel-reviewer --display-name "Plan Cross-Model Review · gpt-5.4/xhigh"  --prompt "$(cat $P/plan_review.node.txt)"
kent workflow node add "$W" --key plan_verdict  --kind agent --agent lead                --display-name "Plan Verdict · gpt-5.5/xhigh"             --prompt "$(cat $P/plan_verdict.node.txt)"

# IMPLEMENT
kent workflow node add "$W" --key impl_preflight     --kind agent --agent diagnostics           --display-name "Team Composition Preflight · gpt-5.5/high" --prompt "$(cat $P/impl_preflight.node.txt)"
kent workflow node add "$W" --key implement          --kind agent --agent lead                  --display-name "Implement (Delegate Mode) · gpt-5.5/xhigh" --prompt "$(cat $P/implement.node.txt)"
kent workflow node add "$W" --key dev_backend        --kind agent --agent backend-dev           --display-name "Backend Dev · gpt-5.5/high"                 --prompt "$(cat $P/dev_backend.node.txt)"
kent workflow node add "$W" --key dev_frontend       --kind agent --agent frontend-dev          --display-name "Frontend Dev · gpt-5.5/high"                --prompt "$(cat $P/dev_frontend.node.txt)"
kent workflow node add "$W" --key dev_firebase       --kind agent --agent firebase-dev          --display-name "Firebase Dev · gpt-5.5/high"                --prompt "$(cat $P/dev_firebase.node.txt)"
kent workflow node add "$W" --key dev_test           --kind agent --agent test-dev              --display-name "Test Dev TDD · gpt-5.5/high"                --prompt "$(cat $P/dev_test.node.txt)"
kent workflow node add "$W" --key dev_integration    --kind agent --agent integration-tester    --display-name "Integration Tester · gpt-5.5/high"          --prompt "$(cat $P/dev_integration.node.txt)"
kent workflow node add "$W" --key dev_join           --kind join  --display-name "Dev Join"
kent workflow node add "$W" --key build_gate         --kind agent --agent lead                  --display-name "Build & Test Gate · gpt-5.5/xhigh"          --prompt "$(cat $P/build_gate.node.txt)"
kent workflow node add "$W" --key review_code        --kind agent --agent code-reviewer         --display-name "Code Review · gpt-5.5/high"                 --prompt "$(cat $P/review_code.node.txt)"
kent workflow node add "$W" --key review_architect   --kind agent --agent architect-reviewer    --display-name "Architecture Review · gpt-5.5/high"         --prompt "$(cat $P/review_architect.node.txt)"
kent workflow node add "$W" --key review_security    --kind agent --agent security-reviewer     --display-name "Security Review · gpt-5.5/high"             --prompt "$(cat $P/review_security.node.txt)"
kent workflow node add "$W" --key review_completeness --kind agent --agent completeness-reviewer --display-name "Completeness Review · gpt-5.5/high"        --prompt "$(cat $P/review_completeness.node.txt)"
kent workflow node add "$W" --key review_concurrency --kind agent --agent concurrency-reviewer  --display-name "Concurrency Review · gpt-5.5/high"          --prompt "$(cat $P/review_concurrency.node.txt)"
# (D4) нода review_crossmodel СНЯТА — per-phase cross-model не запускается; cross-model только на cp_crossmodel
kent workflow node add "$W" --key review_join        --kind join  --display-name "Review Join"
kent workflow node add "$W" --key verdict            --kind agent --agent lead                  --display-name "Phase Verdict · gpt-5.5/xhigh"              --prompt "$(cat $P/verdict.node.txt)"

# CROSS-PHASE
kent workflow node add "$W" --key impl_smoke      --kind agent --agent lead                  --display-name "Smoke Gate · gpt-5.5/xhigh"                    --prompt "$(cat $P/impl_smoke.node.txt)"
kent workflow node add "$W" --key cp_crossmodel   --kind agent --agent crossmodel-reviewer   --display-name "Cross-Phase Cross-Model · gpt-5.4/xhigh"       --prompt "$(cat $P/cp_crossmodel.node.txt)"
kent workflow node add "$W" --key cp_code         --kind agent --agent code-reviewer         --display-name "Cross-Phase Code Review · gpt-5.5/high"        --prompt "$(cat $P/cp_code.node.txt)"
kent workflow node add "$W" --key cp_architect    --kind agent --agent architect-reviewer    --display-name "Cross-Phase Architecture · gpt-5.5/high"       --prompt "$(cat $P/cp_architect.node.txt)"
kent workflow node add "$W" --key cp_security     --kind agent --agent security-reviewer     --display-name "Cross-Phase Security · gpt-5.5/high"           --prompt "$(cat $P/cp_security.node.txt)"
kent workflow node add "$W" --key cp_completeness --kind agent --agent completeness-reviewer --display-name "Cross-Phase Completeness · gpt-5.5/high"       --prompt "$(cat $P/cp_completeness.node.txt)"
kent workflow node add "$W" --key cp_join         --kind join  --display-name "Cross-Phase Join"
kent workflow node add "$W" --key cp_verdict      --kind agent --agent lead                  --display-name "Cross-Phase Verdict+Handoff · gpt-5.5/xhigh"   --prompt "$(cat $P/cp_verdict.node.txt)"

# DEBUG
kent workflow node add "$W" --key debug        --kind agent --agent lead            --display-name "Debug Phase 1 · gpt-5.5/xhigh"       --prompt "$(cat $P/debug.node.txt)"
kent workflow node add "$W" --key dbg_doc      --kind agent --agent doc-analyst     --display-name "P1 Doc Context · gpt-5.5/high"        --prompt "$(cat $P/dbg_doc.node.txt)"
kent workflow node add "$W" --key dbg_log_scan --kind agent --agent log-reader      --display-name "P1 Log Quick Scan · gpt-5.5/high"     --prompt "$(cat $P/dbg_log_scan.node.txt)"
kent workflow node add "$W" --key dbg_p1_join  --kind join  --display-name "Debug P1 Join"
kent workflow node add "$W" --key dbg_advisor  --kind agent --agent diagnostics     --display-name "Debugger Team Advisor · gpt-5.5/high" --prompt "$(cat $P/dbg_advisor.node.txt)"
kent workflow node add "$W" --key dbg_decision --kind agent --agent lead            --display-name "Debug Decision · gpt-5.5/xhigh"       --prompt "$(cat $P/dbg_decision.node.txt)"
kent workflow node add "$W" --key dbg_code     --kind agent --agent code-analyst    --display-name "Deep Code Analyst · gpt-5.5/high"     --prompt "$(cat $P/dbg_code.node.txt)"
kent workflow node add "$W" --key dbg_log      --kind agent --agent log-reader      --display-name "Deep Log Reader · gpt-5.5/high"       --prompt "$(cat $P/dbg_log.node.txt)"
kent workflow node add "$W" --key dbg_doc2     --kind agent --agent doc-analyst     --display-name "Deep Doc Analyst · gpt-5.5/high"      --prompt "$(cat $P/dbg_doc2.node.txt)"
kent workflow node add "$W" --key dbg_web      --kind agent --agent web-researcher  --display-name "Deep Web Researcher · gpt-5.5/high"   --prompt "$(cat $P/dbg_web.node.txt)"
kent workflow node add "$W" --key dbg_join     --kind join  --display-name "Deep Debug Join"
kent workflow node add "$W" --key dbg_synth    --kind agent --agent lead            --display-name "Debug Synthesis · gpt-5.5/xhigh"      --prompt "$(cat $P/dbg_synth.node.txt)"
kent workflow node add "$W" --key dbg_fixspec  --kind agent --agent lead            --display-name "Fix Spec · gpt-5.5/xhigh"             --prompt "$(cat $P/dbg_fixspec.node.txt)"
kent workflow node add "$W" --key dbg_plan     --kind agent --agent planner         --display-name "Debug Fix Planner · gpt-5.5/high"     --prompt "$(cat $P/dbg_plan.node.txt)"
kent workflow node add "$W" --key dbg_fix_dev  --kind agent --agent coder           --display-name "Direct Fix Dev · gpt-5.5/high"        --prompt "$(cat $P/dbg_fix_dev.node.txt)"
kent workflow node add "$W" --key dbg_fix_review --kind agent --agent code-reviewer --display-name "Direct Fix Review · gpt-5.5/high"     --prompt "$(cat $P/dbg_fix_review.node.txt)"

# RETROSPECTIVE + terminal
kent workflow node add "$W" --key retrospective      --kind agent --agent lead                --display-name "Retrospective · gpt-5.5/xhigh"        --prompt "$(cat $P/retrospective.node.txt)"
kent workflow node add "$W" --key retro_artifacts    --kind agent --agent codebase-researcher --display-name "Artifacts Reader · gpt-5.5/high"      --prompt "$(cat $P/retro_artifacts.node.txt)"
kent workflow node add "$W" --key retro_instructions --kind agent --agent codebase-researcher --display-name "Instructions Reader · gpt-5.5/high"   --prompt "$(cat $P/retro_instructions.node.txt)"
kent workflow node add "$W" --key retro_join         --kind join  --display-name "Retro Join"
kent workflow node add "$W" --key retro_synth        --kind agent --agent lead                --display-name "Retro Synthesis · gpt-5.5/xhigh"      --prompt "$(cat $P/retro_synth.node.txt)"
kent workflow node add "$W" --key retro_apply        --kind agent --agent lead                --display-name "Apply Approved Fixes · gpt-5.5/xhigh" --prompt "$(cat $P/retro_apply.node.txt)"
kent workflow node add "$W" --key done --kind terminal --display-name "Done"

# ---------- РЁБРА (132). E() — хелпер: промпт из файла, если он есть ----------
# (F4: ноды и рёбра — ОДИН непрерывный bash-блок; лишний внутренний fence убран.)
E() { f="$P/$1.edge.txt"; [ -f "$f" ] && cat "$f" || true; }

# SPEC (17)  — D2: intake → (server?) → dialog → skeleton|review
kent workflow edge add "$W" --from backlog       --transition intake         --edge-key backlog_intake     --to spec_intake   --context new_session --param 'feature=Описание фичи от пользователя' --prompt "$(E backlog_intake)"
kent workflow edge add "$W" --from spec_intake   --transition server_needed  --edge-key intake_server      --to spec_server   --context new_session --transition-description "нужен серверный анализ" --prompt "$(E intake_server)"
kent workflow edge add "$W" --from spec_intake   --transition no_server      --edge-key intake_spec        --to spec          --context new_session --transition-description "сервер не нужен" --prompt "$(E intake_spec)"
kent workflow edge add "$W" --from spec_server   --transition server_done    --edge-key server_to_spec     --to spec          --context new_session --transition-description "серверная реальность зафиксирована (единственный исход)" --prompt "$(E server_to_spec)"
kent workflow edge add "$W" --from spec          --transition skeleton       --edge-key spec_to_sig        --to skeleton_sig  --context new_session --transition-description "Contract ≠ N/A — генерировать Walking Skeleton" --prompt "$(E spec_to_sig)"
kent workflow edge add "$W" --from spec          --transition review         --edge-key spec_to_review     --to spec_review   --context new_session --transition-description "Contract = N/A — сразу cross-model review" --prompt "$(E spec_to_review)"
kent workflow edge add "$W" --from skeleton_sig  --transition bodies   --edge-key sig_to_bodies      --to skeleton_bodies --context new_session --prompt "$(E sig_to_bodies)"
kent workflow edge add "$W" --from skeleton_sig  --transition bodies   --edge-key sig_to_tests       --to skeleton_tests  --context new_session --prompt "$(E sig_to_tests)"
kent workflow edge add "$W" --from skeleton_bodies --transition built  --edge-key sb_join            --to skeleton_join --context new_session
kent workflow edge add "$W" --from skeleton_tests  --transition built  --edge-key st_join            --to skeleton_join --context new_session
kent workflow edge add "$W" --from skeleton_join --transition gate     --edge-key skel_join_gate     --to skeleton_gate --context new_session --prompt "$(E skel_join_gate)"
kent workflow edge add "$W" --from skeleton_gate --transition advance  --edge-key skel_advance       --to spec_review   --context new_session --transition-description "тесты зелёные, coverage полон" --prompt "$(E skel_advance)"
kent workflow edge add "$W" --from skeleton_gate --transition rerun    --edge-key skel_rerun         --to skeleton_sig  --context new_session --transition-description "красные тесты/некомпилируется — перезапуск стейджей" --prompt "$(E skel_rerun)"
kent workflow edge add "$W" --from skeleton_gate --transition respec   --edge-key skel_respec        --to spec          --context new_session --transition-description "спека двусмысленна / scope overflow" --prompt "$(E skel_respec)"
kent workflow edge add "$W" --from spec_review   --transition judge    --edge-key spec_rev_verdict   --to spec_verdict  --context continue_session --prompt "$(E spec_rev_verdict)"
kent workflow edge add "$W" --from spec_verdict  --transition approved --edge-key spec_approved      --to research      --context new_session --requires-approval --transition-description "нет blocker/high — пользователь одобряет ТЗ" --prompt "$(E spec_approved)"
kent workflow edge add "$W" --from spec_verdict  --transition needs_changes --edge-key spec_needs_changes --to spec     --context new_session --transition-description "blocker/high в ТЗ" --prompt "$(E spec_needs_changes)"

# RESEARCH (14)
kent workflow edge add "$W" --from research --transition research_team --edge-key research_to_criteria  --to research_codebase  --context new_session --prompt "$(E research_to_criteria)"
kent workflow edge add "$W" --from research --transition research_team --edge-key research_to_core      --to research_core      --context new_session --prompt "$(E research_to_core)"
kent workflow edge add "$W" --from research --transition research_team --edge-key research_to_crossfeat --to research_crossfeat --context new_session --prompt "$(E research_to_crossfeat)"
kent workflow edge add "$W" --from research --transition research_team --edge-key research_to_web       --to research_web       --context new_session --prompt "$(E research_to_web)"
kent workflow edge add "$W" --from research_codebase  --transition found --edge-key rc_join    --to research_join --context new_session
kent workflow edge add "$W" --from research_core      --transition found --edge-key rcore_join --to research_join --context new_session
kent workflow edge add "$W" --from research_crossfeat --transition found --edge-key rcf_join   --to research_join --context new_session
kent workflow edge add "$W" --from research_web       --transition found --edge-key rweb_join  --to research_join --context new_session
kent workflow edge add "$W" --from research_join  --transition synth  --edge-key research_join_synth   --to research_synth  --context new_session --prompt "$(E research_join_synth)"
kent workflow edge add "$W" --from research_synth --transition review --edge-key research_synth_review --to research_review --context new_session --transition-description "grounding полон, claims verified" --prompt "$(E research_synth_review)"
kent workflow edge add "$W" --from research_synth --transition rerun  --edge-key research_synth_rerun  --to research        --context new_session --transition-description "пробелы/[CONTRADICTS]" --prompt "$(E research_synth_rerun)"
kent workflow edge add "$W" --from research_review  --transition judge --edge-key research_rev_verdict --to research_verdict --context continue_session --prompt "$(E research_rev_verdict)"
kent workflow edge add "$W" --from research_verdict --transition approved --edge-key research_approved --to design --context new_session --transition-description "grounding подтверждён, BLOCKERs resolved" --prompt "$(E research_approved)"
kent workflow edge add "$W" --from research_verdict --transition needs_changes --edge-key research_needs_changes --to research --context new_session --transition-description "CONTRADICTS/blocker" --prompt "$(E research_needs_changes)"

# DESIGN (12)
kent workflow edge add "$W" --from design --transition design_team --edge-key design_to_arch_high --to design_arch_high --context new_session --prompt "$(E design_to_arch_high)"
kent workflow edge add "$W" --from design --transition design_team --edge-key design_to_arch_comp --to design_arch_comp --context new_session --prompt "$(E design_to_arch_comp)"
kent workflow edge add "$W" --from design --transition design_team --edge-key design_to_web       --to design_web       --context new_session --prompt "$(E design_to_web)"
kent workflow edge add "$W" --from design_arch_high --transition designed --edge-key dah_join  --to design_join --context new_session
kent workflow edge add "$W" --from design_arch_comp --transition designed --edge-key dac_join  --to design_join --context new_session
kent workflow edge add "$W" --from design_web       --transition designed --edge-key dweb_join --to design_join --context new_session
kent workflow edge add "$W" --from design_join  --transition synth  --edge-key design_join_synth   --to design_synth  --context new_session --prompt "$(E design_join_synth)"
kent workflow edge add "$W" --from design_synth --transition review --edge-key design_synth_review --to design_review --context new_session --transition-description "дизайн полон, гейты зелёные" --prompt "$(E design_synth_review)"
kent workflow edge add "$W" --from design_synth --transition rerun  --edge-key design_synth_rerun  --to design        --context new_session --transition-description "архитекторы не сошлись / гейты красные" --prompt "$(E design_synth_rerun)"
kent workflow edge add "$W" --from design_review  --transition judge    --edge-key design_rev_verdict   --to design_verdict --context continue_session --prompt "$(E design_rev_verdict)"
kent workflow edge add "$W" --from design_verdict --transition approved --edge-key design_approved      --to plan   --context new_session --requires-approval --transition-description "reality check пройден — пользователь одобряет дизайн" --prompt "$(E design_approved)"
kent workflow edge add "$W" --from design_verdict --transition needs_changes --edge-key design_needs_changes --to design --context new_session --transition-description "blocker в дизайне" --prompt "$(E design_needs_changes)"

# PLAN (8)  — D5: plan_gate --pass--> plan_review (без plan_reviewer); F5: plan --respec--> spec
kent workflow edge add "$W" --from plan          --transition plan_team --edge-key plan_to_planner       --to plan_planner  --context new_session --transition-description "домен ок — планируем" --prompt "$(E plan_to_planner)"
kent workflow edge add "$W" --from plan          --transition respec    --edge-key plan_respec           --to spec          --context new_session --transition-description "skeleton-блокер / требуется re-spec" --prompt "$(E plan_respec)"
kent workflow edge add "$W" --from plan_planner  --transition planned   --edge-key planner_to_gate       --to plan_gate     --context new_session --prompt "$(E planner_to_gate)"
kent workflow edge add "$W" --from plan_gate     --transition pass      --edge-key plangate_pass         --to plan_review   --context new_session --transition-description "все детерминированные проверки зелёные" --prompt "$(E plangate_pass)"
kent workflow edge add "$W" --from plan_gate     --transition fail      --edge-key plangate_fail         --to plan_planner  --context new_session --transition-description "fenced-код/битые пути/docs-check красный" --prompt "$(E plangate_fail)"
kent workflow edge add "$W" --from plan_review   --transition judge     --edge-key plan_rev_verdict      --to plan_verdict  --context continue_session --prompt "$(E plan_rev_verdict)"
kent workflow edge add "$W" --from plan_verdict  --transition approved  --edge-key plan_approved         --to impl_preflight --context new_session --requires-approval --transition-description "план одобрен пользователем" --prompt "$(E plan_approved)"
kent workflow edge add "$W" --from plan_verdict  --transition needs_changes --edge-key plan_needs_changes --to plan         --context new_session --transition-description "blocker в плане" --prompt "$(E plan_needs_changes)"

# IMPLEMENT (30)  — D4: review_crossmodel снят (нет gate_rev_crossmodel/rcm_join); D3: impl_smoke --debug--> debug
kent workflow edge add "$W" --from impl_preflight --transition proposal --edge-key preflight_impl --to implement --context new_session --prompt "$(E preflight_impl)"
kent workflow edge add "$W" --from implement --transition dev   --edge-key impl_dev_backend     --to dev_backend     --context new_session --prompt "$(E impl_dev_backend)"
kent workflow edge add "$W" --from implement --transition dev   --edge-key impl_dev_frontend    --to dev_frontend    --context new_session --prompt "$(E impl_dev_frontend)"
kent workflow edge add "$W" --from implement --transition dev   --edge-key impl_dev_firebase    --to dev_firebase    --context new_session --prompt "$(E impl_dev_firebase)"
kent workflow edge add "$W" --from implement --transition dev   --edge-key impl_dev_test        --to dev_test        --context new_session --prompt "$(E impl_dev_test)"
kent workflow edge add "$W" --from implement --transition dev   --edge-key impl_dev_integration --to dev_integration --context new_session --prompt "$(E impl_dev_integration)"
kent workflow edge add "$W" --from implement --transition debug --edge-key implement_debug      --to debug           --context new_session --param 'problem=slug и описание проблемы' --prompt "$(E implement_debug)"
kent workflow edge add "$W" --from dev_backend     --transition built --edge-key db_join  --to dev_join --context new_session
kent workflow edge add "$W" --from dev_frontend    --transition built --edge-key df_join  --to dev_join --context new_session
kent workflow edge add "$W" --from dev_firebase    --transition built --edge-key dfb_join --to dev_join --context new_session
kent workflow edge add "$W" --from dev_test        --transition built --edge-key dt_join  --to dev_join --context new_session
kent workflow edge add "$W" --from dev_integration --transition built --edge-key di_join  --to dev_join --context new_session
kent workflow edge add "$W" --from dev_join   --transition gate         --edge-key devjoin_gate --to build_gate --context new_session --prompt "$(E devjoin_gate)"
kent workflow edge add "$W" --from build_gate --transition build_failed --edge-key gate_fail    --to implement  --context new_session --transition-description "сборка/тесты/гейты упали" --prompt "$(E gate_fail)"
kent workflow edge add "$W" --from build_gate --transition review --edge-key gate_rev_code       --to review_code         --context new_session --prompt "$(E gate_rev_code)"
kent workflow edge add "$W" --from build_gate --transition review --edge-key gate_rev_arch       --to review_architect    --context new_session --prompt "$(E gate_rev_arch)"
kent workflow edge add "$W" --from build_gate --transition review --edge-key gate_rev_sec        --to review_security     --context new_session --prompt "$(E gate_rev_sec)"
kent workflow edge add "$W" --from build_gate --transition review --edge-key gate_rev_comp       --to review_completeness --context new_session --prompt "$(E gate_rev_comp)"
kent workflow edge add "$W" --from build_gate --transition review --edge-key gate_rev_conc       --to review_concurrency  --context new_session --prompt "$(E gate_rev_conc)"
kent workflow edge add "$W" --from review_code         --transition reviewed --edge-key rc2_join   --to review_join --context new_session
kent workflow edge add "$W" --from review_architect    --transition reviewed --edge-key ra2_join   --to review_join --context new_session
kent workflow edge add "$W" --from review_security     --transition reviewed --edge-key rs2_join   --to review_join --context new_session
kent workflow edge add "$W" --from review_completeness --transition reviewed --edge-key rcomp2_join --to review_join --context new_session
kent workflow edge add "$W" --from review_concurrency  --transition reviewed --edge-key rconc_join --to review_join --context new_session
kent workflow edge add "$W" --from review_join --transition judge --edge-key join_verdict --to verdict --context continue_session --context-source node:implement --prompt "$(E join_verdict)"
kent workflow edge add "$W" --from verdict --transition needs_changes --edge-key review_needs_changes --to implement  --context new_session --transition-description "blocker/консенсусный high" --prompt "$(E review_needs_changes)"
kent workflow edge add "$W" --from verdict --transition next_phase    --edge-key review_next_phase    --to implement  --context new_session --transition-description "фаза PASS, остались фазы" --prompt "$(E review_next_phase)"
kent workflow edge add "$W" --from verdict --transition phases_done   --edge-key review_done          --to impl_smoke --context new_session --transition-description "все фазы PASS" --prompt "$(E review_done)"
kent workflow edge add "$W" --from impl_smoke --transition smoke_failed --edge-key smoke_fail  --to implement --context new_session --transition-description "smoke/docs-гейт красный" --prompt "$(E smoke_fail)"
kent workflow edge add "$W" --from impl_smoke --transition debug        --edge-key smoke_debug --to debug     --context new_session --param 'problem=slug и описание рантайм-симптома' --transition-description "финальный E2E выявил рантайм-баг → живая диагностика" --prompt "$(E smoke_debug)"

# CROSS-PHASE (13)
kent workflow edge add "$W" --from impl_smoke --transition crossphase --edge-key smoke_cp_crossmodel --to cp_crossmodel   --context new_session --prompt "$(E smoke_cp_crossmodel)"
kent workflow edge add "$W" --from impl_smoke --transition crossphase --edge-key smoke_cp_code       --to cp_code         --context new_session --prompt "$(E smoke_cp_code)"
kent workflow edge add "$W" --from impl_smoke --transition crossphase --edge-key smoke_cp_arch       --to cp_architect    --context new_session --prompt "$(E smoke_cp_arch)"
kent workflow edge add "$W" --from impl_smoke --transition crossphase --edge-key smoke_cp_sec        --to cp_security     --context new_session --prompt "$(E smoke_cp_sec)"
kent workflow edge add "$W" --from impl_smoke --transition crossphase --edge-key smoke_cp_comp       --to cp_completeness --context new_session --prompt "$(E smoke_cp_comp)"
kent workflow edge add "$W" --from cp_crossmodel   --transition reviewed --edge-key cpcm_join   --to cp_join --context new_session
kent workflow edge add "$W" --from cp_code         --transition reviewed --edge-key cpc_join    --to cp_join --context new_session
kent workflow edge add "$W" --from cp_architect    --transition reviewed --edge-key cpa_join    --to cp_join --context new_session
kent workflow edge add "$W" --from cp_security     --transition reviewed --edge-key cps_join    --to cp_join --context new_session
kent workflow edge add "$W" --from cp_completeness --transition reviewed --edge-key cpcomp_join --to cp_join --context new_session
kent workflow edge add "$W" --from cp_join    --transition verdict --edge-key cpjoin_verdict --to cp_verdict --context new_session --prompt "$(E cpjoin_verdict)"
kent workflow edge add "$W" --from cp_verdict --transition fix     --edge-key cp_fix         --to implement  --context new_session --transition-description "blocker/high требуют фикса / final smoke красный" --prompt "$(E cp_fix)"
kent workflow edge add "$W" --from cp_verdict --transition handoff --edge-key cp_handoff     --to retrospective --context new_session --requires-approval --transition-description "implementation.md записан, deferred одобрены" --prompt "$(E cp_handoff)"

# DEBUG (30)  — D3: standalone-вход через триаж spec_intake (intake_debug), dbg_fixspec перемаршрутизирован (to_plan→dbg_plan, defer/passed по контексту)
kent workflow edge add "$W" --from spec_intake --transition to_debug --edge-key intake_debug --to debug --context new_session --param 'problem=slug и описание проблемы' --transition-description "триаж: баг-репорт на готовой фиче → standalone-диагностика" --prompt "$(E intake_debug)"
kent workflow edge add "$W" --from debug --transition phase1 --edge-key debug_to_doc     --to dbg_doc      --context new_session --prompt "$(E debug_to_doc)"
kent workflow edge add "$W" --from debug --transition phase1 --edge-key debug_to_logscan --to dbg_log_scan --context new_session --prompt "$(E debug_to_logscan)"
kent workflow edge add "$W" --from dbg_doc      --transition found --edge-key dd1_join --to dbg_p1_join --context new_session
kent workflow edge add "$W" --from dbg_log_scan --transition found --edge-key dl1_join --to dbg_p1_join --context new_session
kent workflow edge add "$W" --from dbg_p1_join --transition advise --edge-key p1_advisor       --to dbg_advisor  --context new_session --prompt "$(E p1_advisor)"
kent workflow edge add "$W" --from dbg_advisor --transition assess --edge-key advisor_decision --to dbg_decision --context new_session --prompt "$(E advisor_decision)"
kent workflow edge add "$W" --from dbg_decision --transition fixspec  --edge-key decision_fixspec --to dbg_fixspec --context new_session --transition-description "A/early-out: root cause очевиден" --prompt "$(E decision_fixspec)"
kent workflow edge add "$W" --from dbg_decision --transition deep --edge-key deep_code --to dbg_code --context new_session --prompt "$(E deep_code)"
kent workflow edge add "$W" --from dbg_decision --transition deep --edge-key deep_log  --to dbg_log  --context new_session --prompt "$(E deep_log)"
kent workflow edge add "$W" --from dbg_decision --transition deep --edge-key deep_doc  --to dbg_doc2 --context new_session --prompt "$(E deep_doc)"
kent workflow edge add "$W" --from dbg_decision --transition deep --edge-key deep_web  --to dbg_web  --context new_session --prompt "$(E deep_web)"
kent workflow edge add "$W" --from dbg_decision --transition moreinfo --edge-key decision_moreinfo --to debug --context new_session --transition-description "D: пользователь даст больше контекста" --prompt "$(E decision_moreinfo)"
kent workflow edge add "$W" --from dbg_code --transition found --edge-key dc2_join --to dbg_join --context new_session
kent workflow edge add "$W" --from dbg_log  --transition found --edge-key dl2_join --to dbg_join --context new_session
kent workflow edge add "$W" --from dbg_doc2 --transition found --edge-key dd2_join --to dbg_join --context new_session
kent workflow edge add "$W" --from dbg_web  --transition found --edge-key dw2_join --to dbg_join --context new_session
kent workflow edge add "$W" --from dbg_join  --transition synth   --edge-key dbgjoin_synth --to dbg_synth   --context new_session --prompt "$(E dbgjoin_synth)"
kent workflow edge add "$W" --from dbg_synth --transition fixspec --edge-key synth_fixspec --to dbg_fixspec --context new_session --transition-description "root cause найден / гипотезы исчерпаны" --prompt "$(E synth_fixspec)"
kent workflow edge add "$W" --from dbg_synth --transition rerun   --edge-key synth_rerun   --to dbg_decision --context new_session --transition-description "нужен новый раунд гипотез/состава" --prompt "$(E synth_rerun)"
kent workflow edge add "$W" --from dbg_fixspec --transition apply_direct --edge-key fixspec_apply       --to dbg_fix_dev  --context new_session --requires-approval --transition-description "A: scope=small, прямой фикс одобрен" --prompt "$(E fixspec_apply)"
kent workflow edge add "$W" --from dbg_fixspec --transition to_plan      --edge-key fixspec_to_plan     --to dbg_plan     --context new_session --transition-description "B: medium/large — спланировать фикс (ОСНОВНОЙ путь, заменил handoff)" --prompt "$(E fixspec_to_plan)"
kent workflow edge add "$W" --from dbg_fixspec --transition defer_done   --edge-key fixspec_defer_done  --to done         --context new_session --transition-description "C-standalone: отложить (status: deferred)"
kent workflow edge add "$W" --from dbg_fixspec --transition defer_impl   --edge-key fixspec_defer_impl  --to implement    --context new_session --transition-description "C-mid: отложить, вернуться в implement" --prompt "$(E fixspec_defer_impl)"
kent workflow edge add "$W" --from dbg_fixspec --transition revisit      --edge-key fixspec_revisit     --to dbg_decision --context new_session --transition-description "D: пересмотреть root cause" --prompt "$(E fixspec_revisit)"
kent workflow edge add "$W" --from dbg_plan    --transition to_impl      --edge-key dbgplan_impl        --to implement    --context new_session --transition-description "план фикса готов — полный per-phase цикл" --prompt "$(E dbgplan_impl)"
kent workflow edge add "$W" --from dbg_fix_dev    --transition built       --edge-key fixdev_review          --to dbg_fix_review --context new_session --prompt "$(E fixdev_review)"
kent workflow edge add "$W" --from dbg_fix_review --transition passed_done --edge-key fixreview_passed_done  --to done           --context new_session --transition-description "фикс чист, debug standalone → завершение"
kent workflow edge add "$W" --from dbg_fix_review --transition passed_impl --edge-key fixreview_passed_impl  --to implement      --context new_session --transition-description "фикс чист, debug посреди реализации → implement" --prompt "$(E fixreview_passed_impl)"
kent workflow edge add "$W" --from dbg_fix_review --transition fix         --edge-key fixreview_fix         --to dbg_fix_dev    --context new_session --transition-description "blocker/high в фиксе" --prompt "$(E fixreview_fix)"

# RETROSPECTIVE (8)
kent workflow edge add "$W" --from retrospective --transition retro_team --edge-key retro_to_artifacts    --to retro_artifacts    --context new_session --prompt "$(E retro_to_artifacts)"
kent workflow edge add "$W" --from retrospective --transition retro_team --edge-key retro_to_instructions --to retro_instructions --context new_session --prompt "$(E retro_to_instructions)"
kent workflow edge add "$W" --from retro_artifacts    --transition read --edge-key rart_join   --to retro_join --context new_session
kent workflow edge add "$W" --from retro_instructions --transition read --edge-key rinstr_join --to retro_join --context new_session
kent workflow edge add "$W" --from retro_join  --transition synth    --edge-key retrojoin_synth  --to retro_synth --context new_session --prompt "$(E retrojoin_synth)"
kent workflow edge add "$W" --from retro_synth --transition apply    --edge-key retro_apply_edge --to retro_apply --context new_session --requires-approval --transition-description "пользователь одобрил (часть) фиксов" --prompt "$(E retro_apply_edge)"
kent workflow edge add "$W" --from retro_synth --transition no_fixes --edge-key retro_done       --to done        --context new_session --transition-description "фиксы не требуются/отклонены"
kent workflow edge add "$W" --from retro_apply --transition complete --edge-key retroapply_done  --to done        --context new_session

# ---------- ВАЛИДАЦИЯ И ПРИВЯЗКА ----------
kent workflow validate "$W" --mode draft
kent workflow validate "$W" --mode task_creation
kent workflow validate "$W" --mode execution
# validate НЕ ловит нарушение «join = ровно 1 исходящее ребро» — проверь вручную:
# join-ноды skeleton_join, research_join, design_join, dev_join, review_join, cp_join,
# dbg_p1_join, dbg_join, retro_join — у каждой должно быть РОВНО 1 исходящее ребро (здесь так и есть).

# (F3) имя проекта — флагом --name (НЕ позиционным аргументом), путь — --path
kent project create --path /Volumes/EXTERNAL/schoolquiz3.0 --name schoolquiz3.0   # если команда create отличается — создать проект средствами Kent UI
# затем привязать воркфлоу к проекту (по id/пути проекта, выведенному create)
kent workflow link "schoolquiz3.0" "$W" --default
```

Пост-чек:
- Суммарно **80 нод** (по фактическим `node add`: 79 agent/join/terminal + start; +spec_intake +dbg_plan −review_crossmodel −plan_reviewer относительно прежних 80 = 80) и **132 ребра** (по фактическим `edge add`: SPEC 17 + RESEARCH 14 + DESIGN 12 + PLAN 8 + IMPLEMENT 30 + CROSS-PHASE 13 + DEBUG 30 + RETRO 8 = 132).
- **Параллельный фан-аут (несколько рёбер в ОДНОЙ transition-группе) и/или ветвление (несколько transition-групп) — только из agent-нод** (kind=agent): spec_intake (server_needed|no_server|to_debug — триаж), spec (skeleton|review), skeleton_sig (bodies×2), skeleton_gate (advance|rerun|respec), spec_verdict, research (research_team×4), research_synth, research_verdict, design (design_team×3), design_synth, design_verdict, plan (plan_team|respec — F5), plan_gate (pass|fail), plan_verdict, implement (dev×5|debug), build_gate (build_failed|review×5), verdict (×3), impl_smoke (smoke_failed|debug|crossphase×5 — D3), cp_verdict, debug (phase1×2), dbg_decision (fixspec|deep×4|moreinfo), dbg_synth, dbg_fixspec (apply_direct|to_plan|defer_done|defer_impl|revisit — D3), dbg_fix_review (passed_done|passed_impl|fix — D3), retrospective (retro_team×2), retro_synth. `spec_server` и `dbg_plan` имеют РОВНО 1 исходящее ребро (единственный исход). `backlog` (start) имеет РОВНО 1 исходящее ребро (`intake`→spec_intake) — безопасно для Kent; ветвление feature-vs-bug перенесено на agent-ноду spec_intake (см. §6 item 17).
- **Join-ноды (9), у каждой РОВНО 1 исходящее ребро**: skeleton_join→skeleton_gate, research_join→research_synth, design_join→design_synth, dev_join→build_gate, review_join→verdict, cp_join→cp_verdict, dbg_p1_join→dbg_advisor, dbg_join→dbg_synth, retro_join→retro_synth. Ни одно ребро не исходит из join, кроме единственного (validate этого не ловит — проверка вручную выше).
- **requires-approval ровно на 6 рёбрах** (без изменений после правок): spec_approved, design_approved, plan_approved, cp_handoff, fixspec_apply, retro_apply_edge. (Новые рёбра plan_respec, intake_debug, smoke_debug, fixspec_to_plan, dbgplan_impl, fixspec_defer_impl, fixreview_passed_impl — БЕЗ approval.)
- **continue_session ровно на 5 judge-рёбрах**: spec_rev_verdict, research_rev_verdict, design_rev_verdict, plan_rev_verdict (plan_review→plan_verdict — сохранён после снятия plan_reviewer), join_verdict (последний с `--context-source node:implement`).
