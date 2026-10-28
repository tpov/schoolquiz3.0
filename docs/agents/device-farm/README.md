# AWS Device Farm — риг HITL-джорни SchoolQuiz (4.20, 4.26, 5.13, 9.9)

Дата подготовки: 2026-10-28. Ветка `night/e7-commerce`, HEAD `ed232617`.
Статус: **риг готов к запуску, но ни одного обращения к AWS не сделано** — ждёт
кредов владельца. Всё, что можно было проверить без AWS, проверено (см. §2).

Зачем: истории 4.20 (живой прогон конвейера ИИ-проверки), 4.26 (три входа
предложений до одобрения автором, два аккаунта), 5.13 (device-proof
турниров), 9.9 (финальный NOIR-свип + доступность) помечены `hitl = true` и
blocked в tickets.toml — им нужны реальные телефоны, которых у рига нет.
Device Farm даёт реальные устройства (в т.ч. с GMS, которых нет в redroid)
двумя режимами: автоматический прогон с Maestro и remote access — живая
интерактивная сессия владельца с экраном устройства в браузере.

## 1. Архитектура прогона

```
[Device Farm: физический телефон в AWS]      [VPS rnsrv 204.44.66.27]
  com.tpov.schoolquiz.mvp   ──интернет──▶  socat 0.0.0.0:9099 → контейнер
  com.tpov.schoolquiz.mvpb  (cleartext       schoolquiz-demo-firebase-20261004
      │                      разрешён         (auth 9099, firestore 8080)
      ▼                      NSC mvp)       node 0.0.0.0:5001 — functions-emu
[тест-хост DF: контейнер]                   (конвейер ревью 4.20, турниры 5.13)
  adb → телефон
  Maestro CLI (ставится в install-фазе)
  флоу из тест-пакета
```

Хост эмулятора **запечён в APK** (`-PmvpEmulatorHost=204.44.66.27`) — на
устройстве не нужен ни adb reverse, ни ssh-туннель ( adb reverse с облака
невозможен в принципе; ловушка из evidence 2026-10-18-e5-device-proof об
unreliable reverse здесь не действует). IP уже разрешён в
`apps/android-next/src/mvp/res/xml/mvp_network_security_config.xml`
(cleartext только для демо-рига, прод-хостов за ним нет).

## 2. Что уже проверено без AWS (2026-10-28)

- Порты демо-рига доступны из интернета (пробы с Mac владельца):
  `204.44.66.27:9099` → 200, `:8080` → 200, `:5001` → 404 на корне — это
  нормальный ответ functions-эмулятора. ufw inactive, iptables INPUT ACCEPT;
  если провайдер VPS фильтрует — не проявилось.
- Контейнеры рига живы: `schoolquiz-demo-firebase-20261004` (up 10 дней),
  `schoolquiz-functions-emu` (up 8 дней).
- APK-пара собрана и идентифицирована: `run/device-farm/apk-identity-2026-10-28.json`
  (mvp 2268f62a…, mvpb fc9f5051…, подпись debug-ключом рига 0033e7fa…).
- Триггер конвейера ревью — Firestore `onDocumentCreated`
  (`processQuestReviewRequest`), под эмулятором срабатывает сам.

## 3. Креды AWS: где взять, куда положить

Креды — **окружение VPS, не Git** (AGENTS.md: экспорты через
`scripts/transfer.py` в закрытые exports/, в Git не попадают):

```bash
# на VPS, один раз; ключи — IAM пользователя с политикой AmazonDeviceFarmFullAccess
aws configure --profile devicefarm        # Access Key ID, Secret, region us-west-2
export AWS_PROFILE=devicefarm             # или писать --profile в каждую команду
aws devicefarm list-projects              # проверка кредов; AWS-запросов до этого не было
```

Region: Device Farm живёт в us-west-2 (единый регион сервиса). Консоль:
console.aws.amazon.com/devicefarm.

## 4. Project и device pool (один раз)

Консоль: Device Farm → Projects → **New project** `schoolquiz-hitl`.
Внутри проекта → Device pools → **Create pool**:
- `gms-phones` — топ-девайсы с Google Play: Google Pixel 7/8/9, Samsung
  Galaxy S22/S23/S24/A54. Fleet меняется — состав выбирать по факту в
  консоли (фильтр «Google Play services: yes»).
- Для 5.13 (разнородность реальных телефонов) — минимум один Pixel и один
  Samsung в разных прогонах/пулах.

CLI-эквивалент после появления кредов:

```bash
arn=$(aws devicefarm create-project --name schoolquiz-hitl --query project.arn --output text)
aws devicefarm list-devices --filters '[{"attribute":"PLATFORM","operator":"EQUALS","values":["ANDROID"]}]'
aws devicefarm create-device-pool --project-arn "$arn" --name gms-phones \
  --rules '[{"attribute":"ARN","operator":"IN","value":"{\"arns\":[\"<device-arn-1>\",\"<device-arn-2>\"]}"}]'
```

## 5. Сборка APK и тест-пакета (на VPS)

APK уже собраны (2026-10-28, identity json выше). Пересборка после движения
ветки — та же команда (gradle только через `sgw`, лимиты не поднимать):

```bash
cd /workspace/projects/schoolquiz3.0
JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :apps:android-next:assembleMvp \
    :apps:android-next:assembleMvpb -PmvpEmulatorHost=204.44.66.27 --no-configuration-cache
cp apps/android-next/build/outputs/apk/mvp/android-next-mvp.apk  run/device-farm/
cp apps/android-next/build/outputs/apk/mvpb/android-next-mvpb.apk run/device-farm/
bash docs/agents/device-farm/build-test-package.sh   # → run/device-farm/pkg/devicefarm-test-package.zip
```

APK не коммитятся (`*.apk` в .gitignore), живут в untracked `run/device-farm/`.

## 6. Режим A — автоматический прогон (Maestro в custom environment)

Файлы: тест-спека `docs/agents/device-farm/testspec-maestro.yaml`, пакете
`build-test-package.sh` (выше).

Консоль (первый прогон):
1. Device Farm → проект `schoolquiz-hitl` → **Create a new run**.
2. Android app: загрузить `run/device-farm/android-next-mvp.apk`.
3. Framework: **Appium** → **Appium Node.js**.
4. Test package: загрузить `devicefarm-test-package.zip`.
5. Spec: «Edit» под мультфильмом дефолтного spec → **заменить целиком**
   содержимым `testspec-maestro.yaml` → Save.
6. Выбрать пул `gms-phones`, устройства (Start run), execution default
   (parallel — по устройству), timeout 15–25 минут.
7. Ручки прогона — правятся прямо в spec перед запуском (одна строка):
   - `MAESTRO_FLOW` — какой флоу из `flows/` пакета гнать. Дефолт
     `flows/02-request-sync.yaml`: запуск → меню → SYNC NOW → «Last sync» —
     доказывает связность устройство→VPS-риг и анонимный вход.
   - `INSTALL_MVPB=1` — поставить второй клиент `.mvpb` (нужно для 4.26).
   - `MAESTRO_FONT_SCALE=1.3` — повышенный шрифт для 9.9.

CLI-эквивалент (после кредов; ARN подставятся из create-project):

```bash
aws devicefarm create-upload --project-arn "$arn" --type ANDROID_APP --query upload.arn
# положить mvp.apk по upload.url (curl -T), дождаться SUCCEEDED (get-upload)
aws devicefarm create-upload --project-arn "$arn" --type APPIUM_NODE_TEST_PACKAGE ...
aws devicefarm schedule-run --project-arn "$arn" --app-arn <app-upload-arn> \
  --device-pool-arn <pool-arn> --name "4.26-proposals-smoke" \
  --test '{"type":"APPIUM_NODE","testPackageArn":"<pkg-upload-arn>","testSpecArn":<spec-upload-arn>}'
```

Тест-спеку тоже грузят через `create-upload --type APPIUM_NODE_TEST_SPEC` и
затем в `schedule-run` указывают `testSpecArn`; либо правят в консоли.

Что вернётся: Device Farm пишет видео экрана всего прогона сам; Customer
Artifacts (zip на странице результата) содержат `maestro-stdout.log`,
скриншоты каждого шага (`maestro-tests/…`), `logcat.txt`, diagnostics
хоста.

## 7. Режим B — remote access (интерактивная сессия владельца)

Для 9.9 (TalkBack, порядок фокуса — руками не автоматизируется), для живых
джорни 4.20/5.13 «глазами владельца» и для отладки флоу.

1. Консоль: проект → **Remote access** → Start a session → выбрать
   устройство из пула `gms-phones` (или «Top Devices»).
2. В окне сессии: **Install application** → залить
   `run/device-farm/android-next-mvp.apk` (и `.mvpb` — до 3 вспомогательных
   приложений на сессию; оба клиента ставятся рядом — два аккаунта 4.26).
3. Открыть приложение — оно сразу уйдёт в демо-риг VPS (хост запечён);
   быстрый чек сети: браузер устройства на `http://204.44.66.27:8080/`
   должен отдать 200.
4. Владелец гонит джорни руками; для 9.9: TalkBack включается на устройстве
   (Settings → Accessibility → TalkBack), повышенный шрифт — командой
   `settings put system font_scale 1.3` в панели Shell сессии (иконка
   терминала в тулбаре) либо отдельным A-режимным прогоном с
   `MAESTRO_FONT_SCALE=1.3`.
5. Сессия пишет видео; артефакты скачиваются с её страницы.
   Лимиты: сессия ≤ 150 минут (Time left виден в углу), продление невозможно.

CLI: `aws devicefarm create-remote-access-session --device-arn <arn>
--project-arn <arn>`; далее консоль/`devicefarm.html` для управления.

## 8. Evidence — куда класть по культуре проекта

- Текстовые протоколы, скриншоты, stdout, logcat: распаковать Customer
  Artifacts в `docs/agents/evidence/<дата>-devicefarm-<джорни>/` (напр.
  `2026-10-30-devicefarm-4-20-pipeline/`) с README.md, описывающим: какой
  APK (sha256 из identity json), какой флоу, uid аккаунтов, вердикт истории.
- Видео и крупные бинарники — `run/device-farm/<run-name>/` (untracked);
  в evidence-README дать путь и sha256.
- Идентичность APK каждой волны — `run/device-farm/apk-identity-<дата>.json`
  (прецедент: docs/agents/2026-10-04-mvp-apk-identity.json).

## 9. Стоимость (сверить в консоли!)

- $0.17 за устройство-минуту — и для автоматических прогонов, и для remote
  access; бесплатная проба ~250 устройство-минут (однократно).
- Прогон 02-smoke (3–5 мин) на 1 устройстве ≈ $0.51–0.85; сессия remote
  access на джорни 4.20 (до часа) ≈ $10.
- Лимит одного прогона/сессии — 150 минут (≈$25.50 максимум).
- Цифры не пересчитывались в AWS-консоли из-за отсутствия кредов — владелец
  сверит на странице Create run (там показывается оценка) и в Billing.
- Слух о деприкации Device Farm (2025) поиском не подтверждён (в lifecycle-
  списках AWS отсутствует, в ноябре 2025 сервис получал новые возможности);
  при первом входе в консоль проверить статус сервиса.

## 10. Карта джорни: что переиспользуется, что дописывать

| Джорни | Есть сейчас | Для Device Farm |
| --- | --- | --- |
| Связность/смок | `.maestro/mvp/01–07` (меню, синк, каталог арены, урок) — epic 1/2 эра | Готово: дефолт 02-request-sync; 04-arena-lesson-catalog требует «Loaded 4 of 4» — фикстуры каталога на текущем демо-бэкенде могут отличаться, сверить перед использованием |
| 4.20 конвейер ИИ-проверки | Серверная машина целиком в functions (trigger onDocumentCreated); клиентских Maestro-флоу авторинга НЕТ (джорни 4.3–4.5 гонялись ui.sh/руками) | Дописать флоу: меню → Автор → черновик → Send to arena → статус UNDER_REVIEW/REVIEW_DELAYED → исходы. Половина «линтер-отказ → REVIEW_DELAYED» работает и без ключа модели; живые WORDS/FACTS/TRANSLATE — только после §11 |
| 4.26 три входа предложений | UI входов готов (4.22 ResultContent «Сообщить об ошибке», 4.23 «Предложить правку», 4.24 «Предложить автору», 4.25 «Предложения» в Решениях); флоу НЕТ | Дописать 2–3 флоу + прогон с `INSTALL_MVPB=1`: mvpb = автор (одобряет в «Предложениях»), mvp = предлагающий; STALE-отказ — правкой цели между подачей и решением |
| 5.13 device-proof турниров | Пройдено вручную на Pixel 10 Pro (evidence 2026-10-18-e5-device-proof); организаторский маршрут в UI; оппонент — `scripts/course-tournament-device-opponent.js` с VPS (ему всё равно, где устройство); преарация `course-tournament-device-prep.js` | Дописать флоу организатора (Events → Organize → prepare → согласие → эскроу → квалификация); оппонента запускать с VPS параллельно прогону; открытый пункт той волны — доставка outbox-ответов — проверится здесь же |
| 9.9 NOIR-свип + доступность | NOIR-экраны 9.1–9.4 слиты; целевых флоу НЕТ | Автоматизируемо: прогоны ключевых флоу с `MAESTRO_FONT_SCALE=1.3` + скриншоты всех экранов; TalkBack/фокус-порядок — только remote access руками владельца |

Прецедент написания флоу: `.maestro/mvp/arena-e2b/README.md` — селекторы
снимаются с реального устройства (`maestro hierarchy`), в флоу — литеральные
видимые строки, без координат; флоу кладутся в `.maestro/mvp/` и попадают
в тест-пакет сборщиком автоматически.

## 11. Открытые пункты (что требует владельца)

1. **Креды AWS** — IAM-пользователь, политика AmazonDeviceFarmFullAccess,
   `aws configure --profile devicefarm` на VPS (§3). До них ни одного
   обращения к AWS не делалось.
2. **Ключ модели для 4.20**: в `schoolquiz-functions-emu` нет
   `REVIEW_MODEL_API_URL / REVIEW_MODEL_API_KEY / REVIEW_MODEL_NAME` —
   конвейер по построению громко откажет `REVIEW_MODEL_ENV_MISSING` (или
   уйдёт в REVIEW_DELAYED). Владельцу: вписать env в контейнер (секрет не
   в Git) и перезапустить functions-emu. Без ключа 4.20 закрывается только
   наполовину (отказ/REVIEW_DELAYED), живые вердикты WORDS/FACTS/TRANSLATE
   невозможны.
3. **Публичная открытость демо-рига**: Firestore-эмулятор за
   204.44.66.27:8080 не аутентифицируется и сегодня доступен из интернета
   (так уже было для hitl-хостов; Device Farm лишь добавляет устройств AWS
   в список клиентов). Если это неприемлемо — закрыть портами/туннелем,
   но тогда Device Farm-устройствам нужен другой путь к ригу (решение
   владельца).
4. **Флоу джорни 4.20/4.26/5.13/9.9** (§10) — дописать после первой
   smoke-волны; селекторы снимать с реального устройства через
   `maestro hierarchy` (можно прямо в remote access-сессии).
5. **Сверка цен и статуса сервиса** в консоли AWS (§9).
