# Input reconciliation — 07-TOURNAMENT-SERVER

**Статус remedies, 2026-09-28: оба gaps устранены в плановом тексте.** Gap1 → [bounded demo worker/ресурсы](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:115); gap2 → [полная qualification/worker матрица](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:117). Это восстановление требований, не PASS реализации. Ниже сохранён исходный анализ сверенного снимка.

Дата: 2026-09-28. Planning-only, read-only сверка исходников; runtime, сборки, устройства, магазин и финансовые прогоны не запускались. Авторитет — текущий `prd.md` (FR-1…55), последние решения владельца и PRODUCT-CONTRACT; `prd-consolidated.md` не использован как authority. Сверены `prd.md`, `addendum.md`, `prd-consolidated-addendum.md`, `evidence-inventory.md`. Основные документы и код не изменены. HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.

Уточнения владельца сохранены: первоначальная подача 1000 ноликов, частичная повторная 100×число затронутых вопросов без доплат и потолка; автор платит при принятии, сетевой retry не новая попытка; вознаграждение личный уровень/10 за урок после всего цикла, включая обоснованный отказ, с пропорциональным общим пределом полученной платы и сжиганием остатка; review fee не возвращается. Проверка онлайн с серверным прогрессом и паузой. Тариф metadata-only отложен до реализации соответствующего пути и не блокирует весь PRD. Эти правила не отменяют Play refunds, отмену резервов и возврат фонда турнира.

Исходный вход: `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/07-TOURNAMENT-SERVER.md:1–44`.

## Сохранено

- LS-модель, серверная версия/округление, неизменность опубликованного посева, полный frozen cohort, проверка повреждённого/неполного входа, durable phases/hash/lease/fencing и завершение выше прежнего бюджета сохранены: оригинал:15–22 → PRD:315–325,467,507; consolidated addendum:54–55. 4096/4097 не превращены в продуктовую квоту. Разрешённая коррекция численной погрешности не отправляется на повторное продуктовое согласование.
- Автоматический допущенный банк, три EASY и shared HARD по 20, исключение polls, недостаточный банк до резерва, недельный отбор, три дня на стадию, срок согласования с часом+длительностью, два подтверждения, one/both out и серверное продвижение сохранены: оригинал:26,28–36 → PRD:303–343; consolidated addendum:52–57.
- First/second/two third, отсутствие наград technically-out, bye без вымышленного призёра, <2 — один refund, единичный reserve с concurrent spend, escrow conservation и terminal remaining=0 сохранены: оригинал:37–38 → PRD:309–313,339–349; consolidated addendum:57.
- Текущий статус отделён от исторического: `45ab9d82` в HEAD и совпадение семи production SHA отражены как read-only установленное внедрение, SDK502/fake4096–4097 — исторические PASS, HTTP/Auth/scheduler и полный путь открыты: evidence inventory:62–66. Устаревшее «только prototype/вечное Pending >501» не возвращается в описание текущего кода. Общие gates и запрет смешивать prototype с production proof: PRD:469–471,494–515.

## Gaps — 2

### 1. Утеряны точные ограничения будущего запуска worker и вычислительного бюджета

Источник: оригинал:21,26–27,44. В PRD:467 есть измерения, consolidated addendum:55 — invocation budget, :57 — recovery; но в этих четырёх сверяемых документах не закреплены управляемый bounded запуск именно demo worker без внешнего Scheduler/PubSub по умолчанию и явные границы памяти, операций/времени, числа/размера чисел. Общая текущая пауза и server-only не заменяют эти условия будущей реализации.

Минимальное исправление: в техническое приложение к турниру добавить: «Запуск worker на demo управляемый и bounded, без внешнего Scheduler/PubSub по умолчанию и без production deployment. Серверные тестовые часы не задаются клиентом. Зафиксированы проверяемые границы памяти, операций/времени invocation и числовых представлений; они не становятся лимитом участников, а исчерпание бюджета оставляет путь к завершению». Не вводить новые числовые лимиты и не разрешать запуск сейчас.

### 2. Общая строка приёмки не сохраняет несколько обязательных отдельных проверок

Источник: оригинал:42–44. PRD:507 сохраняет размеры, неполный/повреждённый вход и восстановление, но явно не удерживает same-form qualification ties, missing rounds по действующему контракту, конкурентных workers и отдельные crash points после partial scan/lease/commit. Сыгранная ничья матча не является same-form tie отбора; общая фраза «после прерываний» не задаёт точки отказа.

Минимальное исправление: дополнить турнирную приёмочную матрицу перечисленными четырьмя группами, unknown/malformed входом и CLOCK boundaries; результаты полного tournament/qualification/worker/currency Node + реального Firestore/HTTP/rules фиксировать отдельно с input/output hashes, calculation version, final event/bracket/ledger/escrow и измерениями на закреплённом Node22. Это детализация исходной приёмки, не новые сценарии и не утверждение свежего PASS.

## Решения/зависимости

Новых продуктовых вопросов по этим двум gaps нет. Бюджеты/реализация определяются доказательством, а не самовольным cap. Реальные текущие HTTP/worker/fund/UI доказательства остаются будущей приёмкой.

## Снимок сверенных документов

- prd.md: SHA-256 8a2e57611bf0ff844999ea7b665b67320fa33a6637f67400fcfc42d50fa311d7
- addendum.md: SHA-256 c3bbdde5f371cb0a112cdafa4569176b7e5116612461b32c33dd3cc4c86745fd
- prd-consolidated-addendum.md: SHA-256 b527705569a3a8f09b83bf9f5eac0f8772e414adb3751ecbac1979702a6fe1cb
- evidence-inventory.md: SHA-256 27339c262c18bfff37f10a020f2585b9ff59500c3348339c8bfadbeb8b2a5a89
