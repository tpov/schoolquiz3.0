# 3.2 arena-online-o2-adapter — 2026-10-10 ночь

Story: «Arena online HARD next-question transitions adopt the shared ONLINE-O2 delivery/ready-ACK
contract (AD-31): original deadline continues through disconnect, captured window config survives
retry, no second answer clock, no unbounded gap after lost ACK; contract conformance proof per
transition». Оба `after` закрыты к началу смены: контракт принят владельцем 2026-10-08 (2.9) и
доказан живьём (2.6/2.7), первый полный проход арены сделан на риге (3.1).

## Что построено (клиентская половина контракта)

| Файл | Что несёт |
| --- | --- |
| `domain/model/QuestionWindowModels.kt` | проводные факты окна (запрос/вид/вердикт) + типовые отказы; ни одного клиентского времени-источника deadline |
| `domain/logic/ArenaOnlineO2Adapter.kt` | чистый адаптер перехода: детерминированный запрос из замороженного снапшота (после последнего вопроса попытки окна нет — §3), identity-сверка прежде фактов (AD-6), одноразовое принятие серверного deadline через легальный PAUSED→ANSWERING, reconfirm без перезапуска часов (§7), решение о закрытии по истёкшему окну без ready-ACK и без часов |
| `data/remote/QuestionWindowRemoteDataSource.kt` | граница явных вызовов (без авто-ретраев и локальных записей) |
| `data/remote/QuestionWindowWireParser.kt` | строгий разбор форм 2.6: лишний/недостающий/вне-диапазона/противоречивый ключ — malformed, не факт |
| `platform/firebase/arena/FirebaseQuestionWindowRemoteDataSource.kt` | callables `beginLessonQuestionWindow`/`fetchNextLessonQuestion`, сессионные заборы (включая A→B→A), типовой маппинг кодов приёмника; DI в `FirebaseModule` |

Офлайн/подготовленные пути арены не тронуты (контракт §1/§9: «офлайн-сценарии ordinary/Arena не
становятся онлайн»; CAP-1: готовый вопрос без сетевого гейта). Адаптер встаёт рядом с
существующими переходами рановера и ничего в них не меняет; его вызов — дело онлайн-рантайма
арены, которого в сборке ещё нет (граница записана в 2.7; device-строки 3.3 device-половина/3.4+
остаются где были).

## Гейты (sgw ./gradlew, логи рядом)

- `:shared:feature:lesson-runner:domain:jvmTest` — зелёный; новые `ArenaOnlineO2AdapterTest`
  (9 кейсов, по одному на обязательство заголовка) + вся прежняя сюита.
- `:shared:feature:lesson-runner:data:jvmTest` — зелёный; новые `QuestionWindowWireParserTest`
  (7) и `ArenaOnlineO2ReadyAckIntegrationTest` (1: провод → вид → чекпоинт → строка ready-ACK →
  квитанция; половины 2.6 и 3.2 сходятся в одном серверном факте, не в двух часах).
- `:platform:firebase:testDebugUnitTest` — 268 тестов, 2 отказа = **предсуществующие** красные
  fixture-pin (`…CatalogRemoteDataSourceTest.generatedPublicContract…`,
  `…TicketRemoteDataSourceTest.fixtureHashes…`), атрибуция 2026-10-08
  (`docs/agents/evidence/2026-10-08-client-terms-attach/`, `…-functions-contract-drift/`),
  `functions/` этой историей не менялся. Новые 8 тестов источника окна зелёные.
- ktlint: все файлы истории чисты. Предсуществующие нарушения вне истории (файлы 2.6
  `ReadyAckOutboxCodec`/`ReadyAckReceiptParser`, `StartLessonAttemptUseCase`) не тронуты — на
  владельца вместе с вопросом о включении ktlint этих модулей в постоянную матрицу гейтов
  (модульные ktlint-задачи вне корневого прогона эти файлы уже сегодня не пропускают).
- detekt: `:shared:feature:lesson-runner:domain:data` зелёные; `:platform:firebase:detekt` падает
  на HEAD без моих изменений (`Invalid value (27) passed to --jvm-target` — toolchain-аплифт
  42a7cfaa против потолка detekt-плагина 21) — на владельца.

## Живой прогон через реальный приёмник (demo-backend)

`arena-online-o2-live-run.js` (снапшот рядом; живёт в `scripts/demo-fixtures/`) — scripted client
по образцу 2.7/3.3b: хостовой functions-эмулятор 5001 против реальных Firestore/Auth
демо-контейнера, владелец = риг-плеер ck3DaiqEd…, попытки = **реальные арены 3.1**
(cf575a94… — основная, 3da6a7c3… — ветвь истечения; обе SETTLED, урок-банк цены 1,
`ac_0ecb34…lesson_1`, HARD, 2 вопроса). Приёмник окна ключуется (attemptId, index) и состояния
попытки не читает — граница демо-каркаса, записана честно. Пишутся только новые документы
`lesson_question_windows` и один новый ключ READY_ACK в `mutation_keys`; пинованные фикстуры не
переписываются.

Факты прогона (`window-docs-and-ack-record.json`, `live-run-reentrant-pass.log`):

1. begin: окно `win_bf1bfa513f5c`, захваченная конфигурация 60 с + allocatedSeconds 14
   (серверное содержимое q2), открыто серверным моментом;
2. выдача: часы = issuedAtMs 1791578209904 + 14 000 = answerDeadlineMs 1791578223904;
3. повтор запроса (потерянный ACK): тот же вопрос, **тот же deadline**, остаток только убывает,
   документ окна хранит ровно одну выдачу (`delivery` байт-в-байт между чтениями);
4. ready-ACK ключом клиентского кодека
   (`lesson_runner-READY_ACK-cf575…-q1-window-win_bf1bfa513f5c`): ACKED, квитанция несёт факты
   ВЫДАЧИ (не тела ACK), в `mutation_keys` одна completed-запись;
5. повтор ключа: `replayed:true`, результат байт-в-байт тот же, запись не меняется;
6. чужое окно в ready-ACK: 400 «ready-ack identity does not match the window», ключ освобождён
   (записи нет);
7. после последнего вопроса: 400 «no next question: the completed question is the last of the
   pool» (последний вопрос и попытки, и пула совпали на двухвопросной фикстуре);
8. окно второй попытки без выдачи: WINDOW_EXPIRED → повтор → снова WINDOW_EXPIRED (стабильно,
   без неограниченного gap), документ EXPIRED/closedReason WINDOW_EXPIRED, поздний ACK — 400
   «next question was not delivered».

Прогон ре-энтрантен и в записи — второй полный проход поверх окон первого: begin вернул то же
окно в DELIVERED, fetch — ту же выдачу с остатком 0, ACK — ту же квитанцию с replayed:true,
окно истечения — стабильно EXPIRED. Идемпотентность §3/§7 наблюдалась живьём дважды: и как
«повтор внутри окна», и как «повтор визита после истечения».

## На владельца (вне этой истории)

1. Предсуществующие красные fixture-pin тесты `:platform:firebase` (атрибуция 2026-10-08) —
   регенерация капчур-фикстур требует процедуры владельца.
2. `:platform:firebase:detekt` на HEAD: `--jvm-target 27` вне поддерживаемого плагином диапазона
   (после toolchain-аплифта 42a7cfaa) — нужен бамп detekt или фикс цели.
3. ktlint-нарушения в файлах 2.6 и `StartLessonAttemptUseCase` (модульные ktlint-задачи этих
   модулей не входили в матрицу ночных гейтов) — починка или явное исключение.
4. Онлайн-драйвер арен-переходов в сборке отсутствует (продуктовый рантайм онлайн-HARD) —
   приёмниковая половина и клиентский адаптер готовы; on-screen онлайн-режим — будущие истории
   (как записано в 2.7), устройство для него не тратилось: риг на эту смену не поднимался.
