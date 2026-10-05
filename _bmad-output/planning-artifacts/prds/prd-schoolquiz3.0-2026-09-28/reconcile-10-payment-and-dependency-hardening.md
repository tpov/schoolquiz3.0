# Input reconciliation — 10-PAYMENT-AND-DEPENDENCY-HARDENING

**Статус remedies, 2026-09-28: все четыре gaps устранены в плановом тексте.** Gap1 → [карта переходов](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:123); gap2 → [refund failure-matrix](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:125); gap3 → [база dependency-кандидата](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:127); gap4 → [runtime/privacy](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:129). Новые audit, финансовые и runtime PASS не создавались. Ниже сохранён исходный анализ сверенного снимка.

Дата: 2026-09-28. Planning-only, read-only сверка исходников; runtime, сборки, устройства, магазин и финансовые прогоны не запускались. Авторитет — текущий `prd.md` (FR-1…55), последние решения владельца и PRODUCT-CONTRACT; `prd-consolidated.md` не использован как authority. Сверены `prd.md`, `addendum.md`, `prd-consolidated-addendum.md`, `evidence-inventory.md`. Основные документы и код не изменены. HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.

Уточнения владельца сохранены: первоначальная подача 1000 ноликов, частичная повторная 100×число затронутых вопросов без доплат и потолка; автор платит при принятии, сетевой retry не новая попытка; вознаграждение личный уровень/10 за урок после всего цикла, включая обоснованный отказ, с пропорциональным общим пределом полученной платы и сжиганием остатка; review fee не возвращается. Проверка онлайн с серверным прогрессом и паузой. Тариф metadata-only отложен до реализации соответствующего пути и не блокирует весь PRD. Эти правила не отменяют Play refunds, отмену резервов и возврат фонда турнира.

Исходный вход: `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/10-PAYMENT-AND-DEPENDENCY-HARDENING.md:1–43`.

## Сохранено

- Finance и dependencies разделены; текущая planning-задача не разрешает production refunds/deploy/mass migration: оригинал:3 → consolidated addendum:13,62,65,75.
- Постоянный token fence обоих денежных путей, strict RTDN→current verification/binding, cumulative quantity, ambiguous pending/repair, purchase→grant→used/remaining, legacy proof и bounded resumable repair, premium own unused остаток без ущерба gifts/other grants/regen, sales-off recovery/ACK/refunds сохранены: оригинал:9–25 → PRD:387–409; addendum:41–42; consolidated addendum:61–64.
- Grant/refund оба порядка/гонка/loss, wrong app/product/order/quantity/account, разные premium consumption states/multiple grants/nonnegative conservation и реальный sandbox как отдельный слой сохранены: оригинал:29–31 → PRD:403,409,457,508; consolidated addendum:70–75.
- Current package/lock/runtime hashes, актуальный audit с runtime/dev/transitive exposure, clean install/syntax/import/full current Node/HTTP/rules, запрет force/latest и совместимость major, отдельный dependency commit и rollback lock/image без удаления данных сохранены: оригинал:35–43 → PRD:469,494–496; consolidated addendum:65–66. Исторические числа audit и старые67tests не приняты за текущий результат; evidence inventory:88 прямо оставляет current audit/compatibility неизвестными.

## Gaps — 4

### 1. Не сохранён обязательный подготовительный артефакт финансовых переходов

Источник: оригинал:20. Наличие списка денежных состояний PRD:391 и provenance consolidated addendum:62 не заменяет требуемую до изменений таблицу состояния→переход→фактическое authoritative evidence.

Минимальное исправление в техническое приложение: «До изменения purchase/revocation/verification/notification/settlement/clawback/premium/Play-adapter/ledger пути составляется таблица состояний и переходов с фактическим источником доказательства». Не создавать новые статусы или выдуманный provider proof в PRD.

### 2. Финансовая матрица потеряла точность некоторых отрицательных проверок

Источник: оригинал:29–31. PRD:403,508 говорит о потере ответа и гонках в целом, а два устройства PRD:509 привязаны явно к tickets. Явно не сохранены loss после каждой финансовой TX, независимый purchase/refund race двух устройств и требование удержать все негативные regression tests постоянного token fence.

Минимальное исправление: добавить эти три условия к refund acceptance, сохранив duplicate RTDN, wrong package/product/order/quantity/UID, временный Play failure, spent/part-used/unspent, multiple grants, один ledger effect и conservation. Purchase/premium/currency Node + реальные HTTP/Firebase/rules не заменять общей старой зелёной суммой или fake Play.

### 3. Нет явной проверки базы сохранённого dependency-кандидата

Источник: оригинал:38–39,43. Consolidated addendum:65 требует hashes и compatibility, но не говорит, как обращаться с ранее подготовленным dependency-only lock при отличающейся базе и с несовместимой security adaptation.

Минимальное исправление: «Перед использованием сохранённого dependency-кандидата сверить его базу с текущей; при несовпадении подготовить новый dependency-only срез, не копировать lock вслепую. Требующее несовместимого изменения security-исправление оформляется отдельным согласованным ТЗ». Не вводить конкретную новую версию Admin SDK как принятое решение.

### 4. Потеряны конкретные ограничения runtime/privacy dependency-среза

Источник: оригинал:41. Общие «без production credentials» consolidated addendum:75 и audit exposure:65 не сохраняют специальную проверку GAC/legacy messaging/Firestore/gRPC путей и запрет неожиданных telemetry/external requests из private demo из-за обновления lock.

Минимальное исправление: вернуть эти проверки в technical-how dependency-среза, сохранив запуск без production credentials. Это не запрет требуемого отдельно согласованного Play sandbox, а исходная граница dependency compatibility проверки в частном demo.

## Решения/зависимости

Новые цены/политика quantity не изобретаются. Реальные Play SKU/quantity и sandbox proof остаются DEP-1. Четыре gaps закрываются восстановлением исходного technical/acceptance контракта; текущий audit и новые PASS здесь не создаются. Отсутствие возврата review fee не переносится на Play/refund contract.

## Снимок сверенных документов

- prd.md: SHA-256 8a2e57611bf0ff844999ea7b665b67320fa33a6637f67400fcfc42d50fa311d7
- addendum.md: SHA-256 c3bbdde5f371cb0a112cdafa4569176b7e5116612461b32c33dd3cc4c86745fd
- prd-consolidated-addendum.md: SHA-256 b527705569a3a8f09b83bf9f5eac0f8772e414adb3751ecbac1979702a6fe1cb
- evidence-inventory.md: SHA-256 27339c262c18bfff37f10a020f2585b9ff59500c3348339c8bfadbeb8b2a5a89
