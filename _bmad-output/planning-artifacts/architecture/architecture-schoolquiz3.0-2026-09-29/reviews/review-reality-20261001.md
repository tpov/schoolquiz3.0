# Reviewer Gate — Reality — 2026-10-01

**Verdict: PASS.** Проверенные изменения опираются на текущий checkout и финальные UX-входы. Новые контракты не выдаются за реализованные адаптеры; toolchain указан как наблюдённый снимок, а не рекомендация актуальной версии или результат совместимой сборки. Critical/high/medium замечаний в области этой линзы нет.

Область: [initiative spine AD-1–30](../ARCHITECTURE-SPINE.md), уточнённые [EXAM AD-18 и Deferred](../../architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md), [ADR-0011](../../../../../docs/architecture/0011-noir-designsystem.md), code-owned seed и load-bearing контракты UX handoff. Остальные EXAM AD и C1–C4 не перепроверялись как новая реализация. Полная source reconciliation находится в [reconcile-handoff-20261001.md](reconcile-handoff-20261001.md).

## Проверка технологической основы

| Проверенное утверждение | Свидетельство текущего checkout | Вывод |
| --- | --- | --- |
| Kotlin 2.4.20, AGP 9.3.1, Gradle 9.8.0 | `gradle/libs.versions.toml:3–4`; `gradle/wrapper/gradle-wrapper.properties:4` | Pins совпадают со spine. Их наличие не является доказательством существования release, совместимости или успешного build; spine такого вывода не делает. |
| JDK 27 conventions при JVM target 17 | `buildSrc/src/main/kotlin/KmpLibraryConventionPlugin.kt:24,37`; `KmpLibraryAgp9ConventionPlugin.kt:23,35,44`; `JvmLibraryConventionPlugin.kt:18,28` | Формулировка точна для build conventions и не утверждает runtime JDK устройства. |
| Node 22 и существующие Firebase adapters | `functions/package.json:5,13–14`; `functions/index.js:11` импортирует `firebase-functions/v2/scheduler` | Унаследованная инфраструктура, не новый provider/runtime selection. |
| KMP/Compose/Decompose/Koin/Room | Раздельные commonMain domain/data и Android presentation; catalog содержит Room 2.7.0, Decompose 3.1.0, Koin 3.5.6, Compose BOM 2024.09.02; `AppApplication.kt` содержит Koin composition root | Новый стек, starter или dependency upgrade не назначены. Имена технологий соответствуют существующей структуре. |
| SchoolQuizTheme и NoirTheme сосуществуют | `android/core/designsystem/.../SchoolQuizTheme.kt`; `.../noir/NoirTheme.kt` предоставляет CompositionLocals и документирует вложение в SchoolQuizTheme | ADR-0011 и AD-25 сохраняют существующие границы. Семейства UX не ошибочно названы уже существующими Kotlin-классами. |
| Историческая web-сверка не новая рекомендация | Spine прямо датирует внешнюю сверку 09-29 и повторное чтение pins 10-01; `.working/stack-operations.md` имеет заголовок 2026-09-29 | Исторические официальные ссылки сохранены как evidence своего времени. Compatibility/security/release gate остаётся будущим. Новых web-утверждений об актуальных версиях в этой сверке не сделано. |

Новая технология или версия не выбирается, поэтому дополнительных web-запросов для нового binding не потребовалось. Этот PASS не повторяет и не обновляет историческую проверку vendor release matrix.

## Контракты и явно сохранённый разрыв реализации

| Контракт | Прочитанный код | Оценка документа |
| --- | --- | --- |
| EXAM 100/200 points, источник debit открыт | `functions/config/economy-constants.json:32–33` хранит 100/300; Kotlin `EconomyConstants.kt:83–84` — 1/3 полных заряда | Main AD-14 и EXAM AD-18 теперь явно называют старые 300 расхождением, а не новым тарифом. O1 блокирует только реальный debit до pool/split и config reconciliation. C1 reward basis не назначает источник списания. |
| Динамические призы | `tournament-policy.js:84–97,392–406` использует first/second/two-thirds; controller `:90` отдаёт старый DTO | AD-17 и O2 прямо требуют согласованно изменить CREATE/read/reserve/rank/payout и совместимость существующих событий. Рабочая динамическая реализация не заявлена. |
| PROPOSE / CONFIRM / READY и серверные часы | `tournament-service.js:266–295`; `tournament-policy.js:222–299` | Команды и окна соответствуют существующей state machine. Отсутствующая индивидуальная read/ack-проекция отделена в O3, сохранение lifecycle не обещает неизменность DTO. |
| Review по вопросу/сегменту | `DefaultReviewQueueComponent.kt:271–281,463`, `ReviewAssignmentRemoteDataSource.kt`, `functions/index.js:5282–5299` | Старые assignment score и auto-accepted translation признаны несовместимым seed. AD-26 фиксирует целевой контракт; O5 сохраняет незакрытую агрегацию/окончание цикла. |
| Аннотации точных вхождений | Question schema содержит FillBlank `List<String>`; `FillBlankAuthoring.kt:118–138` использует маркеры и replaceFirst | AD-27 не называет HTML или старую строковую модель готовой schema. O6 требует одного формата до параллельной реализации. Удаление answer-revealing metadata из redacted payload сохраняется. |
| Poll vote и private attempts | Survey UI сейчас обрабатывает local draft/feedback; полный новый vote/private lifecycle ограниченным sweep не подтверждён | AD-28/29 — целевой договор, O4/O7/O8 перечисляют зависящие адаптеры и неизвестные правила. Ни локальный feedback, ни UI hiding не объявлены подтверждённым серверным фактом. |
| История | Raw currency ledger operator-only; owner receipts разрешены `firestore.rules:33–40` | AD-30 требует отдельную разрешённую проекцию существующих фактов; не предлагает раскрыть raw audit или объявить receipts полным новым журналом. |
| Ordinary/Premium box sources | `box-service.js:21,137–148`; `gift-boxes.js:23,74–75` | Раздельные источники и общий запас существуют. Принятая явная Premium кнопка остаётся подключением/приёмкой, без утверждения о готовом UI. |
| Scheduler/worker | `functions/index.js:308–317` создаёт course tournament worker и v2 onSchedule adapter | AD-23 различает исходный adapter, capability, demo proof и разрешённый rollout. Наличие source не названо deployment evidence, текущая production activation не заявлена. |

## ADR и доступность

Поправка ADR-0011 явно сохраняет принятие NOIR и границы модулей, но отдаёт актуальные роли/типографику/исключения финальному DESIGN, поведение — EXPERIENCE. Прочитанные UX-файлы имеют `status: final`, `updated: 2026-10-01`. Исторические таблицы, утверждение о моноширинных кнопках и прежний contrast claim не образуют второго источника истины. AD-24/25 и ADR требуют отдельной Android-приёмки, включая fontScale 2.0, TalkBack, keyboard/DPAD, hit regions и contrast после композиции. Готовность переноса final UX не объявлена.

## Риски остаются явно открытыми

- Pool/split экзамена и legacy price reconciliation — до реального debit.
- Prize mapping/meeting projections, review aggregate, range schema/migration, vote API, private access/lifecycle, history projections — до зависимых адаптеров.
- Toolchain compatibility/security, реальный backend/Play/rules/HTTP, непустая migration и Android accessibility — отдельная приёмка текущего кандидата.
- Signing provider/algorithm/keys, environment bindings, cadence/budgets/retention/runbook — до разрешённой активации; новый provider не выбран.

Код и исходные архитектурные документы этой линзой не изменялись. Не запускались Gradle, Node tests, сервисы, workers, эмуляторы/устройства, deployment или финансовые операции. PASS относится к реалистичности архитектурного обновления, не к готовности реализации или выпуску.
