# Извлечение требований ТЗ‑01…06 и доказательств кандидата

Дата: 2026‑09‑28. Только планирование; сборки, тесты, устройства, контейнеры и финансовые сценарии не запускались. Фактический HEAD, проверенный чтением Git: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`, ветка `continuation/mvp-candidate-20260926`. Существующие изменения не редактировались.

Корень всех относительных путей ниже: `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate`.
**P** = `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/`.
Номера после двоеточия — строки источника. Это source extract, не PRD и не приёмочный отчёт. Идентификаторы AR здесь — локальные ключи извлечения, а не окончательные FR-ID.

## Приоритет и ограничения

- `P/PRODUCT-CONTRACT.md:3` имеет приоритет над старым противоречащим текстом. `P/STATE-MANIFEST.json:3–11` описывает снимок **2026‑09‑07**, HEAD `550a6404`, а не нынешний checkout; строка 4 прямо запрещает считать реестр readiness proof. Названия четырёх пакетов ТЗ‑01 — строки 596, 659, 710, 759.
- `P/CONTEXT-AND-STATE.md:24–37` — исторические проверки, не текущий статус; нельзя складывать результаты выполненных, UP-TO-DATE и skipped тестов. Новые срезы дополнили прежний снимок.
- Применены `/workspace/projects/AGENTS.md`, `AGENTS.md`, `.claude/PROJECT-CONTEXT.md`: хранение и Git на VPS; KMP + Decompose + Compose + Koin; экраны получают состояние/callbacks; новый presentation state holder — Component.
- `PRODUCT-CONTRACT.md:53–57`: Telegram, ручной выбор турнирных вопросов, коробка за пять реклам, полные экзамены/сертификаты вне MVP; не обещать недоказанную защиту от локальных часов и глобальный мгновенный offline-отзыв. Сначала изолированная проверка и личная приёмка владельца; перенос и публичный выпуск — отдельные этапы.
- Текущая пауза разработки и запусков приоритетнее исторических команд. `../HANDOFF.md:50–64` фиксирует незавершённый RED тест quiescence, production fix отсутствует.

## Требования и проверяемая приёмка по каждому источнику

### ТЗ‑01 — безопасные базовые контракты

Источники: `P/01-READY-DRAFTS.md:3–51`.

**AR-01. Устойчивые операции и границы пользователя.** Разрешения и неподтверждённые заявки различимы, страницы имеют разные курсоры. Разрешённые действия — явное согласие, отправка, повтор, восстановление, отмена и сверка. Открытие графа/экрана не загружает автоматически и не создаёт новую финансовую операцию. Исходные владелец и session epoch сохраняются через отправку и возврат; A→B→A не возрождает прежнюю сессию. Отмена coroutine не превращается в успех или обычную ошибку (`:26–32`).

Приёмка: потеря ответа/повтор сохраняют ID и тело; старый курсор/чужой epoch отвергаются; lazy graph не читает сеть/БД/часы и отсутствие зависимости не открывает разрешительный обход (`:47–51`).

**AR-02. Продолжать можно только целостную сохранённую попытку.** Header, условия, банк и checkpoint читаются согласованно; повреждение/отсутствие данных ведёт в recovery. COMPLETING не является очередным вопросом. Terminal header не является квитанцией расчёта (`:28–30`).

Приёмка: повреждённый банк даже с прежним hash-полем не запускается; completion и cancel конкурируют безопасно; потеря локального банка не мешает разрешённой отмене/точному повтору уже зафиксированного решения (`:38–49`).

**AR-03. Нельзя сбрасывать или подменять попытку.** Новый claim допустим только для UNUSED/NONE/RESERVED, без checkpoint, revision 0. Запись и freeze сверяют текущую строку и полное immutable binding; точный terminal replay после eviction сохраняется (`:38–42`). Проверить corrupted revision, закрытые состояния, изменение банка/terms и единственность исхода.

Технический addendum: импорт первых 16 файлов и отдельные 6 guards уже исторически выполнен; не повторять старый importer. Сохранять канонический strict codec/fakes и внутренний gate без второго mutex. Семь обязательных binding полей: ownerUid, ticketId, attemptId, snapshotSha256, requestId, termsJson, snapshotJson. Старые ограничения «schema 13» относятся к тому срезу и не означают возврат текущей v15.

### ТЗ‑02 — баланс, резерв и восстановление

Источники: `P/02-ARENA-BALANCE-AND-RECOVERY.md:7–48`.

**AR-04. Честный общий баланс.** Доступный и зарезервированный остатки раздельны. Серверные nolics уже исключают резерв: 100→hold30 означает 70 available/30 reserved, не 40. Локальные tickets и сумма страниц не доказывают общий резерв аккаунта, включая другие устройства (`:7–9,19–26,42`). Life points не смешивать с hearts slots; plasma показывать отдельно только если отображается.

Чтение авторитетного кошелька не создаёт профиль, не регенерирует, не покупает, не резервирует и не пишет ledger. Сохранённый серверный остаток и проекция восстановления различаются. Unknown/missing/malformed/stale/guest/default и подтверждённый ноль различаются; legacy defaults допустимы только по явному серверному контракту. Проверять UID, версию, целые/диапазоны, NaN/Infinity/отрицательные резервы; epoch до отправки, до local write и после ответа (`:17–26`).

Приёмка: своё read-only чтение разрешено, чужое/unauth запрещено настоящими HTTP/rules; кошелёк и ledger до/после идентичны; отмена/расчёт другого устройства отражаются только новым observation; показанный достаточный остаток не гарантирует будущий prepare (`:42–46`).

**AR-05. Восстановление не теряет сохранённое решение.** Local settlement/cancel intent виден независимо от server terminal. CANCELLED + retained SETTLE требует exact reconciliation; SETTLED без принятой квитанции не подтверждает результат. Pending prepare связан с исходным requestId, не «последним ticket». Повреждённый proof не удаляет очередь; банк не нужен для отмены корректного header (`:30–34,45`).

Технический addendum: owner-bound typed observation; read-only reader; explicit refresh без неограниченного polling/нового sender. Typed Room cache необязателен; если появляется — fresh migration и Unknown для legacy. Текущая модель фактически обходится без persistent wallet cache.

Открытая продуктовая деталь исходника: точный ceiling `lifeCost` до выдачи не определён. Nolics ceiling есть; подтверждённый hold lifeCost известен из ticket. Не придумывать тариф/авторезерв; отдельный ceiling зарядов требует решения, если выбранный UX его обещает (`:48`).

### ТЗ‑03 — интерфейс подготовки и разрешений

Источники: `P/03-ARENA-ENTRY-UI.md:7–44`; `PRODUCT-CONTRACT.md:23–29`.

**AR-06. Осознанная подготовка.** Каталог доступен независимо от Entry/баланса; ручная загрузка, автор, рейтинг или отсутствие оценок, цена именно урока. RANDOM — отдельное действие «0 ноликов» с предупреждением о charge hold; сервер выбирает урок. Это не fallback SELECTED и не автозапрос 20 tickets. SELECTED показывает урок, сложность и согласие «не больше N ноликов», N — показанная цена 1–100, не default100; согласие заморожено при обновлении каталога (`:13–17`).

При PRICE_CHANGED — Attention и новое согласие для нового ceiling/ID; автоматического повышения нет (`:24`). Лимиты: 20 RANDOM выдач/день/аккаунт, 5 одновременно подготовленных платных; отмена RANDOM не возвращает квоту (договор `:25`).

**AR-07. Разрешения доступны и понятны при сбоях.** «Мои разрешения и заявки» открываются при пустом/ошибочном каталоге и Unknown/нулевом балансе; отдельные страницы и курсоры tickets/preparations. Показываются confirmed frozen price/lifeCost; Offline, Pending, Attention, NeedsRecovery, явные recovery/отмена с подтверждением/повтор (`:16–18,25–28`).

**AR-08. Одна операция переживает повтор и процесс.** Confirm синхронно блокирует double tap, сохраняет единственный requestId до первого ожидания и согласие до dispatch. Retry повторяет ровно прежнюю команду; неизвестный исход не повод новой заявки. Cancel сохраняет своё решение отдельно от отправки; CANCEL_PENDING не означает release. «Найти» восстанавливает server pages, «скачать» frozen bank конкретного ticket; отсутствие в странице не отменяет ticket (`:22–28`).

Исходный stamp и ID связывают все callbacks, диалоги и страницы; A→B→A их сбрасывает. Восстановление из journal, не raw bank в UI state. Restart не отправляет prepare автоматически без согласованной политики; явный retry предупреждает, что может впервые создать резерв. PREPARE сейчас сохраняется онлайн, CANCEL — и офлайн. LAUNCHED продолжает старый run, terminal/recovery не playable (`:32–36`).

Приёмка: double tap, timeout после hold, cancellation, offline, PRICE_CHANGED, quota, пять selected, оба вида cursor, A→B→A; отсутствие mutation от открытия/refresh/переключения вкладки. EN/RU/UK, доступность, Back/rotation/process restore. Maestro: selected prepare→restart→тот же ticket→explicit cancel→единственный server release; отдельно RANDOM/лимиты/offline cancel, подтверждения ledger (`:40–44`).

### ТЗ‑04 — запись попытки

Источники: `P/04-ARENA-RUN-WRITES.md:15–60`.

**AR-09. Единственный старт и сохранение.** Fresh и read-only resume различаются; повтор/проигравший claim не создаёт seed/deadline/attemptId и не обнуляет прогресс. Frozen bank/version/difficulty принадлежат ticket; никаких live hierarchy/shuffle/new charge. LAUNCHED и initial сохраняются атомарно до возврата (`:22–33`).

**AR-10. Конфликт записи не подменяет ответ.** CAS сохраняет ожидаемую revision; AlreadyCurrent — только побайтно равный canonical checkpoint с тем же binding, другое тело — Conflict с новым чтением, без blind overwrite. Сохраняются FEEDBACK prefix/locked answer, mode/seed/order. COMPLETING и endedAt/final body фиксируются первым допустимым переходом (`:24,37–40`).

**AR-11. Локальное завершение ещё не результат.** Finalize принимает существующий typed Completing и возвращает только FrozenLocally; freeze создаёт retained SETTLE и исходный SUBMIT_ATTEMPT атомарно. Crash между final CAS и freeze, response loss и eviction ведут к точному replay без повторной очереди/сброса backoff. Без checkpoint Completing не реконструируется из header; ABORTED не финансовое завершение, неизвестный HARD score — null (`:25,39–43`).

Приёмка: strict Journal/codec, fresh factory однажды, read-only resume, wrong binding/revision/terms, все четыре фазы, CAS loss с точным/конкурентным телом, crash/freeze/replay, same UID new epoch и cancellation после commit (`:51–60`).

Технический addendum: инъекция clock/seed; existing computeTimer; original-session gate; без второго mutex, второго sender и Run.cancel. Exit использует Entry recordCancelIntent/dispatchCancel. SETTLE/CANCEL — first-decision wins; committed запись не откатывается coroutine cancellation (`:29–49`).

### ТЗ‑05 — настоящее прохождение и квитанция

Источники: `P/05-ARENA-RUNNER-UI.md:3–51`.

**AR-12. Подготовленный ticket проходит онлайн/офлайн и переживает restart.** Fresh только explicit допустимого ticket; восстановленный route — ResumeOnly. Again/Next требуют нового разрешения. Только Continuable открывает Question. CAS завершается до раскрытия следующего вопроса; ошибка оставляет последнее подтверждённое состояние. Taps/timeout/lifecycle при IO не создают второй ответ (`:11–16`).

**AR-13. Сохраняется состояние вопроса и времени.** Frozen order/version/difficulty/attemptId неизменны; ANSWERING сохраняет startedAt/deadline/seed/order, истёкший текущий вопрос получает один timeout, не все ещё не показанные вопросы. FEEDBACK сохраняет locked answer и Continue, PAUSED — паузу. Background не заменяет locked ответ; rotation не начинает урок заново. Сохраняются ordinary timing/Continue/background pause и sessionMode; турнирный запрет паузы часов сюда не переносится (`:20–26`).

**AR-14. Выход, ожидание, результат различимы.** Явный Exit/Back/cross сначала сохраняет cancel intent и лишь затем уходит; pending не равен возврату. Final checkpoint→freeze→PendingCompletion. Подтверждённые процент/награда/право оценки доступны только с настоящей SETTLED, completed=true квитанцией правильного owner; unknown HARD не 0%, legacy не новый confirmed result. CANCELLED/конкуренция завершения восстанавливает прежнюю доставку (`:27–34`).

Приёмка: весь набор типов вопросов; все фазовые restarts, CAS error, timeout+tap, background/rotation IO, CANCEL/SETTLE, same UID new epoch, immutable final. Не ухудшить ordinary Home и accessibility Continue/Info, задержку 250ms (`:38–40`).

Сквозные обязательные кейсы (`:44–51`): selected цена1→offline start→restart FEEDBACK→offline finish→сеть→один расчёт и 80% автору с carry; цена100 и смена рейтинга после подготовки не меняют frozen price; RANDOM без nolics с обычными charges; без ticket/exhausted нет offline start; один ticket на двух устройствах, разные tickets, CANCEL/SETTLE race дают максимум один outcome на ticket; response loss после расчёта→restart→та же receipt и суммы. Unit green без приложения и server ledger ТЗ‑05 не закрывает.

### ТЗ‑06 — отдельный урок, автор и куратор

Источники: `P/06-LESSON-LIFECYCLE.md:3–42`; `PRODUCT-CONTRACT.md:7–19`.

**AR-15. Полный путь конкретного урока.** Создание/редактирование→version→отправка→независимые проверки→Арена→Турнир→Home с явным куратором и историей/причинами/жалобами. Курс остаётся в библиотеке; рейтинг/победа не выдаёт роль и не публикует. Урок имеет свои placement/rating/price/history; quest rating не копируется детям (`:13–17,21–25`).

**AR-16. Права и версии защищены сервером.** Автор не проверяет себя; Auth/серверная роль, не payload. Public/owner/curator view раздельны, ограничены страницами и полями; raw placement/history закрыты даже привилегированному клиенту. Старые данные маркируются legacy; миграция не создаёт rewards, ratings или прохождения. Правка не меняет frozen tickets/event banks, непроверенная версия не подменяет допущенную (`:7–17,36`).

**AR-17. Действия автора/куратора и выплаты видны.** Автор видит статус урока, причины/историю/допустимые действия, долю и журнал выплат. Куратор видит проверки и явно подтверждает переход/снятие с причиной. Одна подтверждённая mutation сохраняет ID при tap/retry; Pending ещё не publication. Оценка 1–3 только после receipt; самооценка и rating без completion запрещены также сервером (`:21–25`).

**AR-18. Рейтинг и цена точны.** Одна актуальная оценка игрока; замена не новый rater. Weighted=(sum+40)/(count+20), сравнение без rounding loss; равный score — равная цена. Линейная цена 1–100 между нижним/верхним уровнем, нет оценок/один уровень —1. Catalogue/quote/prepare используют одинаковую допустимую совокупность; устаревшие/недопущенные размещения исключены; frozen tickets не переоцениваются. Усечённую выборку нельзя выдавать за глобальную; лимиты 1000 raw/12000 reads технические, не новые продуктовые лимиты (`:29–31`, договор `:17–18`).

**AR-19. Авторская экономика и сезон.** RANDOM стоит 0, SELECTED —1–100 за каждое принятое прохождение, обычные charges сохраняются. 80% фактического платежа автору/20% удаляется; дробный carry не теряется. Призы: лучший урок квартала10 BOX, первое Tournament15, первое Home20; edit/remove/republish не повторяет награду. Один winner на всю Арену за календарный квартал UTC, серверно принятые в этом сезоне оценки, минимум20 разных прошедших; tie: count→ранняя публикация→stable ID; нет eligible —no-award. Серверное время/season ID и retry/two workers не повторяют выплату (`:29–32,38`, договор `:14–19`).

Приёмка: author self-review, insufficient/revoked reviews, wrong role, raw GET/PATCH/DELETE denied, безопасная история, retries, editing после admission, account switch, no synthetic migration. 19/20 участников, replacement, equal score/price, UTC boundary, double finalize, milestone один раз; пять оплат по1 при carry0 дают автору суммарно4, цена100 даёт80 (`:36–38`).
Maestro **через реальные пользовательские действия** разных автора/проверяющих/куратора: создать→проверить→Арена→пройти/оценить→выплатить→Tournament→Home; lessonId/version/receipt/history/reward IDs совпадают. Admin запись готовых статусов не заменяет приёмку (`:40–42`).

## Текущее наличие реализации и пределы доказательств

| Область | Что установлено чтением текущего кандидата | Статус приёмки/оставшееся |
|---|---|---|
| ТЗ‑01 | Ancestors `1db53dc1` Entry/read/DI и `dec5627e` guards. Commit bodies описывают исполненные scoped JVM/static/build, отдельно новые и reused tests. | Исторические checks, не свежий full candidate PASS. Старый «не импортирован» из пакета больше не текущий статус. |
| ТЗ‑02 | Ancestor `a76bc8a9`; `ArenaWalletObservation.kt:5–18,33–56` действительно различает stored life, receive-time freshness, stamp и unknown errors; persistent wallet cache отсутствует. | Реализация присутствует; новая app/server acceptance не доказана данным чтением. |
| ТЗ‑03 | Ancestor `65560048`; Entry Components/UI и Android tests есть в tracked tree. | Не объявлять отсутствующим; quotas/cancel/cross-device/current device matrix не подтверждены. |
| ТЗ‑04 | Ancestor `5a7c0d89`; domain `.../repository/ArenaRunRepository.kt:8–97` содержит freshClaim/resumeOnly/CAS/finalize и typed outcomes, FrozenLocally. | Реализация есть; текущие storage/device race checks не запускались. |
| ТЗ‑05 | Ancestors `fb62a91f`, `5993a809`, `6a1373bf`, `cae38889`, `953e686d`, `1d1330ba`, `2a1eabdb`: coordinator/host/navigation/receipt/rating. Commit C1 описывает 1054 свежих JVM и retained13→14 migration; C3 —225 JVM и12 Android UI. | Это исторические slice checks. E2B PASS13 сентября существует (HANDOFF:45), но не нынешний Samsung/APK и не полный offline/two-device matrix. |
| Замена оценки | `ArenaRunRating.kt:51–52`: canSubmit только Pending. `ArenaFrozenRatingRepositoryImpl.kt:149–163`: при retained intent возвращается существующее состояние, а не новая оценка. | Конкретный найденный риск/пробел пути **замены уже принятой оценки на том же ticket**. Не доказано тестом; не утверждает отсутствие серверной замены или замены после нового прохождения. Требование AR‑18 сохраняется. |
| ТЗ‑06 data/views | Ancestor `4b8778fd`; `functions/index.js:380–390,451–452` exports owner/curator/detail/history/payout/review-submission reads и placement/rating. `functions/arena-lifecycle-views.js:6–30` bounded views/role-safe fields; index:523–560 bounded context. | Серверная часть присутствует. Tracked Kotlin поиск новых owner/curator/detail/history endpoints и setLessonPlacement —0 результатов; новый lesson placement/history/curator UI не найден. Исключение: `FirebaseQuestArenaSubmissionRemoteDataSource.kt:85` уже использует listMyQuestReviewSubmissions. Не весь lifecycle-клиент отсутствует. |
| ТЗ‑06 eligibility | Ancestor `34fe664b`; `functions/arena-eligible-universe.js:24–29,87–101` общий transaction-local universe с eligibility checks; лимиты `:5,48–61`. | Исходный конкретный рассинхрон catalogue/prepare уже имеет исправление. Нужна актуальная поведенческая/масштабная приёмка; техлимиты не обещают полную глобальную выборку. |
| TZ06D season | Ancestor `99091eb5`; `docs/agents/2026-09-13-tz06d-implementation-report.md:8` прямо IMPLEMENTED CANDIDATE / NOT DEPLOYED / NOT ACCEPTED. | `:57–63`:39 unit;61 focused +1 intentional skip;88/88 server suites;168/168 rules403; Admin SDK demo E2E44 advances/одна award/receipt/ledger. Это историческое свидетельство, не сегодняшний rollout/acceptance. `:78–85`: old-job drain/review, actual4096/4097 SDK resource/restart measurements, callable/worker/device/offline/two-device/owner acceptance остаются. |
| Текущий HEAD tooling | `spec-arena-room-v15-evidence-readers.md:16–30,52–59` и `1e596126`: точные v14/v15 пары, legacy recovery v14-only, узкие Node22 проверки. | Никакого нового APK/full Arena acceptance. `:42` и HANDOFF:52–58 фиксируют quiescence defect; локальный RED тест добавлен, fix отсутствует. |

Краткие пути Kotlin в таблице:
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/model/ArenaWalletObservation.kt`;
- `shared/feature/lesson-runner/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/domain/repository/ArenaRunRepository.kt`;
- `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/component/ArenaRunRating.kt`;
- `shared/feature/lesson-runner/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/lesson_runner/data/repository/ArenaFrozenRatingRepositoryImpl.kt`.

### Нельзя переносить доказательства из другой ветки

`git log --all` показывает `02debd55` (curator placement decisions), `abc3a051` (Samsung curator acceptance), `82146666` (rating replacement) и поздние physical-proof commits. Они **не встречаются в ancestry HEAD текущего кандидата**. В этом извлечении они не засчитаны как текущая реализация/приёмка. Не предлагать cherry-pick автоматически: это работа последующего build scope с проверкой изменений/предусловий.

### Три разные точки ordinary-course evidence

1. `docs/agents/2026-09-27-mvp-progress-checkpoint.json:8,19–20,107–108,135–143`: source HEAD `15fedc22`, APK `52c13e24…`, assembleMvp PASS, isolated sync107 JVM tests; online/offline открытие первого вопроса **2-question smoke fixture**. Full ciCheck не запущен; production-course latency и final acceptance отсутствуют. Числа storage исключены из планирования.
2. `docs/agents/evidence/2026-09-28-real-course-first-question/README.md:3–20`: source under test `51da9d5b…`, установленная APK lastUpdate27 сентября23:02, Samsung/Android15;1/1 Maestro67s, реальный demo «Математика: полный школьный курс», первый урок40 seeded/20 selected questions. Только Home→Archive→course→section→theme→lesson→**первый вопрос**; без ответа/submit. Успешный local Room read подтверждён, backend был подключён; **не полный offline acceptance**. 5487ms включает Maestro/hierarchy overhead, не app latency metric. Артефакт записан commit `9a4e5e1c`, это не SHA кода под тестом.
3. HEAD `1e596126` добавляет evidence-reader tooling поверх этого. Нельзя приписать ему старую APK или считать сегодняшний телефон равным APK из checkpoint. Не проводилась новая идентификация устройств/установок.

## Как читать оснастку и исторический E2B PASS

- `.maestro/mvp/arena-e2b/README.md:3–29`: путь становится доказательством только с точными APK, retained data, selectors и run context. Лишь verify может overall PASS: SETTLE и RATE_LESSON обязаны принадлежать одной попытке. Предыдущие фазы overall NOT_RUN.
- `:31–54,56–93,120–125`: settled-ticket recovery и старый clock-incident RECOVERY_RECONCILED не новый E2B PASS. Прежний incident повторно не запускать.
- `HANDOFF.md:45` — точный архивный путь PASS13 сентября: `/workspace/infrastructure/android-toolchain/arena-e2b-runs/e2b-20260913T0342EEST-39260f05-r1/phases/e2b-20260913T0342EEST-39260f05-r1.20260913T013949Z.HKBFtH/result.json`. Сам приватный result в рамках извлечения не читался; факт PASS зафиксирован handoff, относится к retained B/старому кандидату.
- `.maestro/mvp/arena-matrix/README.md:8–26`: physical Back helper — только навигационный маршрут, не Arena financial acceptance.
- `:28–63`: response-loss proxy — инструмент будущего fault run; fake HTTP тест не device proof.
- `:65–82`: pending SETTLE reader подтверждает только local exact ticket/outbox/intent state, не server outcome; текущая точная v14/v15 allowlist.
- `:84–189`: выключение радио при adb reverse не доказывает offline. Нужны подтверждённый полный network cut, survived ADB control и последующее server proof. Fake ADB тест не меняет устройство/финансы и не доказывает настоящий offline.
- `:193–242`: sealed launcher online prepare→offline finish останавливается overall NOT_RUN даже после offline leaf; доставка, settlement oracle и rating — отдельные стадии. Recovery/delivery retained record объявлены отдельной будущей работой. Это недостающая приёмочная связка, не новое продуктовое требование.
- Known quiescence defect из HANDOFF и tooling spec блокирует будущий запуск до repair/проверки; в PRD считать зависимостью достоверной приёмки, а не новой feature.

## Уточнения согласованного глобального плана

`_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md` (точный путь от корня кандидата).

- Строки 37–40: первый полный E2B — SELECTED/HARD, сохранённый ответ/restart, SETTLE и rating2 одной попытки; после PREPARE не создавать замену при ошибке.
- Строки 44–51 добавляют обязательные EASY/HARD, Rating Queued→restart→явная sync, позднее завершение отменённого ticket и **разную диагностику offline и недоступного backend**.
- Строки 59–62 уточняют ТЗ06A/B/C/D: новая версия **требует нового допуска**; полный состав сезона без усечения. Это снимает неопределённость старого «согласовать допуск новой версии».
- Строки 86,90–101: последующее поручение — физические телефоны, Android-эмулятор не использовать; один итоговый candidate с совпадающими code/backend/APK/schema/config/flags; полный ciCheck/матрица, личная приёмка владельца, ACCEPTANCE.md и MANUAL-CHECK.md. Текущая pause запрещает исполнения сейчас.
- Строки 7,98–101: реализованное без доказательств не приёмка; перенос в основной KMP и публикация остаются позже. Никакой новой оценки процента готовности.

## Открытые продуктовые вопросы для Coaching (без принятия новых решений)

1. **Предварительное согласие на заряды:** должен ли пользователь до платной подготовки видеть и подтверждать точный максимальный расход зарядов, или достаточно принятого предупреждения о резерве с точным frozen hold после выдачи? В источнике это явно оставлено открытым (ТЗ‑02:48); потолок ноликов уже решён. Спрашивать только если Discovery выбирает UX, обещающий такой потолок.
2. **Место замены оценки:** должна ли замена уже принятой оценки быть доступна из того же завершённого прохождения без нового прохождения? Заменяемая текущая оценка уже обязательна (договор:17, глобальный план:62), само требование не переоткрывать. В текущем клиенте этот путь не представлен; сначала проверить прочие accepted UX-источники.
3. **Измеримая скорость обычного урока:** какое время открытия первого вопроса после полной синхронизации пользователь считает приемлемым на выбранном эталонном телефоне? Текущие 67s/5487ms — время Maestro с overhead, а не готовый критерий продукта. Это вопрос успеха продукта, не выбор реализации. Перед вопросом сверить остальные NFR: если там уже есть принятый порог, применять его, а не спрашивать снова.

Два первых вопроса локальны к Арена UX; третий межсрезовый и опционален. Необходимость author/curator path, полная цена/сезонная политика, новые версии с новым допуском, правила квот и внешняя публикация уже решены; их не спрашивать. Измерения ресурсов/выбор библиотек/импорт другой ветки/worker rollout — технический addendum и будущая реализация, а не вопросы владельцу здесь.

## Уточнение перед Coaching: ограниченная проверка принятых UX-документов

Проверены четыре релевантных документа текущего кандидата; поиск ограничен этими темами, не является полным аудитом всех исторических записей.

### Согласие на charge-cost

- `_bmad-output/specs/spec-charges/SPEC.md:70–82,145–154` задаёт серверные constants, последнюю синхронизированную таблицу для показа/affordability и тариф активности; это **не отдельное обещание точного максимального lifeCost, одобренного перед выдачей Arena ticket**.
- `_bmad-output/specs/spec-charges/settlement-protocol.md:64–68` содержит старую схему: для ordinary/Arena серверного резерва нет, только local hold до результата. Для нынешней Арены это противоречит `PRODUCT-CONTRACT.md:23–26` и заменено одноразовым серверным ticket/reserve. Нельзя использовать старый companion для отмены нынешнего server reserve либо молчаливого release после crash.
- Новой принятой UX-нормы об отдельном ceiling зарядов в этих источниках не найдено. Действующие обязательства уже определены: предупреждение о charge reserve, явное согласие на платную подготовку, ceiling ноликов, затем точный frozen lifeCost. Уточнение отдельного charge ceiling остаётся **условным расширением UX/протокола**, не обязательным блокером всего PRD. Не спрашивать заново тарифы или необходимость резерва.

### Изменение принятой оценки

- `docs/features/lesson-runner/0-spec.md:150,493–496,643–648` действительно имеет старое USER DECIDED правило: rating только после perfect и один раз за lifetime пары user/lesson.
- Для Арены это **устаревшее противоречие**, а не современная продуктовая альтернатива: `PRODUCT-CONTRACT.md:17` и глобальный план:62 уже требуют одну актуальную заменяемую оценку после подтверждённого прохождения. Perfect/lifetime restriction нельзя возвращать в Arena PRD.
- Ни один из проверенных источников не уточняет, как открывается **повторное редактирование**: тот же сохранённый результат без нового прохождения или результат следующего прохождения. Это настоящая узкая UX-неопределённость. Вопрос касается места/момента изменения, не разрешения замены как таковой.

### Время открытия урока

- `docs/features/lesson-runner/0-spec.md:201–215` содержит численный показатель **<100ms от tap до UI change при вводе/ответе**, а также timer tick100ms. Это не время открытия первого вопроса.
- `docs/features/home-and-my-quests/0-spec.md:146–155,657–665,690–708` задаёт offline-first, мгновенный показ cached списков и вопросы, уже находящиеся в Room при открытии урока. Численного lesson-opening SLA в прочитанных NFR/сценариях нет.
- Значит нельзя превращать <100ms в скрыто принятый SLA запуска или 5487ms Maestro в измерение его выполнения. Уточнение измеримого времени от нажатия «начать» до интерактивного первого вопроса на эталонном устройстве остаётся реальным **опциональным success-критерием**.

Рекомендация для краткой Coaching-повестки: основной вопрос — UX повторного изменения оценки; при обсуждении качества — численный критерий открытия уже синхронизированного урока. Отдельный ceiling зарядов поднимать лишь если владелец ожидает подтверждение точной суммы зарядов до prepare. Уже согласованные замена оценки, серверные тарифы/reserve, offline-first и границы MVP повторно не согласовывать. Новых решений этим приложением не принято.

## Coaching: правки языка/опечаток и общий редактор

Проверка только документов, без исследования реализации и без новых продуктовых решений.

**Уже принятый обязательный объём.**
- `P/06-LESSON-LIFECYCLE.md:13,17,21–23,36`: авторское создание/редактирование, отдельная версия урока, независимые проверки, статус/причины/история, явный куратор. Правка не изменяет банк уже выданного ticket или созданного события.
- `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md:61`: новая версия требует нового допуска; выданное содержание сохраняется. Исключения «мелкая опечатка не требует допуска» договор не задаёт.
- `P/PRODUCT-CONTRACT.md:3,7–10,23,33`: новый договор приоритетен; авторство, независимость проверок, причины и история сохраняются; tickets связаны с версией, турнирные наборы неизменны.
- `system-specs/quest-authoring-lifecycle.spec.md:84–93,548–550,561–562`: автор исправляет своё и отправляет новую revision; публичные вопросы не меняются прямой записью клиента; язык/сложность — поля вопроса, default языка берётся из настроек/профиля. Админ не редактирует приватный draft автора.
- `system-specs/quest-authoring-lifecycle.spec.md:619,635–637`: проверяется неизменяемая отправленная копия; проверяющий может сообщить проблему, автор исправляет и отправляет новую копию. Самостоятельная правка текста проверяющим выделена в отдельный moderation/public-edit flow после базовой схемы.

**Что не равно «все могут исправлять чужой опубликованный текст».**
- `docs/architecture/0005-quest-lifecycle.md:51–64,93–107` описывает переводчика/корректора и право оценщика исправлять перевод/логику с авторством ревизии. Это роль в review, не право любого игрока немедленно менять live question. Автоматические полки этого старого ADR заменены новым договором и здесь не наследуются.
- `system-specs/quest-authoring-lifecycle.spec.md:624,649,680` явно относит **предложение дополнения существующего публичного квеста** к последующему расширению. Полный workflow предложений **исправлений от любого игрока** в проверенных ТЗ‑06, договоре и согласованном плане не найден; разрешить всем редактор или очередь таких proposals — новый уточняемый объём, а не условие уже принятого MVP.
- `_bmad-output/specs/spec-quest-lifecycle/SPEC.md:83–85` уже предусматривает сообщения о контенте через жалобы; это не редактирование и не полноценный workflow предложенной текстовой замены.

**Минимальный вариант, который владелец может выбрать без сокращения принятого MVP:** отложить общий редактор/предложения правок от всех; сохранить редактирование автором, сообщения о проблемах и независимые проверки, новую revision и повторный допуск, неизменность выданных tickets/событий. Формулировка «редактирование пока вообще не делаем» была бы слишком широкой и убрала бы уже обязательное authoring/editing. Полноценные языки вопросов и переводческую проверку такой выбор тоже не отменяет. Если выбирать предложения всем — сначала определить, кто принимает предложенный текст и как он становится новой проверенной версией; не давать прямую запись в опубликованный вопрос.

Оговорка: это классификация требований по источникам, а не доказательство готового UI жалоб, авторских revisions либо редактора проверяющего на текущем APK. Решение по новому расширению остаётся за владельцем.
