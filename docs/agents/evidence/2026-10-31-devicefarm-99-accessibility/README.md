# Device Farm · джорни 9.9 (авто-половина, FONT_SCALE=1.3) — FAIL: флоу не тапает первый economy-consent (31.10)

Вердикт: **FAILED (2 прогона, 6.65 устройстве-минут)**. Автоматизируемая половина 9.9
доказана до результата урока включительно (меню → синк → каталог → урок → 2 ответа →
экран результата) на Pixel 10 с font_scale 1.3; прогресс упёрся в дефект флоу, а не в
продукт: свежее устройство обязано дать первое согласие на платные условия (CAP-16),
флоу диалог не закрывает — шаг «Completion confirmed by the server» недостижим без правки флоу.

## Прогоны

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
