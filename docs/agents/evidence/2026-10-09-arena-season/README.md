# 3.12 arena season finalization (story 3.12, epic-arena-completion) — 2026-10-09

История: «Quarterly season settles once from the complete eligible input: UTC quarter,
adopted minimum/tie/no-winner rules, resumable single award, competitive finalize; §4
season fixtures (19/20 distinct pasts, quarter change, tie, no winner) green; published
winner/award immutable». Аудит: **финализационный контур существует целиком** (включая
resumable-ревизию 99091eb5 «make season finalization resumable») и закреплён тестами в
реестре раннера; каждое §4-условие — зелёный тест.

## Чек-лист истории → закрепление (всё существовало, ссылки)

1. **UTC-квартал** — `SEASON_NOT_ENDED` до границы квартала, финализация ровно на Q2:
   `functions/arena-service.test.js:352` («ended UTC season finalizes frozen Arena
   membership after lesson moved away; award ten once per quarter»); тайминг-правила
   (прошедший/преждевременный/будущий квартал) — `functions/arena-policy.test.js:198-207`.
2. **Минимум 19/20 различных прошлых** — 19 различных голосовавших (20-я — повтор того же
   пользователя) → `winner: null`, `arena_awards/` пуст, начислений нет:
   `functions/arena-service.test.js:385` («season cannot be finalized by a player and 19
   distinct ratings award no boxes»).
3. **No-winner / tie** — tie-break правил принятой редакции: счёт → число голосовавших →
   старая публикация → стабильный lessonId: `functions/arena-policy.test.js:192`
   («season winner tie-breaks are score, voter count, older publication, stable lesson
   ID»); нет победителя — нет награды (см. п.2).
4. **Resumable single award** — `PENDING → drainSeason → PUBLISHED` с курсором/листом
   воркера (`functions/arena-service.test.js:401` «resumable finalizer handles a source
   page with repeated attempt data and preserves the canonical latest vote»); воркер:
   пейджинг финализирующихся сезонов, курсор с возобновлением и обёрткой без скана
   терминальных, единственный lease, бюджет времени не расширяется вызывателем,
   батчи снимают минуту-на-страницу для когорт >501
   (`functions/arena-season-worker.test.js:68-114`).
5. **Competitive finalize** — вызываемая финализация только резервирует сезон и читает
   урезанный статус: второй конкурентный вызов `REJECTED/SEASON_ALREADY_FINALIZING`,
   повторное использование mutationId огорожено `MUTATION_REUSE`, игроку —
   `CURATOR_REQUIRED` (`functions/arena-service.test.js:352, 385`; статус-проекция без
   курсора/листа/исходных строк — :459).
6. **Published winner/award immutable, оседание единожды** — повтор финализации
   завершённого сезона отвечает глубоко идентичной копией опубликованного результата
   (`repeat.result deepEqual before`, :352), ровно одна строка `arena_season` в журнале,
   boxCount точен (50 = 5 старых + 15 + 20 + 10); карантин не перезаписывает
   конкурентно-терминальный сезон (:519), отсутствующее/битое состояние — карантин без
   квитанции и награды (:472); голос сезона иммутабелен одной транзакцией (:193).

## Гейты

- Все перечисленные сюиты — часть постоянного functions-раннера: **92 of 92 suites
  passed** 09.10 (прогоны истории 7.7,
  `docs/agents/evidence/2026-10-09-purchase-provenance/gate-functions*.log`); `functions/`
  с тех пор не менялась (коммиты дня касались scripts/фикстур/документации) — гейт
  действует на текущее дерево.

## Scope notes

- Сезонные фикстуры манифеста (`scripts/demo-fixtures/manifest.json → seasonFixtures`)
  остаются несеянными в демо-бэкенде: node/server-половина истории доказывается
  синтетическими акторами сюит; засев сезонного состояния в демо нужен только
  device/worker-строкам с управляемыми тиками и выполняется при их диспетчеризации.
- «Один ticket на двух устройствах», «поздние ответы» и прочие device-строки матрицы —
  не в этой истории.
