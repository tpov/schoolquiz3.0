# Device Farm · джорни 9.9 (авто-половина, FONT_SCALE=1.3) — v4: ядро зелёное, point-tap мимо: на экране результата выхода нет вовсе (выход = Back), × только на экранах вопросов и слева (31.10)

## Прогон v4 (пакет sha256 8c1b2426…) — ядро зелёное повторно; point-tap 90%,6% попал мимо:
на экране результата НЕТ ×-бейджа вовсе, выход — системный Back (31.10 ночь)

- Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/71e1319e-2454-4e88-b31a-acda46aff77d`,
  FAILED, 2.92 устройсте-минуты, окно 21:57:47–22:01:23 UTC; спека `upload:…/c7969f76…`
  (SUCCEEDED, мост Mac→AWS; FONT_SCALE=1.3). Артефакты: `run/device-farm/99-accessibility-v4/`
  (кадры в `frames/`).
- Пройдено живьём (40 шагов COMPLETED): launch → HOME QUESTS → меню → SYNC NOW → «Last sync» →
  «Not synced yet» нет → MVP validated checks → курсс → урок → «val» ×2 (CORRECT) → Continue →
  Sync result now → consent «Accept and send» → **«Completion confirmed by the server»** →
  скрин 99-lesson-result → скролл до «Report a mistake» → скрин 99-result-entries →
  **tap point (90%,6%) механически исполнен**. Упало: следующий `assertVisible "Open menu"`
  (диалог «^Exit$» не поднялся — SKIPPED, раннер остался открыт; последние кадры видео —
  всё ещё экран результата).
- **Причина (проверена по коду, а не по видео):** диагноз v3 «×-бейдж справа вверху» был
  неверен дважды. (1) На экране результата выходного контрола НЕТ ВООБЩЕ:
  `ResultContent.kt` (night-e2) начинается с кикера «LESSON COMPLETE — EASY», внутри только
  Run again / Next lesson / discussion — ни CrossButton, ни Exit. (2) ×-бейдж существует
  только на ЭКРАНАХ ВОПРОСОВ и стоит СЛЕВА: `QuestionProgressHeader.kt` — Row: Close-иконка
  → pills → `Spacer(weight 1f)` → таймер справа. Тап 90%,6% попадает в пустую зону хедера.
- **Точная правка (владельцу; флоу заморожен):** вместо point-tap дать системный Back —
  `pressKey: back` (или свайп от левой кромки); далее существующий `runFlow when visible
  "^Exit$" → tapOn "^Exit"` сработает по диалогу подтверждения. ARENA-каталог и SHOP снова
  не достигнуты. TalkBack-половина 9.9 — по-прежнему remote access руками владельца
  (list-remote-access-sessions = 0 после прогона).

## Прогон v3 (пакет sha256 ad9f3939…)

Вердикт: **FAILED/частичный PASS ядра (1 прогон, 3.0 устройстве-минуты; run
`arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/7ffa98cd-0176-45c5-9ad1-cdd8e967ae68`,
окно 19:34:18–19:38:10 UTC)**. Спека `upload:…/9e263005-f703-49f9-9807-dccb196aefb4`
(SUCCEEDED, мост Mac→AWS; FONT_SCALE=1.3). Артефакты: `run/device-farm/99-accessibility-v3/`
(maestro-stdout, testspec output, видео, logcat, кадры).

Пройдено живьём (36 шагов COMPLETED): launch → HOME QUESTS → меню → SYNC NOW → «Last sync»
→ «Not synced yet» отсутствует → MVP validated checks → курсс → урок → «val» ×2 (CORRECT) →
Continue → **Sync result now → consent-диалог реально поднялся → «Accept and send» COMPLETED →
«Completion confirmed by the server»** → скрин 99-lesson-result → скролл до «Report a mistake» →
скрин 99-result-entries. Ядро джорни (до подтверждения сервером и записей результата
включительно) зелёное, как и в v2.

Упало: `scrollUntilVisible { text: "Exit lesson", direction: DOWN }` — **No visible element
found** (15 с скролла). Причина — селектор, и она же была реальной причиной v2 (диагноз v2
«кнопка ниже кромки» неверен): **«Exit lesson» — это contentDescription иконки-крестика**
(`CrossButton.kt:25`, `R.string.runner_cd_exit_lesson`, префикс runner_cd_ = content desc),
кнопка **без текста** сидит в ФИКСИРОВАННОМ верхнем баре экрана результата (кадр видео:
×-бейдж справа вверху, «LESSON COMPLETE 2/2» под ним). Текстового узла «Exit lesson» на
экране не существует — текстовый селектор Maestro не может совпасть ни при каком скролле
(v2 tap и v3 scroll падали об одно и то же).

### Точная правка флоу на следующую итерацию (пакет заморожен — в отчёт владельцу)

Заменить шаг на тап по ×-бейджу в правом верхнем углу — единственный безтекстовый контрол
джорни:
```yaml
- tapOn:
    point: "90%,6%"   # CrossButton (×) в фиксированном top bar; текста нет — только contentDescription
```
(альтернатива долгосрочная: дать CrossButton семантический testTag и селектить по id).
Дальше хвост уже совместим: runFlow `when visible: "^Exit$"` (диалог подтверждения
`runner_action_exit` = «Exit») — во флоу есть и сработает.

## Прогон v2 (пакет sha256 50ac4967… с consent-закрывашкой) — partial PASS, историческая секция

Вердикт (v2, пакет sha256 50ac4967… с consent-закрывашкой): **partial PASS — ядро джорни доказано живьём до подтверждения сервером включительно; хвост после результата урока упал на навигации (заранее известная правка, не итерировали)**. Прогоны 1–2 (31.10 утро, пакет debd5457…) — FAILED, см. ниже.

## Прогон v2

- Run ARN: `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/5e71a2f8-4fbd-490c-a516-1ad575651f06`
- FAILED (хвост), 2.85 устройстве-минуты, окно 16:56:55–17:00:50 UTC; спека `upload:…/d3d73125…` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/99-accessibility-v2/` (maestro-stdout, testspec output, видео, logcat).

Пройдено живьём (каждый шаг COMPLETED в maestro-stdout): launch → HOME QUESTS → меню → SYNC NOW →
«Last sync» → MVP validated checks → MVP smoke course → урок → «val» ×2 (CORRECT оба) → Continue →
**Sync result now → consent-диалог «Accept and send» (закрывашка v2 отработала) → «Completion confirmed
by the server»** → скрин 99-lesson-result → скролл до «Report a mistake» → скрин 99-result-entries.

Серверное подтверждение результата (главное отличие от прогонов 1–2): в эмуляторе mutation_keys —
`lesson_runner.SUBMIT_ATTEMPT state=completed` в **16:59:21.780Z**, uid устройства `bVvQPnoV…`
(окно прогона 16:56:55–17:00:50) — результат урока реально доехал до сервера и был принят.

Упало (хвост, известная правка): `tapOn "Exit lesson"` — Element not found: после скролла к
«Report a mistake» кнопка «Exit lesson» ниже кромки при font_scale 1.3. Ядро джорни (до результата
урока включительно) зелёное от начала до конца, поэтому по решению координатора — partial PASS,
без итерации. ARENA-каталог и SHOP в этом прогоне не достигнуты.

### Точная правка флоу на следующую итерацию (владельцу)

В `99-accessibility-key-screens.yaml` перед `tapOn: "Exit lesson"` добавить
`scrollUntilVisible: { element: { text: "Exit lesson" }, direction: DOWN, timeout: 15000 }`
(скрин результата при 1.3 не показывает кнопку целиком); далее хвост как задуман
(шторка → ^ARENA$ → Load lessons → SHOP). TalkBack-половина 9.9 — по-прежнему remote access
руками владельца (list-remote-access-sessions = 0 после прогона).

## Прогоны v1 (пакет debd5457…, без закрывашки) — FAILED, историческая секция

| # | Run ARN | Итог | Минуты | Где остановились |
| --- | --- | --- | --- | --- |
| 1 | `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/3e7622b8-e784-409d-afa6-b8cc79cc1538` | FAILED | 2.67 | первый SYNC NOW: «Last sync: .*» не появился |
| 2 | `…/run:69133ff9…/04e06166-aa48-40d4-8523-a0daae277205` | FAILED | 3.98 | «Sync result now» → 120 с ждал «Completion confirmed by the server» |

Спеки (обе SUCCEEDED, залиты мостом Mac→AWS — см. финальную секцию README 2026-10-29-journeys-prepared):
`upload:…/e7a2feba…` (прогон 1, knobs не доехали — баг sed, см. ниже) и `upload:…/826f7815…` (прогон 2).
Артефакты: `run/device-farm/99-accessibility/` и `run/device-farm/99-accessibility-run2/`
(testspec output, logcat, видео, customer artifacts; run/ вне Git по культуре репо).

## Прогон 1: риг был недоступен снаружи — socat-релеи умерли при ребуте VPS

- `host->vps-8080 000` в pre_test; «Last sync» не появился за 90 с.
- Причина: VPS перезагружался 29.10 ~08:45; socat-релеи `0.0.0.0:9099→127.0.0.1:19090`
  и `0.0.0.0:8080→127.0.0.1:18090` (запись в run/device-farm/apk-identity-2026-10-28.json,
  probes 200/200) были запущены руками и ребут не пережили. Эмуляторы внутри — живы.
- Fix (та же смена): релеи подняты заново (`nohup socat TCP-LISTEN:9099,bind=0.0.0.0,fork,reuseaddr
  TCP:127.0.0.1:19090` и `TCP-LISTEN:8080,bind=204.44.66.27,…→127.0.0.1:18090`; 8080 на
  внешнем IP, чтобы не конфликтовать с локальным сервисом на 127.0.0.1:8080). Пробы с Mac:
  9099→200, 8080→200, 5001→404 — сигнатура 28.10. Хвост владельцу: оформить релеи юнитом.

## Прогон 2: канал и риг доказаны, упёрлись в economy-consent

Прошли: install (JDK17+Maestro), mvp install, font_scale 1.3 применён, пинг/роутинг,
launch → HOME QUESTS → меню → SYNC NOW → «Last sync» → MVP validated checks → MVP smoke
course → урок → «val» ×2 (CORRECT оба) → Continue → Sync result now (тап COMPLETED).

Дальше 120 с ожидания «Completion confirmed by the server» — FAIL. OCR кадров видео
(последняя минута, экран LESSON COMPLETE):

```
Waiting for server confirmation. Score and …
SYNC RESULT NOW
Payment terms have changed
Your attempt progress is saved, but the server awaits your consent
to the current prices and rules. Accept the terms and the result
will be sent without losing the attempt.
[Later] [Accept and send]
```

Сервер-side подтверждение: у аккаунта устройства (`users/yk1OddE9…`, единственный
свежий за 3 ч) пусто в `private/…`, callable `submitLessonResultEvents` не вызывался
ни разу — результат не уехал вообще.

### Причина (код, не инфраструктура)

- `onSyncResult` (`DefaultLessonRunnerRootComponent.kt:200`): при
  `verdict() != Sufficient` открывает consent-диалог и НЕ ставит синк в очередь.
- `TermsConsentGate.evaluate` (`shared/feature/economy/domain/…/TermsConsentGate.kt`):
  `captured == null → Required` — свежее устройство ВСЕГДА даёт первое согласие
  перед первым результатом (by design, AD-5/CAP-16).
- Флоу `.maestro/mvp/99-accessibility-key-screens.yaml` после «Sync result now» не имеет
  шага закрытия диалога («Accept and send»). Прежние прогоны флоу на риге до этого шага
  не доезжали (замеры 09-27/28 — только открытие урока), т.е. шаг никогда не выполнялся.

### Нужная правка флоу (в отчёт владельцу; пакет заморожен, не перезаливался)

Во все флоу с «Sync result now» (99, 426; для платных подач — 420/426-предложения, 513-квалификация
проверить по факту) добавить закрывашку:

```yaml
- runFlow:
    when:
      visible: "Accept and send"
    commands:
      - tapOn: "Accept and send"
```

сразу после `tapOn: "Sync result now"` (и аналогично после платных «Send to arena»/
подач предложений, если диалог всплывёт там). После правки — пересборка пакета
`docs/agents/device-farm/build-test-package.sh` и заливка с чистого канала (Mac/другая сеть).

## Побочные починки инструментов (закоммичены)

- `testspec-maestro.yaml`: составной `if [ "$INSTALL_MVPB" = "1" ]; then adb install…`
  Device Farm ломал при конвертации в shell (`{if […]}: No such file or directory`) —
  mvpb не ставился бы в 4.26. Заменён маркером `MVPB_INSTALL_PLACEHOLDER`, который
  раннер разворачивает в одну простую команду.
- `df-run2.sh`/`df-run3.sh`: knobs-патчи sed были в двойных кавычках — `${INSTALL_MVPB:-0}`
  разворачивался bash-ем ДО sed, замены молча не было (прогон 1 ушёл без FONT_SCALE? —
  нет: FONT_SCALE доехал, т.к. подставлялся из переменной; но INSTALL_MVPB=1 для 4.26
  не доехал бы никогда). Паттерны переведены в одинарные кавычки.
- `df-run2.sh`/`df-run3.sh`: `skipAppResigning` → `skipAppResign` (текущий API-контракт).
