# 5.3 competition-admission — 2026-10-16

Story: «UX-O4/access admission adapter (downloaded course OR link) + authorized event
discovery» — спека `_bmad-output/implementation-artifacts/spec-5-3-admission-and-discovery.md`
(route full/pinned, `context: []` — карта кода спеки прочитана как список входов), baseline
`96a9d23c252aee5204ce5a8bc92e90462d732179` (HEAD совпал с `baseline_revision` спеки на момент
начала).

## Что построено

Серверная часть гейта UX-O4/access в `functions/`, без клиентского кода (история 5.9/5.10 —
клиентские поверхности):

| Кусок | Где | Суть |
| --- | --- | --- |
| Чистая политика допуска | `functions/tournament-policy.js` — `planAdmission({event, uid, nowMs, downloadRecord, linkTokenDigest})` | ровно одно основание из серверных записей: аттестованная запись скачивания для `event.source.id` (COURSE_DOWNLOAD) ИЛИ сверенный дайджест ссылки (LINK); окно квалификации остаётся единственным временным гейтом (`OUTSIDE_QUALIFICATION_WINDOW`, полуинтервал `[startsAtMs, endsAtMs)`); без основания — типированный отказ `NO_ADMISSION_BASIS` |
| Минт ссылки | `functions/tournament-service.js`, ветка CREATE | токен (48 hex из `randomBytes(24)`) минтится один раз при CREATE; в `course_tournament_links/{eventId}` хранится только sha-256 (канонический дайджест); токен появляется исключительно в CREATE-ответе организатора — и в его же replay квитанции (чужой аккаунт replay получить не может: `receipt.uid !== uid → PERMISSION_DENIED`); ротации/отзыва/отдельного срока нет — валидность ссылки и есть окно квалификации |
| Проверка при старте | тот же файл, ветка START_QUALIFICATION, только неповторная ветка | в той же транзакции читаются запись скачивания `course_tournament_downloads/{digest([uid, event.source.id])}` и документ ссылки; admission-документ `course_tournament_events/{eventId}/admissions/{digest(uid)}` создаётся create-only внутри окна, один на (eventId, uid); повторный допуск возвращает исход существующей записи (basis и admittedAtMs не переписываются); replay попытки и запросы вне окна admission вообще не трогают — «поздний запрос и повтор не создают новый допуск» |
| Запись скачивания | `functions/course-tournament-controller.js` — `recordDownload` + callable `recordCourseTournamentDownload` в `functions/index.js` | create-only документ на (uid, courseId); повтор (тот же или другой operationId) возвращает сохранённый исход и не пишет ничего; чужой uid невозможен (auth); плохая форма courseId → `INVALID_ARGUMENT` |
| Discovery | тот же контроллер — `listEvents` + callable `fetchCourseTournamentEvents` | пагинация по образцу readMeeting/worker: `orderBy("__name__") + startAfter + limit(pageSize+1)`, `pageSize ≤ 100` (по умолчанию 25), курсор `afterEventId` проверяется как id события; строки — та же allowlist-проекция `safeEvent`, что у `fetchCourseTournamentEvent`: без `serverSeed`, отпечатков, форм, банков и токена; знание eventId допуском не является |
| Redaction CREATE-ответа | `safeResponse` в контроллере | `admissionLink.token` проходит наружу только в CREATE-ответе; все остальные проекции (event/session/sessions/meeting/discovery) токен не несут |
| Правила | `firestore.rules` | два новых deny: `course_tournament_links`, `course_tournament_downloads` (`allow read, write: if false`); admission-подколлекция накрыта существующим `course_tournament_events/{document=**}` |
| Проба правил | `scripts/course-tournament-private-rules.test.js` | обе коллекции добавлены в `PRIVATE`, плюс явный проб admission-подколлекции |

## Owner-visible reading: ARENA_LESSON — link-only

Для `source.kind === "ARENA_LESSON"` соответствующего курса не существует — скачивать нечего,
поэтому в дизъюнкции гейта выполнимо ровно одно основание: такие события допускаются только по
ссылке, организатор раздаёт токен; записи скачивания к ним не применяются (даже аттестованная
запись на тот же source id не является основанием). Это чтение зафиксировано в доксумме
`planAdmission` и в тестах (`tournament-policy.test.js`, `tournament-service.test.js`), не
выбрано молча. Приоритет при обоих основаниях сразу у COURSE-события: аттестованное скачивание,
затем ссылка (детерминировано и задокументировано в доксумме).

## Границы, которые держит код

- Основание допуска подтверждается только серверными записями; клиентское «я скачал» и голая
  строка ссылки — не разрешение. Ссылка не обходит отбор: CLOSE_QUALIFICATION/ранкинг/скобка
  admission не читают (структурно — проверка живёт только в ветке START).
- Заработанные чемпионатные права переживают любое состояние ссылки: отбор заморожен
  существующим freeze до CLOSE, ссылка в скобку не входит (покрыто существующими тестами freeze,
  не тронуты).
- Ровно один admission-документ на (eventId, uid), create-only; повторный допуск возвращает
  существующий исход. Токен в открытом виде хранится ровно в одном документе — квитанции CREATE
  организатора в `course_tournament_operations` (это и есть механизм «replay возвращает токен»);
  assert в `tournament-service.test.js` проверяет, что других носителей нет.

## Проверка

- `cd functions && /root/.zcode/server/node --test tournament-policy.test.js
  tournament-service.test.js course-tournament-controller.test.js
  course-tournament-handler.test.js` — все зелёные: policy 20/20 кейсов (включая новые
  admission-набора), service 38/38 (новые: отказ без основания, link-допуск без записи,
  однократность admission, link-only ARENA_LESSON end-to-end, поздние повторы), controller
  45/45 (новые: discovery-пагинация/redaction/границы, recordDownload, link-допуск
  через контроллер, отсутствие токена в read-проекциях), handler 7/7 (новые: реальные
  callables записи/ discovery, link-поток с replay-возвратом токена). Node v22.16.0.
- Полный набор функций `node run-tests.js` — 109/109 наборов зелёные.
- `node --test tournament-storage-contract.test.js` — 14 явных приватных границ (было 12):
  контрактный тест сам требует deny-строку в `firestore.rules` для каждой коллекции из
  `TOURNAMENT_PATHS`, обе новые добавлены туда же.
- `node check-syntax.js` — 200/200 файлов парсятся; `scripts/course-tournament-private-rules.test.js`
  компилируется (`node --check`).
- `git status --porcelain -- shared/ android/` — пусто (серверная история, клиентский код не
  тронут; `QuestRepository.kt`/`QuestDao.kt` читались как контекст «почему нужна серверная
  аттестация», не изменялись).

### Обновления существующих наборов (вызванные историей, ослабления проверок нет)

- START-фикстуры в `tournament-service.test.js` и `course-tournament-controller.test.js`:
  участникам засеяна аттестованная запись скачивания (fixture-курс) — существующая дисциплина
  entry/session проверяется как раньше, гейт не ослаблен.
- `course-tournament-handler.test.js`: `world()` получил пользователя linky без записи скачивания
  (для link-потока), `endpoints` дополнен двумя новыми callables (auth-тест покрывает их
  автоматически).
- `arena-lifecycle-views-handler.test.js`: перезапинен `ACCEPTED_NON_LIFECYCLE_EXPORT_SET_SHA256`
  (`5dd28359…` → `2d453d6d…`) — в поверхность законно вошли два новых callable из спеки
  («add two new exports on the same pattern»); комментарий перезапина в файле обновлён по
  конвенции. Это единственное обновление вне явно перечисленных спекой START-тестов; оно
  механически неизбежно при mandated экспортах и зафиксировано здесь как story-caused.

## Статус demo-контейнера (честно)

- HTTP/Firestore-эмуляторы demo-контейнера (`schoolquiz-mvp-firebase:4400`) в момент прогона
  **не подняты**: хост резолвится (172.17.0.4), но соединение с портом 4400 не проходит
  («no route to host»).
- Поэтому `scripts/course-tournament-private-rules.test.js` (403-пробы по синтетическим путям
  обеих новых коллекций + admission-подколлекции у анонимного демо-аккаунта) **не исполнялся** —
  зафиксирован как pending demo/rig evidence. Файл расширен и компилируется; содержательный
  инвариант «deny-строка присутствует в firestore.rules для каждой приватной коллекции» покрыт
  исполненным `tournament-storage-contract.test.js` (14 границ), но живая 403-проба через
  эмулятор остаётся на демо-риге.
- Для запуска пробы на поднятом риге: `GCLOUD_PROJECT=demo-schoolquiz-mvp
  FIRESTORE_EMULATOR_HOST=schoolquiz-mvp-firebase:8080
  FIREBASE_AUTH_EMULATOR_HOST=schoolquiz-mvp-firebase:9099 …` — см. шапку проб-файла (точные
  переменные там же); проба ничего не пишет, ожидает 403 на каждый путь.

## Осталось / риски

1. Живая 403-проба правил на демо-риге (pending, см. выше) — единственная непройденная
   проверка из спеки, и только из-за недоступности контейнера.
2. Клиентская обвязка (вызов `recordCourseTournamentDownload` после скачивания курса, показ
   discovery, подстановка токена при старте) — истории 5.9/5.10, спеком явно вынесена из 5.3.
3. Токен ссылки в открытом виде хранится в квитанции CREATE организатора (иначе replay не мог
   бы вернуть потерянный токен). Коллекция квитанций закрыта правилами, replay доступен только
   тому же uid; альтернатива — отдельный «возврат токена» эндпоинт — новой поверхностью не
   заводили (Never: no new product surfaces beyond the two bases).
4. Существующие события, созданные до этой истории, не имеют документа ссылки — для них
   link-основание недоступно (сверки не с чем), download-основание работает; миграций спека не
   требовала, их не делали.
5. Модель доверия download-основания (честная запись): серверная запись создаётся по вызову
   клиента, заверившего завершение скачивания — сервер не может верифицировать локальное
   состояние Room, поэтому основа есть «клиентская аттестация, зафиксированная сервером», с
   проверкой существования курса (`quests/{courseId}` существует, иначе INVALID_ARGUMENT) —
   мусорные/опечаточные id не проходят. Это соответствует UX-O4/access («локальный флаг сам по
   себе не разрешение» — разрешением становится серверная запись); более сильная верификация
   (серверное свидетельство байт) в архитектуре отсутствует.
6. Поверхность ошибок допуска для историй 5.9/5.10 (клиент маппит по message внутри кодов):
   `PERMISSION_DENIED` + message `NO_ADMISSION_BASIS` (нет основы; несовпавший токен приходит
   той же парой), `FAILED_PRECONDITION` + `OUTSIDE_QUALIFICATION_WINDOW` (вне окна),
   `INVALID_ARGUMENT` (не-hex токен, неизвестный курс, границы pageSize/cursor),
   `unavailable` (`INCONSISTENT_ADMISSION`/повреждённая запись — ремонт владельцем).
7. Сопутствующая правка review pass 1: три rig/demo-скрипта приведены к гейту допуска
   (`course-tournament-http.test.js` — скоуп эндпоинтов + recordDownload перед player-START;
   оба `mvp-qualification-*` — посев `course_tournament_downloads` для когорты перед
   bulk-START и перепин SHA-256 источника), иначе единственный реальный HTTP-приём конвейера
   остался бы красным до следующего риг-прогона.
