---
title: '9.2 noir-ordinary-learning — джорни обычного обучения на NOIR (CAP-17)'
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

**Problem:** Epic 9 (epic-ux-noir, CAP-17) требует, чтобы каждый джорни говорил на финальном NOIR. Инвентарь (9.1, метод в tickets.toml) показал: в джорни обычного обучения остались сырые material3-поверхности — экран «Мои квесты» (карточки черновиков на BrandCard, диалог подачи на material3.AlertDialog, спиннер вместо честной загрузки), диалог подтверждения выхода раннера, опрос с дефолтными Checkbox/RadioButton вне NOIR-палитры.

**Approach:** Переодеть свои поверхности экрана в примитивы designsystem/noir (NoirGroup/NoirChip/NoirButton/NoirType + новый общий NoirDialog из 9.1); опрос — токенами (акцент/ink/off). Строки, состояния и контракты компонент не меняются (UX-5/UX-6/UX-14); QuestCard/CatalogSpinner остаются общими компонентами дизайн-системы (не форки). Локале-независимые Compose-тесты по образцу NoirSettingsScreenTest.

## Boundaries & Constraints

**Always:**
- Только presentation-слой; домен/данные/functions не трогаются.
- Строки ресурсов не меняются; NoirButton отображает UPPERCASE по правилу семьи кнопок (NOIR-SPEC §6.1) — это стиль, не смена строк.
- Загрузка списка — NoirSkeletonRows («настоящая загрузка», UX-14); спиннер в диалоге цитаты остаётся (24dp inline), тонирован акцентом.
- Чекбокс остаётся только у множественного выбора опроса (NOIR-SPEC §6.4).

**Never:**
- Не трогать surfaces предложений автора (4.21–4.25): ResultContent и связанные entry — соседний поток.
- Не рестайлить общие компоненты QuestCard/CatalogSpinner (вне истории; риск на другие джорни).
- Никаких новых локальных цветов/шрифтов — только токены noir-пакета.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Черновик SUBMITTABLE + вопросы | карточка в списке | título + счётчик вопросов + кнопка подачи (ghost) | Нет |
| Черновик без вопросов | questionCount = 0 | Кнопки подачи нет (не disabled-серость) | Нет |
| Заявка отклонена | outcome REJECTED с failedQuestions | «Не прошёл» тегом цвета danger + построчный разбор + «Исправить вопросы» (ghost) | Нет |
| Диалог подачи, цитата готова | quote != null | Плата названа строкой, «Отправить» primary, «Отмена» ghost | Нет |
| Диалог подачи, цитата грузится | isQuoteLoading | Спиннер, confirm-кнопки нет — только «Отмена» | Нет |
| Диалог подачи, цитата отказала | isQuoteFailed | «Повторить» primary + «Отмена» | Нет |
| Отправка в полёте | isSubmitting | «Отправляется…» отключена, «Отмена» жива | Нет |
| Выход из попытки | ExitConfirmDialog | NoirDialog; «Выйти» primary, «Отмена» ghost; колбэки те же | Нет |
| Опрос одиночный | allowMultiple=false | RadioButton в NOIR-цветах, клик по строке | Нет |
| Опрос множественный | allowMultiple=true | Checkbox в NOIR-цветах (чекбокс только для multi) | Нет |

</frozen-after-approval>

## Code Map

- `android/core/designsystem/.../noir/NoirComponents.kt` — +NoirDialog (surface-4, radius 20, hairline, mono-действия) и +RowScope.NoirDialogAction (primary/ghost, ровно одна primary на диалог) — общие примитивы 9.1.
- `android/feature/quest/presentation/.../ui/MyQuestsScreen.kt` — карточка черновика: BrandCard → NoirGroup; «Черновик» → NoirChip; тексты → NoirType; причина возврата/отказ → NoirDanger; кнопки → NoirButton ghost; загрузка → NoirSkeletonRows; FAB → акцентный круг 56dp (NoirInk иконка, Role.Button); DraftSubmitDialog → NoirDialog.
- `android/feature/lesson-runner/presentation/.../ui/ExitConfirmDialog.kt` — material3.AlertDialog → NoirDialog.
- `android/feature/lesson-runner/presentation/.../ui/SurveyContent.kt` — CheckboxDefaults/RadioButtonDefaults на noir-токенах (accent/NoirInk/NoirT3).
- Тесты: `.../quest/presentation/src/test/.../ui/NoirMyQuestsScreenTest.kt` (9 кейсов), `.../lesson-runner/presentation/src/test/.../ui/NoirRunnerDialogsTest.kt` (4 кейса); test-зависимости обоих модулей — robolectric-набор по образцу settings-модуля; `robolectric.properties` sdk=34 (robolectric 4.11.1 не тянет targetSdk 35 смерженного манифеста; предсуществующий красный NoirSettingsScreenTest с той же причиной — атрибуция владельцу).
- Do not touch: ResultContent/предложные поверхности, QuestCard/CatalogSpinner, уроки списков, strings.xml.

## Tasks & Acceptance

**Execution:**
- [x] NoirDialog/NoirDialogAction в дизайн-системе (9.1).
- [x] MyQuestsScreen → NOIR (карточки/чип/кнопки/FAB/диалог/скелет).
- [x] ExitConfirmDialog → NoirDialog; SurveyContent → NOIR-цвета.
- [x] Compose-тесты обоих модулей + test-зависимости + robolectric.properties.
- [ ] Гейты: sgw (JDK 27) тесты quest/lesson-runner/designsystem; ktlint-атрибуция.

**Acceptance Criteria:**
- Given черновик со статусом из SUBMITTABLE_DRAFT_STATUSES и вопросами, when экран открыт, then карточка показывает название, счётчик вопросов и кнопку подачи, а без вопросов кнопки нет вовсе.
- Given отклонённая заявка, when карточка открыта, then «Не прошёл» показан цветом danger с разбором по вопросам и кнопкой «Исправить вопросы», клик открывает черновик.
- Given диалог подачи с готовой цитатой, when автор подтверждает, then confirm-колбэк вызван ровно один раз; без цитаты кнопки подтверждения нет; при отказе цитаты — «Повторить».
- Given диалог выхода раннера, when нажать «Выйти»/«Отмена», then вызван ровно один соответствующий колбэк.
- Given опрос, when кликнуть вариант, then onOptionToggled вызван с id варианта; отправка доступна только с выбором.

## Implementation Notes

- Baseline: 75193bfa (night/e7-commerce).
- NoirButton upper-case отображение — правило семьи (§6.1); в тестах сравнение без регистра.
- Бесконечные анимации (скелет/спиннер) в тестах — с mainClock.autoAdvance=false.
- robolectric.properties sdk=34 — локальный пин трёх модулей истории; корневая причина (robolectric 4.11.1 vs targetSdk 35) — решение владельца (апгрейд каталога заденет все robolectric-модули, включая предсуществующие красные settings/economy/app-shell).

## Spec Change Log

- 2026-10-28: первый compile гейта поймал самоссылку override в object-выражении фейка (qualified this); Robolectric-инициализация вскрыла предсуществующий красный targetSdk35-vs-robolectric4.11.1 (NoirSettingsScreenTest падает так же на HEAD — проверено прогоном settings-модуля 2026-10-28), закрыт корнево апгрейдом robolectric до 4.17 в каталоге (история 9.1; локальный пин sdk=34 в трёх модулях истории оставлен для детерминизма).
- 2026-10-28, верификация: `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :android:core:designsystem:testDebugUnitTest :android:feature:quest:presentation:testDebugUnitTest :android:feature:lesson-runner:presentation:testDebugUnitTest :android:feature:quizzes-screen:presentation:testDebugUnitTest :android:feature:exam:presentation:testDebugUnitTest --no-configuration-cache` → **BUILD SUCCESSFUL** на финальном состоянии: designsystem 18/0, quest/presentation 63/0 (включая 9 новых NoirMyQuestsScreenTest), lesson-runner/presentation 240/0 (включая 4 новых NoirRunnerDialogsTest), quizzes-screen 191/0, exam 7/0. ktlint тех же модулей (ktlint*SourceSetCheck): единственное нарушение — **предсуществующее** DefaultMyQuestsComponent.kt:118 (backing-property, приземлено 4.16 в 77bdb0fe, файл этой волной не тронут; вне известной корзины quest-authoring:data — новая находка владельцу). functions/mvp-isolation не тронуты (presentation-only). Тесты settings-модуля после апгрейда robolectric — GREEN (проверено отдельно). Побочный честный факт: сборка в общем дереве с соседним WIP-потоком (4.21–4.25) требует ретраев — часть красных прогонов принадлежала их незакоммиченным файлам.

## Design Notes

- Почему skeleton, а не спиннер: UX-14 требует «показывай настоящую загрузку»; пустой центр вместо списка читается как «пусто». Спиннер оставлен только внутри диалога цитаты (локальный, короткий, с текстом вокруг).
- Почему Ghost на карточных кнопках: бюджет акцента — одна залитая кнопка на экран, она у FAB создания (§6.1/§7).
