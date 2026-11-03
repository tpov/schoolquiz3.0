# Input reconciliation — 08-TOURNAMENT-CLIENT

**Статус remedies, 2026-09-28: оба gaps устранены в плановом тексте.** Gap1 → [bounded reads](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:119); gap2 → [owner/epoch, cancellation и отрицательная wire/DI матрица](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:121). Это восстановление требований, не приёмка клиентской реализации. Ниже сохранён исходный анализ сверенного снимка.

Дата: 2026-09-28. Planning-only, read-only сверка исходников; runtime, сборки, устройства, магазин и финансовые прогоны не запускались. Авторитет — текущий `prd.md` (FR-1…55), последние решения владельца и PRODUCT-CONTRACT; `prd-consolidated.md` не использован как authority. Сверены `prd.md`, `addendum.md`, `prd-consolidated-addendum.md`, `evidence-inventory.md`. Основные документы и код не изменены. HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.

Уточнения владельца сохранены: первоначальная подача 1000 ноликов, частичная повторная 100×число затронутых вопросов без доплат и потолка; автор платит при принятии, сетевой retry не новая попытка; вознаграждение личный уровень/10 за урок после всего цикла, включая обоснованный отказ, с пропорциональным общим пределом полученной платы и сжиганием остатка; review fee не возвращается. Проверка онлайн с серверным прогрессом и паузой. Тариф metadata-only отложен до реализации соответствующего пути и не блокирует весь PRD. Эти правила не отменяют Play refunds, отмену резервов и возврат фонда турнира.

Исходный вход: `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/08-TOURNAMENT-CLIENT.md:1–48`.

## Сохранено

- Новый клиентский путь не принят по наличию legacy screens: evidence inventory:68–70. Шесть точных callables, пять допустимых клиентских операций и запрет клиентских worker operations сохранены: оригинал:7–16 → consolidated addendum:52–53.
- Strict DTO, original operationId/immutable body/owner+epoch, authoritative save до ACK, безопасный paginated discovery без private collection reads сохранены: оригинал:20–24 → PRD:307,337,441–445,459; consolidated addendum:52–56. Неразглашение будущих форм/ключей остаётся обязательным для клиента и диагностики.
- Course archive/допущенная Arena, автоматический банк, весь фонд first+second+2×third до confirm, непоказ успеха при недостатке средств, отдельное событие с датами, CREATE replay и три отдельные EASY попытки/неделя/одна на round/authoritative seeds сохранены: оригинал:28–32 → PRD:303–325.
- UTC/server time и локальный timezone/DST, отдельные stage/scheduling deadlines, два подтверждения, no agreement/absent/hour, непрерывные часы, reconnect той же session/question, unknown outcome → fetch, серверные итог/bracket/two bronze/pending-paid-returned сохранены: оригинал:36–40 → PRD:327–349; consolidated addendum:56.
- UI/state/accessibility/RU-UK-EN/Back/rotation, реальный course→event→qualifiers→seed→agreement→match→ledger путь, controlled server clocks, отдельная полная backend matrix, многодевайсная приёмка и запрет fake/screenshots вместо реального пути сохранены: оригинал:44–48 → PRD:465,494–515; consolidated addendum:70–75.

## Gaps — 2

### 1. Bounded read-политика сжалась до discovery

Источник: оригинал:22. В PRD:307 и consolidated addendum:52 ограничен список/discovery, а для `fetchCourseTournamentEvent/Session/Meeting` отсутствует явное правило ограниченных запросов без бесконечного polling. Общие Unknown/Pending/Forbidden/NotFound и честное ожидание не задают границу повторного чтения каждого из трёх ресурсов.

Минимальное исправление в техническое приложение: «Чтения event/session/meeting ограничены; Unknown, Pending, Forbidden и NotFound различаются. Бесконечный polling не является стратегией ожидания». Число запросов, новый SLA, timeout завершения турнира либо платный retry не изобретать.

### 2. Асинхронный клиентский контракт потерял точные границы cancellation/epoch

Источник: оригинал:20. Consolidated addendum:53 сохраняет owner+epoch как поля, PRD:441–445 — общий результат account isolation, но не фиксируют проверку исходного owner+epoch до SDK-call и после ответа и передачу `CancellationException` наружу. Это обязательное техническое условие исходного входа, а не новый UX.

Минимальное исправление рядом с existing mutation contract: «Исходные owner+epoch проверяются до SDK-call и после ответа; CancellationException передаётся наружу». Проверить original negative wire/DI cases из оригинала:44: malformed DTO, forbidden worker action, user switch, duplicate CREATE/ANSWER, process restore, lost response, late question/time и отсутствие key leakage. Сохранить serial/idempotent отправку ответа (оригинал:39), не допускающую второго локального ответа при unknown outcome.

## Решения/зависимости

Новых продуктовых решений не требуется. Шесть новых Kotlin endpoints пока не доказаны подключёнными в candidate; current backend/UI/device acceptance остаётся открытой. Эти gaps не меняют серверный clock и не разрешают перенос offline Arena pause в турнир.

## Снимок сверенных документов

- prd.md: SHA-256 8a2e57611bf0ff844999ea7b665b67320fa33a6637f67400fcfc42d50fa311d7
- addendum.md: SHA-256 c3bbdde5f371cb0a112cdafa4569176b7e5116612461b32c33dd3cc4c86745fd
- prd-consolidated-addendum.md: SHA-256 b527705569a3a8f09b83bf9f5eac0f8772e414adb3751ecbac1979702a6fe1cb
- evidence-inventory.md: SHA-256 27339c262c18bfff37f10a020f2585b9ff59500c3348339c8bfadbeb8b2a5a89
