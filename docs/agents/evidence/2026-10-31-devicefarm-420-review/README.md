# Device Farm · джорни 4.20-partial (автор → Send to arena → ревью) — FAIL: метка навигации (31.10)

Вердикт: **FAILED (1 прогон, 1.74 устройстве-минуты)**. Связь доказана (синк COMPLETED),
серверная приборка отработала (watch-420: аккаунт устройства `vB7bWL1d…`, topup 0→3000
ноликов до шагов платы). Флоу упал на навигации в шторке: `tapOn "^MY QUESTS$"` — такого
текста в UI v4.0.1 нет (в шторке «Home quests», раздел решений — «Decisions», шопа —
«DESIGN CATALOG»). До «Send to arena»/UNDER_REVIEW/свит живой модели не дошли.

## Прогон

- Run ARN: `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/110a4b02-2593-4e42-bb9c-70a563aee9f6`
- Спека: `upload:…/4e3b57b5-f5f8-4a91-a757-b8480cde261d` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/420-review/`.

Прошли: launch → HOME QUESTS → меню → SYNC NOW → «Last sync: 31.10.2026 15:15».
Упало: `Tap on "^MY QUESTS$"... FAILED — Element not found`.

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
