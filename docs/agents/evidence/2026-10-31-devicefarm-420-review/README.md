# Device Farm · джорни 4.20-partial (автор → Send to arena → ревью) — v2: глубокий прогресс, FALL на «Send to arena» (31.10)

Вердикт v2 (пакет sha256 50ac4967…): **FAILED (1 прогон, 9.28 устройстве-минуты; run
`arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/c91d1254-9356-45f2-a83e-fab176098343`)**,
но доказано живьём всё авторинг-ядро до готового черновика: синк → навигация «My quests» (скролл
9-го пункта шторки, правка v2 работает) → Create quest → квест/раздел/тема/урок → 2 ordering-вопроса
(easy+hard) → скрин 420-questions-done. Упало на `tapOn "Send to arena"` — кнопка ниже кромки
после добавленных вопросов (структура выросла). До UNDER_REVIEW/свитов живой модели не дошли.

## Прогон v2

- Run ARN: см. выше; спека `upload:…/36cc2067…` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/420-review-v2/` (maestro-stdout, testspec output, видео, logcat).
- Серверная приборка (watch-420.log): аккаунт устройства `0ooaOJe9pk0gkCj2PRtjwWf2shFL`,
  topup 0→3000 ноликов (до шагов платы) — до подачи заявка не доехала (кнопка не нажата),
  quest_review_requests пуст, await-review-request корректно отвалился по таймауту.

### Причина падения (кадр видео 17:36:28 + maestro-stdout)

Экран «Quest creation» после «Back to structure»: список структуры + «Easy questions» /
«Hard questions» у нижней кромки — «Send to arena» ниже зоны видимости, maestro не нашёл текст.
Ранее (до добавления вопросов) кнопка была видна — флоу не скроллит перед тапом.

### Точная правка флоу на следующую итерацию (владельцу; пакет заморожен)

Перед `tapOn: "Send to arena"` добавить
`scrollUntilVisible: { element: { text: "Send to arena" }, direction: DOWN, timeout: 15000 }`.
Дальше флоу готов: закрывашка consent уже в пакете, watch-420 + живая модель glm-4.7
(functions-emu, REVIEW_MODEL_* на месте, readModelConfig ok=true) доведут заявку до
настоящих вердиктов «Words»/«Facts» — в этом прогоне конвейер не стартовал (заявки не было).

## Прогон v1 (пакет debd5457…, 31.10 утро) — FAILED на навигации, историческая секция

- Run ARN: `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/110a4b02-2593-4e42-bb9c-70a563aee9f6`
- Спека: `upload:…/4e3b57b5-f5f8-4a91-a757-b8480cde261d` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/420-review/`.
- Прошли: launch → HOME QUESTS → меню → SYNC NOW → «Last sync: 31.10.2026 15:15».
  Упало: `Tap on "^MY QUESTS$"... FAILED — Element not found` (в v4.0.1 пункт называется
  «My quests» и лежит ниже кромки — исправлено в v2, доказано этим прогоном).

## Кадры шторки (OCR видео)

```
Home quests / Archive / Decisions / My lessons: statuses / Curation / Опросы /
Operation history / DESIGN CATALOG / Last sync: 31.10.2026 15:15 / v4.0.1-isolated
```

## Нужные правки флоу (в отчёт владельцу; пакет заморожен)

1. Вход в авторскую зону: уточнить фактический пункт шторки (для 4.26/4.20 это,
   судя по UI, «Decisions»/«Home quests» + «Create quest» на главном экране) —
   сверить на риге с ws-scrcpy и заменить `^MY QUESTS$`.
2. После правки навигации джорни упрётся в economy-consent на платной подаче
   «Send to arena» (1000 ноликов; captured==null → Required, см. evidence
   2026-10-31-devicefarm-99-accessibility) — добавить закрывашку «Accept and send»
   и после неё повторный SYNC NOW.
3. Дальше конвейер готов: watch-420 `await-review-request` гонит настоящий
   review-queue-worker (живая модель glm-4.7 в контейнере) до терминала.
