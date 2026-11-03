# 4.6 independent-testing-review-threshold — evidence (2026-10-10)

История: одно независимое TESTING-ревью на каждый вопрос ревизии допускает контент на
настраиваемом серверном пороге (по умолчанию 2 из 1..3: 1 валит, 2 и 3 пропускают); результат
привязан к применённой ревизии конфига и поколению заданий; проваленная стадия названа по имени
без обязательной причины; переводные решения по сегментам явные (unchecked/correct/error),
текст без содержания никогда не засчитывается; автор видит по-вопросные исходы.

Смена 9 (день, прервалась) сделала код (c1012e76 диспетч, fdf4d469 порог+биндинг, 48581283
сегменты, 478e5384 клиентский провод, 82364e5a reviewer UI + авторский парсер, c8eb0169
journey-тул, 8c4fcf2a фикс FlowRow на карточке очереди) и начала живой прогон на риге
(16:25–16:49, дампы `work-ui/rig-46…58`), но оборвалась до evidence. Эта смена
реконструировала прогон по серверному состоянию и дампам, добила journey, нашла и починила
регрессию влития перевода, собрала гейты и оформила файлы.

## Участники (demo-backend, роль-фикстуры по plan §2 Шаг 4)

- автор `GxeLc2TZJ9U4ZHTAPdS4YzKpWx87` (UserKPWX87) — игрок рига 4.5, автор заявки
  `arena-submission-zq8lz01icnm0` (урок TZ06ELesson, 2 вопроса FILL_BLANK, targetShelf arena);
- ревьюер на риге `AywWqYS6zz0dmspt6lOUcT28BUFJ` (User28BUFJ, adminLevel 100) — НЕ автор;
  уровень TESTING = max(tester, admin, developer) = 100;
- второй ревьюер (скрипт) `Vl7vHHcQgZMVvPpLsMTdU9pQvMW2` (testerLevel 100) — НЕ автор;
- переводчик `Xl4tJ0urney46Translator00000a` (translatorLevel 100, knownLanguages ru+en) и
  сегментный ревьюер `Xl4tJ0urney46SegReview000000b` (translatorLevel 200 — зазор
  TRANSLATION_REVIEW_LEVEL_GAP=100 над уровнем перевода), созданы этой сменой;
- сервер: контейнер schoolquiz-demo-firebase-20261004 (auth 19090, firestore 18090) + хостовый
  functions-эмулятор на 5001 (код index.js не менялся с 15:49 — 48581283 до старта эмулятора;
  перезапущен этой сменой после фикса, см. «Инцидент перезапуска»).

## Живой reviewer flow на риге (hitl, смена 9, дампы rig-46…58)

Риг redroid (schoolquiz-emu), app 0.1.0-isolated под ревьюером User28BUFJ: Home → drawer →
Review → очередь с карточкой заявки TZ06ELesson (rig-51…55) → Testing-деталка с ПО-ВОПРОСНЫМИ
пикерами 1/2/3 (rig-56) → выбраны 1 и 1 (rig-57) → Submit → «Decision waiting to be sent»
(rig-58, решение в outbox) → слив 16:48:34. Скриншоты чёрные (FLAG_SECURE) — доказательства с
экрана = дампы uiautomator. Ревью ушло через аутбокс приложения под ключом
`quest_authoring-SUBMIT_REVIEW-review-action-if7ye4vg6i2c` (replayed:false) — квитанция
сохранилась в Room рига (`rig-room-settle-extract.txt`). Сервер принял запись (acceptedByServer
true): независимость (≠ автор) проверяет приёмник.

## Порог виден на смешанных оценках (живой прогон)

- 16:48:34 — rig-ревью: по-вопросные 1,1; 16:49:42 — скриптованное 3,3 (тул
  `review-questions`, прямой callable). Средние по каждому вопросу (1+3)/2 = **2.0** — ровно
  порог → `passed: true`, `isTested: true`; агрегат в задании: `testingScore: 2`,
  `appliedConfigRevision: 1`, `assignmentGeneration: 1` (`run/00-initial-docs.json`).
  Одиночная запись 1,1 держит вопросы НЕ пройденными (среднее 1 < 2) — «1 валит» доказан
  арифметикой записей и юнит-сюитой («score 1 under the default threshold fails TESTING»).

## Настраиваемый порог + привязка результата (живой прогон, `run/01…04`)

Конфиг `configs/arena_review` писался journey-тулом (это и есть конфигурационный акт):

1. `01` — {testingPassScore: 3, requiredLanguages: [en], configRevision: 2} → TESTING 3,3:
   средние (1+3+3)/3 = 2.33 < 3 → все вопросы `passed: false`, `isTested: false`,
   **appliedConfigRevision: 2** — те же записи, вердикт перевернулся решением владельца;
2. `03` — {testingPassScore: 2, requiredLanguages: [en], configRevision: 3} → TESTING 3,3:
   средние 2.5 ≥ 2 → все `passed: true`, **appliedConfigRevision: 3**.

Каждый агрегат едет вместе с ревизией конфига и поколением заданий: смена порога посреди цикла
не переписывает задним числом принятые решения. Число вне 1..3 громко отказывает всем
ревью-путям (сюита: «out-of-range config threshold refuses loudly»).

## Цикл ревью (живой прогон, `run/05…13`)

- `05` LOGIC User28BUFJ score 2 → `isLogicReviewed: true`, `logicScore: 2`, published **false**
  — перевод en обязателен (requiredLanguages [en], ревизия 3). Проваленная стадия названа по
  имени: `failedStages` заявки, без чьей-либо причины (причина — атрибут только отклонения).
- `07` TRANSLATION en с ПУСТЫМ текстом одного вопроса — принята, но язык НЕ засчитан
  (`translatedLanguages: {}`): «незавершённого перевода не существует», текст без содержания
  никогда не одобряется.
- `09` TRANSLATION en полная → `translatedLanguages: {en: 100}` (уровень переводчика ≥
  REVIEW_PASS_LEVEL 100), `isTranslationReviewed: true` → **PUBLISHED** (`10`,
  publishedAtMs 1791652697916 = 17:18:17 UTC, урок version 1, reviewApprovedAtMs).
- `11` TRANSLATION_REVIEW с явными решениями correct/error по сегментам (у задания к этому
  моменту 4 вопроса: 2 исходных + 2 влитых `__en`-варианта) → `translationScore: 50`;
  сегмент, отсутствующий в записи, остаётся unchecked — ничего не хранит одобрения.
- `12` молчаливый `accepted: true` без решения — провод отказал 400 «requires an explicit
  decision "correct" or "error"» (старый провод запрещён).
- `13` автор читает вердикт живым `listMyQuestReviewSubmissions`: status PUBLISHED,
  failedStages [], questionOutcomes [] — на терминальном исходе незавершённого нет. Пока
  заявка UNDER_REVIEW, автор видит поименованные стадии и по-вопросные исходы: они лежат в
  документе заявки и отдаются этим же view-callable (`00-initial-docs.json`: failedStages
  ["LOGIC"], questionOutcomes обеих вопросов с оценками и порогом).

## Регрессия, найденная живым прогоном, и фикс (`9999bd17`)

TRANSLATION вливает переведённые варианты (`__<lang>`, mergeQuestions) в состав задания;
per-вопросные TESTING-исходы (код 4.6) судили и их — вопросы-варианты никем не оценивались,
`isTested` молча переворачивался в false и блокировал publish-путь required-languages.
Фикс: `testingQuestionOutcomes` судит только исходную ревизию (`task.sourceLanguages`;
вопросы без языка остаются — легаси, чужие языки не являются вопросами ревизии); закреплено
новым кейсом сюиты (полный цикл TESTING→LOGIC→TRANSLATION держит исходы на q1/q2, isTested
true, язык засчитан). functions 94/94.

## Авторская половина на риге (settle вердикта)

Авторский auth-store восстановлен из бэкапа смены 9 (`auth-store-UserKPWX87-backup.xml`),
SYNC NOW 17:24 («UserKPWX87», «Last sync: 10.10.2026 17:24», `rig-59…61`). Room-строка
черновика TZ06Equest: `activeSubmissionId` очищен (вердикт потреблён), `rejectionReason: null`,
`updatedAtMs` = момент публикации 1791652697916 (последующий контент-синк переписал статус из
приватного дерева сервера — выжимка `rig-room-settle-extract.txt`). Экран «My lessons:
statuses»: «Arena royalties: 0 nolics», строк урока нет — **by design до кураторского
перехода**: placement пишет setLessonPlacement, а не публикация (lesson_placements автора =
0; lessons/draft-lesson-to3wvgtrw62p version 1 существует). Данные пережили ещё один рестарт
SystemUI (~17:2x, «Phone is starting…»).

Граница (честно): по-вопросные исходы автору отдаёт серверный view и парсит клиентская модель
(82364e5a), локально settle потребляет только терминальные вердикты — отдельного экрана
рендеринга failedStages/questionOutcomes в UI пока нет (UX-хвост для следующих историй,
не дыра серверного контракта). На риге вердикт приехал при активном ревьюере, поэтому
авторский экран поймал терминальный settle, а не промежуточный по-вопросный список.

## Гейты (только sgw для gradle; functions — node)

- functions node: **94/94 сюит** (`gate-functions-node.log`);
- JVM quest-authoring domain + data + presentation: **BUILD SUCCESSFUL** (`gate-jvm.log`);
- firebase: 272 теста, ровно 2 предсуществующих fixture-pin отказа
  (FirebaseArenaLessonCatalogRemoteDataSourceTest, FirebaseArenaTicketRemoteDataSourceTest —
  атрибуция 2026-10-08, регенерация капчур-фикстур — процедура владельца; `gate-firebase.log`);
- ktlint: модули истории зелёные после `297af3ba` (мой файл 478e5384 содержал один перенос
  строки — исправлен); полная проверка модулей красная на файлах 4.3-эры (FirebaseModule,
  FirebaseLessonLifecycleRemoteDataSource — 1db295c4/8b125e61; DefaultCuratorComponent,
  DefaultAuthorLifecycleComponent — 8b125e61; конфигурация ktlint с тех пор не менялась) —
  вне матрицы истории, список владельца (`gate-ktlint.log`, `gate-ktlint-story.log`);
- detekt: предсуществующий красный плагина (--jvm-target 27 не поддерживается) — список
  владельца (`gate-detekt.log`).

## Инцидент перезапуска functions-эмулятора (честно)

Первый перезапуск этой смены был сделан БЕЗ FIRESTORE_EMULATOR_HOST/FIREBASE_AUTH_EMULATOR_HOST:
один вызов submitReviewAction ушёл «в продакшн» (metadata 169.254.169.254), повис и умер по
таймауту 60s (ECONNRESET, `09-translate-full-en.log` первой попытки перезаписан успешной).
Записей не было ни в демо, ни вовне: вызов упал до первого чтения Firestore. Эмулятор
перезапущен с правильным окружением — все последующие вызовы работали с demo-backend.

## Состояние после прогона

- `configs/arena_review` оставлен в финальном состоянии journey: {testingPassScore: 2,
  requiredLanguages: [en], configRevision: 3} — документированное решение конфигурации, а не
  дефолт; заявка PUBLISHED, задание PUBLISHED, записи ревью — 6 TESTING/LOGIC/TRANSLATION +
  1 TRANSLATION_REVIEW.
- Риг остановлен после блока проверок (docker stop schoolquiz-emu); demo-backend не тронут
  (постоянное решение владельца). Файлы владельца (.maestro/**, local.properties) не менялись;
  gradle.properties остался в состоянии более ранних смен (список владельца).
