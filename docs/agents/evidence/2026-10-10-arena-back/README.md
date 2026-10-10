# 3.10 arena-hardware-back-dispatcher — прогон владельческого harness на redroid-риге (story 3.10, epic-arena-completion) — 2026-10-10 ночь

Hardware Back через системный диспетчер на арен-маршрутах доказан **самим владельческим
harness** (`.maestro/mvp/arena-matrix/run-system-back-catalog.sh`, runnerVersion
`arena-system-back-catalog-v2`, sha256 `b0da6d62…` — файл не менялся): **PASS**, `nextRequired:
none` (`run/result.json`). Цепочка: верифицированный Home → выгруженный каталог (без нажатия
«Load lessons») → **KEYCODE_BACK** → верифицированный Arena root → **KEYCODE_BACK** → тот же
верифицированный Home. Все четыре поверхности оценены ассессором harness в состояние CONTEXT с
**нулевыми unsafe-маркерами** — ни одной prepared-ticket/runner/receipt поверхности ни до, ни
между, ни после Back (иерархия снята перед каждым Back; `run/*-safety.json`).

## Части доказательства

- `run/result.json` — PASS (phase system-back-catalog).
- `run/{initial,catalog-before-back,arena-root-after-back,home-after-back}-safety.json` —
  expected/observed состояния: home → catalog → arena-root → home, `unsafeScreenMarkers: []`
  во всех четырёх, `observedKnownState: true`.
- `run/*-hierarchy.json` — полные иерархии поверхностей (screencap-и чёрные — FLAG_SECURE,
  владелецская device-строка визуальной приёмки не закрыта этим прогоном; вождение и оценка —
  по иерархиям maestro).
- `run/maestro-*.log`, `run/first-system-back.action`, `run/second-system-back.action` —
  журналы flows и записанные Back-действия.
- `run/run-manifest.env` — идентичность прогона: candidateHead `4003a978…` (APK C, debug
  App Check — та же установка, что во всех риг-прогонах 09–10.10), candidateApkSha256 =
  installedBaseApkSha256 = `22a81c8a…` (harness сверил байты установленного base.apk с
  кандидатским APK и сертификат apksigner'ом), selectorMapSha256 `f9f6516c…`.

## Selector map

`selectors.env` — карта видимых текстов установленного кандидата. Ключи HOME/INTERNET/
OPEN_MENU/ARENA_NAV/ARENA_TITLE/CATALOG_PROMPT/LOAD_LESSONS/ARENA_RESERVATIONS +
TICKET_LABEL/TICKET_STATE/OPEN_SAVED/RECEIPT/RATING/LOADED_CATALOG проверены живьём по дампам
этой же установки (device-половина 3.8 того же вечера + прогоны sb-01…sb-03); OPEN_CATALOG и
ARENA_ROOT_PREVIEW на момент записи держались по примеру владельца — и **оба были испытаны
живьём самим harness** (после первого Back ассессор распознал arena-root по arenaTitle +
arenaRootPreview + openCatalog). Файл лежит в этом каталоге и в приватном
/root/arena36-private/run-1/work/sb-selectors/; `.maestro/**` владельца не менялся (запуск —
`bash`, без chmod; runner и flows читались как есть).

## Условия прогона

- Риг: schoolquiz-emu (перезапуск из остановленного состояния, состояние персистентно),
  adb localhost:5555, реверсы 8080→18090 / 9099→19090 / 5001→5001. Перед прогоном приложение
  force-stop (harness сам запускает retained-приложение flow 00-launch-retained, clearState:
  false). FallbackHome-квирк холодного старта обходится прямым `am start` на этапе подготовки
  (к harness приложение вышло из force-stop).
- Бинарные пути переданы явно (`--adb/--maestro/--apksigner/--node/--java-home`): проверки
  harness `[[ -x ]]` не делают PATH-lookup; apksigner —
  /workspace/infrastructure/android-toolchain/sdk/build-tools/36.0.0, JDK /opt/jdks/jdk-27.
- Сервер (demo-backend + functions-эмулятор) не перезапускался; прогон навигационный — серверных
  записей не создаёт (ассессор запрещает любые платные поверхности). После прогона риг
  остановлен (docker stop, состояние персистентно).

## Честные границы

1. `MATRIX_RUN_ID` прогона — `arena38-sb-001` (передан в flows env).
2. Скриншоты harness чёрные (FLAG_SECURE) — зафиксированы как есть; содержательная
   доказательная поверхность — иерархии и safety-оценки.
3. Прогон единичный (не флак-серия): Back-цепочка детерминирована навигационным стеком;
   повтор прогона воспроизводим той же командой (идентичность в run-manifest.env).
