# Сверка implementation-handoff и текущего кода — 2026-10-01

Проверен [общий spine AD-1–30](../ARCHITECTURE-SPINE.md) против [implementation-handoff](../../../ux-designs/ux-schoolquiz3.0-2026-09-28/.working/implementation-handoff.md), уточняющих S7/F9/O1–O12 в [EXPERIENCE](../../../ux-designs/ux-schoolquiz3.0-2026-09-28/EXPERIENCE.md) и ограниченного brownfield sweep. Это source reconciliation перед Reviewer Gate, не отдельный reviewer lens и не runtime-приёмка.

**Verdict: PASS — источник согласован на уровне архитектуры.** Существенных потерянных строк handoff, повторного выбора принятой цены/UX или ложных утверждений о готовом backend не обнаружено. Найденное уточнение приватного адресата внесено в AD-20 и повторно проверено. Известные разрывы кода остаются работой названных адаптеров.

## Покрытие источника

| Источник | Контракт spine | Результат |
| --- | --- | --- |
| O1: 1/2 полного заряда, отказ до start без debit | AD-14, EXAM:AD-18, UX-O1 | Приняты 100/200 points; pool/split остаётся открытым до реального debit. Reward basis не назначает кошелёк списания. |
| O2: динамические призы, каждый один раз | AD-17, UX-O2 | CREATE/reserve/rank/payout связаны одной версией списка; conservation сохранено, fixed two-thirds отвергнуто. Mapping ties/technical outcomes и совместимость старых событий требуют отдельного технического контракта. |
| O3: PROPOSE/CONFIRM/READY | AD-17, UX-O3 | Раздельные команды и индивидуальная разрешённая read/ack-проекция, owner/epoch и восстановление отражены. Серверные окна и отдельный lifecycle сохранены. |
| O5: TESTING по вопросу, перевод и tri-state | AD-4/26, UX-O5 | 1–3/questionId, source/target/question/segment и revision identity, unchecked/correct/error, отсутствие автопринятия текста, серверная полнота и online recovery отражены. TESTING aggregate и окончание цикла не выдуманы. AD-10 сохранён. |
| O6: точные вхождения, FillBlank из выделения | AD-27, UX-O6 | Разделены protected/blank/correctness, разные поля/повторные слова, undo/redo, сериализация и миграция. Сохранена player/exam redaction. |
| O7: голос набором и multiple denominator | AD-28, UX-O7 | Confirmed vote до распределения, denominator по ответившим участникам, stable retry и server uniqueness отражены. HTML не объявлен vote API. |
| O8: приватное обращение, покупки, экзамены | AD-14–16/18/20–24, UX-O8 | Реальные адаптеры, права, recovery и отсутствие fixture-success отражены. AD-20 прямо ограничивает адресата автором урока и не расширяет readership через reviewer/curator role. |
| История операций | AD-30, UX-O10 | Общая own history и author lesson scope, дата/основание/signed amount/resource/status, pending отдельно, разрешённая bounded projection и continuation отражены. Новый ledger не создаётся. |
| O9: текущая версия без гайда | AD-21, UX-O9 | Guide и заглушки выключены до отдельной задачи. Legacy thresholds не приняты, выпуск без гайда не заблокирован. |
| O11: исходные исключения | UX-O11, OPS / EXAM-Q5, EXAM-Q7 | Не придуманы бесплатная правка, возврат/частичная выплата незавершённого review или активация окружения. |
| O12: ordinary auto / Premium explicit | AD-19, UX-O12 | Независимые операции и receipts, общий запас, ordinary после 10 дней без Premium и явный Premium claim без рекламы отражены. Вход не запускает Premium claim. |
| A11Y | AD-24/25, A11Y | fontScale 2.0, TalkBack, keyboard/DPAD, contrast после композиции и специальные поверхности отражены. HTML не считается Android PASS. |
| Сохранённые O4/O10 | AD-29/30 и соответствующие Deferred | Неразрешённые access/lifecycle/defaults приватного формата и предметный состав проекций названы. Принятые поверхности не переоткрыты. |

## Закрытое уточнение

**H-1 — CLOSED: AD-20 теперь называет конкретного получателя приватного сообщения по уроку.** В повторно прочитанной версии приватное сообщение/ошибка адресовано разрешённому автору этого урока; общая Личка не даёт reviewer/curator права читать его только из-за роли и не обещает двусторонний чат или ответ. Публичное обсуждение остаётся отдельным. Это соответствует EXPERIENCE S7/UJ-4, не создаёт нового продуктового вопроса и не меняет источник.

## Подтверждения и разрывы кода

Пути ниже относительно checkout `.local/worktrees/mvp-candidate`. Прочитаны названные контракты, не весь проект; отсутствие найденного адаптера не заявляется как доказательство отсутствия во всём репозитории.

| Область | Наблюдение | Следствие для handoff |
| --- | --- | --- |
| Exam price | `functions/config/economy-constants.json:32–33`: THEME_TEST=100, FINAL_EXAM=300. `shared/feature/economy/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/domain/model/EconomyConstants.kt:83–84`: 1 и 3 полных заряда | Legacy 300 не отменяет принятые 200. UX-O1 правильно оставляет согласование конфигурации до debit; EXAM AD-18 ранее не выбирал конкретный ordinary/plasma split. |
| Tournament prizes | `functions/tournament-policy.js:84–97` считает first+second+2×third; `:392–406` разрешает только прежние placement slots. `functions/course-tournament-controller.js:90` сохраняет только first/second/third | Изменения нужны во всей цепочке, не только в форме CREATE. AD-17/UX-O2 это фиксируют. |
| Meeting | `functions/tournament-service.js:266–295` уже разделяет PROPOSE/CONFIRM/READY. `functions/course-tournament-controller.js:79` safeMeeting не возвращает индивидуальные proposals/confirmations/ready | Сохранять команды/lifecycle; расширять только разрешённую read/ack-проекцию с восстановлением. |
| Review | `android/feature/quest-authoring/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest_authoring/presentation/component/DefaultReviewQueueComponent.kt:271–281` применяет assignment score для TESTING/LOGIC; `:463` ставит accepted из `translatedText.isNotBlank()`. `shared/feature/quest-authoring/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/quest_authoring/data/remote/ReviewAssignmentRemoteDataSource.kt` содержит общий score и boolean segment accepted. `functions/index.js:5282–5299` агрегирует прежнюю форму | Нужна адаптация client/domain/server вместе. Текущий код не является источником нового UX; AD-26 это явно говорит. |
| Text ranges | `shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/QuestionContent.kt:189` — FillBlank `protectedTextSegments: List<String>`. `android/feature/quest-authoring/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quest_authoring/presentation/logic/FillBlankAuthoring.kt:118–138` восстанавливает маркеры через текст и replaceFirst | Это не устойчивая occurrence identity во всех полях. AD-27/UX-O6 правильно оставляют единый schema/migration контракт. |
| Annotation redaction | `shared/core/question-schema/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/question_schema/RedactedQuestionContent.kt:189–203` исключает protected metadata, потенциально раскрывающую ответ | Новые аннотации не должны автоматически становиться публичным player/exam payload. AD-27 сохраняет этот контракт. |
| Poll | `android/feature/lesson-runner/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/lesson_runner/presentation/ui/QuestionTypeContent.kt:44–65` выполняет local draft/feedback; отдельный vote API ограниченным sweep не подтверждён | AD-28/UX-O7 корректно требуют серверное подтверждение, уникальность голоса и продолжение серии. |
| History | `functions/currency-ledger.js:18–25` описывает operator-only ledger в `audit/currency_ledger/entries`; `firestore.rules:33–40` уже разрешает владельцу только свои server-written receipts | Наличие ledger/receipts не доказывает полноту нового списка. AD-30 требует разрешённую проекцию, не открытие raw audit. |
| Daily boxes | `functions/box-service.js:21` разделяет VISIT/OFFLINE_STREAK/PREMIUM_DAILY; `:137–148` использует отдельный Premium clock/source. `functions/gift-boxes.js:23,74–75` задаёт порог 10 | Базовые серверные источники существуют; связка с принятым явным UI claim ещё требует доказательства. AD-19 не объявляет её готовой. |

## Границы проверки

Сохранены принятые Clean/KMP/Decompose границы, владельцы фактов, server-authoritative money/ACK, owner/epoch, operation identity, отдельные tournament/exam lifecycle, review settlement, immutable certificates и EXAM C1–C4. Код не изменялся. Gradle/Node/runtime/Play/Android accessibility проверки не запускались; этот документ не содержит нового PASS реализации.
