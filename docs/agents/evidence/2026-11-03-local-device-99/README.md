# Локальная кампания · джорни 9.9 (авто-половина, FONT_SCALE=1.3) — PASSED (с адаптацией хвоста под эту сборку): ключевые экраны сняты на повышенном шрифте, арена-каталог загружен с сервера, магазин снят; вкладки «ARENA» в полосе этой сборки нет (хвост переведён на INTERNET-вкладку) (03.11)

## Прогон

- Устройство A14 (RF8W80MBR3Z), mvp-APK 02.11 (`c33d3e3f…`), per-app локаль en-US,
  шрифт `font_scale 1.3` (поставлен на время джорни, возвращён 1.0 после), анимации 0.
  Свежий аккаунт (pm clear перед прогоном; джорни бесплатна — вотчер не нужен).
- Флоу `.maestro/mvp/99-accessibility-key-screens.yaml` — ядро: 20 шагов COMPLETED
  (HOME QUESTS → меню → SYNC NOW → «Last sync» → «Not synced yet» отсутствует →
  MVP validated checks → курс → урок «val» ×2 CORRECT → «Sync result now» →
  consent → «Completion confirmed by the server» → скролл до «Report a mistake»).

## Находки хвоста (флоу правлен по кадрам на своём устройстве)

1. **Back с результата** (фикс 719b4dbb) — отработал: `pressKey back` ведёт на
   уровень выше (на этой сборке — на экран темы, а не в шелл; скрин
   `screens/99-theme-screen-font13.png`). Условная ветка «^Exit$» — SKIPPED.
2. **Вкладки нижней полосы этой сборки: LOCAL / INTERNET / EVENTS / SHOP.**
   Вкладки «ARENA» НЕТ (это объясняет фолл v6 «Tap ARENA — Element not found»
   даже после скрим-тапа v7: закрывашка шторки была верной, но селектор цели —
   нет). Арена живёт за вкладкой **INTERNET**: её экран называется «ARENA»
   (заголовок, «Arena lessons», LOAD LESSONS, бронирования) — шаг «Open lesson
   catalog» в этой сборке отсутствует.
3. **Арена-каталог на сервере живой**: «LOAD LESSONS» → «Loaded N of N lessons»
   (прошло с первого раза; на демо-риге арена-фикстуры на месте).
4. **SHOP** — вкладка открылась, «Premium» виден при 1.3 (скрин
   `screens/99-shop-font13.png`).

## Артефакты

- Флоу: `.maestro/mvp/99-accessibility-key-screens.yaml` (ядро, 20 шагов),
  `.maestro/mvp/99-tail-local.yaml` / `99-tail-local2.yaml` (хвост, PASSED 6/6).
- Скрины: `screens/` (2 кадра на 1.3; промежуточные кадры ядра — в trace прогона,
  `/Users/tpov/.maestro/tests/2026-11-03_*`).

## Вердикт

**PASSED** (авто-половина): ключевые экраны при 1.3 зелёные, включая арена-каталог
и магазин, которые ни одна DF-волна не дотащила. TalkBack-половина 9.9 — по-прежнему
ручной remote access владельцем (вне этой кампании). Правка владельцу: в committed
флоу хвост «ARENA» заменить на INTERNET-путь (правки приложены в этой кампании).
