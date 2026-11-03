# ТЗ-01. Подключить готовые контракты без повторной разработки

Зависимость: принят ТЗ-00. Результат — проверенные domain/data-порты Entry и чтения Run, их DI и усиление новых записей checkpoint. **Без UI, новых денег, таймеров и write-adapter Run.**

## Пакеты и действительный статус

| Пакет | Объём | Статус перед передачей |
|---|---:|---|
| `arena-entry-facade` | 6 файлов; 32 новых теста | Полностью просмотрен ведущим агентом; не импортирован/не компилировался |
| `arena-run-read` | 6 файлов; 35 новых тестов | Только revision 2; полностью просмотрен, не импортирован/не компилировался |
| `arena-entry-koin-wiring` | 4 файла; 6 новых тестов | Полностью просмотрен, не импортирован/не компилировался |
| `arena-checkpoint-write-guards` | 6 файлов; 13 новых тестов | Новый черновик; **полного review ведущего агента нет**, не импортирован/не компилировался |

Всё находится в локальном `work/implementation-drafts`; точные абсолютные пути/хеши — `STATE-MANIFEST.json`. Написанные тесты не считаются пройденными. Локальные standalone ktlint/Detekt не заменяют Gradle и Room.

## Этап A — первые 16 файлов

1. Полностью прочитать README, manifest, изменения production и тестов каждого из первых трёх пакетов. Сверить SHA самих draft и фактических source/dependency preimage.
2. До копирования построить **совместную** картину зависимостей. DI ссылается на шесть ещё отсутствующих файлов façade. RunRead изменяет journal, на прежний SHA которого ссылаются другие пакеты. Проверять исходные anchors до изменений и ожидаемые совместные байты после них; не отключать проверки из-за ожидаемого порядка импорта.
3. RunRead использовать только revision 2. Его journal base `2651c4c74052bc3222ab1733fe9513e13246e35a8fd9ff2f3a2ad87b8ea9e666`, post `3fbdb2a3ba4e9737b3a71b3800fdd100b45096a9a0403f88d768e65c249979df`.
4. Сохранить root SDK test override `5d2e60…05e2`. Файл из старого session draft не возвращать. В DI-пакете изменение Firebase Gradle — только прямая test-зависимость kotlinx.datetime; не обновление runtime.
5. Импортировать только 16 согласованных целей. Сверить mirror/server SHA. Не запускать старый importer вслепую: он не настроен на все эти пакеты/взаимозависимости.

### Неизменяемые свойства

- `ArenaEntryRepository`: локальные tickets и pending preparations с разными opaque cursors; явные record-consent/dispatch/recover/cancel/reconcile. Никакого autoload, нового background sender или новой генерации ID в retry.
- Owner + исходный epoch сохраняются до отправки и после возврата dispatcher. A→B→A не равно прежней сессии A. CancellationException не превращается в Result.failure/успех.
- `ArenaRunRepository.read`: одна согласованная строка под существующим gate; header/terms/bank/checkpoint проверяются вместе. Раздельные чтения summary и checkpoint не заменяют этот контракт.
- COMPLETING не показывается как очередной Question. Missing/unsafe bank или checkpoint дают recovery, не fresh-start.
- Terminal header закрывает runner, но не является финансовой квитанцией. Entry строгий settlement proof и outbox ACK не заменяются одним словом `SETTLED`.
- Journal/resolver уже держат gate; внешний adapter не оборачивает их вторым тем же mutex.
- Ленивая регистрация DI не читает сеть/базу, не вызывает clock на создании графа и не выдаёт разрешение на запуск.

## Этап B — отдельный review и 6 write-guards

Только после зелёного этапа A. Изучить patch/README/тесты нового пакета. Его journal base — **post RunRead revision 2**, не исходный journal. Не терять `readRunView` при переносе.

- Required binding содержит семь полей без defaults: ownerUid, ticketId, attemptId, snapshotSha256, requestId, точные termsJson и snapshotJson.
- Fresh claim требует UNUSED/NONE/RESERVED, пустой checkpoint и revision ровно 0. Повреждённую строку с revision 7 нельзя обнулить в fresh.
- CAS/первый freeze заново проверяют текущую строку транзакции: закрытые состояния, LAUNCHED, terminal-null, revision и неизменяемые байты банка/terms. Валидировать только старый hash из pre-read недостаточно.
- Сохраняются exact replay после eviction и отмена без безопасного банка. Terminal replay не надо заново заставлять проходить active RESERVED guard.
- Не менять SQL, schema 13, entity/Storage API, отправитель, финансовую квитанцию, алгоритм оценки/времени.
- Согласовать canonical `ArenaJournalFakes`, не завести второй permissive fake ради тестов.

## Проверки и приёмка

Этапы A и B — отдельные коммиты и логи. Запустить восемь scoped JVM-наборов из общего договора, затронутые ktlint/Detekt, MVP APK и нужные AndroidTest APK. Для B обязательно реальные Room-тесты и Journal с настоящим strict codec.

Проверить: потерю ответа/повтор; смену аккаунта до/внутри/после dispatch; stale cursor; canonical terms; corrupted bank с прежним hash-полем; конкурентные CANCEL/SETTLE; eviction/replay; lazy graph без запросов; отсутствующую обязательную зависимость без разрешительного fallback. Все существующие тесты сохранить.

Итог: подключённые порты и проверки компилируются/проходят; никаких новых кнопок запуска и финансового «успеха». Зафиксировать новые post-SHA для journal, DAO, shared fake и DI — они станут базой ТЗ-02/04.
