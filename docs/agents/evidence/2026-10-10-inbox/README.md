# 4.9 inbox-private-error-reports — evidence (2026-10-10/11)

История: приватные отчёты об ошибке урока и публичные комментарии сходятся в общий inbox (AD-20);
приватный текст касается всего урока и не может переоткрыть ни вопрос, ни ответ (таких полей
приёмник не принимает); отчёт доходит до разрешённого автора; круг читателей каждой строки
назван словами; роль ревьюера/куратора сама по себе права читать чужой отчёт не даёт; публичное
обсуждение живёт отдельно; rules-тесты + device proof.

## Хронология смен

**Смена 12 (прервалась ~01:20):** сервер и клиент 4.9 целиком — 619fe796 (lesson_report.
SUBMIT_ERROR в реестре мутаций: строго три поля, адресат из документа размещения, окончательный
отказ для необработанных уроков, doc-exists replay; listInboxItems — проекции только по
lessonAuthorUid/плейсментам автора, без reporterUid и полей вопросов; rules закрыли
lesson_error_reports наглухо, +7 проверок раннера) и b83c20dd (клиентский транспорт
SUBMIT_LESSON_ERROR, InboxRepository/UseCase'ы, InboxScreen с бейджами читателей, «Report an
error»-диалог в detail ревью-очереди, drawer-секция Inbox). Гейты собраны (e25af346: functions
97/97, rules 200/200, firebase JVM 275). Риг поднят, APK 8896fa21 установлен, journey-тул
заложен (8c112e29), демо-бэкенд отремонтирован (73e04e8b). UI-фаза: curator-сессия (rig-00…30 —
ревью-очередь и curation живьём, baseline «curator inbox 0»), затем fresh-install и
бутстрап нового аккаунта (rig-31…36) — оборвалась на «My lessons: statuses».

**Смена 13 (эта):** реконструкция по дампам + серверу, доведение journey до конца. Ключевая
находка: «UserYXJZY9» из дампов прерванной смены — это **ник профиля, а не auth-uid**; фактический
uid риг-сессии — `34M7XwdduXxbpYcmiP7BPqyXjZy9` (auth-store приложения, профиль создан
приложением в 01:20 с nickname UserYXJZY9). Фикстуры адресовались по нику и были перенацелены на
настоящий uid (placement/R2-отчёт), ложный профиль profiles/UserYXJZY9 удалён; роль ревьюера
(adminLevel 100, прецедент bootstrap-curator тула tz06c) выдана настоящему uid. Тул расширен
сеянием фикстур и inbox-поверхностями снапшота (749a21aa), в seed-comment починен
предсуществующий баг задвоения хоста в REST-URL.

## Участники (demo-backend: контейнер schoolquiz-demo-firebase-20261004, auth 19090 / firestore 18090, хостовый functions-эмулятор 5001, socat-мосты 8080/9099→контейнер, 5001→хост)

- риг-игрок/автор/ревьюер `34M7XwdduXxbpYcmiP7BPqyXjZy9` (nickname UserYXJZY9, ANONYMOUS,
  adminLevel 100 — роль demo-фикстура);
- куратор-адресат `uwNZMpyUKJoOKJEOcpH7KO44E1Dx` (User44E1DX, adminLevel 100) — живьём не
  заходил, читает inbox реальным view-callable через тул;
- сторонний комментатор `cur48r3player0000000` (RigPlayer48) — публичный комментарий по
  rules-легальной поверхности;
- APK рига: 0.1.0-isolated, sha256 8896fa21… (`run/apk-sha256.txt`), данные сохранялись с
  session-open, fresh-install прерванной смены (rig-33) пересоздал сессию приложения.

## Фикстуры блока (data-level, сеяние тула, оракул их видит как добавления)

- `ibx1_*` — цепочка «Inbox journey lesson one» (catalog/quest/section/theme/lesson + 2
  SingleChoice-вопроса + placement home), **автор — куратор**; на ней открытое задание
  `admin/review/lessons/ibx1_lesson` (UNDER_REVIEW, checks пусты → TESTING открыт,
  assignmentId `ibx1sub_ibx1_lesson`) + запись журнала `admin/review/sync_changes`;
- `ibx2_*` — цепочка «Inbox journey lesson two», **автор — риг-игрок** (адресат второй стороны);
- R2 `ibxr2curator01` — приватный отчёт куратора на lesson two, поданный scripted-пробой
  (replay-as = реальный submitMutation с собственным ключом; `replayed:false`, свежая запись) —
  это контент для стороны получателя; сам приватный отчёт устройства (R1) тулом не подменялся;
- публичный комментарий RigPlayer48 на lesson two (rules: any signed-in, allowlist полей).

## Обязательства истории — доказательства

1. **Пустой inbox (baseline)** — UI: rig-38 «Nothing addressed to you yet» (автор после
   bootstrap-а профиля); серверно тем же uid до сеяния (run/00: curator 0 entries). После
   перезапуска смены rig-37 подтверждает сессию (ник UserYXJZY9, тот же APK).
2. **Записи inbox с именованным кругом читателей и ссылкой на урок** — rig-39: карточка
   «Comment»/`ibx2_lesson`/текст/«Public comment by RigPlayer48» и карточка «Private report»/
   `ibx2_lesson`/текст/«Private: only the lesson's author reads this»; серверный ответ с теми же
   kind/readers — run/42 (entries 2, kinds [lesson_comment, private_error_report]).
3. **Подача приватного отчёта устройством (UI, AD-20/AD-6)** — rig-40…42: Review-очередь
   показывает задание «Inbox journey lesson one» (Testing, «1 assignment»), detail открывает
   «Report an error»-диалог («Private error report», подсказка «What is wrong with the whole
   lesson? Only its author will read this.»), текст введён с клавиатуры рига, submit даёт
   «Report queued for the lesson's author»; SYNC NOW дренит очередь (Last sync 02:18), сервер
   получает отчёт `lesson-error-rw1yvr9hq56e` (run/45: lessonAuthorUid = куратор из
   placement-документа, reporterUid = риг-игрок, текст байт-в-байт, schemaVersion 1).
4. **Отчёт доходит до разрешённого автора** — run/46: inbox куратора (не заходившего в
   приложение) содержит ровно одну запись private_error_report — R1; у куратора есть роль
   ревьюера/куратора (adminLevel 100), но видит он её как **автор урока**.
5. **Роль сама по себе права не даёт; публичное отдельно** — run/47 + rig-43: inbox
   риг-игрока (подавшего отчёт, adminLevel 100) содержит только комментарий и R2 — R1 в нём
   отсутствует; комментарий и отчёт едут разными kind с разными readers. Прямые чтения
   lesson_error_reports закрыты правилами (7 rules-проверок, gate-rules.log).
6. **Идемпотентность приёмника** — run/48+49: повтор R1 прежним ключом мутации вернул
   `replayed:true` с тем же createdAtMs, отчётов по-прежнему 1; R2 задокументирован тем же
   свойством при подаче (`replayed:false` — свежая запись под своим ключом).
7. **Оракул «только преднамеренные касания»** — run/51: compare 40-before → 50-after: 23
   добавления (2 цепочки уроков, задание+журнал, 2 отчёта, комментарий, 2 ключа мутаций),
   **ноль удалений и ноль изменений** — чужой материал не тронут.

## Гейты (смена 12, e25af346; код 4.9 после этого не менялся)

- functions: 97/97 (прогон против demo-backend; первый проход 96+1 load-flake
  arena-lifecycle-views, сюита отдельно 11/11 + полный повтор зелёный);
- rules: 200/200 (+7 inbox-проверок этой истории);
- firebase JVM: 275 (2 предсуществующих fixture-pin красных — владелец);
- detekt: предсуществующий красный плагина (jvm-target 27) — владелец;
- эта смена меняла только `scripts/demo-fixtures/inbox-journey.js` (node, вне gradle-гейтов) и
  evidence.

## Хвост для владельца

- Конвенция прошлых смен «User-подобные uid в дампах = auth-uid» неверна: drawer показывает
  nickname профиля; настоящий uid надо читать из auth-store (run-as shared_prefs) — в прерванной
  смене фикстуры было бы адресовано по нику. Здесь перенацелено и задокументировано.
- APK 8896fa21 собран из состояния, байт-в-байт равного текущему дереву (sha сверён), свернутых
  секций drawer при adminLevel 100 не наблюдается — «пропажа» Curation/Inbox в этой смене была
  артефактом свайпов мимо скроллируемой области (список 8 пунктов, скролл работает).
- Риг остановлен (`docker stop schoolquiz-emu`) после блока проверок; demo-backend жив
  (постоянное решение владельца). Состояние демо: фикстуры ibx1_*/ibx2_* и их отчёты/комментарий
  остаются как экспонаты истории 4.9; profiles/34M7…adminLevel 100 возвращён после джорни.
