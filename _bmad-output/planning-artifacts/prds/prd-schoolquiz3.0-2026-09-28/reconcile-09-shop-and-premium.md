# Input reconciliation — 09-SHOP-AND-PREMIUM

**Статус remedies, 2026-09-28: product/acceptance scope — no gaps.** Единственная коррекция статуса gold-цен передана root; evidence-inventory этим действием не менялся. Согласованные цены уже сохранены в `prd.md` и [техническом companion](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:15). Ниже сохранён исходный анализ; он не является новым вопросом владельцу о цене.

Дата: 2026-09-28. Planning-only, read-only сверка исходников; runtime, сборки, устройства, магазин и финансовые прогоны не запускались. Авторитет — текущий `prd.md` (FR-1…55), последние решения владельца и PRODUCT-CONTRACT; `prd-consolidated.md` не использован как authority. Сверены `prd.md`, `addendum.md`, `prd-consolidated-addendum.md`, `evidence-inventory.md`. Основные документы и код не изменены. HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.

Уточнения владельца сохранены: первоначальная подача 1000 ноликов, частичная повторная 100×число затронутых вопросов без доплат и потолка; автор платит при принятии, сетевой retry не новая попытка; вознаграждение личный уровень/10 за урок после всего цикла, включая обоснованный отказ, с пропорциональным общим пределом полученной платы и сжиганием остатка; review fee не возвращается. Проверка онлайн с серверным прогрессом и паузой. Тариф metadata-only отложен до реализации соответствующего пути и не блокирует весь PRD. Эти правила не отменяют Play refunds, отмену резервов и возврат фонда турнира.

Исходный вход: `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/09-SHOP-AND-PREMIUM.md:1–42`.

## Сохранено

- Полезная текущая навигация/товары, gold10/60/150, premium1/7/30/365 за gold и Play, серверная цена/validation, Play ProductDetails и unavailable с причиной сохранены: оригинал:13–15 → PRD:357–367,417–427. Позднее согласованные gold-цены1/5/15/100, отдельный gold premium экран, 30-дневная сравнимая стоимость и экономия явно включены; вопрос цены заново не задаётся.
- Active/expired/unknown freshness/pending purchase/refund, точная граница срока, grants, цвет ника, две регенерации×2 по реальным интервалам с остатками времени/holds, UI countdown не выдаёт награды: оригинал:16–18 → PRD:369–373,399–403,465,508.
- Один тип коробки/общий баланс, ordinary daily≤1 и отдельная premium daily без рекламы, остальные разрешённые источники не урезаны, offline streak не доказывается переводом часов/часовым поясом/долгим отсутствием, онлайн opening с durable intent/receipt и повторным тем же результатом: оригинал:22–25 → PRD:375–385,508.
- Идемпотентная server-verified покупка/account binding, сохранность identity на restart/process death, честные PENDING/unknown, ACK/consume по state/type, recovery/query/refunds при выключенных новых продажах и redaction сохранены: оригинал:29–34 → PRD:387–409,459; consolidated addendum:61–66,75.
- Полная server/UI/real Play sandbox матрица, gold premium и box-intent, expiry/account switch, loss после grant до ACK, restart/restore, чужой аккаунт, partial-used refund/grants, единичность и ledger сохранены: оригинал:38–42 → PRD:403,409,508–515; evidence inventory:74–86. Fake/Redroid не выдан за real Play; конфигурация/устройство — зависимость этой приёмки, а не невозможность остальных частей. Текущая официальная Billing совместимость проверяется при исполнении, отдельно от денежной переработки; новая магазинная политика не выдумана.

## Product/acceptance scope — no gaps

Существенных пропусков требований исходного входа в функциональном и приёмочном тексте не выявлено. Ниже одна реальная несогласованность статуса; второго дефекта для количества не добавлено.

## Коррекция статуса — 1

`evidence-inventory.md:78` по-прежнему перечисляет «четыре premium gold prices» среди внешних вопросов. Это противоречит PRD:359–367,530, addendum:43 и consolidated addendum:15: владелец уже установил 1/5/15/100. Оригинал:15 требовал получить отсутствующие цены; данная часть требования теперь выполнена продуктовым решением, но её реализация/приёмка этим не доказана.

Минимальное исправление: заменить только этот пункт реестра на «Gold premium prices согласованы: 1/5/15/100 для1/7/30/365 дней; внедрение и приёмка открыты». Premium SKU, фактический Play каталог, tester/account, quantity policy и пригодное устройство оставить внешними зависимостями. Кодовые gold SKU не объявлять опубликованными в Console, не приравнивать gold-цены к Play-ценам.

## Решения/зависимости

Новых вопросов о согласованных gold-ценах, daily box, рекламе и единице коробки нет. Остались фактические Play IDs/config/quantity/tester/device и будущая настоящая sandbox приёмка (PRD:530–533), а также полный UX rework и отдельная синхронизация server constants/configs.

## Снимок сверенных документов

- prd.md: SHA-256 8a2e57611bf0ff844999ea7b665b67320fa33a6637f67400fcfc42d50fa311d7
- addendum.md: SHA-256 c3bbdde5f371cb0a112cdafa4569176b7e5116612461b32c33dd3cc4c86745fd
- prd-consolidated-addendum.md: SHA-256 b527705569a3a8f09b83bf9f5eac0f8772e414adb3751ecbac1979702a6fe1cb
- evidence-inventory.md: SHA-256 27339c262c18bfff37f10a020f2585b9ff59500c3348339c8bfadbeb8b2a5a89
