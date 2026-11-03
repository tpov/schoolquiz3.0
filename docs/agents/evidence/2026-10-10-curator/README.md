# 4.8 curator-transitions — evidence (2026-10-10)

История: кураторские переходы несут авторизованное решение и видимую причину (long-press меню
по AD-21); рейтинги никогда не заменяют кураторского одобрения; несостоявшаяся замена сохраняет
валидную прежнюю ревизию; подтверждённая неисправность запрещает новое использование, не
переписывая замороженные попытки.

Смена 10 (предыдущая, прервалась ~20:46) сделала код и начала живой прогон: коммит 5d4d0099
(переходы за long-press меню с подтверждением — короткое нажатие открывает урок, AD-21),
9139c685 (demo App Check provider — rig-callables гибли на живом обмене токенов debug-провайдера)
и незакоммиченный JWT-вид токена, без которого SDK браковал константу до отправки (дозревало до
b9cf228b этой смены вместе с тестом на разбираемость claims). UI-фаза прогона дошла до rig-78
(сервер отказал INVALID_TRANSITION на невалидном переходе arena→home — поименованное
«несостоявшаяся замена» живьём) и оборвалась. Эта смена реконструировала состояние по дампам и
серверу, довела journey до конца, собрала гейты и оформила файлы.

## Участники (demo-backend: контейнер schoolquiz-demo-firebase-20261004, auth 19090 / firestore 18090, + хостовый functions-эмулятор 5001, поднятый с env)

- куратор на риге `uwNZMpyUKJoOKJEOcpH7KO44E1Dx` (User44E1DX, adminLevel 100) — анонимный
  аккаунт, созданный ригом в прерванной смене (~19:45), роль по plan §2 Шаг 4;
- игрок-фикстура `cur48r3player0000000` (auth-юзер этой смены) — замороженная попытка и живой
  рейтинг;
- уроки: `ac_0ecb34aeec8b4557b13a390f10b3a548_lesson_1/2/3` — синтетические catalog-фикстуры
  4.1/4.2 (полная цепочка quest→section→theme→lesson, reviewApproved, полка arena);
- APK рига: 0.1.0-isolated, sha256 0ac7f57e… (вытянут с рига и переxэширован после прогона,
  `apk-sha256.txt`; JWT demo-токен внутри подтверждён) — байт-в-байт равен текущей сборке.

## Обязательства истории — доказательства

1. **Авторизованное решение + видимая причина (long-press, AD-21)** — живой прогон:
   `work-ui/rig-79…84`. Длинное нажатие на карточку урока открывает меню переходов (To arena /
   To tournament / To home / Retire / Reject, rig-81); короткое нажатие открывает сам урок
   (5d4d0099, тесты DefaultCuratorComponentTest). Диалог подтверждения называет урок, цель и
   «The reason is recorded with the decision» (rig-82); пустая причина блокируется клиентом
   раньше транспорта («Name the reason for the transition first», rig-76/77 из прерванной
   смены). Валидный переход lesson_1 → To tournament с причиной «curator journey promotion
   tournament» → тост «Applied: lesson placed to tournament» (rig-84), сервер:
   placement v2, `decidedByUid` = куратор, `lastReason` = причина, история APPLIED
   arena→tournament с mutation key `transition-yeal77y9o9wc` (run/06 + history-view — причина
   бесплатнотекстом лежит серверно, view отдаёт куратору поименованный reasonCode; сам текст
   решения в интерфейсе истории не рендерится — граница честно, контракты view/хистори
   доказаны тестами и живым чтением). Клиент шлёт переход прямым callable с AD-2-ключом
   мутации (TransitionLessonUseCase: ключ приходит снаружи и не меняется между попытками
   одного намерения) — не через outbox; повтор ключа возвращает ту же квитанцию.
2. **Рейтинги не заменяют кураторского одобрения** — живой прогон: реальная попытка-фикстура
   (см. п.4), затем живой `rateArenaLesson` (реальный callable, игрок ≠ автор): APPLIED,
   агрегат 4/2 → 7/3 (run/05-rating-callable-receipt.json; повтор того же mutationId вернул
   ту же квитанцию decidedAtMs — идемпотентность). placement-документ урока при этом
   **байт-в-байт не изменился** (05-before/05-after, shelf arena, placementVersion 1);
   в lesson-документе изменились ровно пять рейтинговых полей (averageRating/ratingCount/
   ratingVersion/ratingUpdatedAtMs/lastModifiedAt). Каталог/полки не сдвинулись. Серверная
   сюита arena-service.test.js:308 («ratings alone never admit a lesson», forge-readmit не
   куратора → CURATOR_REQUIRED) закрепляет то же на уровне контракта.
3. **Несостоявшаяся замена сохраняет валидную прежнюю ревизию** — два живых случая:
   (a) UI-отказ прерванной смены: переход arena→home отклонён сервером INVALID_TRANSITION
   (rig-78, баннер «Rejected by server (INVALID_TRANSITION) for target home»); placement
   остался shelf arena, placementVersion 1, отказ записан в lastRejection + историю REJECTED
   с причиной (run/03-failed-replacement-kept.json + history-view, зафиксировано ДО
   валидного перехода); (b) probe не-куратора (п.4 ниже) — переход отклонён, полка не тронута.
   Серверная сюита закрепляет ветку rejection-пути (lastRejection «attempted skipped rung» +
   shelf сохранён + REJECTED-запись в истории).
4. **Подтверждённая неисправность запрещает новое использование, не переписывая замороженные
   попытки** — живой прогон: на lesson_3 посеяна SETTLED-фикстура попытки по адресу, который
   читает живой loadCompletion (result_events_public/cb/events/cb0a8b535ed0_…, конвенция
   tz06d; попытка — фикстура, всё поверх неё — реальные пути; seed в тулe
   curator-journey.js). Затем куратор через UI (long-press → Retire → причина «confirmed fault
   broken answer key» → Confirm) увёл урок: тост «Applied: lesson placed to retired» (rig-89),
   сервер placement v2 shelf retired + история APPLIED arena→retired с причиной (run/08).
   Замороженная попытка после отзыва — все засеянные поля байт-в-байт, квитанция SETTLED
   нетронута (08-frozen-attempt-intact.txt). «Новое использование» закрыто: игрок-каталог
   `listArenaLessons` (источник новых попыток) до/после — ушли РОВНО два урока: повышенный
   lesson_1 (tournament) и неисправный lesson_3 (retired), ничего больше (run/04 vs run/08).
5. **Только куратор** — живой probe: `setLessonPlacement` под игроком → REJECTED
   CURATOR_REQUIRED (run/07-curator-required-probe.json).

## Оракул дерева (run/00-before-tree → run/09-after-tree, run/10-tree-oracle.json + accounting)

10 строк дифа, каждая учтена, необъяснённых касаний ноль:

- эта смена: 4 changed (placement+lesson обоих переходов), 1 changed + 1 added (агрегат и голос
  живого рейтинга);
- прерванная смена (до UI-фазы, между 00 19:49:58Z и снапшотом run/02 ≈20:34Z): 2 removed +
  2 changed — синтетические aeu-фикстуры сентября: настройка добавила questType REGULAR всем
  четырём placement-докам (run/02 фиксирует промежуточное состояние) и затем удалила две
  stale-строки («placement-repair» в именах run/01–02); удаление не атрибутируется уцелевшим
  скриптом — полные before-состояния всех четырёх документов сохранены в run/01 и run/02.
  Ни одна команда journey этой смены aeu-документы не касалась; поведение eligible-universe на
  stale-строках покрыто arena-eligible-universe.test.js и живыми доказательствами 4.2.

Истории переходов (подколлекции lesson_placements/*/history) вне набора меток снапшота — так же,
как в 00; доказательства истории — run/03, 06, 08 capture + history-view.

## Гейты (только sgw для gradle; functions — node)

- functions node: **96/96 сюит** (`gate-functions-node.log`), включая 308-й тест
  «ratings never manufacture curator approval; confirmed-fault removal stops new use and leaves
  frozen attempts intact»;
- JVM quest-authoring domain + data + presentation: **BUILD SUCCESSFUL**
  (`gate-jvm.log`), включая расширенные DefaultCuratorComponentTest (5d4d0099: меню за
  long-press, причина обязательна, короткое нажатие открывает урок);
- firebase: 275 тестов, ровно 2 предсуществующих fixture-pin отказа
  (FirebaseArenaLessonCatalogRemoteDataSourceTest, FirebaseArenaTicketRemoteDataSourceTest —
  атрибуция 2026-10-08, регенерация капчур-фикстур — процедура владельца; `gate-firebase.log`);
- ktlint модулей истории (presentation + platform:firebase тесты): **BUILD SUCCESSFUL**
  (`gate-ktlint-story.log`); полная ktlint-проверка platform:firebase красная на файлах
  4.3-эры — вне матрицы истории, список владельца;
- detekt обоих тронутых модулей: предсуществующий красный плагина («Invalid value (27) passed
  to --jvm-target») — конфигурационный отказ до анализа кода, список владельца
  (`gate-detekt.log`).

## Границы (честно)

- Скриншоты рига чёрные (FLAG_SECURE) — доказательства с экрана = дампы uiautomator
  (work-ui/rig-70…89).
- Причина перехода хранится серверно (placement.lastReason, история) и обязательна на входе;
  history-view отдаёт куратору поименованный reasonCode, бесплатнотекстовый рендер причин в UI
  истории — UX-хвост следующих историй (view-контракт и хранение доказаны).
- Переходы идут прямым callable с AD-2-ключом (не outbox): смена аккаунта/офлайн-повтор
  перехода — предмет путей синхронизации, покрытых эпиком 2.
- Замороженная попытка — засеянная фиксстура (tz06d-конвенция) по адресу живого loadCompletion:
  сытьё попытки реальным приёмником потребовало бы билетного банка контента, которого у
  синтетических catalog-фикстур нет (lesson_content пуст); всё поверх фикстуры (rating,
  retire, каталог, байт-сверка) — реальные пути.
- RIG-эмулятор остановлен после блока проверок (docker stop schoolquiz-emu); demo-backend не
  тронут (постоянное решение владельца). Файлы владельца (.maestro/**, local.properties) не
  менялись; gradle.properties остался в состоянии более ранних смен (список владельца).
