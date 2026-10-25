# RED-база корневого ciCheck — памятка (2026-10-05, ночной трек E)

schemaVersion: 1, capturedAtUtc: 2026-10-05T17:22:41Z, дерево: night-e1f @ b8bc23ff (ветка night/e1-followups).

## Что записано 04.10 (подтверждено по evidence, БЕЗ новой сборки)

Ночной трек E 05.10 НОВОЙ СБОРКИ НЕ ДЕЛАЛ (правило ночи: тяжёлые сборки ведёт оркестратор).
Подтверждение записанного RED — чтение evidence 04.10 и проверка предусловий сегодня:

- `docs/agents/evidence/2026-10-04-root-cicheck/cicheck-result.json` — result **FAIL**, exit 1,
  единственная упавшая задача `:apps:android-next:processDebugGoogleServices`
  («File google-services.json is missing…»), команда
  `JAVA_HOME=/opt/jdks/jdk-27 ./gradlew ciCheck --no-configuration-cache --no-parallel --max-workers=1`
  на HEAD `1e596126` ветки `continuation/mvp-candidate-20260926`.
- `root-cicheck-run1.log` (BUILD FAILED in 19s) и `root-cicheck-run2-exit-code.log`
  (BUILD FAILED in 15s, EXIT_CODE=1) — детерминированный повтор, отказ идентичен.
- `docs/agents/evidence/2026-10-04-root-cicheck/cicheck-result.json:44`: тесты, detekt,
  ktlint, functionsTest, scriptsTest НЕ исполнялись — ciCheck прервался на первой упавшей
  задаче (нога assembleDebug).

## Предусловие RED сегодня (проверено в дереве b8bc23ff)

Ночь 05.10 повторно проверила предусловие в своём дереве (b8bc23ff) без сборки:

```
ABSENT:  apps/android-next/src/debug/google-services.json
ABSENT:  apps/android-next/src/google-services.json
ABSENT:  apps/android-next/src/Debug/google-services.json
ABSENT:  apps/android-next/google-services.json
---mvp variant---
apps/android-next/src/mvp/google-services.json   (tracked, git ls-files подтверждает)
---gitignore---
.gitignore:19:**/google-services.json
.gitignore:54:debug/   (покрывает apps/android-next/src/debug/google-services.json)
```

Все четыре пути поиска из ошибки плагина пусты; debug-вариант, который собирает
`ciCheck → :apps:android-next:assembleDebug`, остаётся без конфига. Определение гейта —
`build.gradle.kts:68-84` (assembleDebug + test/allTests/compileAndroidTests/functionsTest/
scriptsTest/detekt/ktlintCheck/detektMetadataCommonMain).

## Условие снятия RED

1. Положить owner-локальный **debug** `google-services.json` в один из путей поиска плагина;
   канонический — `apps/android-next/src/debug/google-services.json` (плагин также принимает
   `apps/android-next/src/google-services.json`, `apps/android-next/src/Debug/google-services.json`,
   `apps/android-next/google-services.json`). Файл должен быть именно owner-локальным:
   `.gitignore` исключает его (`**/google-services.json`, `debug/`), коммитить его нельзя.
   Он существует в checkout владельца на Mac (rootCause в cicheck-result.json) — владельцу
   нужно либо скопировать его на VPS-дерево, либо прогнать ciCheck на Mac.
2. Повторить базовую команду и записать результат (включая exit code и, при отказе,
   разбор упавшей задачи) в `docs/agents/evidence/<дата>-rerun-cicheck/`.
3. История-черновик на этот прогон добавлена в `_bmad-output/mvp-candidate/epic-candidate-rig/tickets.toml`
   как 1.7 «re-run ciCheck и записать» — БЕЗ поля status (не диспатчится, пока файл не появится).

## Что это НЕ доказывает

- **RED не является сигналом о качестве кода.** ciCheck прервался на конфигурационной задаче
  до исполнения каких-либо тестов: detekt, ktlint, test, allTests, compileAndroidTests,
  functionsTest, scriptsTest, detektMetadataCommonMain ни разу не выполнялись в этой ноге
  (cicheck-result.json:44). «RED» здесь означает «гейт не прошёл», а не «найдены дефекты».
- **Не доказывает, что кандидат сломан.** mvp-вариант собирается: `assembleMvp` на том же
  HEAD — BUILD SUCCESSFUL, APK `eeb8fc6e…` (docs/agents/2026-10-04-mvp-apk-identity.json);
  у mvp-варианта google-services.json закоммичен, у debug — нет. Это различие вариантов,
  не дефект.
- **Не доказывает поведение в checkout владельца.** На Mac файл есть, поэтому там RED
  воспроизводиться не обязан; запись 04.10 описывает только VPS-дерево.
- **Не доказывает корректность самого файла**, когда он появится: гейт проверит лишь его
  наличие/валидность для плагина, но не совпадение с прод-конфигом Firebase и не годность
  для release/Play-сборок (release-варианты этим гейтом не собираются вовсе).
- **Не доказывает повторяемость в будущем**: владелец может сменить debug-конфиг; база
  04.10 привязана к HEAD `1e596126` и состоянию дерева на дату.
