# Архитектурный checkpoint — свежая проверка и открытые ограничения

Дата: 2026-09-13. Статус: **PARTIAL / NOT RELEASE READY**.

## Авторитетное состояние

Авторитетный Git: `/srv/schoolquiz-mvp-20260906/integration`, ветка
`integration/mvp-20260906`. Начальная точка этого архитектурного пакета:
`8fc7af452e6727fec5487ef35e4e388359e14b8d`.

| Commit | Содержание | Отчёт |
| --- | --- | --- |
| `45ab9d82047ae1313864f91f3358cb1f41e79ef9` | TZ07A: точный полный расчёт отбора, сохранённые фазы и защита публикации | `2026-09-13-tz07a-qualification-calculation.md` |
| `e80e24843f30ed84b752d6597e702203bf16e2d5` | Оснастка и доказательства узкого восстановления старой попытки Арены | `2026-09-13-arena-legacy-recovery.md` |
| `c7742584cf59b1feba753b1a9b723a8698d685da` | TZ10A: монотонные quantity-aware grant/refund и кумулятивные квитанции | `2026-09-13-purchase-partial-refunds.md` |
| commit, содержащий этот файл | TZ06D: возобновляемая v7 finalization сезона Arena и evidence probes | `2026-09-13-tz06d-implementation-report.md` |

## Свежий текущий server candidate

TZ06D больше не является design-only: реализован v7 candidate с сжатым authenticated
Patricia/Merkle trie, независимым source replay, durable phases и server-only worker fence.
Формальное доказательство и ограничения находятся в
`2026-09-13-tz06d-season-finalization-design.md` и implementation report.

На текущем source candidate штатный `functions/run-tests.js` завершился **88/88 suites PASS**.
Отдельно прошли реальный loaded-rules Emulator probe (**168/168 expected 403**) и изолированный
Admin SDK Emulator E2E finalization (44 advances, одна award/receipt/ledger effect). Эти проверки
не означают deployment: Firebase и Functions не перезапускались и не развёртывались.

Прямой 4097-key sparse-trie gate прошёл в fake Firestore модели: 380 branch documents, максимум
16 writes на транзакцию. Он намеренно вынесен из обычного runner из-за времени выполнения; это не
заменяет фактические Firestore Emulator resource measurements для 4096/4097.

## Исторические доказательства, не подменяющие текущую приёмку

Ранее на неизменённом source был проведён общий Node 22 gate: **86/86 suites PASS**, 533 714 ms,
и syntax check PASS. Он остаётся полезным фоном для TZ07A/TZ10A, но не считается доказательством
нового TZ06D v7 candidate.

Исторические крупные TZ07A fake-case measurements:

| Вход | Продолжений | Время fake-case | Замеченные чтения; максимум на транзакцию |
| --- | --- | --- | --- |
| 502 игрока / 502 попытки | 35 | 3 687 ms | 8 380; 305 reads / 101 writes |
| 501 игрок / 1503 попытки | 55 | 9 079 ms | 22 544; 655 reads / 101 writes |
| 4096 игроков / 4096 попытки | 248 | 113 623 ms | 68 014; 305 reads / 101 writes |
| 4097 игроков / 4097 попытки | 248 | 115 274 ms | 68 030; 305 reads / 101 writes |

Это не Arena TZ06D resource proof и не продуктовые лимиты.

## Открытые блоки до релиза

- NOT RUN: deploy/config inspection, drain/review старых finalization jobs и измерения настоящего
  Firestore Emulator/SDK на 4096/4097 с restart/interruption recovery.
- NOT RUN: общий callable HTTP/worker recovery matrix, полный tournament-prize flow, новая Android
  сборка/установка, `ciCheck`, Room/device acceptance, настоящий offline/two-device и Play sandbox.
- NOT DONE: остальные продуктовые срезы TZ06–TZ10 и полная финальная матрица TZ11.
- NOT ACCEPTED: ручной результат владельца. Открытый дефект денег, потери данных или доступа
  остаётся release-blocker.

APK устройства B остаётся прежним C1+C3 с checksum
`c0bf9657ca6d1ef00454af4c08522a476e2e669b8fc6262ea7d73d6613c8c4d2`.
Не было push, публичного deploy, миграции в KMP, очистки приложения, удаления истории/фикстур,
изменения спасённого экспорта или перезапуска Firebase.
