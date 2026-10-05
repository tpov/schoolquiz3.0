# Reviewer Gate — Reality: online owner clarification — 2026-10-01

**Verdict: PASS после одной документальной поправки.** Основной spine согласован с новыми решениями владельца и честно обозначает требуемую адаптацию текущего кода. EXAM-зеркало теперь явно наследует общую конфигурацию next-question window и frozen sitting snapshot. Critical/high замечаний нет; единственное medium OR-1 исправлено и повторно проверено.

Проверен только delta к `/tmp/schoolquiz-online-clarification-20261001/main/ARCHITECTURE-SPINE.md`: initiative AD-2/4/14/15/26/29/31, сужение O1/O5/O4 и ONLINE-O1; также changed EXAM AD-3/4/18/30 и `spec-theme-exams/exam-protocol.md`. Остальные архитектурные решения не переоткрывались. UX-source alignment проверяется отдельным gate. Код, версии технологий и runtime не входят в изменение и не менялись.

## Закрытое замечание

### OR-1 — medium — CLOSED: общая настройка окна не должна становиться exam hardcode

**Где:** EXAM `ARCHITECTURE-SPINE.md`, AD-3 и AD-30; `spec-theme-exams/exam-protocol.md:66`.

**Наблюдение:** main AD-31 задаёт одну серверную конфигурацию/default60 и сохраняет применённое значение/deadline. EXAM использует «60-second»/«lasts 60 seconds»/«is 60 seconds», не называя общую настройку и её frozen snapshot. Builder, читающий только предметный companion, может реализовать отдельную константу или перечитать изменившееся значение посреди попытки.

**Исправление:** явно наследовать одну общую server-owned next-question setting с default60, фиксировать её применённое значение/config revision в попытке, не переоценивать существующие gaps на retry/config refresh и не создавать отдельный exam override. Первому ACK не назначать новое число. Это документальная синхронизация уже принятого правила, не новый продуктовый выбор.

**Повторная проверка:** AD-30 прямо наследует shared server-owned configuration initiative AD-31 с default60 и sitting rule snapshot, запрещает отдельный exam hardcode и изменение окна при обновлении config. Protocol:66 повторяет тот же binding/anchor и frozen snapshot. AD-3 отсылает к AD-30; краткое обозначение60 не является отдельной настройкой. Замечание закрыто.

## Подтверждённые выводы

| Изменение | Основание / current code | Результат |
| --- | --- | --- |
| TESTING: одна независимая оценка и server threshold default2 | Новые owner decisions в `.memlog.md:105,108`; текущий `functions/index.js:225,6089–6090` уже содержит порог2, но как hardcode; `:5282,5329–5346` агрегирует старые assignment-level scores/senior band | AD-26 правильно фиксирует новый per-question/revision контракт и отсутствие quorum. O5 больше не просит выбрать число/порог. Config revision/assignment generation и неизменность результата на retry сохранены. Текущий aggregate не выдан за готовый новый adapter. |
| Normal full-cycle rejection | PRD `prd.md:157,163`: нет возврата платы за отклонение, выполненная проверка с обоснованным отклонением оплачивается после полного цикла | AD-26 не переоткрывает принятое. O11 ограничен новым возможным окончательным прекращением незавершённого цикла. |
| Exam entry STANDARD-only | `spec-charges/SPEC.md:45–53,145–154`: plasma never pays toll; `functions/config/economy-constants.json:32–33` ещё100/300 и Kotlin `EconomyConstants.kt:83–84` ещё1/3 | Main AD-14 и EXAM AD-18 снимают ложный выбор pool/split. Legacy final300 остаётся явным implementation drift;100/200 приняты. |
| Private one sitting без зарядов | Owner decisions `.memlog.md:109,113`; mode scope прямо private individual run | AD-29 запрещает deferred resume и STANDARD/PLASMA entry/play actions; technical recovery сохраняет identity/deadline, не расходует новую попытку. Automatic timeout skip не paid-correct. Не введена альтернативная плата, не отменены author submission fees или private course tournament fund. |
| Текущее answer time не останавливается сетью; окно начинается после закрытия вопроса | Owner correction `.memlog.md:112` и derived anchor `:114` | AD-31 различает исходный answer deadline и последующее подключение/загрузку. Timely answer либо исходный expiry определяет anchor; reconnect/late worker не продлевают срок. Готовый следующий вопрос показывается сразу; после последнего нет лишнего окна. |
| Frozen answer receipt и no extension | Неизменный EXAM AD-31 сохраняет первый committed answer и server receipt time; обновлённый exam-protocol:68 прямо запрещает заменить своевременный ответ timeout; current tournament session сохраняет pending ingress `firstReceivedAtMs` и closes timely answer по нему | Main/EXAM не разрешают re-answer, paid-correct timeout, замену уже принятого ответа, перенос deadline на reconnect или sweeper. Sole exam terminal writer и режимные последствия сохранены. |
| Tournament integration — реальный незакрытый seam | `functions/tournament-policy.js:291–294` устанавливает `endsAtMs=startedAtMs+duration`; `functions/tournament-session.js:229` FORCED_CUTOFF закрывает остаток; `:247–249` следующий question timer начинается сразу и ограничивается `session.endsAtMs` | ONLINE-O1 обоснован: текущий runtime не обеспечивает полноценное общее окно. Документ не притворяется, что rule уже реализован, и не сокращает60/не продлевает этап молча. Решение требуется до tournament adapter, остальные режимы не заблокированы. |
| Первый ACK и offline scopes | Main AD-31 и EXAM AD-3/30 сохраняют отдельный existing bounded first-delivery contract; main AD-2/31 исключают превращение готового offline ordinary/Arena в обязательный online | Новое число первого ACK не выдумано; mode lifecycle и offline authorization не объединены. |

## Пределы PASS

Будущий ONLINE-O1 — реальный интеграционный вопрос общего cutoff, а не повторный выбор принятой длительности следующего окна. Configured threshold, per-question review evidence, private runner и новая timing state machine требуют реализации и проверки; документация этого не скрывает. В этой линзе не запускались тесты, сборки, финансовые операции, службы или устройства; runtime readiness не подтверждалась.
