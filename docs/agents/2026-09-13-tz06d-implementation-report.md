# TZ06D — отчёт реализации возобновляемой финализации сезона Arena

## Статус и границы среза

- **Исходный commit:** `ab4cf3d74b7cd31415af9afe836a067a15c72942` в
  `integration/mvp-20260906`.
- **Commit среза:** Git commit, содержащий этот отчёт (`feat(arena): make season finalization resumable`).
- **Статус:** **IMPLEMENTED CANDIDATE / NOT DEPLOYED / NOT ACCEPTED**.

Это серверный кандидат TZ06D. Не выполнялись deployment Functions/rules/indexes, перезапуск
Firebase, замена или импорт экспорта, сборка/установка APK, публикация либо ручная приёмка
владельцем.

## Изменённые файлы

- `functions/arena-mutation-reservations.js` — приватное резервирование незавершённой операции
  и совместимое разрешение квитанции.
- `functions/canonical-json.js`, `functions/canonical-json.test.js` — каноническое кодирование
  checkpoint-данных.
- `functions/arena-season-finalization.js` — возобновляемый reducer v7, lease/revision fencing,
  quarantine повреждённых checkpoint, ограниченные страницы, независимая проверка источника,
  публикация award/receipt/ledger одним эффектом.
- `functions/arena-season-worker.js`, `functions/arena-season-worker.test.js` — ограниченный
  server-only scheduler sweep.
- `functions/arena-service.js`, `functions/arena-service.test.js` — boundary callable,
  reservation/replay, status projection, проверки award/ledger и тесты.
- `functions/arena-handler.test.js`, `functions/arena-lifecycle-views-handler.test.js` —
  регрессии границ handler/lifecycle.
- `functions/index.js` — exports scheduler и curator status с заданными runtime options.
- `firestore.rules` — явный deny для reservation и worker-state корней и покрытие приватных
  сезонных вложенных путей.
- `firestore.indexes.json` — исключение `childrenJson` приватных trie-узлов из индексов.
- `scripts/arena-season-finalization-private-rules.test.js` — read/write/delete probe закрытых
  путей через реальный Emulator.
- `scripts/arena-season-finalization-emulator.test.js` — изолированный Admin SDK E2E finalizer.
- `docs/agents/2026-09-13-tz06d-season-finalization-design.md` и этот отчёт — дизайн,
  доказательства и ограничения.

## Реализованная проверка целостности

Ранний XOR-вариант v5 отклонён: 257 заменённых листьев дают ненулевое линейное подмножество с
нулевым XOR без криптографической коллизии. Полный 256-child-vector trie v6 также отклонён:
на 4097 ключах он требовал бы порядка 123 000 документов и около 1,97 GiB payload.

Кандидат v7 использует сжатый 256-way Patricia/Merkle trie для `selection_tree` и
`aggregate_tree`. Корень и ветви коммитят версию, домен, quarter/finalization, сжатый prefix и
упорядоченные sparse edges. Лист хранит фиксированные `keyHash`/`leafHash`, а не логический ключ
в `childrenJson`. Источник независимо пересчитывается и проверяется перед агрегацией, выбором
победителя и публикацией. Пропавшая ветвь или несовпадение sealed root переводят job в
`REQUIRES_REVIEW`; они не дают повторно выдать награду. Overlay сначала выполняет все необходимые
чтения, включая split-узлы, и только затем пишет, соблюдая Firestore read-before-write правило.

## Выполненные проверки

| Проверка | Результат | Что именно доказывает / ограничение |
| --- | --- | --- |
| Локальные unit/regression: `canonical-json`, `arena-service`, `arena-season-worker` | **39 passed, 0 failed**, 87 019,93 ms | Включая XOR rollback, парный rollback, corruption, `DIVERGENT_BRANCH`, sparse-tree paths и fake-transaction discipline. Это не Admin SDK Emulator. |
| Явный 4097-key scale gate до отделения из обычного запуска | **PASS** в ограниченном demo-контейнере; 380 branch docs, максимум 16 writes/transaction, 1 419 270,932875 ms | In-memory fake Firestore, не billed Firestore reads/writes, не peak memory и не реальный Emulator resource proof. |
| Синтаксис текущего server candidate | **PASS**, Node v22.23.1 | `node --check` для finalizer, service, worker, index и двух Emulator probes. |
| Сфокусированный server test set | **61 passed, 0 failed, 1 skipped**, 29 070,613432 ms | Единственный skip — намеренно вынесенный тяжёлый 4097 gate; его body не удалён. |
| Штатный Functions runner | **88 из 88 suites PASS** | Свежий полный server runner на текущем v7 candidate. |
| Rules probe на загруженном Firebase Emulator | **PASS**, 168/168 ожидаемых 403 | 4 роли × 14 закрытых путей × GET/PATCH/DELETE; mounted rules SHA-256 `712819e935cb6d59d5813fad5b3fadfafc6d4ec57d2f161e66d95c1eea63e567`. |
| Admin SDK Emulator E2E | **PASS** | Изолированная finalization `8927-Q4` с v7 дошла до terminal за 44 advances; ровно один award, receipt и author ledger effect; tree shape проверена. |

Обычный runner не должен молча заменять scale gate: для него требуется явный запуск
`ARENA_SEASON_FINALIZATION_SCALE=1 node --test functions/arena-service.test.js`.

## Побочные данные проверок

- Rules probe `asf_rules_050b17b26c5c4afb8c4b33408a9eb4fa` создал 3 demo Auth accounts и
  оставил 2 изолированных fixture documents (`fixtureDeleted: false`) как evidence.
- E2E создал отдельную future-quarter fixture: `8927-Q4`, с уникальными lesson/finalization/
  receipt/award/ledger identifiers. Она не относится к пользовательским данным и не заменяла
  экспорт.

## Ограничения и оставшаяся работа

- До rollout нужны deployment rules/functions/indexes, проверка конфигурации и безопасный drain
  или review всех старых in-flight jobs: v7 намеренно не resume-ит старые calculation versions.
- Нужны реальные замеры Firestore Emulator/SDK для 4096 и 4097 ключей: duration, reads, writes,
  memory, restart/interruption recovery. Пройденный 4097 gate — только модель транзакции.
- Нужны широкий TZ06D/TZ11 Arena matrix, HTTP callable и worker-recovery matrix, новая Android
  сборка/установка, `ciCheck`, device/Room/offline/two-device acceptance, Play sandbox и ручная
  приёмка владельца.
- Этот срез не закрывает прочие TZ06–TZ10 и не является утверждением готовности MVP или релиза.
