# Reviewer gate — technology and code reality

Дата: 2026-09-29. Линза: TECHNOLOGY / CODE REALITY. **Вердикт: PASS после двух исправлений, проверенных в ходе gate.** Открытых блокирующих расхождений этой линзы нет. Это PASS архитектурного документа, не подтверждение реализации, совместимости сборки или runtime.

## Граница проверки

Проверены общий spine 2026-09-29, обновлённый exam spine 2026-08-29 (AD-1–37), solution-design, epic-breakdown, e2-plan, актуальный exam-protocol и ADR-0007. Сопоставлены существующие исходники и Gradle/Node конфигурация кандидата; несущие внешние технологические утверждения повторно проверены по первичным официальным источникам, а не приняты из .working/stack-operations.md на веру.

Авторитетное чтение и этот отчёт — только VPS checkout `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate`, HEAD `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`. Dirty planning-изменения оценивались как рабочий снимок. Код, зависимости, runtime и inputs этим reviewer не изменялись. Не запускались Gradle, Node tests, npm install, эмуляторы, устройства, workers, Scheduler/PubSub, deployment или финансовые операции. Production credentials не читались. Отсутствие найденного модуля не приравнивается к доказательству всех отрицательных runtime фактов.

## Findings и disposition

### R-1 — MEDIUM, CLOSED: E2 companion выдавал августовскую разведку за текущий план

**Найдено.** `e2-plan.md` называл assessment-scoring/redaction новыми, предписывал старые schema 3→4/5 migrations, утверждал отсутствие Node suite в ciCheck и текущий dead-end HARD. На текущем кандидате существуют `functions/assessment-scoring.js`, `question-redaction.js`, `attempt-scoring.js`, Kotlin redacted types, parity fixtures, AppDatabase version 15; root ciCheck уже зависит от functionsTest. Поэтому исполнение старого плана могло повторно создавать существующее и применять неверный migration seed.

**Исправление проверено.** В начале файла теперь явный Historical/superseded marker 2026-09-29, ссылки на актуальные spines/epic breakdown, запрет пересоздавать модули или применять старые migrations к активному кандидату. Исторический body допустимо сохранён как evidence. Текущие E1/E2 и solution-design прямо предписывают reuse/inspection. Закрыто без стирания истории и без ложного утверждения новой приёмки.

### R-2 — HIGH, CLOSED: exam формулировки отменяли принятую local-half обычной разблокировки

**Найдено.** Exam AD-18 запрещал local spending, AD-34 называл lesson unlocks server-owned без оговорки. В brownfield `OutboxLessonUnlockQueue.kt` связывает unlock + provisional nolics debit + outbox одной Room TX; `LessonUnlockQuarantineRollback.kt` возвращает нолики/закрывает урок при terminal quarantine. Это не случайная реализация: комментарии и SYNC memlog фиксируют явное решение владельца 2026-09-03. `LessonUnlockLocalHalfTest.kt` проверяет атомарность против SQLite, но в этом review тест не запускался.

**Исправление проверено.** Main AD-7, exam AD-18/34 и E3 теперь явно сохраняют ordinary offline local half, stable retry и visible quarantine rollback. Сервер остаётся владельцем подтверждённого financial fact; локальная покупка доступа не создаёт подтверждённый EASY progress, необходимый для exam admission. Запрет client-side exam charge и standalone local debit не распространяется на разрешённый ordinary путь. Закрыто.

### Ранее найденные reconciliation проблемы

B-1 pair cursor и B-2 MON ceiling исправлены и повторно проверены; PASS дописан в `reconcile-baseline.md`. Certificate ownership теперь согласован: exam issue/sign/verify, profile только read-only projection/UI; ADR-0007 и E9 не сохраняют альтернативного владельца. Этот reality gate не повторяет продуктовую линзу.

## Технологии: кодовый seed и независимая внешняя проверка

| Утверждение | Кодовые наблюдения | Первичная проверка / вывод |
| --- | --- | --- |
| Kotlin 2.4.20 + Gradle 9.8.0 + AGP 9.3.1 действительно названы существующими releases | `gradle/libs.versions.toml` и wrapper содержат именно эти pins | Kotlin 2.4.20 выпущен 7 сентября; Gradle 9.8.0 — 24 сентября; AGP release notes перечисляют 9.3.1. Это реальные версии, не выдуманная будущая комбинация. [Kotlin releases](https://kotlinlang.org/docs/releases.html), [Gradle 9.8](https://docs.gradle.org/9.8.0/release-notes.html), [AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes). |
| Весь toolchain ещё требует compatibility gate | Kotlin 2.4.20 / wrapper 9.8.0; AGP bridge flags `android.builtInKotlin=false`, `android.newDsl=false`; отдельный KmpLibraryAgp9ConventionPlugin с experimental comment | Официальная fully-supported KGP матрица заканчивается Gradle 9.7.0 и AGP 9.3.1. Документация допускает более новые версии с возможными ограничениями; это риск доказательства, не доказанный build failure. Main spine правильно сохраняет будущий gate и не даёт ложного PASS. [KGP matrix](https://kotlinlang.org/docs/gradle-configure-project.html). |
| JDK27 conventions при JVM target17 возможны как разные настройки | JvmLibrary/KmpLibrary/KmpLibraryAgp9 задают toolchain27 и target17; AndroidApplication compile/target compatibility17 | Gradle9.8 поддерживает Java27 для daemon/toolchains; AGP9.3 требует минимум Gradle9.5/JDK17. Из этого не следует совместимость всех processors/plugins, особенно Room/KSP; report не объявляет её. [Gradle Java27](https://docs.gradle.org/9.8.0/release-notes.html#support-for-java-27), [AGP compatibility](https://developer.android.com/build/releases/agp-9-3-0-release-notes). |
| Android-first KMP/Compose/Decompose/Koin fit сохраняется | Модули domain/data и Android presentation разнесены; catalog Decompose3.1.0/Koin3.5.6; app composition root; neutral scoring/question-schema commonMain | Decompose официально поддерживает Compose component integration; Koin — commonMain/platform modules. Это подтверждает применимость подхода, а не автоматически бинарную совместимость старых pins с новым compiler. Новые docs не предписывают upgrade Koin4.2. [Decompose](https://arkivanov.github.io/Decompose/getting-started/installation/), [Koin KMP](https://insert-koin.io/docs/reference/koin-core/kmp-setup/). |
| Room KMP — реальная основа, не новый выдуманный adapter | Room2.7.0, bundled SQLite2.5.0; AppDatabase/DAOs в commonMain, exported migrations, version15 | Room2.7.0 существует; официальная KMP документация размещает database/DAO/entities в common sources. Нынешний пример docs уже показывает другую major/alpha — это не разрешение мигрировать проект на неё. [Room releases](https://developer.android.com/jetpack/androidx/releases/room#2.7.0), [Room KMP](https://developer.android.com/kotlin/multiplatform/room). |
| Firebase Node22 поддержан | `functions/package.json engines.node=22`; `firebase.json runtime=nodejs22` подтверждает effective override; lock admin13.8.0/functions7.2.5/google-auth-library10.6.2 | Firebase перечисляет Node22 как поддерживаемый runtime; admin13.8.0 и functions7.2.5 имеют официальные releases. Не объявлена свежая vulnerability/dependency acceptance. [Firebase runtime](https://firebase.google.com/docs/functions/manage-functions#set_nodejs_version), [Admin releases](https://firebase.google.com/support/release-notes/admin/node), [Functions7.2.5](https://github.com/firebase/firebase-functions/releases/tag/v7.2.5). |
| Scheduler — adapter, не единственная возможная capability invocation | `index.js` содержит onSchedule exports; `tournament-worker.js` — отдельную createTournamentWorker/sweep capability с trusted dependencies, persisted cursor/lease | Firebase onSchedule использует Cloud Scheduler и допускает повторные/перекрывающиеся invocation. Следовательно, single logical terminator требует CAS/fencing, а наличие source export не доказывает deploy. Main AD-23 и exam AD-30 соответствуют этому; demo controlled ticks не требуют автоматического включения production scheduler. [Firebase scheduling](https://firebase.google.com/docs/functions/schedule-functions). |
| Android BoM/KTX seed не ошибочно объявлен готовым к major upgrade | Catalog BoM33.2.0 и существующие auth/database/storage/messaging KTX aliases | Firebase BoM34 удалил KTX modules; будущий upgrade требует migration, но текущий BoM33.2.0 этим фактом не выключен. Stack notes честно фиксируют условный будущий риск. [Android SDK release notes](https://firebase.google.com/support/release-notes/android). |

Effective app SDK читается из AndroidApplicationConventionPlugin: compile/target35,min26; catalog34 не является effective app target. `apps/android-next` имеет applicationId `com.tpov.schoolquiz`, variant mvp с suffix `.mvp` и MVP_EMULATORS=true. Устаревшее PROJECT-CONTEXT имя `.next` не переносится в операционный seed; stack notes отмечают различие. Актуальные Play publishing требования не проверялись этой линзой и не выдаются за пройденный release gate.

## Brownfield: что существует и что остаётся target change

| Область | Проверенная реальность | Соответствие target |
| --- | --- | --- |
| Общий scoring | `shared/core/scoring` содержит Kotlin value types/формулы, зависит от neutral question-schema; `assessment-scoring.js` — JS arithmetic; JVM ScoringFixtureParityTest и серверные fixtures существуют | Exam AD-7 и E1 верно требуют reuse через adapters и общие golden cases. Новый scorer per mode не нужен. Файлы тестов — не новый PASS. |
| Redaction и one-time mapping | `question-redaction.js`, `question-key-store.js`, `attempt-scoring.js` и отдельный RedactedQuestionContent уже существуют. attempt-scoring напрямую использует assessment arithmetic и translateSubmittedAnswer | Target canonical-answer boundary обоснован. Exam должен адаптировать эти контракты и сохранять issued permutation; нельзя повторно применять mapper либо ослаблять QuestionContent nullable correctness. |
| Турнирный lifecycle | Реальные `tournament-session.js`, `course-tournament-controller.js`, `tournament-worker.js`. Session проверяет owner/form/request, использует private keys и existing attempt scorer; worker имеет trusted-only sweep и continuation | Отдельный exam lifecycle вписывается без переписывания существующего tournament runtime. Continuous tournament timing не объявлено exam ACK timing. |
| Полные exam/certificate | Settings/исходники не показывают готовых exam sitting/admission/certificate orchestration modules/endpoints; есть partial mode/economy basis | Main, exam Structural Seed и solution-design честно называют интеграцию оставшейся работой. Архитектурный контракт не ошибочно назван deployed или готовым приложением. |
| Publication/review | `index.js publishSubmissionIfReady` пока проверяет whole target lesson set через allReady; ReviewAssignment не содержит assignee/generation | Независимая publication вопросов, immutable manifest/evidence и assignment generation — planned changes, не ратификация текущей реализации. Общий memlog это явно фиксирует. |
| Config | EconomyConstantsRepositoryImpl сохраняет bootstrap/last good value, но Table refresh записывает ответ без явной freshness projection и monotonic response-order protection | Versioned manifest/freshness из main AD-5 — нужное целевое изменение. Не объявлено, что нынешний cache уже удовлетворяет ему. |
| Outbox и module boundary | OutboxEngine/Store в core; зависимости core/outbox — coroutines/network, без feature modules. Feature queue/rollback владеет собственной local-half | Общий AD-7 соответствует модульной границе. Нет требования нового parallel sender/mutex или разбора чужого payload в core. |
| Миграции | AppDatabase version15; существующие schemas/migrations и local SQLite test sources | Historical schema3/4/5 не активная база. Main AD-22 требует фактическую installed schema перед будущим upgrade. |
| Gate/Node suites | ciCheck включает functionsTest/scriptsTest; functionsTest имеет onlyIf node_modules; run-tests.js обнаруживает все *.test.js | Отдельный Node runner реален, но skipped functionsTest нельзя объявить денежной приёмкой. Main AD-24 требует статусы/evidence и не считает наличие файла теста его запуском. |
| Runtime/deployment | Прочитан исходный firebase.json/onSchedule/worker; runtime inventory и deployments не опрашивались | Source schedules не доказывают действующий scheduler; demo runner/environment/capacity остаются будущими gates, как и требует документ. |

## Архитектурная пригодность

Текущие neutral contracts и платформенные adapters дают достаточное основание для выбранной layered архитектуры. Main dependency graph не требует SDK в domain, прямого feature ownership в core или общего exam/tournament state machine. Названия будущих assessment calls в protocol — целевые интерфейсы, не утверждение существующих callables. Допустимы дальнейшие implementation details по adapter/class/table placement, поскольку владельцы фактов, mutable boundaries и источник окончательного решения определены.

Certificate issue/sign/verify принадлежит exam; profile может располагать UI/cache, не определяя authority. Отдельные ещё не выбранные mixed-stars, correction denominator/rounding, renewal identity и permitted sweeper adapter названы prerequisites до соответствующей реализации. Их отсутствие в исходниках не является скрытым brownfield claim и не требует фиктивного уже готового модуля.

Дополнительно проверена внесённая во время gate граница initial certificate issuance: terminal success + immutable issuance fact + durable signing/publication work атомарны; собственно подпись/публикация вне TX, pending явный. Это реализуемый server-owned механизм с устойчивым retry и без повторной оценки исправленного результата; текущим реализованным кодом он не объявлен. New dependency/provider или external I/O внутри retriable TX не предписаны.

## Ограничения PASS

- Совместимость всего набора Kotlin/AGP/Gradle/KSP/Room/Compose и его security exposure — **NOT RUN**, а не PASS; документ это отражает и не выбирает новые зависимости.
- Поведенческая корректность exam/finance/races, реальный Firestore/rules/HTTP, непустая migration, два устройства и Play sandbox — **NOT RUN**. По одному source review её доказать нельзя.
- Численные worker cadence/budget/lag и production adapter/capacity будут нужны до запуска; отсутствие production schedule сегодня не блокирует архитектуру controlled demo capability.
- Сообщённый root lint результат (main0; exam low на допустимом users/{uid} template) не переиспользован как semantic proof. Этот reviewer lint не запускал; users/{uid} — корректное обозначение Firestore path, не незаполненный placeholder.


## Снимки проверенных документов

- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md`: `889da3d76f8af3a07167c7ff5c23f50b07588d10575cbf5b78abb5a067435d04`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md`: `efbe677ad0da240491c81cc3d0378565903f8fb8cfaacf09ba974be13a8ca7bc`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/solution-design.md`: `cfeeb8fec595ad43423b612bd55767fb94a5b57333ddad1352eefaca9aaa5714`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/epic-breakdown.md`: `2954815708d5ab495acd3f6d3ce85e8971fee612dda18321a7c9ed22189a6227`
- `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-29/e2-plan.md`: `b19e6f9d7d975ee013cc588e87bb1cfae54a5e6d8609b079c59692eff4e95a20`
- `_bmad-output/specs/spec-theme-exams/exam-protocol.md`: `6fca68712a9c794a3e8d91e40be63601f05606c5f11d27ec3bebd24e3fe563a2`
- `docs/architecture/0007-certificates.md`: `36cb6aafdb70821cce4dc449c718dd1602b680f07530332921859c4f9b254030`
