# Device Farm · джорни 4.26 (три входа предложений) — v2 FAIL: mvpb-клиент без cleartext (31.10)

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
