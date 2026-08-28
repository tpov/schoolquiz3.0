# Директива правок промптов v2 (по 29 замечаниям проверки)

Правь файлы в `/private/tmp/claude-501/-Users-tpov-tg-watch-userbot/1672033a-247c-4679-8d37-c1d0b621863e/scratchpad/v2/prompts/` на месте.

## D0. ЕДИНЫЙ МЕХАНИЗМ ЦИКЛА REVIEWER↔CODER (закрывает #1, #17, #18) — САМОЕ ВАЖНОЕ

Факт о Kent: `kent run --agent <роль> "<задание>"` — **блокирующий**: возвращает финальный отчёт субагента.
`kent run --session <id> "<сообщение>"` — продолжает ЕГО сессию и тоже возвращает ответ.
Асинхронного «сообщения в чужую сессию» у субагента нет — но синхронный спавн-и-ожидание есть.

**Единственный режим (никаких развилок «если можешь — сам, иначе лид»):**

Задание coder-у (backend-dev / frontend-dev) содержит ПОЛНЫЙ цикл:
1. Реализация по своему role-файлу.
2. Build Gate запускает САМ coder (`./gradlew ciCheck --no-configuration-cache` + phase validation + Test Deletion Gate).
3. При PASS **сам coder поднимает ревьюеров**, по одному вызову на ревьюера, каждый — блокирующий:
   `kent run --agent code-reviewer "=== PHASE N REVIEW — ASSIGNMENT FROM CODER === ... Build Status: PASSED (commit <sha>) ..."`
   (роли: code-reviewer, architect-reviewer, security-reviewer, completeness-reviewer, + concurrency-reviewer по тегу фазы).
   Вызов возвращает findings ревьюера — **это и есть его сообщение coder-у**.
4. Coder чинит findings и делает re-check ТОЙ ЖЕ сессией ревьюера:
   `kent run --session <session-id ревьюера> "исправлено <file:line>, re-check"` — итерирует до "passed".
5. Только когда все ревьюеры вернули passed — coder отдаёт лиду ОДИН финальный RESULT.
   ERROR лиду — только при architectural mismatch / повторном blocker того же класса / reviewer disagreement.

Лид в шагах 2-4 **пассивен**: не запускает билд, не поднимает ревьюеров, не релеит findings.

**Фолбэк (детерминированный, единственное условие):** если вызов `kent run` внутри сессии работника
возвращает ошибку (вложенный спавн недоступен в этой инсталляции Kent) — работник обязан завершить ран
отчётом `NESTED_SPAWN_UNAVAILABLE` + готовым текстом блока ASSIGNMENT FROM CODER; тогда ревьюеров
поднимает лид этим текстом дословно и релеит findings через `kent run --session`. Формулировать именно так:
проверка = фактическая ошибка вызова, а не «если у тебя есть возможность».

Убрать из implement.md все «ты — тупой relay», «ревьюеров поднимаешь ты [лид]» как безусловные,
и все формулировки прямого peer-обмена между работниками («шлёшь ревьюеру», «EVIDENCE coder-у»)
заменить на механику выше.

## D1. Роли: только из списка (закрывает #15, #19)
- `sub-planner` НЕ существует. В задании planner-у: «верни лиду список вертикалей и рекомендуемое разбиение».
  Лид поднимает по дополнительному субагенту роли **`planner`** на вертикаль со своим scope.
- Суффиксов `-2` у ролей НЕТ. Масштабирование = **второй ран той же роли** (`kent run --agent code-reviewer` дважды)
  с разным scope в задании; `-2` — только внутренняя метка лида в реестре роль→session-id и в Run Ledger.
  Исправить везде: implement.md, crossphase.md.

## D2. Убрать неисполнимое (закрывает #16, #21, #20, #28)
- design.md: убрать флаги Codex CLI `-o` и `-c file=path`. Вместо них — в тексте задания субагенту
  crossmodel-reviewer: «запиши отчёт в docs/features/<slug>/reviews/design-<lens>.md» + список путей к коду
  (shared/feature/<slug>/domain/, */build.gradle.kts модулей из 01-architecture.md, *.kt для типов из 06-api-contract.md).
- plan.md: убрать «проверь наличие CLI (codex или claude)». Недоступность роли обрабатывать как в cp_review:
  СТОП + ask_question.
- design.md: заменить «Запустите /feature-research <slug>» на «grounding создаёт нода `research`; возвращаю
  пайплайн исходом needs_research».
- spec.md: в шаблоне 0-spec.md строку про `/feature-retrospective` заменить на «нодой `retrospective` пайплайна».

## D3. Базовый файл правил — единая формула (закрывает #5, #24)
Во ВСЕХ 9 файлах: «прочитай `CLAUDE.md`, а если его нет — `AGENTS.md` в корне репо».
(В schoolquiz3.0 сейчас CLAUDE.md отсутствует, есть AGENTS.md — формула покрывает оба случая.)

## D4. Ретроспектива анализирует то, что реально исполнялось (закрывает #4, #23)
В задании instruction-reader (Фаза 0.2) пункт про `.claude/commands/` заменить на:
«промпты нод Kent-воркфлоу — выгрузи через `kent workflow inspect <workflow-uuid> --json` (ноды: node_key,
subagent_role, prompt_template; рёбра: key, transition, prompt_template) и прочитай каждый; каждое утверждение —
со ссылкой на node-key/transition. Каталог `.claude/commands/` читай только как исторический источник, если он есть».
Gate 1 переписать соответственно. В Фазе 3/4 цели фиксов уже включают промпты нод — оставить.

## D5. Путь из debug в plan не должен упираться в STOP (закрывает #3)
plan.md, Grounding Gate Check: условие СТОП переформулировать —
«СТОП, если нет НИ `2-grounding.md`, НИ `fix-spec-*.md`».
Добавить режим **fix-spec-driven pass**: если пришли из debug (есть fix-spec со статусом handoff-to-implement) —
Traceability берётся из секций Root Cause / Evidence / Proposed Fix / New-Updated Tests фикс-спеки,
phase-01 = сам фикс + regression tests, полный review-цикл сохраняется.

## D6. Честные пометки вместо тихих отклонений (закрывает #2, #6, #10, #11, #13, #14, #8)
Ввести единый маркер `[V2-ОТКЛОНЕНИЕ: ...]` / `[V2-ДОБАВЛЕНО: ...]` прямо в тексте промпта, коротко:
- spec.md, tier: `[V2-ОТКЛОНЕНИЕ: в источнике tier маршрутизирует пайплайн (Light=Spec-lite→Implement,
  Medium=без design). В v2 маршрутизации по tier НЕТ — все фичи идут полным путём; tier пишется в 0-spec.md
  как приоритет. Это осознанный вырез, а не «advisory по замыслу».]`
- spec.md, триаж: `[V2-ДОБАВЛЕНО: шага триажа в источнике нет — он нужен графу как вход в debug.
  При сомнении дефолт — писать спеку, а не уходить в debug.]`
- implement.md, параллельные фазы: `[V2-ОТКЛОНЕНИЕ: иерархических phase-lead нет; независимые фазы
  выполняются последовательно в любом порядке.]` — и убрать/пометить строку Parallel в таблице зависимостей.
- research.md и cp_verdict: пометить approval-точки как `[V2-ДОБАВЛЕНО: гейт графа]` (в источнике переход
  между командами делал человек вручную — это его аналог).
- impl_preflight: пункт Confidence пометить `[V2-ДОБАВЛЕНО]`; сохранение proposal в файл оставить как есть.
- implement.md: пометить `[V2-ОТКЛОНЕНИЕ: в источнике ревьюеры спавнятся сразу и блокируются blockedBy;
  в Kent блокировка реализована порядком спавна — ревьюер поднимается только после Build Status: PASSED.
  Требование «ревьюер без строки Build Status: PASSED обязан вернуть ERROR» сохраняется как носитель гейта.]`
- spec_review: дополнительный блок проверок пометить `[V2-ДОБАВЛЕНО]` либо сократить до 4 пунктов источника.

## D7. Мелкие правки (закрывают #7, #9, #12, #22, #25, #26, #27, #29)
- plan.md: описание `check-plan-paths.sh` свести к «прогони хук, если он есть в репо; ненулевой exit = FAIL»;
  шкалу severity привести к **blocker/high/medium/low** (как во всём пайплайне).
- debug.md (dbg_fixspec): для исходов `to_plan` и `defer` добавить обязательное обновление README фичи
  (ссылка на fix-spec в «Debug History»; для defer — ещё и «Pending Fixes»).
- spec.md: при Task Splitting вариант A — продолжать текущий ран по ОДНОЙ части (выбранной пользователем),
  для остальных выдать готовые команды запуска отдельных задач Kent; не терять части молча.
- research.md: добавить перед блоком ИСХОДЫ шаг «HUMAN APPROVAL»: показать сводку (находки, cross-feature
  summary, статусы BLOCKER→RESOLVED, открытые delta-вопросы) и ждать явного одобрения через ask_question;
  без него исход `approved` не выдавать. Пометить `[V2-ДОБАВЛЕНО: гейт графа]`.
- design.md: перенумеровать шаги подряд (0,1,2,3,4) — сейчас пропущен «ШАГ 2».
- research.md, debug.md: про Context7 MCP добавить «если недоступен — обычный web-поиск + пометка
  [CONTEXT7 UNAVAILABLE]».
- retro.md: инвертировать приоритет поиска — «делегируй web-researcher субагенту; если WebSearch доступен
  лично — можешь сам».
- dbg_deep: в fallback-таблице указывать полный путь `.claude/skills/systematic-debugging/SKILL.md`.

## Инварианты после правок
- Ни одного `{{плейсхолдера}}`; ни одного упоминания Teams/TeamCreate/SendMessage/TaskCreate как доступного
  инструмента (только как пояснение происхождения механики).
- Роли только из списка: backend-dev frontend-dev firebase-dev test-dev integration-tester coder code-reviewer
  architect-reviewer security-reviewer completeness-reviewer concurrency-reviewer plan-reviewer codebase-researcher
  code-analyst doc-analyst log-reader web-researcher diagnostics planner architect-high-level architect-component
  design-architect domain-designer crossmodel-reviewer product-manager.
- Блок ИСХОДЫ в конце каждого промпта = ровно transition-имена из GRAPH.md для этой ноды.
- Все вопросы пользователю — только у лид-нод через ask_question.
