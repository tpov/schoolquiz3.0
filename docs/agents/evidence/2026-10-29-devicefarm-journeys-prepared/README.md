# Device Farm · четыре hitl-джорни — смена заблокирована инцидентом AWS (2026-10-29)

Вердикт: **BLOCKED (0 прогонов; бюджет смены не тронут — 0 из 15 прогонов, 0 из 120 устройстве-минут, $0)**.
Все четыре джорни подготовлены до готовности «одна команда до прогона»; прогоны не состоялись
из-за внешнего отказа Device Farm — заливка тест-пакетов по presigned URL.

## Что случилось (факты, всё UTC)

- Вчера 21:03–21:40 заливки CLI-ем работали (APK `c1bc3e88`, пакет `7ab75fa8`, спеки v2–v5b —
  все SUCCEEDED, финальный PASS прогона 21:50).
- Сегодня ~22:35 каждый `create-upload` → PUT по возвращённому presigned URL даёт
  `SignatureDoesNotMatch` — URL-ы подписаны ключом `AKIAUJHLTYS5MXEWK2IE` (тем же, что и во
  вчерашних успешных), но подпись не проходит проверку S3.
- Проверено: curl (http1.1/h2, `--path-as-is`, путь с %3A и декодированный, dualstack и обычный
  хост, заголовок `x-amz-content-sha256: UNSIGNED-PAYLOAD`), python urllib, wget; с VPS и с Mac
  (два разных внешних IP); оба бакета (`prod-us-west-2-uploads`, `…-uploads-testspec`) и оба типа
  (`APPIUM_NODE_TEST_PACKAGE`, `APPIUM_NODE_TEST_SPEC`, `ANDROID_APP`).
  Канонический запрос в ответе S3 побайтово соответствует параметрам URL — ломается именно
  проверка подписи на стороне S3/DF. Ключи AWS на VPS при этом валидны (`sts get-caller-identity`
  ок, все API devicefarm работают; файл кредов менялся владельцем в 20:48 — на час РАНЬШЕ
  последних успешных заливок, значит ротация ключей не причина).
- Похоже на тот же свежий паттерн отказа v4-presigned PUT, что описан публично 1 октября
  (frame.io forum, «SignatureDoesNotMatch on S3 presigned URLs»), — на нашу волну накрыло
  Device Farm-бакеты между 21:40 и 22:35 UTC.

Фоновый health-probe остаётся крутиться на VPS (systemd-юнит `df-probe2`, запущен 02:04 UTC,
100 попыток × ~3 мин ≈ до 07:00 UTC): лог `/tmp/df-upload-probe.log`, одна строка на попытку;
при `http=200` строка `RECOVERED`. Инцидент длился не меньше 22:35→02:23 UTC (все попытки 403).
Пробных мусорных upload-документов (probe-*) в проекте DF немного — можно игнорировать/чистить.

## Что подготовлено и закоммичено (ночь/e7-commerce, HEAD c03e7938)

| Джорни | Флоу | Прогон |
| --- | --- | --- |
| 9.9 (авто-половина) | `.maestro/mvp/99-accessibility-key-screens.yaml` — меню/синк/урок/арена-каталог/магазин, скриншот каждого экрана, `MAESTRO_FONT_SCALE=1.3` | `bash run/device-farm/df-run.sh 99-accessibility flows/99-accessibility-key-screens.yaml 1.3 0` |
| 4.20-partial | `.maestro/mvp/420-author-submit-review.yaml` — черновик → Send to arena → UNDER_REVIEW → REVIEW_DELAYED | `… df-run.sh 420-review flows/420-author-submit-review.yaml '' 0` + параллельно `run/device-farm/watch-420/watch.sh` |
| 4.26 | `.maestro/mvp/426-proposals-three-entries.yaml` — три входа (REPORT/COPY_EDIT/NEW_LESSON), mvp+mvpb, исходы в «Решениях» | `… df-run.sh 426-proposals flows/426-proposals-three-entries.yaml '' 1` + `watch-426/watch.sh` |
| 5.13 | `.maestro/mvp/513-tournament-organizer.yaml` — организатор до settle раунда 1 | `… df-run.sh 513-tournament flows/513-tournament-organizer.yaml '' 0` + `watch-513/watch.sh` (оппонент по HTTP из VPS) |

Операционный контур:
- `scripts/devicefarm-rig-prep.js` — watcher/препарация: await-users (появление аккаунтов
  устройства), topup (кошелёк ≥3000 ноликов под платы 1000/100×N), seed-author-lesson
  (mvpb-курс «DF author course» фиксированных названий), setup-513 (курс `df513-course`,
  80 вопросов, оппонент-аноним), await-review-request / await-proposals (ждут документы и
  прогоняют НАСТОЯЩИЙ `review-queue-worker` до терминала), await-tournament. Проверено на риге
  живьём: topup, оба сида, полный свип предложения REPORT → WORDS/FACTS по 3 попытки
  `REVIEW_MODEL_ENV_MISSING` → `CHECK_DELAYED`/`REVIEW_DELAYED` (тестовая запись
  `df-test-proposal-1`, помечена testFixture `df-devicefarm-2026-10-29`).
- `run/device-farm/df-run.sh` — патч дефолтов спеки (флоу/шрифт/mvpb) → заливка пакета+спеки →
  schedule-run (jobTimeoutMinutes 20). Заливка заменена с curl на python urllib (curl 8.x
  нормализует путь %3A — но это НЕ причина инцидента, см. выше).
- `run/device-farm/df-collect.sh` — сбор Customer Artifacts/видео по run ARN.
- Тест-пакет собран: `run/device-farm/pkg/devicefarm-test-package.zip`
  sha256 `debd5457ca8331d3a0e6bca382a4ad8271abaa46ccbd8726ea589fa722b62259` (11 флоу, оба APK).

## Порядок восстановления (когда probe скажет RECOVERED или владелец починит)

1. `bash docs/agents/device-farm/build-test-package.sh` (пересобрать, если флоу менялись)
2. Для каждой джорни по приоритету 9.9 → 4.26 → 4.20 → 5.13:
   - запустить watch-скрипт (кроме 9.9 — там препарации нет);
   - запустить df-run.sh с аргументами из таблицы;
   - после прогона `aws devicefarm list-remote-access-sessions` (должно быть 0) и
     `bash run/device-farm/df-collect.sh <run-arn> <имя>`;
   - evidence — по культуре в `docs/agents/evidence/2026-10-29-devicefarm-<джорни>/`.
3. Бюджет: ≤15 прогонов, ≤120 устройстве-минут на смену; таймаут прогона 20 мин уже в df-run.sh.

## Хвосты владельцу (кроме инцидента AWS)

1. **Ключ модели для 4.20/4.26**: в `schoolquiz-functions-emu` нет `REVIEW_MODEL_API_URL/KEY/NAME`
   — живые «Слова»/«Факты» невозможны; без ключа конвейер честно уходит в REVIEW_DELAYED
   (заявки) и CHECK_DELAYED (предложения; до автора в «For my lessons» не доезжают —
   право только у CHECK_PASSED/решений). Одобрение предложения автором в 4.26 и живые
   стадии 4.20 остаются owner-хвостом и после восстановления заливок.
2. **Свип review-queue-worker не подключён таймером** — рабочий модуль не вызывается ничем в
   index.js (спроектирован «отдельным процессом на VPS», AD-32). На риге его гонит
   devicefarm-rig-prep.js; для прода решение о запуске/расписании — за владельцем.
3. **TalkBack-половина 9.9** — remote access руками владельца (интерактивные сессии в этой
   смене не создавались: `list-remote-access-sessions` пуст).
4. **Диагностика инцидента**: если presigned-заливки не оживут — тикет AWS (у нас на руках
  RequestId из ошибок S3, таймлайн, канонические запросы).

## Дополнение 29.10 ~17:30 UTC — канал больших заливок всё ещё закрыт, запущен ночной цикл

- Живая проба 16:0x UTC: presigned PUT малого файла — 200 (казалось, инцидент ушёл).
- Разведка 16:1x–17:20 UTC: **малые** заливки (1 КБ–2 МБ) стабильно доходят до S3
  (статусы FAILED на мусорном контенте = байты долетели), а **большие** (пакет
  73.8 МБ) — ни разу: то мгновенный 403 SignatureDoesNotMatch, то
  SSLEOF на середине потока; ~40 попыток всеми способами (urllib/curl,
  скрипт/руками, разные имена/реплеи URL). Один явный хронический артефакт:
  df-samesess (73.8 МБ, 16:24) — SUCCEEDED, и df-debug-pkg (16:17) — PUT 200,
  т.е. канал открывался на минуты и снова закрывался. Реплей «плохого» URL
  руками даёт тот же 403 — URL создаются уже плохо подписанными в такие окна.
- Вывод: это не клиент и не наши ключи — верхний канал VPS↔S3 dualstack
  режет/портит длинные исходящие TLS-потоки (провайдер RackNerd?), малые
  проходят. Данные — в /tmp/df-s3put.log (трассировки 403 с StringToSign).
- df-run.sh: добавлен upload_with_retry (на случай мерцания; сам по себе
  контекст этого скрипта ловил отказы даже при ручных успехах — причина не
  найдена, скрипт переписан начисто как run/device-farm/df-run2.sh).
- **Запущен ночной цикл** (nohup, копия — docs/agents/device-farm/night-loop-2026-10-29.sh,
  лог /tmp/df-night-all.log): до 240 попыток заливки пакета (~5 ч, чередуя
  urllib/curl); как только пакет получит SUCCEEDED — автоматически заливаются
  спеки и прогоняются джорни по приоритету 9.9 → 4.26 → 4.20 → 5.13
  (с watch-скриптами и df-collect). Бюджет смены не тронут (0 прогонов).
