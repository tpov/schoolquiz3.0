# UX-O7 poll contracts — story 8.1 (epic-social-features) — 2026-10-09

## Deliverables

- **Contract (full edition):** `_bmad-output/mvp-candidate/epic-social-features/ux-o7-poll-contract.md`
  — серия/revision/вопрос identity, уникальность участник×вопрос, delivery/recovery через общий
  outbox, один снапшот счётов, retry ≠ второй голос, правка до отправки ≠ post-vote revision.
  Каждый пункт гейта contract-map «UX-O7» закрыт ссылками на разделы (шапка контракта).
- **Закрепляющие тесты (транспортная половина):**
  `shared/core/outbox/src/jvmTest/kotlin/com/tpov/schoolquiz/shared/core/outbox/UxO7PollVoteIdentityContractTest.kt`
  — 4 теста, все зелёные:
  1. `vote key is derived from the series-revision-question triple and never varies` — ключ из
     тройки идентичности, детерминизм, серверный charset, разведение осей (вопрос/revision/серия),
     отказ пустому намерению;
  2. `vote record carries one choice set and the series ref in the operation namespace` — один
     набор (не отметки-голоса), NFR4-namespace операции и ссылки;
  3. `retry resends the frozen choice set under the same key` — NoNetwork → retry тем же телом
     под тем же ключом, identity в хранилище не трогается, receipt ровно один;
  4. `repeat of a known vote key returns the same receipt without a network call` — известный
     ключ не переотправляется (второй участник в знаменателе не появляется).

## Gate

`sgw ./gradlew :shared:core:outbox:jvmTest --no-configuration-cache` → BUILD SUCCESSFUL
(1m 07s), UxO7PollVoteIdentityContractTest: 4 tests / 0 failures / 0 skipped
(test-results XML в этом дереве не коммитится; прогон воспроизводим командой выше).

## Boundary decisions (почему тесты только транспортные)

Код опросов ещё не существует (гейт сознательно стоит «before real vote writes»). Правила §2/§3/§5
(идентичность тройки, серверная уникальность, формула процентов, скрытие распределения,
различимость unknown/zero) нормативны в контракте и становятся кодом с 8.2+; тесты на
несуществующих классах не писались. Константа `polls.SUBMIT_VOTE`, членство в
`permanentReceiptOperations`, серверный обработчик `MUTATION_HANDLERS` и гейт
`OutboxOperationsRegistryTest` — истории 8.2+ (в тестах операция фигурирует литералом ровно в
том виде, который контракт фиксирует). Паттерн повторяет историю 2.5 (online-o2): контракт +
транспортные контракт-тесты до появления реализации.

## Sources

AD-28 (spine 2026-09-29, с прямым «правила … фиксируются в O7 до vote API»), AD-1/2/6/7/8,
UX-15 (ux-constraints), CAP-24 + OQ-7 (SPEC), contract-map «Remaining gates» → UX-O7,
reconcile-experience-20261001 («право менять принятый голос не назначено»), инцепция эпика 8.
Полный список с путями — в шапке контракта.
