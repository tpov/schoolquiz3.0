# Agent Communication — общие правила для всех teammates

Эти правила обязательны для всех агентов работающих в Teams (devs, reviewers, analysts). Призваны избавить от coordination overhead, выявленного в retrospective.

## Self-starting on assignment

Когда lead шлёт assignment через SendMessage — **начинай работу немедленно**, без ack, без "понял, приступаю", без "жду подтверждения".

Prompt в assignment содержит всё нужное:
- Путь к role file (например `docs/features/<slug>/plan/phase-NN/backend.md`)
- Список rules для чтения
- Формат финального отчёта
- Scope твоей ответственности

Если чего-то критического не хватает (missing file, ambiguous scope) — зафиксируй в **Open Questions** в финальном отчёте, **не** шли промежуточный DM "мне нужен X".

## Message Types (5 разрешённых)

Каждый DM или отчёт lead-у должен быть одного из типов. Если не попадает — не отправляй.

| Type | Когда | Пример |
|------|-------|--------|
| **REQUEST** | Нужна работа/верификация от другого teammate | "Проверь что тип `UserId` существует в `core/UserId.kt`" |
| **EVIDENCE** | Нашёл finding для другого teammate | "CRASH в `CallService.kt:142`, stacktrace: NullPointerException at line 142" |
| **RESULT** | Финальный отчёт lead-у (один раз на задание) | "PHASE 01 DONE: files X,Y changed, tests green, 0 open questions" |
| **NOTIFICATION** | Рассылка context без ожидания ответа | "Domain skeleton в `domain/mute/` готов, 10 тестов зелёных. Phase-01 будет integration." |
| **ERROR** | Не могу выполнить — нужна эскалация lead-у | "Scope требует менять `build.gradle.kts` — это backend-dev owner. Запрашиваю delegation." |

## ЗАПРЕЩЕНО (turn впустую)

- **ACK**: "принято", "понял", "ок", "приступаю" — начинай работу немедленно без подтверждения
- **STATUS**: "в процессе", "начал", "работаю", "готово" — это `TaskUpdate(status: ...)`, не DM
- **NEGOTIATION**: "а можно я сделаю X?" — координация = задача lead-а через phase files; scope неясен → Open Question в финальном RESULT отчёте
- **GREETING**: "привет", "здравствуй" — prompt содержит "Начни НЕМЕДЛЕННО без ack"

**Правило**: если сообщение не передаёт evidence (с `file:line`) и не просит конкретную работу — не отправляй. Lead видит прогресс через `TaskList`, статус-DM не нужны.

## Статус — через TaskUpdate, не DM

Когда начинаешь работу → `TaskUpdate(status: in_progress)`.
Когда завершил → `TaskUpdate(status: completed)`.
Lead видит progress в TaskList без твоих DM.

## Финальный отчёт — один SendMessage lead-у

В конце работы один SendMessage lead-у с:
- Что сделано (список файлов, AC coverage)
- Open Questions (не реализованное, противоречия с spec/design, missing context)
- Validation результат (тесты зелёные? build зелёный?)

Не шли множественные отчёты "часть 1", "часть 2" — один итоговый.

## Reviewers — подожди build gate через TaskList

Если ты reviewer (code-reviewer, architect-reviewer, security-reviewer, completeness-reviewer, concurrency-reviewer):

1. Твоя задача создаётся lead-ом через `TaskCreate` с `addBlockedBy: [build_task]`
2. Пока `build_task` не completed — твоя задача в TaskList показывает `blocked`
3. **Не начинай review** пока задача blocked. Ждёшь пока lead пометит build completed
4. Когда task unblocked — lead шлёт SendMessage с kickoff prompt (assignment). С этого момента — начинай немедленно (см. Self-starting выше)
5. Review по реальному коду (после build pass), не по plan файлам до build

## Архитектурный mismatch — STOP, не импровизация

Если в ходе работы обнаруживаешь расхождение с spec/design, которое требует:
- Переписать feature domain contract
- Убрать функциональность описанную в spec
- Изменить архитектурный паттерн (class structure, layer boundary)
- Пропустить AC

→ **STOP**. Зафиксируй в Open Questions. Не решай молча. Lead эскалирует пользователю.

## Scaffold ownership

Файлы `build.gradle.kts` (root + per-module), `libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`, `AndroidManifest.xml` (root) — владение `backend-dev`. Другие teammates запрашивают изменения через SendMessage lead-у. Параллельное редактирование = merge conflict.

## Walking Skeleton awareness

Если domain layout фичи из `.claude/PROJECT-CONTEXT.md` уже содержит файлы (для текущего KMP проекта обычно `shared/feature/<feature_slug>/domain/src/commonMain/`, сгенерировано на spec-этапе через `domain-designer`):
- Backend-dev: обёрточный integration work, не rewrite domain
- Test-dev: integration tests, не дубликат pure domain tests
- Frontend-dev: работает с готовыми domain types

Architectural mismatch в existing skeleton → Open Question, не silent rewrite.

## Quality over speed

Нет hard limits на итерации, размер кода, количество тестов. Escalation signals (повторяющиеся findings того же класса, много отвергнутых гипотез) — повод эскалировать пользователю о направлении, не тихая остановка по счётчику.
