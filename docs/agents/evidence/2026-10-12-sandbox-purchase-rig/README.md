# 2026-10-12 — ШАГ 3 (sandbox-покупка): риг-фрагмент — воспроизведён блокер «нет GMS»

Решение владельца: полный sandbox-цикл (premium_30d с trial-3d → gold_pack_small → refund →
RTDN/клобэк) на эмуляторе. Коммит дерева: `77042284` (ветка `night/e3-e8-inception`).

## Что сделано на свежем риге

- Поднят новый риг: `docker run -d --name schoolquiz-emu --memory 1g --privileged
  -p 5555:5555 -v /dev/binderfs:/dev/binderfs redroid/redroid:12.0.0-latest`;
  boot ~25с; Android 12 (SDK 31), x86_64, `redroid12_x86_64`.
- **GMS-чек**: 96 пакетов, `pm list packages | grep -iE "gms|vending|google"` — ПУСТО.
  Play Store и `com.google.android.gms` отсутствуют (`rig-gms-check.txt`).
- Собран свежий **debug**-APK из HEAD: `sgw ./gradlew :apps:android-next:assembleDebug
  --no-configuration-cache` — BUILD SUCCESSFUL, applicationId `com.tpov.schoolquiz`
  (не `.mvp`: продовой id обязателен для биллинга). Нюанс: в worktree
  `apps/android-next/google-services.json` был подменён на демо-mvp
  (`demo-schoolquiz-mvp`); для продовой сборки возвращён прод-конфиг
  (`school-quiz-89336951`, файл в .gitignore — swap безопасен).
- APK установлен (`Streamed Install … Success`), приложение поднялось, работало до конца
  прогона (pid 2020), crash-buffer пуст.

## Read-path на проде — работает (это риг проверить МОЖЕТ)

- Анонимная автентификация: uid `YMQZ9uRlqVeIFadJgGRLQQFMyMm1` (`app-uid.txt`).
- App Check: `DebugAppCheckProvider` с секретом из `local.properties`; проект
  `762375057396` = `school-quiz-89336951` (`firebase-logcat-extract.txt`).
- Данные приезжают из продового Firestore: на первом скриншоте счётчики разделов «0
  квестов», через ~1 минуту — «Игры 2 / Квесты 2 / Школа 6 / Опросы 2».
- Полка магазина (`shot 03`): серверные цены рендерятся (Charge 20 000, Golden charge 1 —
  из `configs/economy`), балансы (0/0) в шапке. Плей-позиции — Premium, Small/Medium/Large
  gold pack — честно заблокированы: «Google Play is unreachable — prices appear once it
  answers» (поведение истории 1.4). Чеков ещё нет — список (история 5.4) пуст.

## Purchase-path — на риге невозможен (воспроизведено и зафиксировано)

`logcat` при открытии полки (`billing-logcat-extract.txt`), billing 9.1.0:

```
W/BillingClient( 2020): Reconnection failed with result: 3
W/BillingClient( 2020): There was an exception while unbinding service!
W/BillingClient( 2020): java.lang.IllegalArgumentException: Service not registered:
    com.android.billingclient.api.zzbz@…
E/FirebaseInstanceId( 2020): Google Play services missing or without correct permission.
```

`result: 3` = BILLING_UNAVAILABLE, сервиса `com.android.vending` на устройстве нет —
BillingClient соединиться не может, покупка/acknowledge/лист Play недостижимы в принципе.
GMS-прокидка в redroid рецепта в репо не имеет; сторонние GApps-сборки redroid не проходят
Play-сертификацию и для license-тестов не годятся. Вывод прежний (совпадает с
`2026-10-11-premium-subscription`): **end-to-end sandbox — только на устройстве с Play**.

## Серверный фрагмент с VPS (без устройства)

Ключ `firebase-adminsdk-h5hhr` (`.local/history/broken-mac/zealous-pike-8e91a1/functions/…`)
имеет `datastore.admin` — Firestore-срезы работают, но у него НЕТ `roles/pubsub.publisher`:

- срез ДО покупок: `users/YMQZ…` не существует, `purchase_settlements`: 0, `receipts`: 0 —
  приложение рига читало экономику, экономических записей не создавало;
- публикация test notification в `play-rtdn` с VPS → 403 PERMISSION_DENIED (GET/list/publish).

Канал проверяет владелец: кнопкой Send test notification в Play Console либо из Cloud Shell:

```
gcloud pubsub topics publish play-rtdn --project=school-quiz-89336951 \
  --message="$(printf '{"version":"1.0","packageName":"com.tpov.schoolquiz","testNotification":{"version":"1.0"}}' | base64 -w0)"
```

Затем `purchase_notifications/<messageId>` должен получить запись с kind=test.

## Задача владельца (phone-HITL)

Готово к запуску на телефоне (тестер `oleg12794@gmail.com`):

- `scripts/devices/sandbox-purchase.sh <serial> [preflight|premium|gold|refund|verify|all]`
  — шаги прогона с паузами, скриншоты и текстовые дампы до/после;
- `scripts/devices/sandbox-verify.js <uid>` — серверный срез (`users/{uid}`, settlements,
  receipts, notifications, ledger; ожидания по шагам описаны в шапке).
- RTDN-канал без покупки: Play Console → Monetization setup → Send test notification;
  для продовой команды из Cloud Shell:
  `gcloud pubsub topics publish play-rtdn --message="$(printf '{"version":"1.0","packageName":"com.tpov.schoolquiz","testNotification":{"version":"1.0"}}' | base64 -w0)"`.

## Артефакты

`.working/sandbox-purchase-20261012/` (корень репо): `shots/00–05*.png` (дом, полка,
низ полки), `logs/rig-gms-check.txt`, `logs/billing-logcat-extract.txt`,
`logs/firebase-logcat-extract.txt`, `logs/app-uid.txt`, `report.html` (HTML-отчёт прогона).
Здесь: `rig-gms-check.txt`, `billing-logcat-extract.txt`, `firebase-logcat-extract.txt`,
`app-uid.txt`, `shot-02-home.png`, `shot-03-shop.png`.

## Состояние рига

Контейнер `schoolquiz-emu` ОСТАНОВЛЕН после прогона (`docker stop`, данные в контейнере —
повторный прогон поднимается той же командой из раздела «Что сделано»).
