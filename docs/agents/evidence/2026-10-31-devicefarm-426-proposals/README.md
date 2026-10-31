# Device Farm · джорни 4.26 (три входа предложений) — v3 FAIL: cleartext-фикс доказан живьём, флоу упёрся в отсутствие сид-каталога на HOME QUESTS (31.10)

## Прогон v3 (пакет sha256 ad9f3939…)

Вердикт: **FAILED (1 прогон, 2.34 устройстве-минуты; run
`arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/f6eb351b-33c1-4191-88c8-ef318054f589`,
окно 19:05:07–19:08:11 UTC)**. Спека `upload:…/28526588-a75c-41d6-8192-416f0d7e4220`
(SUCCEEDED, мост Mac→AWS: прямой PUT спеки с VPS по-прежнему ломан провайдером —
`SignatureDoesNotMatch` на presigned S3, инцидент 30-31.10 не закрыт). Артефакты:
`run/device-farm/426-proposals-v3/` (maestro-stdout, testspec output, видео, logcat, кадры).

**Доказано живьём (главная цель v3 — снят блокер v2):**

- **mvpb-клиент достучался до демо-рига** — cleartext NSC-фикс работает на реальном устройстве:
  `Launch app com.tpov.schoolquiz.mvpb → SYNC NOW → "Last sync: .*" COMPLETED` (маэстро-трейс,
  шаги 1–7). v2 падал ровно здесь.
- Оба аккаунта живут на сервере: mvpb `FXMLUUW7OOOqk6anmHmTwGGsb2Ma` (создан 19:06:13.227Z),
  авторский сид-курс залит сразу за его появлением (`seeded-426`, 19:06:17.7Z — catalogs/quests/
  sections/themes/lessons/questions + журнал `catalogs/df426-catalog/sync_changes`, все 7 записей);
  mvp-предлагающий `LMHLHW1AQ8Um1ATXSLiQEeft6bYC` (создан 19:06:43.315Z), topup 0→3000 ноликов
  (watch-426 отработал штатно, лог `run/device-farm/watch-426/watch.log`).
- Установка второго клиента по INSTALL_MVPB=1 и параллельная работа двух изолированных
  приложений на одном устройстве — без сбоев.

**Падение (шаг 16 из маэстро-трейса):** `Assert that "DF author catalog" is visible... FAILED` —
после синка mvp и тапа «HOME QUESTS» сид-каталог на экране домашних квестов не появился
(на экране только карточки «MVP checks» / «MVP validated checks» из бандла; кадр видео 19:07:23,
спустя 40 с после завершения синка).

### Разбор (сервер доказанно не виноват — гонки сид/синк нет)

- Timestamps эмулятора: сид 19:06:17.711–18.459 → users/mvp создан 19:06:43.315, т.е. каталог
  и его журнал лежали на сервере за **25 с до** синка предлагающего. fetchChangedSince (курсор
  бандла ~дата сборки) обязан был вернуть df426-catalog; toCatalogDto строку не отбрасывает
  (единственное обязательное поле `name` на месте).
- Кадр видео 85 с (19:06:32, шторка mvp): **«Last sync: 23.10.2026 19:06»** — устаревший
  last-sync бандла (v4.0.1-isolated). Флоу ждёт `Last sync: .*` — паттерн совпадает и со
  стертым лейблом, поэтому флоу пошёл дальше, не дождавшись фактического первого синка
  (завершился 19:06:43, судя по users-документу).
- Но и после завершения синка (окно ассерта + кадр 19:07:23) каталог на экране не появился.
  Гипотезы в порядке правдоподобия: (1) каталогная полка изолированной сборки не подхватывает
  строки, приезжающие пост-фактум синком (Room-кэш/курсор — тот же класс, что «Active events»
  5.13 v2b: экран читает только то, что было до входа); (2) каталог-пулл первого синка не
  применился (нужен репро на риге глазами, ws-scrcpy). Для владельца: это кандидат в реальные
  клиентские баги — серверная половина (журнал, сид, тайминги) чиста.

### Точная правка флоу на следующую итерацию (пакет заморожен — в отчёт владельцу)

1. Ловушка «Last sync: .*»: во всех флоу на свежем устройстве заменить ожидание на паттерн,
   не совпадающий с бандловым лейблом (например два цикла: `tapOn "^SYNC NOW$"` + wait, и
   повторный цикл перед ассертами контента), либо ждать конкретную свежую дату.
2. Перед `assertVisible: "DF author catalog"` вставить повторный вход в экран
   (назад → HOME QUESTS) и/или второй SYNC NOW — по аналогии с фиксом 513 v3.
3. Хвост джорни (3 подачи + авторская половина «For my lessons») не достигнут из-за этого
   ассерта; конвейер предложений с живой моделью в этом прогоне не стартовал (заявок нет).

---

## Прогон v2 (пакет sha256 50ac4967…) — FAIL: mvpb-клиент без cleartext (31.10, историческая секция)

Вердикт: **FAILED (1 прогон, 2.68 устройстве-минуты; run `arn:…:run:69133ff9-bd68-4307-82e3-02855263c8f8/901a2456-d023-48bc-adc5-474f389d75a3`)**.
Прогон v2 (пакет sha256 50ac4967…, INSTALL_MVPB=1) упал на первом же шаге mvpb: «Last sync»
не появился за 90 с — **второй клиент физически не может достучаться до демо-рига**. Причина —
дефект сборки APK (не флоу и не риг): у мvpb нет network security config, cleartext к
204.44.66.27 запрещён политикой Android. До трёх входов предложений не дошли; watch-426
аккаунтов не увидел («no mvpb user») — серверная приборка не стартовала.

## Доказательство (бинарники обоих APK + код)

- `run/device-farm/android-next-mvp.apk`: манифест ссылается на `networkSecurityConfig`,
  в пакете есть `res/xml/mvp_network_security_config.xml` (cleartext для 204.44.66.27).
- `run/device-farm/android-next-mvpb.apk`: ссылки НЕТ, xml-файла НЕТ (проверено распаковкой
  обоих APK + сканом string-pool бинарных манифестов; артефакт проверки — лог ниже в этом README
  не приводится, воспроизводится `unzip -l … | grep network` + `aapt dump xmltree`).
- Код: NSC живёт в source set `apps/android-next/src/mvp/` (AndroidManifest.xml:6 +
  res/xml/mvp_network_security_config.xml). Билд-тайп `mvpb` (apps/android-next/build.gradle.kts)
  наследует `initWith(mvp)`, но source set `src/mvpb/` содержит ТОЛЬКО google-services.json —
  манифест и NSC в мерж mvpb не попадают, falls back к main-манифесту без cleartext-политики.
- Кадр видео (17:13:58, шторка mvpb): «Not synced yet!» при живых пробах устройства
  (host->vps-8080 200 в pre_test) — то есть сеть есть, запрещён именно cleartext HTTP.

## Прогон

- Run ARN: см. выше; спека `upload:…/fe980984…` (SUCCEEDED, мост Mac→AWS), INSTALL_MVPB=1
  (adb install mvpb — Success в testspec output).
- Артефакты: `run/device-farm/426-proposals-v2/` (maestro-stdout, testspec output, видео, logcat).
- Упало: `Assert that "Last sync: .*" is visible... FAILED` (90 с) — первый SYNC NOW mvpb.

## Нужная правка (владельцу; требует пересборки APK и перезаливки пакета — не в этом пакете)

1. Дать mvpb ту же cleartext-политику: манифест `apps/android-next/src/mvpb/AndroidManifest.xml`
   (копия src/mvp/AndroidManifest.xml — `@xml/mvp_network_security_config` + tools:replace
   метаданные) ИЛИ поднять NSC в main source set с условием. После правки — пересборка обоих
   APK (`sgw ./gradlew :apps:android-next:assembleMvp :apps:android-next:assembleMvpb
   -PmvpEmulatorHost=204.44.66.27`), пересборка тест-пакета build-test-package.sh и заливка
   с чистого канала (Mac).
2. После этого джорни 4.26 готова к повтору как есть: watch-426 + df-run3 (INSTALL_MVPB=1);
   живая модель glm-4.7 уже в functions-emu — предложения должны дойти до CHECK_PASSED
   и стать видимы автору в «For my lessons» (закрывашка consent в флоу уже есть).
