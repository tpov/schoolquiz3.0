# Отчёт трека E — лёгкие хвосты эпика 1 (ночь 2026-10-05)

Ветка: `night/e1-followups` (от `b8bc23ff`), дерево `/workspace/projects/schoolquiz3.0/.local/worktrees/night-e1f`.
Сборок Gradle не было, установок на устройства не было, владелец-файлы (`offline-settlement-contract.test.js`,
`local.properties`) не менялись и не коммитились. Всё закоммичено локально, без push.

## 1. Памятка по RED-базе ciCheck — СДЕЛАНО (без новой сборки)

Записанный результат 04.10 подтверждён чтением evidence (новой сборки не было — по правилам ночи):

- `docs/agents/evidence/2026-10-04-root-cicheck/cicheck-result.json` (дерево mvp-candidate):
  **FAIL, exit 1**, единственная упавшая задача `:apps:android-next:processDebugGoogleServices`
  («File google-services.json is missing»), HEAD `1e596126`, команда
  `JAVA_HOME=/opt/jdks/jdk-27 ./gradlew ciCheck --no-configuration-cache --no-parallel --max-workers=1`.
- `root-cicheck-run1.log` (BUILD FAILED in 19s) и `root-cicheck-run2-exit-code.log`
  (BUILD FAILED in 15s, EXIT_CODE=1) — детерминированный повтор. Тесты/detekt/ktlint/
  functionsTest/scriptsTest не исполнялись (cicheck-result.json:44).
- Предусловие RED перепроверено сегодня в дереве b8bc23ff без сборки: все 4 пути поиска
  плагина пусты (проверено `[ -f ]` по каждому), mvp-вариант закоммичен (`git ls-files`),
  `.gitignore:19` `**/google-services.json` и `.gitignore:54` `debug/` исключают debug-файл.
  Определение гейта — `build.gradle.kts:68-84`.

Оформлено в `docs/agents/evidence/2026-10-05-cicheck-baseline/` (README.md с памяткой +
cicheck-baseline.json): условие снятия RED (owner-локальный debug `google-services.json`
в `apps/android-next/src/debug/…`, файл не коммитится по политике; затем повтор базовой
команды и запись результата) и блок «что это НЕ доказывает» (RED — не сигнал о качестве
кода; кандидат не сломан — assembleMvp зелёный с APK `eeb8fc6e…`; поведение на Mac
владельца не описывает; зелёный перезапуск не доказывает валидность прод-конфига Firebase).

Черновик истории **1.7 «re-run ciCheck и записать»** добавлен в
`_bmad-output/mvp-candidate/epic-candidate-rig/tickets.toml` ЭТОГО дерева — **без поля
status** (TOML валиден: python3 tomllib; 1.7 — единственная история без status), `hitl=false`
(Gradle-прогон, не устройства; вход — owner-локальный файл).

## 2. Proposal подключения 17-кейсового контракт-теста — СДЕЛАНО

Файл: `docs/agents/night-2026-10-05/proposal-quiescence-contract-test-wiring.md`.

- Тест владельца прочитан дословно, не менялся, не коммитился. Дифф против
  закоммиченной версии (428 → 459 строк) подтверждён: хук `FAKE_PREFLIGHT_QUIESCENCE_JSON`,
  тест `testInvalidQuiescenceStopsBeforePreparationAndTransportCut` (строки 389–407)
  и матрица 17 кейсов (строки 446–455: `{}`, null, `[]`, `[0,0,0,0,0]`, `""`, лишний
  ключ, строка-«0», по каждому из 5 счётчиков — отсутствие и `=1`).
- Окружение VPS проверено: `node --version` → **v24.14.0**, `npm --version` → **11.9.0**.
- Тест владельца запущен дословно (без модификаций) дважды этой ночью:
  - в `node:22-bookworm-slim`, repo :ro, `--network none`: `offline settlement composition tests: PASS`, exit 0, 2m34.9s;
  - хостовым node v24.14.0: PASS, exit 0, 2m30.6s.
- Baseline до диффа: `cd scripts && npm test` → exit 0, 2.588s (14 файлов).
- Зависимости теста идентичны между b8bc23ff и рабочим деревом владельца (`diff`:
  leaf/guard/runner/reader и все 5 flows — IDENTICAL); единственные незакоммиченные
  правки владельца — сам тест и `local.properties` (git status mvp-candidate).
- В proposal: обязательный порядок (сначала владелец коммитит тест, иначе цепочка
  молча гоняет старую 428-строчную версию — ложное покрытие), дифф `scripts/package.json`
  (в `test` — `&& node ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js`,
  в `lint` — `&& node --check …`), обоснование через `scriptsTest` → `ciCheck`
  (`build.gradle.kts:60-66,76`), цена ~+2,5 мин к npm test. Сам `scripts/package.json`
  НЕ менялся; применение диффа проверено in-memory на копии (merged JSON valid: true).

## 3. Runbook утренней hitl-сессии 1.3/1.5 — СДЕЛАНО

Файлы: `docs/agents/night-2026-10-05/runbook-hitl-1.3-1.5.md` +
`evidence-templates/install-over-evidence-template.json` (1.3) и
`evidence-templates/device-inventory-template.json` (1.5) — оба валидный JSON, все
значения `null`/пустые для утреннего заполнения.

- Пошаговые adb-команды с Mac: сверка APK (`shasum -a 256` против
  `eeb8fc6e6c4dcc6f06ba3168c3a945323450cc3e8b50942975797198a63a5fbb`, apksigner против
  cert `00:33:E7:FA…`; APK 42 038 981 байт — docs/agents/2026-10-04-mvp-apk-identity.json),
  SSH-туннель к VPS-loopback 18090/19090 + `adb reverse tcp:9099→19090, tcp:8080→18090`
  (приложение хардкодит `127.0.0.1:9099/8080` — FirebaseInitializer.kt:26-31,49-55;
  практика «reverse только 9099 и 8080» — HANDOFF.md:43),
  фиксация состояния ДО (`dumpsys package`, бэкап schoolquiz.db/-wal/-shm через
  `run-as cat` после `force-stop`, shared_prefs, счётчики outbox/lesson_attempts/
  lesson_attempt_intents/arena_ticket_records/arena_frozen_rating_intents/mutation_receipts,
  room_master_table против identityHash v15 `e3a1b9e13aff4304afeba070b8895b6c` — из
  schemas/…/15.json), `install -r` с правилом СТОП на INSTALL_FAILED_UPDATE_INCOMPATIBLE
  (без uninstall/clear), чек-лист сохранности (аккаунт, кошелёк, уроки, история,
  identity установки, pending-операции) «до/после», инвентаризация 1.5 для Samsung
  `RF8W80MBR3Z` и Pixel, раздел запретов.
- Все adb/sqlite-команды взяты из реально прочитанного кода (имя БД `schoolquiz.db` —
  PersistenceModule.kt:48-52; таблицы — @Entity tableName в shared/core/persistence;
  launcher `.MainActivity` — AndroidManifest.xml:16-25; `allowBackup=false` — :15).
- На устройствах ночью ничего не запускалось — runbook не исполнялся (hitl).

## 4. Контрольный экспорт демо-бэкенда — НЕ ВЫПОЛНЕН (объективная причина), состояние задокументировано

**Заданный экспорт не сделан**: на момент задачи (после 18:00Z) контейнер
`schoolquiz-demo-firebase-20261004` был уже **EXITED** — `docker exec` невозможен,
а перезапускать контейнер мне запрещено правилами ночи («Контейнер не останавливать/
не перезапускать»; владелец по evidence 04.10 — владелец stop/start). При первом
просмотре ночью (~17:16Z) контейнер был Up 24 hours; я его не останавливал (все мои
docker-операции — ps/inspect/logs/port и свежий `docker run --rm` для теста §2).

Forensics (всё — чтение):

- `docker inspect`: ExitCode **137**, OOMKilled **false**, FinishedAt **2026-10-05T17:59:47Z**.
- `docker logs --tail 30`: извне пришёл SIGTERM → clean shutdown → начал
  `--export-on-exit /tools/firebase-output-root/export` → убит SIGKILL посреди экспорта.
- Прерванный экспорт **не записал ни файла**: оба каталога вывода
  (`control-save-1`, `export`) — по 12 файлов, mtime 2026-10-04T17:37, временных
  остатков нет.
- Целостность данных на диске проверена: staged-источник импорта — sha256 дерева
  `2e4237d84530f63520703c519803e3e4edd6f629fa8259e920bc4e7ba9e898ff` (совпадает с
  манифестом 04.10); control-save-1 и export — по **12 файлов / 98 auth-пользователей**
  (`len(accounts.json['users'])`).
- Следствие: состояние эмулятора в памяти после рестарта 04.10 (~17:41Z) потеряно;
  последняя durable-состояние — база 04.10. Источник SIGTERM по логам не определяется.

Оформлено: `docs/agents/evidence/2026-10-05-demo-backend-night-state/backend-night-state.json`
(taskStatus NOT_PERFORMED + вся хронология + готовая команда экспорта для будущего
прогона), примечание добавлено в runbook §0 (утро начинается с
`docker start schoolquiz-demo-firebase-20261004` — по дизайну переимпортирует
read-only staged export). Каталог `control-save-night-20261005` НЕ создавался —
пустышка не нужна.

## 5. Утренний подъём 06.10 (решение владельца) — ВЫПОЛНЕНО, задача 4 закрыта

Решение владельца: поднять `schoolquiz-demo-firebase-20261004` из staged-экспорта
тем же способом, что при восстановлении 04.10, **без** `--restart`-политики. Выполнено
06.10 (контейнер StartedAt **2026-10-06T20:05:20Z** UTC); способ — `docker start`
существующего контейнера, как в evidence §6 восстановления 04.10 и примечании runbook §0.

Пред-проверки (до подъёма):

- Контейнер в состоянии из §4: `Exited (137)`, FinishedAt `2026-10-05T17:59:47Z`
  (`docker inspect`); конфигурация не менялась — entrypoint/command и 7 маунтов
  совпадают с `raw-restore-log.md:33-52`.
- Staged-источник перехэширован той же командой, что evidence §1
  (`find | sort | xargs sha256sum | sha256sum`) →
  `2e4237d84530f63520703c519803e3e4edd6f629fa8259e920bc4e7ba9e898ff`, 12 файлов —
  манифест 04.10 совпал.
- Порты 18090/19090 свободны (`ss -tln`); RestartPolicy контейнера уже `no`
  (`{"Name":"no","MaximumRetryCount":0}`) — рестарт-политика не добавлялась.

Подъём и проверки живости (записано, что именно ответило):

- `docker start schoolquiz-demo-firebase-20261004` → Up; в логе старта —
  «Importing data from /tools/firebase-import/firestore_export/…» (staged-источник)
  и «✔ All emulators ready!».
- `curl http://127.0.0.1:18090/` → тело `Ok`, **HTTP 200** (Firestore).
- `curl http://127.0.0.1:19090/` → `{"authEmulator":{"ready":true,…}}`, **HTTP 200** (Auth).
- `POST /identitytoolkit.googleapis.com/v1/projects/demo-schoolquiz-mvp/accounts:query`
  (Bearer owner) → **userInfoCount: 98** — базлайн.
- Firestore REST spot-check `users/1wqSlL9TOX1h28ifZGixKxBugdSJ` →
  lifePoints=1000, standardHearts=10, goldHearts=3, nolics=1000 — значения базлайна
  04.10 (`backend-restore-result.json:32`); состояние эмулятора = staged-базлайн,
  как и задумано при `docker start`.

Контрольный экспорт (закрывает задачу 4 / вопрос владельцу 1-вариант А):

```
docker exec -w /tmp schoolquiz-demo-firebase-20261004 \
  /tools/firebase-cli/node_modules/.bin/firebase \
  emulators:export /tools/firebase-output-root/control-save-night-20261005 \
  --config /repo/firebase.mvp.json --project demo-schoolquiz-mvp --non-interactive
→ i  Found running emulator hub … → ✔  Export complete
```

- Каталог: `/workspace/infrastructure/android-toolchain/firebase-exports/demo-restore-20261004/control-save-night-20261005`
  (рядом с `control-save-1` и `export`), создан экспортом заново.
- **12 файлов**, 31 380 531 байт; **98 auth-пользователей**
  (`len(accounts.json['users'])`) — полный аналог control-save-1 из evidence §5.
- Контейнер НЕ останавливался: после экспорта — `Up`, `RestartPolicy=no`.

## Итог по задачам

| # | Задача | Статус |
|---|---|---|
| 1 | Памятка RED-базы ciCheck + evidence + черновик 1.7 без status | ✅ сделано |
| 2 | Proposal подключения контракт-теста (дифф, без правки package.json) | ✅ сделано |
| 3 | Runbook hitl 1.3/1.5 + 2 пустых шаблона evidence-json | ✅ сделано |
| 4 | Ночной контрольный экспорт в control-save-night-20261005 | ❌ не выполнен — контейнер Exited(137) в 17:59:47Z; перезапуск запрещён; задокументировано + проверена целостность at-rest |

## Вопросы владельцу

1. **Кто и когда перезапустит демо-бэкенд?** Контейнер убит в 17:59:47Z (SIGTERM извне,
   137, OOMKilled=false; я не трогал). Ночной экспорт и утренняя hitl-сессия 1.3/1.5
   зависят от него.
   - Вариант А: утром `docker start schoolquiz-demo-firebase-20261004` (по дизайну
     переимпортирует staged export, 98 пользователей; потеряны только in-memory изменения
     после 17:41Z 04.10). Последствие: быстрое восстановление к базлайну 04.10.
   - Вариант Б: поднять сейчас доп. запросом оркестратору ночью. Последствие: экспорт
     можно выполнить ночью, но непонятно, кто/что послало SIGTERM — риск повтора.
   - Вариант В: не поднимать до разбора причины убийства. Последствие: hitl 1.3/1.5
     утром сдвигается, экспорт остаётся невыполненным.
2. **Экспортировать ли изменившееся состояние до убийства?** Оно потеряно (export-on-exit
   был убит до записи). Если после 17:41Z 04.10 в эмулятор писали важное (прогоны других
   эпиков) — его нет ни в одном экспорте; если нет — потерь нет, база 04.10 канонична.
   - Вариант А: база 04.10 канонична, потерь нет — просто поднять контейнер.
   - Вариант Б: были важные записи — принять потерю и восстановить их скриптами.
   Последствие без решения: неявное допущение «потерь нет».
3. **Коммитить ли 459-строчный тест владельца до применения диффа подключения?** Proposal
   требует именно такой порядок (иначе цепочка молча гоняет старую версию).
   - Вариант А: владелец коммитит тест, затем дифф package.json отдельным коммитом
     (рекомендовано в proposal). Последствие: 17-кейсы попадают в ciCheck.
   - Вариант Б: дифф сразу, тест позже. Последствие: ложное покрытие — НЕ рекомендуется.
   - Вариант В: отдельный npm-скрипт вне цепочки `test`. Последствие: тест выпадает из
     канонического гейта.
4. **Локализовать отправителя SIGTERM контейнеру?** `dmesg` OOM-строк не показал,
   OOMKilled=false; вероятно, внешний `docker stop`/перезагрузка демона ~17:59Z.
   - Вариант А: проверить историю shell/docker на VPS и добавить рестарт-политику
     (`--restart unless-stopped`) при пересоздании. Последствие: ночная устойчивость.
   - Вариант Б: считать разовым событием, ничего не менять. Последствие: риск повтора.

## Что НЕ делалось (честно)

- Никаких Gradle-сборок/тестов (включая повторный ciCheck — это история 1.7, не ночь).
- Утренний runbook не исполнялся (нет устройств), proposal не применён к package.json,
  тест владельца не коммичен, экспорт в control-save-night-20261005 не выполнен.
