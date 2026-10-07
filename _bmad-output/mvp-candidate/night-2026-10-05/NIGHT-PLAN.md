# Ночной план SchoolQuiz — 5→6 октября 2026

База всех треков: `continuation/mvp-candidate-20260926`, базовый коммит `b8bc23ff`
(planning baseline: инициатива mvp-candidate, 10 эпиков, tickets, хребет 2026-09-29,
оценочные контракты, спеки, PRD-входы, evidence 27.09/04.10).

## Точка остановки (на 5 октября, 16:00 UTC)

- **Epic 1 `epic-candidate-rig` — закрыт 04.10.** Истории 1.1, 1.2, 1.4, 1.6 — done
  (бэкенд `schoolquiz-demo-firebase-20261004` на 18090/19090; APK `eeb8fc6e…`,
  сертификат `00:33:E7:FA…`, схема Room v15; quiescence-гейт исправлен и подтверждён
  RED-оракулом владельца; корневой ciCheck выполнен и записан — RED,
  `processDebugGoogleServices`, нет owner-локального google-services.json).
  Истории 1.3 и 1.5 — NOT RUN, hitl (физические Samsung RF8W80MBR3Z / Pixel на Mac владельца).
- **Epic 2 `epic-ordinary-delivery` — разблокирован, инсепшена нет** (в папке эпика нет
  tickets.toml). Он же владелец контракта ONLINE-O2 (contract-map, Remaining gates).
- **Эпики 3, 4, 7, 8** ждут эпик 2 (ONLINE-O2 / durable delivery / config foundations).
  Инсепшены нет. **Эпики 5, 6, 9, 10** ждут несколько предшественников.
- Отложенное эпика 1: (1) корневой ciCheck остаётся RED до появления owner-локального
  debug google-services.json; повторный прогон некому записать; (2) 17-кейсовый
  контракт-тест quiescence существует только в незакоммиченной правке владельца
  `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` — в закоммиченном
  дереве старая 428-строчная версия, автоматической проверки нет.
- UX-очередь (отдельный трек html-review): v49 course-exams-r1 — последний макет
  очереди, НЕ одобрен; после v49 — консолидация, не расширение очереди.

## Треки ночи (три параллельных фоновых агента)

| Трек | Дерево | Ветка | Отчёт (в своей ветке) |
|---|---|---|---|
| P: инсепшен эпика 2 + черновик ONLINE-O2 | `.local/worktrees/night-e2` | `night/e2-inception` | `docs/agents/night-2026-10-05/report-e2.md` |
| D: черновики инсепшенов эпиков 3/4/7/8 | `.local/worktrees/night-drafts` | `night/e3-e8-drafts` | `docs/agents/night-2026-10-05/report-drafts.md` |
| E: хвосты эпика 1 (без сборок) | `.local/worktrees/night-e1f` | `night/e1-followups` | `docs/agents/night-2026-10-05/report-e1f.md` |

### Общие правила для всех треков

- Коммитить локально, **без push**; коммитить только файлы, написанные треком.
- **Никаких Gradle-сборок** этой ночью: на том же VPS идут ночные треки Skillify,
  правило «одна тяжёлая сборка одновременно». Разрешены Node-скрипты в
  `node:22-bookworm-slim` (repo read-only, network none) и лёгкие докер-операции.
- Не трогать незакоммиченные файлы владельца: `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js`
  (это его RED-оракул, держать дословно), `local.properties`. Не коммитить `.working/`.
- Каталог `_bmad-output/planning-artifacts/ux-designs/` не закоммичен (337M) — читать
  по абсолютному пути основного дерева, не копировать.
- Планировочные входы: `_bmad-output/specs/spec-schoolquiz3.0/SPEC.md` (CAP, Constraints),
  `contract-map.md` (Remaining gates), хребет `architecture-schoolquiz3.0-2026-09-29/`
  (AD-1..31, gates), `initiative-mvp-candidate.md` (OQ-2/4/5/6/7, touch points),
  `USER-CONTINUATION-PLAN.md` (§2–§5, приёмочные матрицы), эпики в своей папке.
- Формат историй — как в `epic-candidate-rig/tickets.toml`: `[[story]]` с id, slug,
  title (что и с каким доказательством), covers, after, hitl, и комментарии-статусы.
  Каждая история формулируется с доказуемым done-when и путём evidence
  (`docs/agents/evidence/<дата>-<slug>/`).
- Стоп-условия: требуемый вход противоречит спеке/хребту — фиксировать расхождение
  в отчёте и останавливаться; история не формулируется без доказуемого критерия —
  пометить `refined = false` и вопрос в отчёт; через 2 безуспешные попытки
  разрешения конфликта — стоп.
- Язык отчётов — русский; в конце отчёта секция «Вопросы владельцу» — каждый вопрос
  с 2–3 вариантами и последствиями.

### Трек P — инсепшен эпика 2 + ONLINE-O2 (только планирование, без реализации)

1. Инсепшен `epic-ordinary-delivery/tickets.toml`: разбить Done-when эпика (6 пунктов)
   на истории 2.x в порядке сборки; отметить зависимые от устройств (hitl) и от
   ONLINE-O2; для каждой — covers (CAP-1/16/18/19), after, риск.
2. Черновик контракта `epic-ordinary-delivery/online-o2-contract-draft.md`:
   delivery/ready-ACK receipt, identity/retry, связь со стартом answer-clock,
   поведение при потере ACK (без бесконечного gap и замещающих часов) — по
   contract-map Remaining gates (ONLINE-O2), AD-31, EXAM AD-3 как существующее
   доказательство (не перезапись), USER-CONTINUATION-PLAN §4. Сверить с текущим
   outbox-кодом (прочитать, не менять) и перечислить расхождения код/контракт.
3. Отчёт: порядок сборки историй, какие можноdispatch’ить сразу после «да» владельца,
   какие ждут контракта; вопросы владельцу.

### Трек D — черновики инсепшенов эпиков 3/4/7/8

Для каждого эпика — черновик `tickets-draft.toml` рядом с эпиком (НЕ трогать
основной tickets.toml инициативы): истории по Done-when эпика и §2–§5 плана,
с пометкой `refined = false` и явным списком «ждёт: ONLINE-O2 / epic-2 / hitl / OQ».
Особое: эпик 5 — OQ-6 (cutoff reconciliation) как hitl-вопрос; эпик 7 — inventory
Play SKU/денежных цен как внешняя зависимость (не сочинять SKU); эпик 4 — TZ06B/C
серверные views; эпик 3 — матрица Арены из §2 плана. Утром владелец решает,
утверждать ли черновики до завершения эпика 2 (прецедент Skillify: черновики
мержатся только после «да»).

### Трек E — хвосты эпика 1 (лёгкий)

1. Памятка по RED-базе ciCheck: воспроизвести/подтвердить записанный результат
   (без новой сборки — по evidence 04.10), оформить `docs/agents/evidence/2026-10-05-cicheck-baseline/`
   с описанием условия снятия RED (файл google-services.json) и черновиком истории
   1.7 «re-run ciCheck и записать» в `epic-candidate-rig/tickets.toml` (status не ставить).
2. Предложить дифф подключения контракт-теста в тестовую цепочку
   (`scripts/package.json` — проверить наличие node/npm на VPS, написать proposal-файл,
   сам тест владельца не менять и не коммитить).
3. Runbook утренней hitl-сессии 1.3/1.5: пошаговые adb-команды с Mac, путь к APK
   `eeb8fc6e…`, чек-листы сохранности (аккаунт, кошелёк, уроки, история, identity
   установки, pending-операции), пустые шаблоны evidence-json для заполнения утром.
4. Ночной контрольный экспорт демо-бэкенда: docker exec в работающий
   `schoolquiz-demo-firebase-20261004` → emulators:export в НОВЫЙ каталог
   `control-save-night-20261005/`; контейнер не останавливать/не перезапускать,
   данных не менять; запись в отчёт с количеством файлов/пользователей.

## Что ночью НЕ делаем

- Никакой реализации эпика 2 (ждёт «да» владельца на инсепшен — прецедент E2/E3).
- Никаких установок на устройства, никаких Play-операций, никаких Functions.
- Не трогаем старое дерево `sync/outbox-unification` в корне schoolquiz3.0.
