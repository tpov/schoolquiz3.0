# Архитектурный checkpoint — свежая проверка и открытые ограничения

Дата: 2026-09-13. Статус: **PARTIAL / NOT RELEASE READY**.

## Код и коммиты

Авторитетный Git — `/srv/schoolquiz-mvp-20260906/integration`,
ветка `integration/mvp-20260906`. Начало этого архитектурного пакета:
`8fc7af452e6727fec5487ef35e4e388359e14b8d`.

| Коммит | Содержание | Отчёт |
| --- | --- | --- |
| `45ab9d82047ae1313864f91f3358cb1f41e79ef9` | TZ07A: точный полный расчёт отбора, сохранённые фазы и защита публикации | `2026-09-13-tz07a-qualification-calculation.md` |
| `e80e24843f30ed84b752d6597e702203bf16e2d5` | Оснастка и доказательства узкого восстановления старой попытки Арены | `2026-09-13-arena-legacy-recovery.md` |
| `c7742584cf59b1feba753b1a9b723a8698d685da` | TZ10A: монотонные quantity-aware grant/refund и кумулятивные квитанции | `2026-09-13-purchase-partial-refunds.md` |

Следующий документационно-проверочный коммит добавляет этот отчёт, дизайн TZ06D и
три дополнительные проверки закрытых вложенных путей в
`scripts/course-tournament-private-rules.test.js`. Он не меняет production-код.
Номер итогового HEAD после этого коммита записан в локальном `STATE.json` пакета.

## Один свежий общий серверный прогон

Каталог доказательств:
`/srv/schoolquiz-mvp-tools-20260906/tz06c/architecture-gates-e5WkJZto`.

- `functions-gate-result.json`: PASS; Node `v22.23.1`; sourceUnchanged=true.
- `functions-source-manifest.json`: SHA-256 корневых JS и package-файлов Functions
  до прогона; все сверены повторно после. Это не полный снимок Android-проекта.
- `functions-behavior-node22.log`: **86/86 suites PASS**, 533,714 ms, запуск штатного
  `functions/run-tests.js` (того же, что вызывает `npm test`). Не сумма старых логов.
- `functions-syntax-node22.log`: PASS, 18,910 ms, штатный `check-syntax.js`.

Из большого нового расчёта в этом **общем** прогоне:

| Вход | Продолжений | Время fake-case | Замеченные чтения; максимум на транзакцию |
| --- | --- | --- | --- |
| 502 игрока / 502 попытки | 35 | 3,687 ms | 8,380; 305 reads / 101 writes |
| 501 игрок / 1503 попытки | 55 | 9,079 ms | 22,544; 655 reads / 101 writes |
| 4096 игроков / 4096 попыток | 248 | 113,623 ms | 68,014; 305 reads / 101 writes |
| 4097 игроков / 4097 попыток | 248 | 115,274 ms | 68,030; 305 reads / 101 writes |

Последние две записи sampled RSS: 146,178,048 и 150,233,088 байт. Это измерения
fake-хранилища, не billed Firestore usage, не гарантированный peak и не продуктовые
пределы участников. Весь прогон последовательный, в `schoolquiz-mvp-maestro`:
0.5 CPU / 512 MiB, Node heap 192 MiB. Ограничения контейнеров не повышались.

## Scripts и реальные проверки правил

Полные зарегистрированные `scripts` test/lint PASS, Node 22.23.1. Логи:
`/srv/schoolquiz-mvp-tools-20260906/tz06c/architecture-legacy-eYhpn6Y0/tests/`
`scripts-all-node22.log` и `scripts-lint-node22.log`. Новый purchase SDK harness
отдельно прошёл `node --check` и фактический SDK-запуск; в обычный scripts test
он намеренно не включён как пишущая demo-проверка.

В каталоге общего gate:

- `tournament-private-rules.log`: PASS, 15 реальных HTTP-denial probes, включая
  job head, players и session-deduplication под qualification_calculations.
  Создан один новый demo Auth account; Firestore-записей нет. Проверены synthetic
  отсутствующие пути, не попытка прочитать чужие пользовательские документы.
- `purchase-private-rules.log`: PASS, GET/PATCH/DELETE для buyer, appAdmin и
  anonymous запрещены; fixture и updateTime неизменны. Два новых demo Auth accounts
  и два create-only synthetic документа оставлены как доказательства.

Фактический mounted rules SHA:
`030ac44ea8f3bf79adab937fe7a0dac9f0e40718e02be77829b6053694e5acda`.
Расширенный tournament probe SHA:
`07f5954243a0aaa6a52d9f21c3bef13392287a55c2367f98e9e3ee93ddf2ee23`.
Purchase probe SHA:
`5f080d6459db7ec040cb526b2bbeedd0ae247aa4d490e847eea8e9e8f8356c87`.

Правила не перезагружались и не ослаблялись. Эти точечные проверки не заменяют
полную rules-матрицу или положительный end-to-end callable сценарий.

## SDK и восстановление: разные виды доказательств

- TZ07A actual SDK: 2 игрока/6 попыток с 7 прерываниями транзакций; отдельно
  502 разных игрока/502 попытки до ranking и bracket. PASS, независимые имена
  запусков и source pins в `qualification-sdk-aSsenU2p`. Не HTTP/worker scheduler.
- TZ10A actual SDK: два конкурирующих partial RTDN, fake Play V2 remaining=2
  после quantity=5; ровно один cumulative refund q3, итог gold=140. PASS;
  13/13 документов подтверждены. Evidence: `tz10a-actual-sdk-race-20260913.5OFvCu`.
  Первый FAIL оснастки сохранён отдельно: до денежных записей он не дошёл.
- Arena e499: локальное восстановление подтверждено отдельным reviewed audit;
  автоматический `REQUIRES_REVIEW` не переписан в PASS. Исторические две разные
  отметки settlement времени остаются FAIL строгой свежей приёмки.

Все перечисленные каталоги SDK находятся под
`/srv/schoolquiz-mvp-tools-20260906/tz06c/`. Сырые Auth/Room/account evidence
приватны; локальный пакет содержит только отчёты, gate-summary и хеши.

## Что не выполнено этим пакетом

- NOT RUN: свежий полный callable HTTP-прогон на едином конечном backend candidate,
  scheduling/worker recovery matrix и весь tournament-prize flow. Emulator не
  перезапускался; mounted source SHA сам по себе не доказывает версию уже тёплого
  процесса Functions. Перед очередным тяжёлым HTTP-прогоном проверить безопасный
  экспорт и ресурсный запас: наблюдались четыре warm runtime, около 1.067 GiB
  общего использования при лимите 1.367 GiB. Это предупреждение, не дефект продукта.
- NOT RUN: новая Android-сборка/установка, `ciCheck`, Room/device acceptance на новом
  финальном кандидате, полный настоящий офлайн/два устройства/Play sandbox.
- NOT IMPLEMENTED: описанный в `2026-09-13-tz06d-season-finalization-design.md`
  bounded season finalizer; документ явно DESIGN ONLY.
- Открыты остальные продуктовые/client срезы и окончательная матрица из принятого
  плана. Полная приёмка владельца не запрашивалась и не получена.

APK устройства B остался прежним C1+C3, checksum
`c0bf9657ca6d1ef00454af4c08522a476e2e669b8fc6262ea7d73d6613c8c4d2`.
Не было push, публичного deploy, миграции в KMP, очистки приложения, удаления
истории/фикстур, изменения спасённого экспорта или перезапуска Firebase.
