# TZ06B safe read views (story 4.2, epic-authoring-review) — 2026-10-09

История «ограниченные серверные views статуса урока, истории, разрешённых причин отказа
и author payouts; автор/куратор проверяются сервером; private collections закрыты». Аудит
показал: **вью-семья TZ06B уже существует целиком**, пробелом был только rules-контур.

## Что уже существовало (не пересоздавалось, покрытие закреплено)

- **Views-модуль** `functions/arena-lifecycle-views.js` (v1): авторские/кураторские
  пагинированные проекции с курсором — `listOwnedLessonLifecycle`, `listCuratorLessonLifecycle`,
  `getLessonLifecycle`, `listLessonLifecycleHistory` (история решений), `listMyArenaRoyalty`
  (author payouts), `listMyQuestReviewSubmissions` (статусы подачи с **разрешёнными** причинами
  отказа: `authorVisibleRejectionReason` проецирует только автор-видимую часть, бесплатного
  текста ревьюера там нет). Авторство/кураторство проверяет сервер (`authorizeLesson`: автор
  или `isArenaCurator` по доверенному профилю; чужой урок неотличим от несуществующего).
- **Handler-сюита** `functions/arena-lifecycle-views-handler.test.js` — 6 кейсов, среди них
  «TZ06B lifecycle readers preserve the accepted callable surface», «actual callable enforces
  author/curator scope, hides foreign lesson existence, and projects no raw private field»,
  ограниченность журнала (concurrency/объём) и generic-unavailable на отказах источника.
- **Правила**: raw-коллекции за вью закрыты явно — `lesson_placements` (read/write false),
  `lesson_ratings` (вошедший читает карточку, запись сервер; `votes` закрыты),
  `quest_review_requests` (read/write false), `arena_royalty_accounts` (read/write false),
  `audit/**` (включая `currency_ledger` — read/write false).

## Дозаполнено этой историей: правила в runner-реестре

`scripts/rules-emulator-test.js` имел 127+ поведенческих проверок, но **ни одной** на закрытие
private-коллекций TZ06B — а deny-проверка слепа к удалению правила (default-deny даёт тот же
отказ, что явный блок; это задокументировано в самом раннере на примере `question_keys`).
Добавлена сюита `testAuthoringViewsPrivacy()` (+22 проверки):

- deny на чтение/запись `lesson_placements` (игрок/автор/админ), `quest_review_requests`
  (автор и рецензент), `arena_royalty_accounts` (автор/админ), `audit/currency_ledger`
  (игрок/админ), `lesson_ratings` на запись (игрок/админ) и `lesson_ratings/{id}/votes`
  на чтение/запись;
- allow: вошедший читает карточку `lesson_ratings` (единственная клиент-читаемая коллекция
  семьи);
- текстовое закрепление четырёх явных блоков закрытия (`lesson_placements`,
  `quest_review_requests`, `arena_royalty_accounts`, `audit`) — чтобы ослабление было
  решением, а не случайностью.

## Gates (все зелёные)

- `gate-rules.log` — rules-раннер под эмуляторами auth+firestore: **171/171 проверок прошло**
  (149 до + 22 новых). Прогон на отдельных портах (18089/19098): хостовый 8080 занят процессом
  владельца (signals), конфиг временный `/tmp/wm/firebase-rules-test.json`, продовые
  `firebase.json`/порты не тронуты.
- `gate-functions.log` — полный functions-гейт без emulator env: **92 of 92 suites passed**.
- `gate-functions-demo-backend.log` — тот же гейт против живого demo-backend
  (`FIRESTORE_EMULATOR_HOST=127.0.0.1:18090`, `GCLOUD_PROJECT=demo-schoolquiz-mvp`):
  **92 of 92 suites passed**. (Первая попытка этого прогона дала 91/92 — разовый сбой
  чисто-юнитового `arena-lifecycle-views.test.js` под нагрузкой; сюит-alone 11/11 зелёный,
  повтор полного прогона зелёный — зафиксировано здесь же.)

## Scope notes

- «Typed paged» — курсорная пагинация вью (signed cursor, страница ≤20), закреплённая
  handler-сюитой; контракт вью эпика-8 (CAP-26/AD-30) не менялся.
- Демо-фикстуры ролей (план §2 Шаг 4, общий манифест с server half 3.3) для 4.2 не
  потребовались: права проверяются на доверенных профилях внутри handler-сюиты с
  синтетическими актёрами, rules-сьют использует своих акторов (`tester`/`admin`/…).
- Демо-гигиена прогонов: правила и runners не пишут в demo-backend, кроме штатных
  тестовых транзакций сюит (2.13-практика); probe-ключей/профилей не добавлено.
