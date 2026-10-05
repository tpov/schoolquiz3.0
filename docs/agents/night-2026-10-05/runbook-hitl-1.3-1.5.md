# Runbook утренней hitl-сессии — истории 1.3 и 1.5 (у Mac владельца)

schemaVersion: 1, подготовлено ночью 2026-10-05 (трек E), дерево night-e1f @ b8bc23ff.
Исполнитель утром: владелец, на Mac, с физическими устройствами. Серверные ADB-прогоны 04.10
устройства не видели (docs/agents/2026-10-04-epic1-rig-run.json) — вся сессия локальная.

Цель: закрыть истории 1.3 «install-over-populated-device» и 1.5 «device-inventory» эпика
epic-candidate-rig (CAP-20: обновление существующей установки без потери состояния
пользователя) с доказательствами в `docs/agents/evidence/<дата>-<slug>/`.

## 0. Кандидат и бэкенд (из evidence 04.10, неизменны)

- **APK**: `apps/android-next/build/outputs/apk/mvp/android-next-mvp.apk` в дереве
  `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate` на VPS
  (HEAD сборки `1e596126`).
  - размер: 42 038 981 байт; SHA-256:
    `eeb8fc6e6c4dcc6f06ba3168c3a945323450cc3e8b50942975797198a63a5fbb`
  - сертификат (debug keystore, apksigner): SHA-256
    `00:33:E7:FA:2C:7E:32:86:5A:5E:99:82:7A:06:8B:EC:76:52:90:4A:A2:76:E1:DB:9C:F4:23:D7:5E:6C:47:D3`
  - package: `com.tpov.schoolquiz.mvp`, versionCode 1, versionName `0.1.0-isolated`
    (источник: docs/agents/2026-10-04-mvp-apk-identity.json)
- **Бэкенд**: контейнер `schoolquiz-demo-firebase-20261004` на VPS, project
  `demo-schoolquiz-mvp`, loopback-порты VPS: **18090** → Firestore (внутри 8080),
  **19090** → Auth (внутри 9099). Functions/Database/Storage эмуляторы в контейнере
  НЕ запущены (старт был `--only auth,firestore`). Базлайн данных: 98 auth-пользователей,
  history 0 (docs/agents/evidence/2026-10-04-demo-backend-restore/).
- **Приложение** в MVP-варианте ждёт эмуляторы на `127.0.0.1:9099 (Auth)` и
  `127.0.0.1:8080 (Firestore)` — hardcode в
  `platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseInitializer.kt:26-31,49-55`.
  На ФИЗИЧЕСКОМ устройстве `127.0.0.1` — это само устройство, поэтому нужны
  `adb reverse` (так же, как в сентябрьских прогонах — HANDOFF.md:43 «reverse только 9099 и 8080»).
- **Ночное состояние (2026-10-05, важно)**: контейнер был найден EXITED (137) в
  17:59:47Z — SIGTERM извне, export-on-exit убит SIGKILL посреди экспорта, файлов не
  записал (docs/agents/evidence/2026-10-05-demo-backend-night-state/backend-night-state.json).
  Данные на диске целы: staged-источник sha256 `2e4237d8…` совпадает с манифестом
  04.10, control-save-1 и export — по 12 файлов / 98 auth-пользователей, mtime 04.10.
  Изменения в памяти эмулятора после рестарта 04.10 (~17:41Z) потеряны.
  **Перед сессией поднять контейнер** (владелец): `docker start schoolquiz-demo-firebase-20261004`
  — по дизайну переимпортирует read-only staged export (98 пользователей), затем шаги §3.

## 1. Доставить APK на Mac и сверить (5 мин)

```bash
# с Mac (путь к VPS уточни своим ssh-алиасом)
scp <vps>:/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/apps/android-next/build/outputs/apk/mvp/android-next-mvp.apk ~/Downloads/
shasum -a 256 ~/Downloads/android-next-mvp.apk
# ОЖИДАНИЕ: eeb8fc6e6c4dcc6f06ba3168c3a945323450cc3e8b50942975797198a63a5fbb
# не совпало — СТОП, кандидата нет (не собирать заново без владельческого решения)
```

Опционально (если стоит apksigner из Android SDK):

```bash
apksigner verify --print-certs ~/Downloads/android-next-mvp.apk | grep -i "sha-256"
# ОЖИДАНИЕ: 00:33:E7:FA:...:47:D3
```

## 2. Подключить устройства (10 мин)

```bash
# Samsung RF8W80MBR3Z — USB-кабель в Mac; на телефоне подтвердить «Разрешить отладку по USB»
adb devices -l
# ОЖИДАНИЕ: RF8W80MBR3Z  device usb:...
# Pixel (исторически по wireless adb): adb-56271FDCH00C5S-ow9AvR._adb-tls-connect._tcp
#   если не виден: Настройки → Параметры разработчика → Беспроводная отладка → Подключить
#   (adb pair при необходимости). Pixel нужен только для 1.5 (инвентаризация).
```

Дальше всюду `-s RF8W80MBR3Z` (сериалы устройств с пробелами/суффиксами — бери точную
строку из `adb devices -l`).

## 3. Туннель к демо-бэкенду + adb reverse (5 мин)

Контейнер на VPS слушает только loopback VPS. С Mac: SSH-туннель, затем reverse.

```bash
# Терминал A на Mac — держать открытым всю сессию:
ssh -N -L 18090:127.0.0.1:18090 -L 19090:127.0.0.1:19090 <vps>

# Терминал B на Mac — проверить туннель (любой HTTP-ответ = живо):
curl -s -o /dev/null -w 'auth:%{http_code}\n' http://127.0.0.1:19090/
curl -s -o /dev/null -w 'firestore:%{http_code}\n' http://127.0.0.1:18090/

# Reverse на устройство (порядок аргументов: adb reverse <на устройстве> <на Mac>):
adb -s RF8W80MBR3Z reverse tcp:9099 tcp:19090
adb -s RF8W80MBR3Z reverse tcp:8080 tcp:18090
adb -s RF8W80MBR3Z reverse --list
# ОЖИДАНИЕ: (reverse) tcp:9099 tcp:19090 / tcp:8080 tcp:18090
```

Примечание: Functions (5001) не туннелируем — эмулятора Functions в контейнере нет;
для 1.3 он не нужен. Если приложение что-то вызовет по 5001 — это зафиксируем как
ограничение стенда, не как дефект установки.

## 4. История 1.3 — шаг A: зафиксировать состояние ДО установки (15 мин)

Создай локальный каталог доказательств и бэкапов:

```bash
EV=~/hitl-2026-10-05; mkdir -p $EV/before $EV/backups
S=RF8W80MBR3Z
adb -s $S shell dumpsys package com.tpov.schoolquiz.mvp \
  | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime" \
  | tee $EV/before/package-info.txt
```

**Ожидание до установки**: versionCode 1, versionName `0.1.0-isolated`,
firstInstallTime = дата сентябрьской установки, lastUpdateTime `2026-09-27 23:02:37`
(HANDOFF.md:43) — или актуальное на утро; зафиксировать как есть.

Остановить приложение (консистентная копия БД) и снять бэкапы:

```bash
adb -s $S shell am force-stop com.tpov.schoolquiz.mvp

# Room-база + sidecars (имя базы: schoolquiz.db — PersistenceModule.kt:52)
for f in schoolquiz.db schoolquiz.db-wal schoolquiz.db-shm; do
  adb -s $S exec-out run-as com.tpov.schoolquiz.mvp cat databases/$f > $EV/backups/$f
done
ls -la $EV/backups/   # размеры > 0; .wal/.shm может отсутствовать — ок

# shared_prefs (там живёт Firebase Auth store — identity установки/сессии)
adb -s $S shell run-as com.tpov.schoolquiz.mvp ls shared_prefs/ | tee $EV/before/shared-prefs-list.txt
mkdir -p $EV/backups/shared_prefs
for f in $(adb -s $S shell run-as com.tpov.schoolquiz.mvp ls shared_prefs/); do
  adb -s $S exec-out run-as com.tpov.schoolquiz.mvp cat shared_prefs/$f > $EV/backups/shared_prefs/$f
done

# Счётчики сохранности (sqlite3 на Mac; если не установлен: brew install sqlite)
sqlite3 $EV/backups/schoolquiz.db "PRAGMA user_version;"                    # ОЖИДАНИЕ: 15
sqlite3 $EV/backups/schoolquiz.db "SELECT identity_hash FROM room_master_table;"
# ОЖИДАНИЕ: e3a1b9e13aff4304afeba070b8895b6c  (15.json; v14 = ca5439bdc37decd3f21cb843d10bb3f4)
sqlite3 $EV/backups/schoolquiz.db "SELECT COUNT(*) FROM outbox;"            # pending-операции
sqlite3 $EV/backups/schoolquiz.db "SELECT COUNT(*) FROM lesson_attempts;"   # история
sqlite3 $EV/backups/schoolquiz.db "SELECT COUNT(*) FROM lesson_attempt_intents;"
sqlite3 $EV/backups/schoolquiz.db "SELECT COUNT(*) FROM arena_ticket_records;"
sqlite3 $EV/backups/schoolquiz.db "SELECT COUNT(*) FROM arena_frozen_rating_intents;"
sqlite3 $EV/backups/schoolquiz.db "SELECT COUNT(*) FROM mutation_receipts;"
tee -a $EV/before/db-counts.txt <<'EOF'
user_version=15
identity_hash=e3a1b9e13aff4304afeba070b8895b6c
# затем вставь фактические COUNT(*) по таблицам в порядке команд выше
EOF
```

Все имена таблиц — из `shared/core/persistence/src/commonMain/kotlin/.../persistence/`
(OutboxEntity.kt:24 `outbox` и др.); полный список в шаблоне evidence.

UID аккаунта (для сверки «аккаунт сохранён»): скопируй на устройство свежий скриншот
профиля ПОСЛЕ запуска (шаг B) либо найди uid в бэкапе auth-store XML (файл
`com.google.firebase.auth.api.Store.*.xml` в shared_prefs) и запиши в
`$EV/before/account-uid.txt`. Сверка с бэкендом (uid подставь):

```bash
curl -s "http://127.0.0.1:18090/v1/projects/demo-schoolquiz-mvp/databases/(default)/documents/users/<UID>" \
  | tee $EV/before/backend-user-doc.json   # кошелёк: lifePoints/hearts/nolics — поля документа
```

## 5. История 1.3 — шаг B: установка поверх и запуск (10 мин)

```bash
adb -s $S install -r ~/Downloads/android-next-mvp.apk
# ОЖИДАНИЕ: Success
# Если INSTALL_FAILED_UPDATE_INCOMPATIBLE (подпись не совпала) — СТОП:
# НЕ выполнять pm uninstall / pm clear без явного «да» владельца; записать вывод.
adb -s $S shell dumpsys package com.tpov.schoolquiz.mvp \
  | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime" \
  | tee $EV/after-package-info.txt
# ОЖИДАНИЕ: firstInstallTime НЕ изменился (identity установки),
#           lastUpdateTime обновился на сегодня
adb -s $S shell am start -n com.tpov.schoolquiz.mvp/.MainActivity
# подождать ~30 c, приложению дать догрузиться; скриншот по желанию:
adb -s $S exec-out screencap -p > $EV/after-launch-home.png
```

## 6. История 1.3 — шаг C: чек-лист сохранности (15 мин)

Каждый пункт — фактическое значение ДО (шаг A) vs ПОСЛЕ, записанное в evidence-json
(шаблон в §9). Чинить ничего не нужно: цель — доказать сохранность, а отклонение —
честно записать.

| # | Что | Как проверить ПОСЛЕ | Критерий CAP-20 |
|---|---|---|---|
| 1 | **Аккаунт** | скриншот профиля / uid из logcat (`adb -s $S logcat -d \| grep -i uid` ограниченно) или повторный бэкап auth-store XML; сверить с `$EV/before/account-uid.txt`; бэкенд-док `users/<UID>` отвечает | uid тот же; если Firebase отверг refresh-токен и приложение молча создало нового anonymous (fallback в AppApplication.kt:119-121) — это ОТКЛОНЕНИЕ, записать |
| 2 | **Кошелёк** | `curl .../documents/users/<UID>` до/после запуска; поля lifePoints/standardHearts/goldHearts/nolics | значения не «обнулились»; сравни с before-backend-user-doc.json |
| 3 | **Уроки (контент)** | в приложении открыт каталог/целевой урок; сетевые запросы идут через reverse (см. `adb -s $S shell settings` нет; просто видимый UI) | каталог загружается; доступ к прежнему целевому уроку есть |
| 4 | **История** | свежий бэкап schoolquiz.db (force-stop → cat → sqlite3) → `SELECT COUNT(*) FROM lesson_attempts;` | count равен before (плюс возможные новые записи от твоих действий — записать, что изменилось и почему) |
| 5 | **Identity установки** | firstInstallTime не изменился; `settings get secure android_id` до/после одинаков; auth-store XML в shared_prefs сохранился (имя файла + размер) | всё совпадает |
| 6 | **Pending-операции** | свежий бэкап БД → `SELECT COUNT(*) FROM outbox;` + `lesson_attempt_intents`, `arena_ticket_records`, `arena_frozen_rating_intents` | counts не упали до 0 против before; записи не исчезли без факта доставки |

Для «до/после» при каждом повторном бэкапе БД: сначала
`adb -s $S shell am force-stop com.tpov.schoolquiz.mvp`, затем cat трёх файлов заново
в `$EV/after*/`.

**Откат не делаем** (CAP-20 упоминает rollback, но скоуп истории 1.3 — update-with-retention;
отдельная история не назначена). Если owner захочет откат здесь же — `adb install -r`
со старым APK при СВЕРЕННОЙ подписи, но это решение владельца, не шаг runbook.

## 7. История 1.5 — инвентаризация устройств (10 мин, Samsung + Pixel)

Для КАЖДОГО устройства (S=RF8W80MBR3Z, затем Pixel-serial):

```bash
S=<serial>
adb -s $S shell getprop ro.product.model                     # модель
adb -s $S shell getprop ro.product.brand
adb -s $S shell getprop ro.build.version.release             # Android
adb -s $S shell getprop ro.build.version.sdk                 # API level
adb -s $S shell getprop ro.build.version.security_patch
adb -s $S shell getprop ro.product.cpu.abi
# приложение:
adb -s $S shell pm list packages | grep tpov
adb -s $S shell dumpsys package com.tpov.schoolquiz.mvp | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime"
# подпись установленного APK: сверь локальный файл (шаг 1) — на устройстве деривация недоступна
adb -s $S shell pm path com.tpov.schoolquiz.mvp              # путь base.apk (записать)
# схема БД (после шагов §4/§6Known): user_version=15, identity_hash e3a1b9e1...
# роль (заполняет владелец): primary-test-device / secondary / etc.
adb -s $S shell pm path com.google.android.gms               # GMS присутствует
adb -s $S shell dumpsys package com.google.android.gms | grep versionName
adb -s $S shell pm list packages | grep android.vending      # Play Store
adb -s $S shell dumpsys package com.android.vending | grep versionName
```

Отсутствующие настройки (Play-аккаунт, тестовый трек, GMS не установлен и т.п.) —
записать в evidence как «external dependency», НЕ блокируя остальное
(эпик Done-when 5; USER-CONTINUATION-PLAN §2 Шаг 0).

## 8. Запреты на время сессии

- НЕ выполнять `adb uninstall`, `pm clear`, `adb backup`/restore, установку с `--no-signing`-подобными трюками.
- НЕ останавливать/перезапускать контейнер `schoolquiz-demo-firebase-20261004`, не менять данные бэкенда (кроме того, что само приложение запишет в Firestore штатно).
- НЕ собирать APK заново и не менять версии — кандидат зафиксирован (eeb8fc6e…).
- НЕ коммитить бэкапы БД/prefs с устройства в git (приватные данные) — они остаются в $EV на Mac.

## 9. Пустые шаблоны evidence-json

Рядом с этим runbook: `evidence-templates/install-over-evidence-template.json` (история 1.3)
и `evidence-templates/device-inventory-template.json` (история 1.5). Утром скопировать в
`docs/agents/evidence/<дата>-install-over-populated-device/` и
`docs/agents/evidence/<дата>-device-inventory/` (дата = день сессии), заполнить все
`null`/`""`/`[]` значения фактически полученными выводами, приложить сырые выводы команд
(tee-файлы) и бэкапы НЕ прикладывать (приватно), только счётчики/хеши.

## 10. Если что-то пошло не так

- Туннель/reverse отвалился (приложение «Требуется авторизация» или пустой каталог):
  проверить `curl` (§3) и `adb reverse --list`, пересоздать reverse, перезапустить приложение.
- Приложение падает при старте: `adb -s $S logcat -d -b crash > $EV/crash.txt` — приложить к evidence, сессию по 1.3 считать НЕ пройденной (это и есть результат записи).
- Устройство не видится: переподключить USB, `adb kill-server && adb devices`; для Pixel — переподключить wireless debugging.
- Любой FAIL — записать как есть; исторические зелёные прогоны с новой приёмкой не складываются (USER-CONTINUATION-PLAN §5).
