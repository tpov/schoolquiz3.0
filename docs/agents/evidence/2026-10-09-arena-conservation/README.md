# 3.11 arena price/rating conservation (story 3.11, epic-arena-completion) — 2026-10-09

История: «Prices and ratings use the full eligible set (no truncated page) with fractional
author shares conserved: prices 1 and 100; 5 payments of 1 credit the author 4 total;
operation retry has no second effect (plan §4 деньги; AD-13/AD-9); node/server conservation
tests green in the runner registry». Аудит показал: **консервационный контур существует
целиком и закреплён тестами в раннере**; историей дозакреплена живая привязка к
демо-фикстурам (цены 1 и 100 в полном юниверсе — верификатор из серверной половины 3.3).

## Чек-лист истории → закрепление (всё существовало, ссылки)

1. **Цены по полному допустимому набору, без усечённой страницы** — юниверс читает
   размещения с жёсткими бюджетами (`functions/arena-eligible-universe.js` LIMITS:
   placements 1000, queryDocuments 1000, readDocuments 12000) и при переполнении
   отказывает целиком (`UNIVERSE_TOO_LARGE`), а не молча ценит подмножество;
   усечённый запрос и порча рейтинга роняют чтение
   (`functions/arena-eligible-universe.test.js:71` «truncated placement query and corrupt
   rating fail closed», параллельный загрузчик не переподписывает счётчик чтений — :92).
2. **Цены 1 и 100** — относительная шкала `assignLessonPrices` (минимум → 1, максимум →
   100, промежуточные уровни 1/51/100/1, ничьи схлопываются в один уровень, пустой набор
   → пусто, кривая статистика → отказ): `functions/arena-policy.test.js:101-123`.
   Живая привязка: банки манифеста `scripts/demo-fixtures/manifest.json` держат 1 и 100 в
   полном 15-строчном юниверсе демо-бэкенда — `scripts/demo-fixtures/verify-demo-fixtures.js`,
   лог `docs/agents/evidence/2026-10-09-arena-fault-rig/fixtures-verification.log`
   (серверная половина 3.3).
3. **«5 оплат по 1 дают автору суммарно 4»** — пятинный fractional escrow
   (`splitAuthorRoyalty`: authorCarryFifths/sinkCarryFifths/reservedNolics, инвариант
   «платёж + открытий резерв = автор + раковина + резерв» на каждом шаге):
   политика — `functions/arena-policy.test.js:211` («five one-nolic payments pay author
   four, sink one, and clear the conserved reserve», authorCredits [0,1,1,1,1],
   reserves [1,1,1,1,0]); путь оседания тикета — `functions/arena-entry-runtime.test.js:132`
   («five one-nolic accepted runs conserve author four, sink one, with separate independent
   holds», totalPayments 5 / totalSink 1 / reserved 0 в arena_royalty_accounts/author).
4. **Доли консервированы на любых разбиениях и границах чисел** —
   `functions/arena-policy.test.js:231` («partition-independent and safe at
   MAX_SAFE_INTEGER»: Σавтор = 4/5·Σ, Σраковина = 1/5·Σ, хвост — в резерве); кривые
   входы (дробные/отрицательные/переполнение) и несогласованный резерв — отказ.
5. **Повтор операции без второго эффекта** — на пути оседания: повторный submit той же
   попытки/билета отклонён `already-exists`
   (`functions/arena-entry-runtime.test.js:111, 166`); замороженная форма тикета переживает
   публикацию, random-завершение роялти не платит
   (`functions/arena-tickets.test.js:400-416`); на транспорте — доказано серверной
   половиной 3.3 (bca0a086): потеря ответа после ACK + повтор того же mutationId отвечен
   `{replayed: true}` без второго эффекта.
6. **Бухгалтерия роялти на тикетах** — отдельные независимые холды, сходящиеся итоги
   (`functions/arena-tickets.test.js:389, 441` — totalSinkNolics, резервы по шагам).

## Гейты

- Тесты пунктов 1–6 — часть постоянного functions-раннера (`node run-tests.js`,
  реестр сборок): **92 of 92 suites passed** 09.10 (гейты истории 7.7 этого же дня,
  `docs/agents/evidence/2026-10-09-purchase-provenance/gate-functions*.log` — тот же
  прогон, консервационные сюиты в него входят).
- Живая привязка цен — `fixtures-verification.log` (VERIFIED 1 и 100).

## Scope notes

- Серверная проверка производится на синтетических акторах внутри сюит; демо-фикстуры
  манифеста дают живую линию цен для матричных строк (3.4+) и воспроизводимость.
- Сезонная финализация (полный проверенный состав, без усечения каталога, однократные
  награды) — отдельная история 3.12; существующие arena-season-finalization.js /
  arena-season-worker.js не перекрывались этой историей.
