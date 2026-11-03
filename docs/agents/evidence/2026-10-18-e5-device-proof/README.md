# 5.13 device-proof — первая живая сессия Epic 5 на физическом устройстве — 2026-10-18

Story: 5.13 device-proof-hitl (эпик 5, соревнования). Устройство владельца: **Pixel 10 Pro
(serial 56271FDCH00C5S), Android 17 (API 37), USB к Mac**, приложение `com.tpov.schoolquiz.mvp`
4.0.0-isolated, ветка `device/e5-13-proof` (worktree `.local/worktrees/e5-device`, база 7f84599f =
«ticket 5.12 done»). Демо-риг: `schoolquiz-demo-firebase-20261004` (auth 9099/firestore 8080,
опубликовано на host как 127.0.0.1:19090/18090) + `schoolquiz-functions-emu` (0.0.0.0:5001),
проект `demo-schoolquiz-mvp`. Аккаунт устройства: **uid qZMm4Fw5eNGy1YJSKX8p9YZo323d
(UserZO323D)**; оппонент **1qFe2HStSQbFy1meqKwwj3U2RqO2**; событие
**event_898965896ba94090a5c4457ca8dae3d6** (QUALIFICATION, окно с 16:17 device / 13:17Z,
7 суток); эскроу {reserved 10, remaining 10, paid 0, returned 0}; кошелёк устройства 100→90.

## Что доказано на устройстве (скриншоты-состояния в /tmp Mac, ключевые шаги ниже)

1. **Запуск и вход**: анонимный вход в демо-риг, bootstrap профиля (UserZO323D), синк.
2. **Организаторский маршрут 5.10 целиком**: Events → «Organize a tournament» → курс
   `dp_b8b37fbc029e41d6b75b52989040de73_course` (80 вопросов, 60 EASY/20 HARD, владелец = uid
   устройства) → «Prepare the course bank» → **«Bank ready: 60 easy, 20 hard questions»**
   (вызов prepareCourseTournamentBank, auth VALID, 2.6с) → согласие с замороженными условиями
   (5/3/1/1, депозит 10, «Opens at») → **«Tournament created»** + readout эскроу
   (Reserved 10 / Remaining 10 / Paid 0 / Returned 0), сервер: статус QUALIFICATION, кошелёк
   100→90 — депозит списан ровно один раз.
3. **Обнаружение**: браузер «Active events» показывает живые события рига (4× Qualification
   open, Bracket running); деталь события — призы v1 и честное «No ranking has been published
   for this event».
4. **Допуск**: оверлей «Admission» → «I downloaded the course» → **«Download attested by the
   server»** (база COURSE_DOWNLOAD в `course_tournament_downloads`).
5. **Участие**: маршрут «Pick an event» → START_QUALIFICATION («Round 1: In progress») →
   **вопрос отрисован на устройстве** («Which keyword declares a read-only binding?»,
   варианты [ ]var/[x]val/[ ]lateinit), выбор опции, «Submit answer» → честные AD-21-состояния
   («Answer is being delivered…», «Waiting for an earlier operation to finish»).
6. **Оппонент по HTTP**: `scripts/course-tournament-device-opponent.js` — download + 3 раунда
   × 20 ответов, все SETTLED (транспорт и серверная машина квалификации работают end-to-end).

## Находки device-proof (реальные дефекты, невидимые JVM-тестами и redroid)

1. **[исправлено на ветке] GMS-транспорт заворачивает Kotlin Long в protobuf-обёртки**:
   `{"@type":"type.googleapis.com/google.protobuf.Int64Value","value":"5"}` — снято tcpdump'ом с
   провода (START round/create). Серверный контракт (`exact()`) отвергает объект →
   INVALID_ARGUMENT на каждый CREATE/START/PROPOSE/CONFIRM/READY с числовыми полями.
   Проявляется ТОЛЬКО на устройстве с GMS (redroid без GMS шлёт обычные числа; http-харнес —
   числа из Node). Фикс: `FirebaseTournamentRemoteDataSource.wireLong()` — Long → Double
   (точно до 2^53; double GMS не заворачивает). После фикса в проводе обычные числа
   (`"startsAtMs":1792328939837`), CREATE с устройства прошёл.
2. **[исправлено на ветке] Согласие замораживает прошедший startsAtMs**: UI фиксирует
   `startsAtMs = now()` в момент открытия диалога; сервер ОТВЕРГАЕТ прошедший startsAt
   (проверено зондами: future → SOURCE_NOT_ELIGIBLE (форма ок), past → INVALID_ARGUMENT).
   Пользователь, продержавший диалог дольше секунды, всегда получал отказ. Фикс:
   `DefaultTournamentOrganizerComponent` — `nowMs() + CREATE_START_SLACK_MS (60_000)`.
3. **[открыто, instrumentировано] Доставка ответа через outbox не доходит**: строка
   `tournament.SESSION_OP` (ANSWER, round 1) висит WAITING; send() доходит до транспорта
   (guard isOnline=true),callable падает INTERNAL/Connection reset. **Корень эпизода 17:12–17:55:
   schoolquiz-functions-emu был OOM-killed (Exited 137, OOMKilled=true) — VPS ушёл в 8/9 GB
   (мои gradle-сборки 3g heap + эмулятор).** После рестарта эмулятора и освобождения памяти
   (6 GB свободно) новых попыток дрены не было — SyncWorker читает каталог/профиль (2× вызовы
   в логе), но строку outbox не берёт (attempt не растёт) — причинa не установлена, см.
   «Следующей смене». В транспорте оставлены диагностические логи `MvpOutbox` (guard/код/стек).

## Отклонения и решения (на усмотрение владельца при слиянии)

- **Подпись mvp-сборки = debug-ключ** (было legacy jks): пароль legacy — секрет владельца,
  на риг-сборках недоступен; `.mvp` — изолированный applicationId, поверх прод-приложения не
  встаёт. Старое приложение 0.1.0-isolated снесено с устройства (другая подпись, данные прошлой
  эпохи рига).
- **Хост эмулятора параметризован**: `-PdeviceEmulatorHost=<lan-ip>` → BuildConfig.EMULATOR_HOST →
  `initializeFirebaseSecurity(emulatorHost)`; + LAN-хост сессии 192.168.31.218 добавлен в
  `src/mvp/res/xml/mvp_network_security_config.xml` (cleartext к ригу; GMS-стек firebase-auth
  блокирует cleartext вне 127.0.0.1 — «Cleartext HTTP traffic not permitted» — finding сессии).
- **Сеть устройства**: `adb reverse` на этой связке (platform-tools 36 ↔ adbd Android 17)
  TCP принимает, данные не несёт (долго диагностировалось; toybox-nc давал ложные «пусто»).
  Рабочий путь: ssh -L на LAN-интерфейсе Mac (9099→19090, 8080→18090, 5001→5001) + Wi-Fi.
- **Роли**: пункт «Active events» требует TESTER+MODERATOR+ADMIN+DEVELOPER ≥ LEVEL_1; выдано
  `users/{uid}` {tester,moderator,admin,developer}=100 и локально в Room user_stats
  (sync не переносит эти поля из профиля — отдельный кандидат в backlog). pm clear
  com.tpov.schoolquiz.mvp выполнялся дважды при диагностике входа.
- **Сессия ответа попала на старое harness-событие** `ct_41c506b5…` (первая карточка «Pick an
  event» — окно ещё открыто, 7 суток): строка ответа в outbox относится к нему. Чистый прогон —
  на нашем событии (вторая карточка) после починки доставки.

## Команды продолжения

- Риг: контейнеры живы; functions-emu перезапущен после OOM (`docker start
  schoolquiz-functions-emu`); /etc/hosts (schoolquiz-mvp-firebase→172.17.0.3), симлинк /repo,
  форвардер :5001 в firebase-контейнере — на месте. Память VPS: держать ≤1 тяжёлую сборку;
  OOM-убийство эмулятора — причина второго блокера этой смены.
- Препарация фикстур: `node scripts/course-tournament-device-prep.js <device-uid>` (из
  worktree, env как в http-харнесе). Оппонент: `node scripts/course-tournament-device-opponent.js
  <eventId> <courseId> <uid> <token>`.
- Сеть Mac: `ssh -N -o ControlMaster=no -o ControlPath=none -b <lan-ip> -L
  <lan-ip>:9099:127.0.0.1:19090 -L <lan-ip>:8080:127.0.0.1:18090 -L <lan-ip>:5001:127.0.0.1:5001
  rnsrv` (форварды, стартованные из-под песочницы/мультиплексора, соединения с LAN молча
  теряли — отдельная ловушка этой смены).
- Сборка: `sgw ./gradlew :apps:android-next:assembleMvp --no-configuration-cache
  -PdeviceEmulatorHost=<lan-ip> "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=512m"
  JAVA_HOME=/opt/jdks/jdk-27`. Xmx3g — разовое решение: mergeExtDex/R8 не влезает в 1024m из
  gradle.properties (сначала OOM на mergeExtDexMvp). Требует решения владельца на постоянство.
- Дрена ответа: строка outbox (id 1) due; триггер — рестарт приложения (WorkManager); после
  доставки — раунды 2/3 на нашем событии, CLOSE свипом (in-process контроллер, как в
  lifecycle-demo), встреча/матч (устройство PROPOSE/CONFIRM/READY по HTTP, оппонент — скрипт),
  сеттлмент свипом, результаты.

## Итог смены

Organizer-маршрут (prepare → согласие → CREATE → эскроу → дебет кошелька), обнаружение,
допуск и старт квалификации доказаны на физическом устройстве против живого демо-рига.
Найдены и исправлены на ветке два проводных дефекта клиента (Int64Value-обёртки, прошедший
startsAt) — оба прошли бы в прод. Открыт пункт: доставка outbox (instrumented, причина
квалифицирована: OOM-убийство эмулятора в эпизоде; пост-рестартное поведение дрены —
первый шаг следующей смены). Встреча/матч/награды на устройстве — после доставки ответа.
