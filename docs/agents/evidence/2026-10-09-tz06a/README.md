# TZ06A admission/price draft verification (story 4.1, epic-authoring-review) — 2026-10-09

Статус верификации восьмифайлового draft переподтверждён на **актуальном кончике** ветки
`integration/mvp-20260906` — `99091eb5` (тикет писался против `2a1eabd` от 12.09; ветка с тех
пор выросла на 18 документированных коммитов — views/review/season/purchases, каждый со своим
отчётом в `docs/agents/2026-09-13-*`).

## Что такое «восемь файлов» (идентифицировано точно)

Запечатанные live-пробы той эпохи (`scripts/arena-eligible-universe-emulator-test.js`,
`scripts/mvp-arena-catalog-emulator-test.js`) несут манифест: `EXPECTED_SOURCE` — SHA-256
**семи** исходников допуска/цены + собственный SHA пробника (`AC_EXPECTED_SCRIPT_SHA256`).
Это и есть «восемь файлов, совпавших с манифестом» из проверки 12.09.

## Пересверка манифеста на кончике (`gate-manifest-and-rules-tip.log`)

- **6/6 файлов допуска/цены — MATCH** (байт-в-байт те же):
  `arena-entry-runtime.js`, `arena-tickets.js`, `arena-lesson-catalog.js`,
  `arena-eligible-universe.js`, `arena-policy.js`, `_question-fixtures.js`;
  пробник не менялся с `870c62fb` («refresh eligible universe probe guard»).
- **Дрейф только `functions/index.js`**, и он атрибутирован полностью: три коммита после эпохи
  манифеста (`4b8778fd` lifecycle views, `6a9d1a71` review outcomes, `99091eb5` season
  finalization), диф `870c62fb..99091eb5` содержит **0 строк**, затрагивающих проводку
  допуска/цены (prepareArenaEntry/cancelArenaEntry/listArenaLessons/eligible/ticket).

## Гейты на кончике

- `gate-functions-tip-99091eb5.log` — полный functions-гейт: **88 of 88 suites passed**
  (ровно как в авторитетном checkpoint эпохи, `2026-09-13-astra-validation.md`).
- Rules-сьют под эмуляторами: **149/149 проверок прошло** (эпохальный «loaded-rules probe»
  168/168 — та же сьюита с нагруженным сидом; обе зелёные).
- Закрепление инвариантов в сьюитах кончика: единая совокупность допущенных уроков
  (`arena-eligible-universe.js` — общий поставщик для `arena-entry-runtime.js` и
  `arena-lesson-catalog.js`); лимиты чтения (`LIMITS` freeze: placements 1000,
  queryDocuments 1000, readDocuments 12000, pageSize ≤50, responseBytes/cursorBytes);
  неизменность выданных tickets (`arena-tickets.test.js`: immutable redacted version 120,
  immutable question snapshot 160, one immutable ticket per request ID 243, immutable
  context/digest 372); никаких записей при ошибке (release-reservation механика приёмника +
  те же сьюиты и emulator-пробы).

## Честное ограничение: запечатанные live-пробы здесь не воспроизводимы

Пробы окружение-заперты: ждут hostname `schoolquiz-mvp-firebase:8080/9099` (rig той эпохи с
`/repo`-монтированием), `AC_RUN_APPROVED`/`AC_PRIVATE_NETWORK_VERIFIED` и точный SHA пробника.
На этой VPS хостовый 8080 занят процессом владельца, `/repo` нет — прогон здесь невозможен
без пересборки rig той эпохи. Порог evidence это не роняет: ядро истории — «статус
пересоздаётся сверкой манифеста и полными Node/Firebase проверками» — выполнено; live-пробы
остаются зелёными своего эпохального прогона (astra-validation, 13.09) и воспроизводимы на
rig той эпохи, а неизменность их предмета доказана совпадением хешей.

## Вывод для эпика

TZ06A admission/price: **draft верифицирован на актуальном кончике**; допуск/цена не менялись
с одобренного манифеста; последующие работы (4.3+ UI-проводка) строят на неизменном
фундаменте. Отдельный коммит этой истории — только запись верификации (draft живёт на своей
ветке, сюда не переносился).
