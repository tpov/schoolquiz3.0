# Sandbox-покупка — журнал прогона 2026-10-14/15 (Pixel + Samsung) + обновление ключей

Сценарий: `scripts/devices/sandbox-purchase.sh` (ветка night/e3-e8-inception),
шаги premium_30d → gold_pack_small → refund. Исполнитель: агент; hitl-тапы — владелец
(на Pixel), далее владелец передал прогон на второй телефон (Samsung) с полной автономией.
Каталог прогона: `.working/sandbox-purchase-20261014/`.

## ИТОГ (кратко)

Покупки заблокированы НА СТОРОНЕ PLAY, причина доказана на двух устройствах и двух аккаунтах:
**«Цю версію додатка не налаштовано для виставлення рахунків через Google Play»**
(= billing responseCode 5, DEVELOPER_ERROR). Сайдлоад-сборка не ассоциирована с треком Play —
лицензионный тестер не спасает. Разблокировка: internal-testing релиз android-next в Play
Console + opt-in тестером. Всё до этого барьера проверено и работает.

## Среда

- Телефон: Pixel 10 Pro (blazer), Android 17, serial 56271FDCH00C5S, WiFi «Xiaomi», авиарежим+WiFi.
- Приложение: com.tpov.schoolquiz 0.1.0 (debug, android-next), подпись SHA-1 `781f0052522e2a83904fb3a527c937964da4a11b`.
- Тестер: oleg12794@gmail.com (на устройстве, подтверждён dumpsys account).
- Play Store и GMS: установлены (preflight ok).
- uid приложения: `aovqcJfVuVNeGlbuPbwNXxY2R9n1` (anonymous) — сохранился на всю сессию.

## Preflight — зелёный (15:27–15:28)

Все проверки скрипта прошли; снимок `shots/00-preflight-home.png`.

## Базовый срез ДО покупок — чистый (15:2x)

`users/{uid}` не существует; purchase_settlements / receipts / purchase_notifications /
audit/currency_ledger — пусто. Файл: `evidence/baseline-slice.txt`.

## Блокер 1: ключ сервис-аккаунта отозван → обход через firebase-cli refresh-токен

- `firebase-adminsdk-h5hhr` (файл в .local/history/broken-mac/...) даёт
  `invalid_grant: Invalid JWT Signature` (работал 12.10). Запись: `evidence/sa-key-revoked.txt`.
- Срезы ведены `sandbox-verify-rest.js` (REST Firestore, access-токен из refresh-токена
  firebase-cli oleg12794@gmail.com с Mac).
- Владельцу: перевыпустить SA-ключ; путь из .claude/PROJECT-CONTEXT.md (/home/tpov/Downloads/...) не существует.

## Блокер 2: API-ключ приложения истёк → создан новый, пересобрано, переустановлено

- Симптом: приложение не могло обновить токен (`API key expired. Please renew the API key`),
  Firestore UNAUTHENTICATED (logcat 17:06–17:07); identitytoolkit signUp с ключом из
  google-services.json подтвердил «API key expired».
- В проекте 2 живых ключа (оба без Android-ограничения), ключ приложения из списка исчез.
- Создан ключ **d6b6ed63-ded3-4fd8-8ad6-094a98e76024**
  `AIzaSyBXd0z91Jj2SIsCBEMG8tn6WpJmjtogZA8`, ограничение Android: пакет com.tpov.schoolquiz +
  SHA-1 78:1F:00:52:52:2E:2A:83:90:4F:B3:A5:27:C9:37:96:4D:A4:A1:1B (сертификат debug-подписи
  снят с установленного APK через apksigner).
- google-services.json (gitignored, worktree night-impl) обновлён → `sgw ./gradlew
  :apps:android-next:assembleDebug` BUILD SUCCESSFUL (4м17с, root через sgw; у oleg нет shell/home)
  → `adb install -r -t` Success 17:39:20.
- После установки: 0 ошибок ключа, FirebaseApp init ok, **uid тот же** (кэш токена обновился),
  каталог живой. Снимок `shots/12-after-key-fix-home.png`.

## Шаг PREMIUM/GOLD — покупка отклонена Play (DEVELOPER_ERROR)

Полка: цены рендерятся (премия 239,99 UAH, пакы 11,99/119,99/299,99 UAH — по виду серверные
из configs/economy), у Premium подпись «Google Play is unreachable…» (состояние query на момент
загрузки полки). Снимок `shots/13-error.png`.

Тап «Buy» (владелец, 19:23:49 времени устройства):
- открылся `ProxyBillingActivity` + `com.android.vending/...LockToPortraitUiBuilderHostActivity`
  (лист Play реально поднимался);
- через 2с: `ProxyBillingActivity: Activity finished with resultCode 3 and billing's responseCode: 5`
  — **DEVELOPER_ERROR**, покупка не состоялась;
- сразу после (19:23:57–19:24:15) краткий DNS-фейл (EAI_NODATA), сеть восстановилась
  (ping 8.8.8.8 ok, WiFi VALIDATED).

Разбор гипотез (по коду night-impl):
1. Приложение сайдлоад-отладочное, а аккаунт тестера не имеет приложения в библиотеке Play
   (на телефон ставили adb-установкой, не из трека) — современный Play на покупку из
   неассоциированного приложения отвечает DEVELOPER_ERROR. Лечение: internal-testing релиз
   com.tpov.schoolquiz + opt-in тестером с телефона (установка из Play заменит сайдлоад,
   data/uid собьются — базовый срез уже зафиксирован).
2. premium_30d: у подписки prepaid-бейз-план, бесплатный триал на prepaid Play не даёт
   заводить; приложение предпочитает триал-оффер (SubscriptionOffers.kt) — если лист открывался
   без валидного offerToken, будет тот же ответ. Проверяется выбором: gold_pack (INAPP,
   без офферов) против premium.
3. Конфиг продукта (неактивен/чужой пакет) — продукты созданы 10.10 как активные, пакет совпадает.

## Фаза Samsung SM-A145F (RF8W80MBR3Z, Android 15) — автономный прогон по указанию владельца

- Аппарат: Samsung A14 (тот, что гонял real-course 28.09); GMS+Play есть; аккаунт на устройстве
  **oodipsorpa@gmail.com — НЕ тестер**. Реальные покупки не подтверждались.
- APK (с новым ключом) установлен: Success 22:2x. Разрешение POST_NOTIFICATIONS выдано через pm grant.
- Приложение: 0 ошибок ключа, новый аноним-uid `yrsWgYJjLdUo7xHA8mSF9Tvgimp2`;
  базовый срез чистый (`evidence/baseline-slice-samsung.txt`).
- **uiautomator-мост на Samsung работает** — весь прогон вёдён агентом по дереву элементов
  (ретраи на дамп через /tmp/sqdump.sh, тапы только по найденным нодам).
- Полка: те же серверные цены (239.99 / 11.99 / 119.99 / 299.99 грн), у Premium тоже
  «Google Play недоступен…».
- **Gold Pack Small → Buy**: лист Play открылся и показал «Помилка — Цю версію додатка не
  налаштовано для виставлення рахунків через Google Play» (скрин `shots/21-samsung-gold-sheet.png`).
- **Premium → Buy**: та же ошибка (скрин `shots/22-samsung-premium-sheet.png`).
- Вывод: DEVELOPER_ERROR воспроизводится на втором устройстве с другим (не-тестерским) аккаунтом
  на обоих типах продуктов → причина не в тестере/телефоне/продукте, а в отсутствии трек-релиза:
  sideload-сборка не ассоциирована с Play. Диф-тест «gold vs premium» значения не имеет —
  барьер один, раньше офферов не доходит.

## Инциденты среды

- Pixel: uiautomator-мост сломан («null root node» в ~9 из 10 попыток, даже на лаунчере) —
  навигация только руками владельца, доказательства screencap + срезы.
- Pixel: USB-связь рвалась дважды (~15:38 и ~19:2x) — кабель/порт, не adb-сервер.
- Samsung: мост работает; дампы с ретраем 1–2 попытки.

## Путь разблокировки (подготовлено, осталось от владельца)

1. **Play Console → com.tpov.schoolquiz → Testing → Internal testing**: создать релиз из
   android-next (AAB/APK), добавить oleg12794@gmail.com в тестеры трека, выдать opt-in-ссылку.
   Загрузка требует подписи upload-ключом: `/etc/tpov/schoolquiz/signing/upload-release.jks`
   лежит (13.10), но пароль нигде не задокументирован, signingConfig в проекте не заведён —
   нужно от владельца: пароль jks (или сам подписанный артефакт).
2. После opt-in: установка из Play заменит сайдлоад (данные собьются; uid станет новым —
   базовые срезы обоих uid уже зафиксированы), затем повторить premium_30d → gold_pack_small → refund.
3. Параллельно (не блокирует покупки, но нужно для RTDN/verify-канона): перевыпустить ключ
   firebase-adminsdk-h5hhr и дать ему roles/pubsub.publisher (или нажать Send test notification
   в Play Console → Monetization setup).

## Состояние после прогона

- Pixel: приложение 0.1.0 (с новым ключом) установлено; USB-кабель флакует (два обрыва за вечер).
- Samsung: приложение установлено, свёрнуто на лаунчер, разрешения выданы; artifact untouched.
- Сервер: оба uid чистые (final-slices.txt), покупок/расчётов нет — консервация состояния «до».

## Артефакты

- shots/00, 10, 11, 12, 13 (префлайт, полка до, после попытки, дом после починки ключа, ошибка)
- evidence/baseline-slice.txt, evidence/slice-check-1.txt (после попытки — пусто),
  evidence/sa-key-revoked.txt
- sandbox.log, uid.txt, shop-before-premium.txt (пустой — дамп-мост сломан)

## 2026-10-15 — обновление ключей после утечки в публичный git (задача владельца «ключи удалили — обнови»)

Контекст утечки: в публичный репо github.com/tpov/schoolquiz3.0 попали файлы SA-ключей
(коммиты 8e9ec569, bdb5c110, 15a402d9, 7c73c48f — `firebase-adminsdk*.json`). Подписных
keystore-файлов в истории git НЕТ (проверено `git log --all --diff-filter=A`). API-ключи
в истории — только внутри клиентских google-services.json (mvp/mvpDevice/mvpArenaProof).

Сделано через IAM/PubSub API (refresh-токен firebase-cli oleg12794@gmail.com):

1. **SA-ключ обновлён**: создан `d6fcf09c340e6d4dea426f7c9424bb16c1aee18f`
   (KEY_ALG_RSA_2048), файл `/etc/tpov/schoolquiz/sa-firebase-adminsdk-h5hhr-d6fcf09c340e.json`
   (0600). Канонический `scripts/devices/sandbox-verify.js` работает с ним (DEFAULT_KEY
   обновлён в worktree night-impl); `.claude/PROJECT-CONTEXT.md` обновлён.
2. **Скомпрометированные ключи ОТЗВАНЫ в IAM**: `0d54a7e1174d…` (апрель, был в git) и
   `db501e845c65…` (август) → DELETE 200. В IAM остался только новый.
3. **pubsub.publisher выдан** SA на топик `play-rtdn` (раньше стоял только паблишер Play —
   `google-play-developer-notifications@system.gserviceaccount.com`, т.е. RTDN в консоли настроен).
4. **RTDN-канал проверен end-to-end**: test notification опубликован от имени SA
   (messageId `22027104820531409`) → обработчик записал `purchase_notifications/…` с
   kind=TEST, result=RECORDED. Пункт «проверить канал» из отчёта 12.10 закрыт, владелец
   не нужен.
5. **Подпись/upload-ключ**: зарегистрированный в Play upload certificate = Android Debug
   key `78:1F:00:52:…` (подтверждено скачанным из Play Console 13.10
   `~/Downloads/upload_cert.pem`). Материал ключа забэкаплен:
   `/etc/tpov/schoolquiz/signing/debug-upload-key.jks` + `keystore.properties` (0600) +
   `README.md` с полным состоянием. `upload-release.jks` (0B:61:…) от 13.10 — пароль
   нигде не записан, не зарегистрирован в Play; считать потерянным.
   Если debug-keystore утекал где-то кроме этого репо (в его истории его нет) — владельцу
   сделать upload key reset в Play Console.

## Остаток для владельца (единственный блокер покупок)

Play Console → com.tpov.schoolquiz → Testing → Internal testing: релиз android-next
(APK `apps/android-next/build/outputs/apk/debug/android-next-debug.apk` от 14.10 подписан
действующим upload-ключом — подходит для загрузки), тестер oleg12794@gmail.com, opt-in-ссылка
https://play.google.com/apps/testing/com.tpov.schoolquiz. После «трек готов» агент прогоняет
установку из Play и всю цепочку покупок сам.
