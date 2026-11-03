# Architecture reviewer gate — RUBRIC lens

Дата: 2026-09-29. Независимая проверка good-spine rubric.
**Вердикт: PASS для initiative → feature и feature → epic build-substrate.**
Открытых архитектурных blockers: **0**. Новых обязательных product decisions этой проверкой не добавлено.

PASS относится к архитектурным контрактам текущего принятого MVP. Это не проверка реализации, runtime, физического устройства, Play sandbox или готовности к выпуску. Все чтение и артефакты — на VPS; сборки/службы/финансовые операции не запускались.

## Проверенный срез

Полностью прочитаны:
- main `architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md` — initiative, AD-1–24;
- exam `architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md` — feature, AD-1–37;
- canonical PRD и addendum 09-28 с 09-29 exam amendments, все FR-1–56/NFR-1–8;
- SPEC-theme-exams/SPEC.md, exam-protocol.md;
- exam solution-design.md, epic-breakdown.md;
- ADR-0007 certificates, exam architecture memlog и применимые поздние решения spec/main memlogs;
- main .working/stack-operations.md и brownfield source boundaries.

Проверены адресно импортированные SYNC:AD-30/31 и MON:ADM-8/16. Исторический e2-plan просмотрен как старое свидетельство; его новый superseded banner проверен повторно. Это не повторный кодовый аудит: наблюдения о текущих модулях и версиях использованы в пределах явно названного источника и его evidence limits.

Первоначально проверенные срезы SHA-256 (итоговая узкая повторная сверка и новые hashes ниже):
- main: `9f67638c305c44c4eebbb740e2d46b1f3d517be60e624573e2810d7fdb6675b7`;
- exam: `451ccb19293c27d1e3ff96a70f89794e0a0404ae3dac6d1466ab08281f569249`;
- SPEC: `885d44e0e9290fcf120fedb838e7e5dc6522b29d9ecf3e93774bad4a460be235`;
- protocol: `a0f6bc9c935ca1686b8deceeaa1a5354b66d48fff003129be3bcdd882858655d`;
- ADR-0007: `ca8467e168227d290a4b0f499bc39095821dfba0b1d424ed8e954f848cdf2c4d`.

Сообщённый root lint: main 0; exam 1 low для `users/{uid}`. Это реальный Firestore path в EXAM:AD-8, не незаполненный template. Lint не подменяет настоящую semantic review и здесь не запускался повторно.

## Рубрика

| Критерий | Результат и основания |
|---|---|
| Реальные divergence points на initiative → feature | **PASS.** Main AD-1 разделяет publication, learning, каждый mode, exam issue/sign/verify, read-only profile, economy, review, communication, config. AD-2 запрещает общий lifecycle по удобству reuse. AD-3/5/6/7/9 закрепляют revision/config/operation/outbox/financial seams, где независимые команды иначе могли бы выбрать несовместимые владельцы и способы commit. Это решения о согласованности, не каталог экранов. |
| Реальные divergence points на feature → epic | **PASS.** EXAM AD-8/9/18/19/24–31/34–37 задают admission transaction, stable theme comparison, separate final records, start/charge, one-open sitting, first ACK/answer, sole terminal writer, correction и certificate authority. E1–E10 используют эти решения и явные dependencies; E4 не заменяет tournament engine, E3 владеет full EASY progress definition, E5 publication bank, E9 exam-owned certificate integration. |
| Enforceable Binds / Prevents / Rule | **PASS.** ADs имеют привязки к FR/CAP/соседним контрактам и проверяемые запреты: exact immutable body конфликтует при reuse ID, transient local state не receipt, owner+epoch fence, first-write-wins answer, single terminal CAS, additive migration, frozen attestation. Широкие Binds у cross-cutting AD-1/21/24 допустимы: narrow owners и правила далее названы. «Все FR» не используется как единственное обоснование. |
| Владение данными, авторитетность и конкуренция | **PASS.** Сервер владеет financial/publication/admission/certificate facts; клиент — projection и durable intent. Main AD-6–12 фиксируют loss/retry/account/cancellation/retained proof; EXAM AD-18/29–31 ограничивают duplicate starts/answers/terminators. Terminal result и admission/cooldown/first-pass effects согласованы transactionally; certificate выдаётся только от подтверждённого final result. Core не становится владельцем feature payload/state. |
| Версии/совместимость контрактов | **PASS.** Publication revision/manifest, config component revisions и frozen sitting/ticket/event terms отделены от текущего latest start. Старые approved revisions не заменяются rejected редакцией; quarantine не стирает историю. Course updates сохраняют stable theme passes, new themes требуют сдачи. Corrections append evidence; issued certificate claims/key identity/history frozen. Cursor pair `(changedAtMs,docId)` согласован с SYNC:AD-31 во всех четырёх journals. |
| Coverage и тихие non-goals | **PASS.** Map покрывает FR-1–56/NFR-1–8 и CAP-1–21; числа/полная матрица остаются в governing PRD, не обязаны дублироваться в spine. Current exam включён целиком, mandatory pretest удалён, ordinary/Arena offline не превращены в online exam. Partial publication/review fee budget+burn/config sync/inbox privacy/whole redesign сохраняются. Нет нового iOS/web scope, DM/Telegram, five-ad box, manual tournament draw или автоматически импортированного полного MON scope. |
| Brownfield fit | **PASS.** KMP/domain-data/Decompose/Compose/Koin, существующие scoring/redaction и tournament lifecycle сохранены. Новые общие контракты только neutral; отсутствует engine rewrite ради exam. Миграции от фактической schema, no reset/destructive fallback, existing auth/pending/receipt сохраняются. Старый E2 extraction plan помечен historical/superseded, старые schema3→4/5 и claims «модуль отсутствует» больше не task list. |
| Наследование без действующего конфликта | **PASS.** Main квалифицирует EXAM/SYNC/MON IDs, поздний owner decision приоритетен. Explicit overrides убирают pretest, reviewer-offline, optimistic paid admission, terminal-only ACK/deletion и destructive reset. MON ADM-16 ограничен common drop/opening, global cap2/five-ad прямо исключены. Certificate exam/profile ownership согласован между main, EXAM AD-19, E9 и ADR-0007. |
| Операционный/environment envelope | **PASS.** Main AD-23/24 задают один logical server worker owner с fencing/recovery, demo bounded controlled ticks, production adapter отдельным rollout; client не выбирает server clock/worker action. Budgets не становятся product cap или endless Pending. VPS-only, private demo/no production credentials/public ADB/ports, no planning runtime, actual env registry и identity финального candidate сохранены. Toolchain compatibility limitation честно помечена будущим gate; исторические PASS/источник кода не объявлены новым execution. |
| Deferred не разрешает несовместимые builds | **PASS с implementation prerequisites ниже.** До затронутой реализации должны появиться конкретные canonical contracts; общий owner и обязательная semantics уже заданы. Они не открывают нескольким фичам независимый выбор несовместимой власти, не позволяют подменить current policy старым вариантом и не сокращают принятый MVP. |

## Проверка предыдущего finding

**F-PRD-1 resolved.** Main:38 и AD-19:172–174 явно ограничили старый MON ADM-16. Ordinary daily максимум 1, premium daily отдельно, прочие награды не расходуют эти daily limits; старые общий cap2 и five-ad path не действуют. Resolution добавлен в [reconcile-prd.md](reconcile-prd.md) с сохранением первоначального FAIL как истории.

## Implementation prerequisites: обязательны позже, но не архитектурные blockers

Это не «missing architecture owner». В каждом случае один владелец и граница реализации названы; следующим шагом нужен один canonical contract для его потребителей, а не независимые решения в разных epic.

| Контракт | Где уже установлен prerequisite | Владелец и граница следующей работы |
|---|---|---|
| Полное EASY coverage по составу/версии урока | SPEC:154; EXAM AD-27; E3:46–48 | Learning/result ingestion, E3: определить authoritative question-coverage evidence до exam admission; local ordinary unlock и server gate применяют согласованный predicate. Нельзя «догадаться» через stars или 20-question subset. EXAM не получает право независимо выбирать definition. |
| Mixed-theme stars, correction arithmetic/version fixtures | EXAM Deferred:301,303; SPEC:151,153; protocol:79,85; E6:80/E7:90 | Exam/server scoring projection и correction owner: зафиксировать mapping до result display, exclusion/denominator/rounding до rescoring. Pass mark и client render-only уже определены; no local guessed formula, no fabricated correction from missing evidence. Это деталь одного owned contract, а не отсутствие владельца оценки. |
| Renewal/issuance identity и история | EXAM Deferred:302; SPEC:152; protocol:101; E9:108 | Exam certificate owner, E9: до реализации годового повторного получения связать новую successful final sitting с новой выдачей/history, сохранить прежний signed artifact. Улучшение до expiry не может молча заменить active certificate. Нельзя использовать этот prerequisite для исключения year-end re-earning из текущего accepted contract. |
| Worker cadence/budgets/provider settings и acceptance envelope | Main AD-23/Deferred:251; EXAM:304; E4:58 | Exam/tournament owners через отдельные deployment adapters: выбрать target demo execution до unattended validation, prove bounded recovery/capacity до соответствующего запуска. Functional owner/termination policy уже определены; production Scheduler не нужен для разрешённого demo proof. Нет требования выбрать новый SLA сейчас. |
| Тариф lesson edits без affected questions и прекращение неполного review cycle | Main Deferred:249; PRD:578–579 | Это оставшиеся business decisions владельца, а не свободные архитектурные defaults. До соответствующей paid path не выводить0/100/1000 и не сжигать/распределять unfinished cycle как completed. Обычные review units/rounding/escrow/права полностью принадлежат review+economy. |

### Узкая заметка для E4

При описании единой transition table E4 нужно явно проверить сценарий **first ACK принят → answer deadline истёк → клиент больше не приходит** отдельно от первого/between-question ACK-gap timeout. EXAM AD-30/protocol:60 не позволяют старому gap timer обрезать уже начавшееся answer allowance; CAP-4/10 и protocol:62 требуют конечного server outcome без клиента. Владельцем обоих путей уже является один exam lifecycle/sole sweeper (AD-30), поэтому это coverage canonical E4 lifecycle contract и будущей приёмки, не новый владелец, policy override или основание требовать общий tournament engine. Никакой новый числовой timeout этим review не выбран.

## Что не считается finding

- Не требуется переписать в spine все PRD числа, UI states, тестовые размеры и закрытые non-goals; точная нормативная ссылка достаточна.
- Отсутствие свежих build/runtime/Play PASS ожидаемо для planning и не недостаток архитектурного решения.
- Старые memoranda и brownfield notes сохраняют исторические решения. Приоритет amended SPEC/PRD и актуальных spine explicit; наличие отвергнутой истории не reopens pretest/rungs/local certificate issuance.
- Незаполненный mixed-star mapping, rescore denominator или renewal key не допускает выпуск незавершённой фичи, но сам по себе не делает initiative ownership несовместимым: scope, owner и before-implementation gate уже названы.
- Публичная server verification не требует нового standalone verifier, identity verification, proctoring или accreditation.
- Legitimate Firestore `{uid}` не template defect.

## Gate conclusion

Архитектура фиксирует нужные границы на обоих уровнях и допускает дальнейшую декомпозицию с перечисленными обязательными prerequisites. **RUBRIC PASS, blocking findings 0.** Документы/spines/memlogs этой reviewer-задачей не изменены; записаны только этот review и resolution к предыдущей reconciliation.

## Итоговая повторная сверка узких fixes, 2026-09-29

**RUBRIC PASS подтверждён на итоговом срезе; blocking findings 0.**

1. **Ordinary offline unlock сохранён.** Main AD-7, EXAM AD-18/34, SPEC invariant и E3 согласованно оставляют unlock + provisional local nolics debit + outbox в одной Room TX, исходный retry и visible quarantine rollback. Это provisional состояние не становится authoritative financial fact или confirmed EASY admission. Запрет device-side exam charge ограничен экзаменами. Никакой второй sender, rollback Arena settlement или offline exam из этого не выводится.
2. **Гонка первичной выдачи сертификата и correction закрыта.** Main AD-16, EXAM AD-19, SPEC CAP-8, protocol persistence, solution-design, E9 и ADR-0007 фиксируют terminal success + immutable logical issuance fact + durable signing/publication work одной транзакцией. Fact связывает issuanceId, earning resultVersion, claims, dates и key identity. Внешняя подпись выполняет уже принятое обязательство вне TX; pending показан явно; retry не читает позднее исправленный grade для повторного решения о праве, не меняет дату/ID. Rollback не фиксирует ни success, ни issuance. Это technical atomicity принятого initial-issuance контракта, не новая льгота, renewal policy или основание revocation.
3. **Brownfield historical plan не активен.** e2-plan.md содержит явный superseded banner со ссылками на текущие spine/epics, запретом повторного создания существующих scoring/redaction и применения исторических schema3→4/5. Старое тело сохранено как evidence, не pending work.

Итоговые SHA-256, к которым относится окончательный PASS:
- main: `889da3d76f8af3a07167c7ff5c23f50b07588d10575cbf5b78abb5a067435d04`;
- exam: `efbe677ad0da240491c81cc3d0378565903f8fb8cfaacf09ba974be13a8ca7bc`;
- SPEC: `830f35d298101da94beb52a97b8af561de47623fd05bcd45d636424e8551f6e3`;
- protocol: `6fca68712a9c794a3e8d91e40be63601f05606c5f11d27ec3bebd24e3fe563a2`;
- ADR-0007: `36cb6aafdb70821cce4dc449c718dd1602b680f07530332921859c4f9b254030`.

Implementation prerequisites из основной review по-прежнему действуют и не становятся самостоятельными архитектурными blockers. Никаких запусков или новых проверок реализации этой повторной сверкой не выполнено.
