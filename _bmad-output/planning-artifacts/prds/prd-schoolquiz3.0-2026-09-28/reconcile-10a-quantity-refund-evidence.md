# Input reconciliation — 10A-QUANTITY-REFUND-EVIDENCE

**Статус после remedies, 2026-09-28: no gaps, исправлений по этому входу не потребовалось.** Ограниченный характер historical10A сохраняют [purchase proof/provenance](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:61), [current-candidate evidence rule](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:70) и `evidence-inventory.md:82–88`. Дополнения по ТЗ-10 не расширяют историческое10A до real Play или текущего общего PASS.

Дата: 2026-09-28. Planning-only, read-only сверка исходников; runtime, сборки, устройства, магазин и финансовые прогоны не запускались. Авторитет — текущий `prd.md` (FR-1…55), последние решения владельца и PRODUCT-CONTRACT; `prd-consolidated.md` не использован как authority. Сверены `prd.md`, `addendum.md`, `prd-consolidated-addendum.md`, `evidence-inventory.md`. Основные документы и код не изменены. HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.

Уточнения владельца сохранены: первоначальная подача 1000 ноликов, частичная повторная 100×число затронутых вопросов без доплат и потолка; автор платит при принятии, сетевой retry не новая попытка; вознаграждение личный уровень/10 за урок после всего цикла, включая обоснованный отказ, с пропорциональным общим пределом полученной платы и сжиганием остатка; review fee не возвращается. Проверка онлайн с серверным прогрессом и паузой. Тариф metadata-only отложен до реализации соответствующего пути и не блокирует весь PRD. Эти правила не отменяют Play refunds, отмену резервов и возврат фонда турнира.

Исходный вход: `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/10A-QUANTITY-REFUND-EVIDENCE.md:1–144`.

## Result — no gaps

Это исторический evidence report, а не новое утверждение о готовности текущего кандидата. В сверяемых документах существенного расширения его доказательной силы либо утраты обязательного remaining scope не выявлено.

| Исходный факт/ограничение | Где сохранено и как трактуется |
|---|---|
| Ограниченный server-only multi-quantity срез, исторические HEAD/preimage/uncommitted state; не Android/Play/full10 (оригинал:3–16) | Evidence inventory:82–88 отдельно фиксирует более поздний commit `c7742584` в HEAD с ранее проверенной ancestry и совпадением6productionSHA. Старое «uncommitted» не выдано за нынешний статус. Это чтение Git/исходников, не runtime acceptance. |
| Строгий single-line V2/V1 SKU/order/account/original quantity binding, >1gold требует proof, monotonic refundable quantity (оригинал:54–60) | PRD:391,409; addendum:41; consolidated addendum:61,63. Неоднозначная/pending/неподтверждённая покупка не получает товар; RTDN не monetary command. |
| Cumulative partial/full, additive schema1→2, immutable receipt/audit/ledger, legacy full key; premium quantity>1 unsupported (оригинал:61–65) | PRD:409,451; consolidated addendum:61–63; evidence inventory:82. Старые записи не объявлены свежими verification receipts. |
| RTDN только запускает current verification, partial remainder требует authoritative PURCHASED, full cancellation отдельный путь (оригинал:76–78) | PRD:391,409; consolidated addendum:61–62; неоднозначный исход pending/repair. Точные исторические provider details остаются в исходнике/extract, не объявлены заново проверенной официальной политикой магазина. |
| 48 targeted PASS, legacy runners, plan-only harness, первый harness-only FAIL, второй SDK fake-Play PASS, red logs/private retained evidence (оригинал:18–50,80–125) | Evidence inventory:84 сообщает исторический48/SDK fake/rules и позже86suites со source drift157/162+6new. PRD:471,490,494–515; consolidated addendum:70–75 запрещают суммирование старых PASS, удаление red logs, подмену выполнения SHA/plan-only. Детальная историческая хронология и manifest/harness границы доступны через evidence inventory:98–104 → source-extract-commerce. Их не требуется превращать в новый универсальный13-path test quota. |
| Known credited old ACK replay не доказывает real historical ACKed/consumed purchase, unknown consumed требует proof/repair (оригинал:129–133) | PRD:409; consolidated addendum:62; evidence inventory:86. Безопасный известный replay не принят за все legacy recovery. |
| Android/Billing/UI/restore/account/reply loss/update, realPlay/ACK/refund/PubSub и остальные premium/HTTP/rules/fullgate сценарии тогда не выполнены (оригинал:134–144) | PRD:397–409,494–515,530–533; evidence inventory:84–94. Более поздние исторические rules/fullgate доказательства явно отделены; весь нынешний HEAD не объявлен их PASS. Isolated Billing с disabled recovery оставлен незакрытым. |

## Минимальные исправления

Не требуются для данного входа. Не следует возвращать устаревшее «full functions gate никогда не запускался»: в исходном отчёте его нет, но более позднее историческое доказательство существует и корректно ограничено manifest drift. Не следует объявлять real Play недоступным без фактической проверки; это внешняя зависимость будущей разрешённой приёмки.

## Открытый исходный scope

Текущая end-to-end Play/legacy/premium/refund/client/device acceptance остаётся обязательной. Реальная Console quantity policy не выводится из кодовой поддержки multi-quantity gold. Technical gaps отдельного ТЗ-10 перечислены в `reconcile-10-payment-and-dependency-hardening.md` и не превращают положительное ограниченное историческое10A evidence в отрицательный результат.

## Снимок сверенных документов

- prd.md: SHA-256 8a2e57611bf0ff844999ea7b665b67320fa33a6637f67400fcfc42d50fa311d7
- addendum.md: SHA-256 c3bbdde5f371cb0a112cdafa4569176b7e5116612461b32c33dd3cc4c86745fd
- prd-consolidated-addendum.md: SHA-256 b527705569a3a8f09b83bf9f5eac0f8772e414adb3751ecbac1979702a6fe1cb
- evidence-inventory.md: SHA-256 27339c262c18bfff37f10a020f2585b9ff59500c3348339c8bfadbeb8b2a5a89
