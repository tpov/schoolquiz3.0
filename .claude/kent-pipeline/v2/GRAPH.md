# Quiz Feature Pipeline v2 (гибрид: граф-стадии + субагенты Kent)

Источник: 7 команд `.claude/commands/feature-*.md` (прочитаны целиком).
Принцип: **граф** держит спайн стадий, гейты, вердикты, WAIT-точки (approval) и петли;
**воркеры** поднимаются лид-нодой как субагенты Kent в рантайме — как Teams в источнике.

## Контракт спавна (вшивается в КАЖДЫЙ лид-промпт)

```
Работников поднимаешь как субагентов Kent (аналог TeamCreate+Agent из источника):
  /opt/homebrew/bin/kent run --agent <роль> "<полное self-contained задание>"      # новый работник
  /opt/homebrew/bin/kent run --session <session-id> "<сообщение>"                   # продолжить его сессию (re-check, доп. задание)
  /opt/homebrew/bin/kent run steer <session-id> "<сообщение>"                       # сообщение в активный ран
  /opt/homebrew/bin/kent run wait --output-mode=json <session-id>                   # повторно опросить уже известную сессию
Роли: backend-dev frontend-dev firebase-dev test-dev integration-tester coder
      code-reviewer architect-reviewer security-reviewer completeness-reviewer concurrency-reviewer plan-reviewer
      codebase-researcher code-analyst doc-analyst log-reader web-researcher diagnostics
      planner architect-high-level architect-component design-architect domain-designer
      crossmodel-reviewer (gpt-5.4 — ДРУГАЯ модель, для cross-model ревью) product-manager

ЖЁСТКО:
- Субагент headless: он НЕ может задать вопрос пользователю. ВСЕ вопросы задаёшь ТЫ через ask_question.
- Задание субагенту — self-contained: "начни немедленно, без ack", путь к его role-файлу
  .claude/agents/<роль>.md, нужные .claude/rules/*, формат финального отчёта.
- Субагенты общаются между собой через файлы репо и через тебя (relay), плюс `run --session` для re-check.
- Число работников — по фактической потребности (N критериев → N исследователей), НЕ фиксировано.

ПАРАЛЛЕЛЬНОСТЬ. `kent run --agent` и `kent run --session` — БЛОКИРУЮЩИЕ: возвращают финальный отчёт
работника. Чтобы поднять нескольких работников ОДНОВРЕМЕННО, запускай их фоновыми процессами shell
и жди все разом:
  kent run --agent test-dev   "<задание>" > run/agents/test-dev.out   2>&1 &
  kent run --agent backend-dev "<задание>" > run/agents/backend-dev.out 2>&1 &
  wait
  # затем прочитай .out-файлы — это финальные отчёты работников
Последовательный шаг (когда нужен результат до следующего действия) — обычный вызов без `&`.
`kent run wait <session-id>` НУЖЕН только для повторного опроса уже известной сессии; после
блокирующего вызова отчёт уже получен — второй раз ждать не нужно.
Session-id работника бери из вывода его рана (Kent печатает готовую команду `run steer`), записывай
в реестр роль→session-id в Run Ledger и используй для re-check через `kent run --session <id>`.
```

## Проверено на живом Kent (2026-07-26)

- **Вложенный спавн РАБОТАЕТ**: субагент поднял субагента и вернул его ответ (`INNER_OK`).
- **PATH сессий Kent НЕ содержит `/opt/homebrew/bin`** → голая команда `kent` даёт
  `command not found`. Во всех промптах используется АБСОЛЮТНЫЙ путь `/opt/homebrew/bin/kent`.
- Флага `--prompt-file` у `kent workflow node update` НЕТ — только `--prompt`.

## Ноды (21)

| # | key | kind | роль | что делает (источник) |
|---|-----|------|------|------------------------|
| 1 | `backlog` | start | — | вход |
| 2 | `spec` | agent | product-manager | feature-spec Phase 0 (slug/type/tier/триаж баг-vs-фича) + Phase 1 server-анализ (спавн codebase-researcher) + Phase 2 диалог + 2.5 splitting + Phase 3 запись 0-spec.md + 3.5 lock |
| 3 | `skeleton` | agent | lead | feature-spec Phase 3.8: Stage A domain-designer → Stage B (domain-designer bodies ∥ test-dev тесты) → gradle-гейт, 5 исходов |
| 4 | `spec_review` | agent | crossmodel-reviewer | feature-spec Phase 4 (Codex-ревью ТЗ) |
| 5 | `spec_verdict` | agent | lead | обработка findings + Phase 6 сводка к одобрению |
| 6 | `research` | agent | lead | feature-research целиком: N исследователей = N критериев, core-scan, cross-feature scanner, web; синтез 1-research.md; Grounding Gate 2-grounding.md (лид сам читает код — Independent Verification) |
| 7 | `design` | agent | lead | feature-design: спор 2 архитекторов + web (субагенты, relay через session), Codex ПОСЛЕДОВАТЕЛЬНО по группам док с петлёй на линзу, Gates 1-8, lead judge |
| 8 | `plan` | agent | lead | feature-plan: planner возвращает разбиение по вертикалям, лид поднимает дополнительные раны роли `planner` по числу вертикалей; 2 линзы cross-model ревью (роль crossmodel-reviewer), hooks + check_pipeline_docs |
| 9 | `impl_preflight` | agent | diagnostics | feature-implement 0.6 Team Composition Proposal (read-only advisor) |
| 10 | `implement` | agent | lead | feature-implement 1-2.4 на ОДНУ фазу: спавн devs+reviewers, **билд гоняет сам coder**, coder броадкастит ревьюеров, автономный цикл reviewer↔coder, лид ПАССИВЕН; масштабирование = повторный ран той же роли с другим scope (метки вида `-2` живут только во внутреннем реестре лида, в аргументе `--agent` их нет) |
| 11 | `phase_verdict` | agent | lead | 2.4: сбор RESULT/ERROR, PASS/REJECT, переход к следующей фазе |
| 12 | `impl_smoke` | agent | lead | 2.5: ciCheck + assembleDebugAndroidTest + E2E connectedAndroidTest + check_pipeline_docs |
| 13 | `cp_review` | agent | lead | 3: Codex cross-phase (единственная cross-model точка) ∥ повторно все same-model ревьюеры |
| 14 | `cp_verdict` | agent | lead | 3.5 scorecard + 4 final smoke + 4.5 deferred approval + 5 handoff/implementation.md |
| 15 | `debug` | agent | lead | feature-debug Phase 1: doc-analyst + log-reader (субагенты Kent), классификация, diagnostics-advisor, early-out, вопрос A/B/C/D |
| 16 | `dbg_deep` | agent | lead | Phase 2: targeted команда по proposal, kickoff, convergence loop, diagnostic logs |
| 17 | `dbg_fixspec` | agent | lead | Phase 3: fix-spec-<date>.md + вопрос A/B/C/D |
| 18 | `dbg_apply` | agent | lead | 3.3.A прямой фикс: backend/frontend-dev + test-dev, build gate, обязательный code-reviewer |
| 19 | `retrospective` | agent | lead | feature-retrospective 0-3: 2 читателя, WAIT evidence, root cause, WebSearch, 12 паттернов, предложения фиксов |
| 20 | `retro_apply` | agent | lead | 4-6: применение одобренных фиксов, invariants, lessons-learned, README |
| 21 | `done` | terminal | — | конец |

**join-нод НЕТ** — фан-аут ушёл внутрь сессий лидов (главная грабля Kent устранена структурно).

## Рёбра (~33)

SPEC:
1. backlog -[intake]-> spec
2. spec -[to_debug]-> debug (триаж: баг на готовой фиче)
3. spec -[skeleton]-> skeleton (Contract ≠ N/A)
4. spec -[review]-> spec_review (Contract = N/A)
5. skeleton -[advance]-> spec_review
6. skeleton -[respec]-> spec (двусмысленность/scope overflow → Task Splitting)
7. spec_review -[judge]-> spec_verdict  (continue_session)
8. spec_verdict -[approved ⚑]-> research   **APPROVAL**
9. spec_verdict -[needs_changes]-> spec

RESEARCH / DESIGN / PLAN (в источнике каждая команда запускается человеком → approval-ребро = этот же гейт):
10. research -[approved ⚑]-> design   **APPROVAL**
11. design -[approved ⚑]-> plan   **APPROVAL**
12. design -[needs_research]-> research (нет/неполный grounding — источник: СТОП)
13. plan -[approved ⚑]-> impl_preflight   **APPROVAL**
14. plan -[respec]-> spec (блокер в existing skeleton → re-spec)

IMPLEMENT:
15. impl_preflight -[proposal]-> implement
16. implement -[phase_done]-> phase_verdict
17. implement -[debug]-> debug (живая диагностика)
18. phase_verdict -[next_phase]-> implement
19. phase_verdict -[needs_changes]-> implement (REJECT → fix loop)
20. phase_verdict -[phases_done]-> impl_smoke
21. impl_smoke -[smoke_failed]-> implement
22. impl_smoke -[debug]-> debug (рантайм-баг из E2E)
23. impl_smoke -[crossphase]-> cp_review
24. cp_review -[verdict]-> cp_verdict  (continue_session)
25. cp_verdict -[fix]-> implement
26. cp_verdict -[handoff ⚑]-> retrospective   **APPROVAL**

DEBUG:
27. debug -[deep]-> dbg_deep (B/C)
28. debug -[fixspec]-> dbg_fixspec (A / early-out)
29. dbg_deep -[fixspec]-> dbg_fixspec
30. dbg_deep -[rerun]-> debug (D: больше контекста)
31. dbg_fixspec -[apply_direct ⚑]-> dbg_apply   **APPROVAL** (только scope=small)
32. dbg_fixspec -[to_plan]-> plan (B: medium/large — план вокруг fix spec)
33. dbg_fixspec -[defer]-> done (C)
34. dbg_fixspec -[revisit]-> debug (D)
35. dbg_apply -[passed]-> done
36. dbg_apply -[passed_impl]-> implement (если фикс шёл посреди реализации)

RETRO:
37. retrospective -[apply ⚑]-> retro_apply   **APPROVAL**
38. retrospective -[no_fixes]-> done
39. retro_apply -[complete]-> done

Approval-рёбер: 7 (spec_approved, research_approved, design_approved, plan_approved, cp_handoff, fixspec_apply, retro_apply).
continue_session: 2 (spec_rev_verdict, cp_review→cp_verdict).

## Что это возвращает из источника (чего не было в v1)
- N исследователей на N критериев + core-scan на пакет (research)
- разбиение планирования по вертикалям: planner возвращает разбиение, лид поднимает дополнительные раны роли `planner` (plan)
- спор архитекторов + Codex ПО ГРУППАМ ДОКУМЕНТОВ с петлёй на каждую линзу (design)
- **билд гоняет coder, он же зовёт ревьюеров; автономный цикл reviewer↔coder; лид пассивен** (implement)
- масштабирование работников: повторный ран той же роли с другим scope (`--agent backend-dev` дважды и т.п.); различающие метки живут только во внутреннем реестре лида
- параллелизм работников: несколько `kent run --agent` фоновыми процессами shell (`&`) с общим `wait` и чтением .out-файлов — блокирующий ран сам возвращает финальный отчёт
- динамический состав debug-команды по diagnostics-proposal
- test-dev в прямом фиксе debug
