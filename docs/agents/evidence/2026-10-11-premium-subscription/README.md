# 2026-10-11 — M2.1: premium как одна подписка (риг-фрагмент HITL)

Коммит истории: `e86e4e6f` (ветка `night/e3-e8-inception`). Спека:
`_bmad-output/implementation-artifacts/spec-m2-1-premium-is-one-subscription.md`.

## Что проверено на риге (redroid 12, без GMS)

- APK `assembleMvp` из дерева `e86e4e6f` установлен поверх данных (`adb install -r`) — Success.
- Приложение поднялось на живой сессии (home без логина), магазин открылся тапом по «SHOP».
- `rig-01-shop.xml`: строка **Premium** — первая на полке, её строка состояния:
  «Google Play is unreachable — prices appear once it answers» (redroid без Play — BillingClient
  не подключается; поведение истории 1.4 честного объяснения распростёрто на премиум-строку).
  Паки золота — то же объяснение; балансные ряды (Charge / Golden charge) живые с «Buy →».
- Crash-buffer logcat пуст, приложение отвечает.

## Что риг проверить НЕ может

Sandbox-покупку (offer token, лист Play, подтверждение подписки, расчёт срока): redroid не содержит
Play Store / GMS, `BillingClient` соединиться не может. License tester в Play Console настроен, но
для end-to-end нужен билд на устройстве с Play (или эмулятор с GMS-образом) — задача владельца/
отдельного рига. Серверная сторона расчёта подписки покрыта functions-тестами (99/99, включая
`subscriptionsv2`-фейки и Refund-order сценарии).

## Состояние рига после проверки

Эмулятор `schoolquiz-emu` ОСТАНОВЛЕН (docker stop) после блока проверок. Демо-облако
(schoolquiz-demo-firebase-20261004) не тронуто; фикстуры смены 13 (ibx1_*/ibx2_*) на месте.
