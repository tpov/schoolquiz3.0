---
title: '9.6 noir-exams — джорни экзаменов и сертификатов на NOIR-токены (CAP-17)'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '75193bfa'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** Инвентарь 9.1: экраны экзамена (ExamRunContent: entry/sitting/closure/result) и сертификатов (CertificateContent) — сырой material3 (Button/Checkbox/LinearProgressIndicator/OutlinedTextField/MaterialTheme.typography) на структурах, где контент-строки — плейсхолдеры EXAM_*/CERT_* до подтверждения владельцем.

**Approach:** Token-переодевка без изменения строк и структуры: NoirType/Noir-цвета, NoirButton, NoirSwitch для согласия с условиями (on/off → свитч, NOIR-SPEC §6.4), NOIR-цвета для OutlinedTextField (§6.7), indeterminate-загрузка → акцентный CircularProgressIndicator. Протокол подтверждения контента владельцем не затрагивается (это стиль, не контент).

## Boundaries & Constraints

**Always:**
- Строки ExamText.*/CertificateText.* байт-в-байт (плейсхолдеры остаются плейсхолдерами).
- Структура состояний, проводка ACK (LaunchedEffect onQuestionRendered), draft-семантика keystrokes — без изменений.
- Только presentation; exam-домен/данные не трогаются.

**Never:**
- Не менять формулировки/не «закрывать» контент-подтверждение стилем.
- Никаких правильностных пометок на экзамене (безопасная финальная проекция, UX-7).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Витрина допуска | Entry | Название режима, строка условий, свитч согласия, отказ по типу, старт | Старт disabled пока canRequestStart=false |
| Сидинг | Running | Счётчик «i / N», отказ, вопрос/ожидание ACK, черновик в NOIR-поле, submit/finish | Черновик едет только с кнопки |
| Ожидание закрытия | AwaitingClosure | Строка ожидания + честный отказ + busy | Нет |
| Результат | ExamResultProjection | Процент/звёзды/проход/совет/next — серверные значения дословно | Нет |
| Сертификаты | CertificateView | Loading/LoadFailed/VerifyUnavailable/None/Earned(+signingPending)/Verified — те же состояния | Нет |

</frozen-after-approval>

## Code Map

- `android/feature/lesson-runner/presentation/.../exam/ExamRunContent.kt` — типографика/цвета на NoirType; Checkbox → NoirSwitch (с contentDescription строки условий); Button → NoirButton; LinearProgressIndicator → акцентный CircularProgressIndicator; OutlinedTextField → NOIR-цвета (S2/outline/accent).
- `android/feature/exam/presentation/.../CertificateContent.kt` — та же семья: NoirButton, NOIR-поле верификации, акцентный спиннер, NoirType.
- Do not touch: ExamRunView.kt (state-классы без UI), ExamRunStateMachine, CertificateListStateMachine, strings.

## Tasks & Acceptance

**Execution:**
- [x] ExamRunContent + CertificateContent → NOIR-токены.
- [ ] Гейты: sgw (JDK 27) — lesson-runner/presentation + exam/presentation тесты; ktlint-атрибуция.

**Acceptance Criteria:**
- Given любые состояния ExamRunView/CertificateView, when экраны отрисованы, then строки EXAM_*/CERT_* не изменились и структура состояний прежняя.
- Given согласие с условиями, when экран допуска отрисован, then on/off — свитч с contentDescription (§6.4), не чекбокс.
- Given занятость, when busy, then индикация загрузки акцентная и не «вечный спиннер» там, где есть отказ.

## Implementation Notes

- Baseline: 75193bfa (night/e7-commerce).
- exam/presentation не имеет compose-тестовой обвязки — новая не заводится: изменения чисто визуальные, состояния покрывает CertificateListStateMachineTest/ExamRunStateMachineTest; визуальная приёмка — 9.9 (риг).

## Spec Change Log

- 2026-10-28, верификация: тот же пяти-модульный sgw-прогон (см. 9.2) — **BUILD SUCCESSFUL**: lesson-runner/presentation 240/0 (ExamRunContent живёт здесь), exam/presentation 7/0 (машина состояний сертификатов). ktlint обоих модулей чист. Строки EXAM_*/CERT_* не менялись (diff кода — только стили/компоненты); протокол контент-подтверждения владельца не затронут.

## Design Notes
