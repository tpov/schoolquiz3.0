---
title: '9.4 noir-authoring-curation — джорни авторства и кураторства на NOIR, включая поверхности предложений (CAP-17)'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'pinned'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '69681a00'
context: []
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** Джорни авторства/кураторства говорит на NOIR наполовину. Волна-2 эпика 9 нашла сырые material3-примитивы в четырёх экранах quest-authoring (InboxScreen — AssistChip/Card/OutlinedButton; AuthorLifecycleScreen — Card/OutlinedButton; CuratorScreen — AlertDialog/DropdownMenu/OutlinedTextField/TextButton; QuestQuestionEditorContent — Checkbox/FilterChip/RadioButton со стандартными m3-цветами поверх noir-типографики), а поверхности предложений 4.22–4.25 (входы «Сообщить об ошибке»/«Предложить правку» на результате с их диалогами цитат, диалог целевого пути в QuestCreate, секция «Предложения» в Decisions) тогда строились соседним потоком — теперь слиты в HEAD 69681a00 и несут свои сырые m3-хвосты (TextButton, AlertDialog, FilterChip-селекторы, спиннеры дефолтного тона). Разблокировано решением владельца «все эпики сразу» (риг-прогон 4.26 валидирует их позже).

**Approach:** Механический переезд на примитивы designsystem/noir по образцу 9.5/9.6/9.2: кнопки → NoirButton (primary — главное действие состояния, ghost — второстепенные; «рядом одна залитая» §6.1), диалоги подтверждения/предложений → NoirDialog/NoirDialogAction (9.1), поля → §6.7 (паттерн 9.6 ExamRunContent), чекбокс/радио/FilterChip-селекторы → NOIR-цвета (паттерн 9.2 SurveyContent; тонировка соответствует тонам NoirChip §6.5), спиннеры → акцент. Контейнеры Card(NoirS1) → NoirGroup. testTag байт-в-байт, строки/семантика/контракты компонент не меняются.

## Boundaries & Constraints

**Always:**
- Только presentation-слой; домен/данные/functions не трогаются.
- Все testTag — байт-в-байт: `inbox-entry-<id>`, `decisions-row-*`, `decisions-logs-*`, `decisions-preview`, `decisions-mark-valid`, `decisions-mark-invalid`, `decisions-comment` (риг/тесты читают их по resource-id).
- Строки ресурсов и in-code литералы — без изменений; NoirButton uppercase-отображение — правило семьи (§6.1), не смена строк (заголовки диалогов предложений уже uppercase в ресурсах).
- Поля ввода — m3 OutlinedTextField с NOIR-цветами §6.7 (фон NoirS2, обводка NoirOutline, фокус/курсор/label — акцент, текст NoirT1).
- Спиннеры — CircularProgressIndicator с `color = LocalNoirAccent.current`.
- Диалоги — общий примитив NoirDialog (surface-4, радиус 20, hairline, mono-действия); ровно одна primary-кнопка на диалог (§6.1).

**Never:**
- Не трогать: 9.7 LegacyReferralCard/ShopView (дизайн-решение владельца), PollsScreen/соц-модуль (соседний трек эпика-8 в полёте), functions/домен/data, Brand*/SchoolQuizDesign-семью как компоненты (использование в QuestCreate-форме остаётся), старую форму QuestCreate (StructureDropdown/CatalogSpinner/настройки приватной ссылки — вне инвентаря 9.4), app-shell `arena_reopen` (территория 9.3), DiscussionSheet в ResultContent (не предложная поверхность — записана straggler'ом), staged-удаления индекса и незакоммиченный WIP соседа (social/outbox/SyncModule).
- Не заводить новых локальных цветов/шрифтов/компонент — только токены и примитивы noir-пакета (тонирование m3-контролов токенами — разрешённый паттерн: поля §6.7, чекбокс/радио 9.2).
- Не менять семантику/поведение: long press + равнозначный semantic action карточки куратора (AD-21), причина перехода обязательна, плата в предложениях — показ, не власть.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Inbox: список есть | entries непуст | карточки NoirGroup, kind/lessonId — NoirChip Neutral, testTag inbox-entry-<id> сохранён | Нет |
| Inbox: пусто/ошибка | entries пуст / errorMessage | строка пустоты NoirT3 / ошибка NoirDanger | Нет |
| Куратор: подтверждение перехода | pendingConfirmation | NoirDialog: заголовок, текст с причиной, поле §6.7, confirm primary + dismiss ghost, обе отключены при isTransitioning | onDismissRequest жив при busy |
| Куратор: меню переходов | long press карточки | DropdownMenu на NoirS2 с текстом NoirT1, пункты те же 5 целей | Нет |
| Автор: список уроков | royalty + entries | RoyaltyCard/HistoryCard → NoirGroup, refresh/load-more ghost | ошибка NoirDanger |
| Редактор вопросов: мультивыбор | multiple=true | Checkbox: checked=акцент, checkmark=NoirInk, unchecked=NoirT3 | Нет |
| Редактор: одиночный выбор | multiple=false | RadioButton: selected=акцент, unselected=NoirT3 | Нет |
| Редактор: полка типов/чипы вопросов | любой тип | FilterChip: unselected — фон NoirS2/обводка NoirOutline/текст NoirT2; selected — фон акцент×10%/обводка акцент×45%/текст акцент (тона NoirChip) | Нет |
| Результат: «Сообщить об ошибке» | sheet открыт | NoirDialog: заголовок runner_report_header, поля плата/вариант — как в 4.22 (BasicTextField в NOIR-рамке), cancel ghost + send primary(enabled=canSend) | Sending отключает send |
| Результат: «Предложить правку» | CopyEditSheet | NoirDialog с прокручиваемым контентом (высота ≤480), отметки/плата те же, cancel ghost + send primary(enabled=canSend) | load failed/need_two — NoirDanger |
| QuestCreate: «Предложить автору» | диалог открыт | AlertDialog → NoirDialog (заголовок qa_proposal_header, селекторы пути, плата), cancel ghost + send primary(enabled=targetComplete && !Sending); вход-кнопка — NoirButton Ghost под залитой «Отправить на арену» | Failed — NoirDanger |
| Decisions: «Предложения» | полки/деталь | surface-toggle и полки — FilterChip в тонах NoirChip; ✓/✗ — как в 4.25 (текст с цветами NoirSuccess/NoirDanger) | stale/unreadable — те же строки |
| Любая поверхность | isLoading | индикатор акцентного тона | Нет |

</intent-contract>

## Code Map

- `android/feature/quest-authoring/presentation/src/main/.../screen/InboxScreen.kt` — AssistChip→NoirChip, Card→NoirGroup (testTag inbox-entry-<id> на модификаторе), OutlinedButton→NoirButton ghost (fillWidth=false), CPI→акцент, error→NoirDanger, bodyMedium→NoirType.rowSub.
- `android/feature/quest-authoring/presentation/src/main/.../screen/AuthorLifecycleScreen.kt` — RoyaltyCard Card→NoirGroup, OutlinedButton→ghost, CPI→акцент, error→NoirDanger.
- `android/feature/quest-authoring/presentation/src/main/.../screen/CuratorScreen.kt` — TransitionConfirmDialog: AlertDialog→NoirDialog + NoirDialogAction (confirm primary, dismiss ghost, enabled=!isTransitioning), reason-поле §6.7; LifecycleEntryCard/HistoryCard Card→NoirGroup (combinedClickable/semantic action AD-21 сохранить); DropdownMenu→containerColor NoirS2 + пункты NoirT1; OutlinedButton→ghost; CPI→акцент; error→NoirDanger.
- `android/feature/quest-authoring/presentation/src/main/.../screen/QuestQuestionEditorContent.kt` — Checkbox/RadioButton → defaults-цвета 9.2; FilterChip (TypeRail, QuestionChip, NewQuestionChip, AddRowButton) → цвета тонов NoirChip; CPI → акцент; контейнеры Surface/schoolQuizDesign-токены и EditorTextField (уже токенизированы 4.x) — не трогать.
- `android/feature/quest-authoring/presentation/src/main/.../screen/QuestCreateScreen.kt` — ТОЛЬКО 4.24-поверхности: NewLessonProposalDialog AlertDialog→NoirDialog (заголовок qa_proposal_header, actions: cancel ghost + send primary), ProposalTargetDropdown-меню → NoirS2; вход «Предложить автору» BrandPrimaryButton→NoirButton Ghost (§6.1: под залитой «Отправить на арену»). Остальная форма — не трогать.
- `android/feature/quest-authoring/presentation/src/main/.../screen/DecisionsView.kt` — surface-toggle/полки предложений/фильтры → FilterChip в тонах NoirChip (4.25-дополнение и слепая зона того же метода); вердикт-хвост 4.17: mark valid→NoirButton Primary, invalid→Ghost (testTags сохранены), comment-поле §6.7 (testTag decisions-comment), Card→NoirGroup (testTags decisions-*/row-*/logs-*/preview сохранены), TextButton load-more→ghost, CPI→акцент, Button/OutlinedButton дефолтные → NoirButton.
- `android/feature/lesson-runner/presentation/src/main/.../ui/ResultContent.kt` — ТОЛЬКО предложные поверхности + соседний сырой хвост того же файла: ErrorReportSheet/CopyEditSheet → NoirDialog (заголовки runner_report_header/runner_copyedit_header, cancel ghost + send primary, контент/платы/состояния байт-в-байт, CopyEdit — прокрутка внутри); TextButton sync-now→NoirButton ghost fillWidth=false. Входы-строки ErrorReportRow/CopyEditRow уже noir. DiscussionSheet — не трогать.
- Reuse: NoirDialog/NoirDialogAction/NoirButton/NoirGroup/NoirChip (designsystem/noir/NoirComponents.kt), §6.7-паттерн `exam/ExamRunContent.kt:136-153`, чекбокс/радио-паттерн `ui/SurveyContent.kt:64-79`.
- Тесты-соседи (не рендерят UI, контракты не менять): DecisionsProposalsComponentTest, DefaultInbox/Curator/Decisions/QuestCreate*Test, CopyEdit/ErrorReportProposalPresentationTest.

## Tasks & Acceptance

**Execution:**
- [x] `InboxScreen.kt` — переодеть карточки/чипы/кнопки/спиннер/цвета ошибок.
- [x] `AuthorLifecycleScreen.kt` — NoirGroup + ghost-кнопки + акцентный спиннер.
- [x] `CuratorScreen.kt` — NoirDialog подтверждения, §6.7-поле, NoirGroup-карточки, тонированное меню, ghost-кнопки.
- [x] `QuestQuestionEditorContent.kt` — NOIR-цвета чекбокса/радио/FilterChip, акцентный спиннер.
- [x] `QuestCreateScreen.kt` — NewLessonProposalDialog→NoirDialog + Ghost-вход (только 4.24-поверхности).
- [x] `DecisionsView.kt` — FilterChip-тона, mark-controls→NoirButton, §6.7-поле, Card→NoirGroup, спиннеры.
- [x] `ResultContent.kt` — ErrorReportSheet/CopyEditSheet→NoirDialog, sync-now→ghost.
- [x] Гейты: sgw (JDK 27) — quest-authoring domain+data jvmTest; quest-authoring/lesson-runner/app-shell presentation testDebugUnitTest; ktlint затронутых модулей.

**Acceptance Criteria:**
- Given любой затронутый экран, when отрисован, then ни один testTag не переименован и ни одна строка ресурса/литерал не изменены (grep-сверка до/после).
- Given диалог перехода куратора / отчёта / правки / предложения урока, when он открыт, then оболочка — NoirDialog surface-4/радиус-20/hairline, ровно одна primary-кнопка, вторая — ghost.
- Given ряд «Отправить на арену» + «Предложить автору», when обе видимы, then одна залитая (Brand) + одна ghost (§6.1).
- Given чекбоксы/радио/чипы редактора и Decisions, when выбраны/не выбраны, then цвета — только noir-токены (акцент/NoirInk/NoirT3/NoirS2/NoirOutline), Material-дефолтов нет.
- Given гейты, when прогнаны, then BUILD SUCCESSFUL; ktlint моих файлов чист (известные корзины атрибутируются).

## Implementation Notes

- Baseline: 69681a00 (night/e7-commerce). Дерево: в индексе ~6143 чужих staged-удалений (решение владельца) — коммит только pathspec-ом, счётчик staged-удалений не должен измениться; WIP соседа (social/outbox/SyncModule, worktree-модификации) не трогать.
- Реализация выполняется in-place сессионным агентом (в harness нет вложенного Agent-инструмента — субагентский шаг step-03 исполняется этим же агентом; отмечено честно, как и lenses_ran: [] у 9.1/9.2/9.5/9.6).

## Spec Change Log

- 2026-10-28, реализация: селекторный чип оформлен общим примитивом `NoirSelectableChip` в designsystem/noir (NoirComponents.kt) — правило 9.1 «всё общее — в designsystem/noir» запрещало дублировать тонирование в трёх фичах (QuestQuestionEditor, QuestCreate-диалог, DecisionsView). Компиляция вскрыла эффективную версию material3 в дереве — 1.4.0 (сигнатура `filterChipColors(containerColor, …, iconColor, …)`, `filterChipBorder(enabled, selected, …)` с обязательными enabled/selected), не 1.3.0 из BOM-документации; примитив написан под фактическую сигнатуру компилятора.
- 2026-10-28, реализация: гейт app-shell presentation testDebugUnitTest вскрыл предсуществующий дефект 4.25 — незакешированный апостроф `Proposer's variant` в `qa_proposals_own_variant` (values/strings.xml:175) ломал mergeDebugUnitTestResources («Invalid unicode escape»); app-shell-тесты не гонялись с лендинга строки. Починено экранированием `\'` — рендер строки байт-в-байт тот же.
- 2026-10-28, верификация: `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:quest-authoring:domain:jvmTest :shared:feature:quest-authoring:data:jvmTest --no-configuration-cache` → **BUILD SUCCESSFUL** (1m20s); `… :android:feature:quest-authoring:presentation:testDebugUnitTest :android:feature:lesson-runner:presentation:testDebugUnitTest :android:feature:app-shell:presentation:testDebugUnitTest …` → **BUILD SUCCESSFUL** на финальном дереве: quest-authoring 72/0, lesson-runner 245/0, app-shell 59/0. testTag/строки — байт-в-байт (grep-сверка до/после, diff пуст). ktlint: designsystem main, quest-authoring presentation (мои файлы), lesson-runner presentation (мои строки) — чисты; предсуществующие корзины атрибутированы (см. Review Triage Log).

## Review Triage Log

### 2026-10-28 — Review pass (quick, self-review; вложенные ревью-субагенты в harness недоступны — lenses_ran: [], как в 9.1/9.2/9.5/9.6)
- verdicts: 6 находок — high 0, medium 0, low 0, false 0, maybe-false 0 (все «patch»-класса, исправлены в полёте)
- findings:
  - `[low]` `[patch]` DecisionsRowCard: замена Card(onClick) на NoirGroup теряла клик строки — исправлено: `.clickable(role = Role.Button, onClick = onClick)` в модификаторе (паттерн CuratorScreen combinedClickable); testTag сохранён.
  - `[low]` `[patch]` mark-valid NoirButton с дефолтным fillWidth=true в Row выталкивал вторую кнопку (0 ширины) — исправлено fillWidth=false.
  - `[low]` `[patch]` QuestCreateScreen: неиспользуемые импорты (AlertDialog/clickable/NoirT3/NoirTOff) и предсуществовавший disorder импортов (NoirT-блок, LINK_EXPIRY_DAY_PRESETS) — убраны/отсортированы (файл в моём diff).
  - `[low]` `[patch]` ResultContent: disorder импортов (BasicTextField после verticalScroll, предсуществовал) и `canSend`-многострочник в переписанном мной CopyEditSheet — исправлены.
  - `[medium]` `[patch]` NoirSelectableChip написан под сигнатуру m3 1.3.0 из памяти — первый же compile-гейт поймал (эффективная 1.4.0: iconColor, обязательные enabled/selected) — переписан под фактическую API компилятора.
  - `[high]` `[patch]` Гейт app-shell поймал предсуществующий дефект 4.25 (незакешированный апостроф в qa_proposals_own_variant ломал resource-merge app-shell) — починен экранированием, рендер байт-в-байт; находка владельцу: app-shell-гейт не гонялся волнами 4.22–4.25.
  - `[defer → атрибуция]` ktlint-корзины вне моего diff: quest-authoring:presentation main — DefaultQuestCreateComponent (12), QuestCreateComponent (2), QuestCreateUiState (2), NewLessonProposalUiState (1) — файлы 4.24, ktlint модуля тогда не гонялся; lesson-runner:presentation main — 241 нарушение в 19 файлах (ExamRunStateMachine 56, RunnerStateMapper 44, ArenaRunHost 26, ExamRunContent 23, LessonRunnerPresentationModule 23, ResultContent 18 — все в 4.22/4.23-authored строках, не тронутых мной, …) — предсуществующие на HEAD 69681a00. Мои файлы/строки чисты.

## Design Notes

- Почему тонированный FilterChip, а не NoirChip: NoirChip — статичный ярлык без состояний; селекторные чипы (полка типов, полки предложений, фильтры) двусостояние. Тонирование m3 FilterChip цветами тонов NoirChip (§6.5: фон surface-2/обводка outline; роль — фон×10% + обводка×45% + текст) сохраняет паттерн «m3-контрол + NOIR-цвета» полей/чекбоксов без заведения локального компонента.
- Почему вход «Предложить автору» — Ghost: рядом залитая «Отправить на арену» (Brand, вне инвентаря 9.4); §6.1 «рядом только одна залитая». Семья кнопки меняется на уровне использования, Brand-компонент не трогается.
- Заголовки диалогов уже uppercase в ресурсах (runner_report_header «REPORT A MISTAKE» и т.п.) — слот title NoirDialog подставляет их без изменений.

## Verification

**Commands:**
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0 && JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:quest-authoring:domain:jvmTest :shared:feature:quest-authoring:data:jvmTest --no-configuration-cache'` -- expected: BUILD SUCCESSFUL.
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0 && JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :android:feature:quest-authoring:presentation:testDebugUnitTest :android:feature:lesson-runner:presentation:testDebugUnitTest :android:feature:app-shell:presentation:testDebugUnitTest --no-configuration-cache'` -- expected: BUILD SUCCESSFUL.
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0 && JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :android:feature:quest-authoring:presentation:ktlintMainSourceSetCheck :android:feature:lesson-runner:presentation:ktlintMainSourceSetCheck --no-configuration-cache'` -- expected: мои файлы чисты (известные предсуществующие корзины атрибутировать).
- grep-сверки testTag/строк до/после по затронутым файлам -- expected: байт-в-байт.

**Manual checks (if no CLI):**
- Чтение диффа: ни одного изменения в домене/data/functions; компонентные интерфейсы не тронуты; строки не тронуты.

## Auto Run Result

**Summary:** Джорни авторства/кураторства говорит на NOIR: 8 поверхностей переодето (InboxScreen, AuthorLifecycleScreen, CuratorScreen + диалог подтверждения, QuestQuestionEditorContent, QuestCreateScreen-диалог 4.24 + вход-кнопка, DecisionsView — предложений и вердикт-хвост, ResultContent — предложные диалоги 4.22/4.23 и sync-хвост) плюс новый общий примитив NoirSelectableChip в designsystem/noir. testTag/строки/семантика/контракты — байт-в-байт; поведение (long press AD-21, обязательная причина, плата-как-показ, enabled-логика кнопок) сохранено.

**Files changed:**
- `android/core/designsystem/.../noir/NoirComponents.kt` — +NoirSelectableChip (§6.5-тона на m3 FilterChip, API 1.4.0).
- `android/feature/quest-authoring/.../screen/InboxScreen.kt` — NoirGroup/NoirChip/ghost-кнопки/акцент/NoirDanger.
- `android/feature/quest-authoring/.../screen/AuthorLifecycleScreen.kt` — NoirGroup/ghost/акцент.
- `android/feature/quest-authoring/.../screen/CuratorScreen.kt` — NoirDialog+NoirDialogAction подтверждения перехода, §6.7-поле причины, NoirGroup-карточки, меню на NoirS2.
- `android/feature/quest-authoring/.../screen/QuestQuestionEditorContent.kt` — чекбокс/радио/чипы в NOIR-токах, акцентный спиннер.
- `android/feature/quest-authoring/.../screen/QuestCreateScreen.kt` — NewLessonProposalDialog→NoirDialog, меню селекторов на NoirS2, вход «Предложить автору»→Ghost.
- `android/feature/quest-authoring/.../screen/DecisionsView.kt` — surface-toggle/полки/фильтры→NoirSelectableChip, mark-controls→NoirButton (valid primary/invalid ghost), §6.7-комментарий, Card→NoirGroup, акцентные спиннеры.
- `android/feature/lesson-runner/.../ui/ResultContent.kt` — ErrorReportSheet/CopyEditSheet→NoirDialog (surface-4/radius-20/hairline вместо плавающего на скриме контента), cancel ghost + send primary, sync-now→ghost.
- `android/feature/quest-authoring/.../res/values/strings.xml` — экранирование апострофа qa_proposals_own_variant (предсуществующий дефект 4.25, блокер app-shell-гейта; рендер не изменён).
- `_bmad-output/mvp-candidate/epic-ux-noir/tickets.toml` — 9.4 → done.

**Review findings breakdown:** патчей в полёте — 6 (clickable-восстановление, fillWidth, импорты×2, m3-1.4.0-API, апостроф-дефект); deferred/атрибуция — ktlint-корзины quest-authoring:presentation (4 файла 4.24) и lesson-runner:presentation (19 файлов, 241 нарушение, все вне моих строк); отклонённых — 0.

**Follow-up review recommendation:** false — все находки закрыты в полёте, гейты зелёные на финальном дереве; визуальная валидация — риг 9.9 (hitl).

**Verification performed:**
- `sgw (JDK 27)` domain+data jvmTest — BUILD SUCCESSFUL.
- `sgw (JDK 27)` presentation testDebugUnitTest ×3 — BUILD SUCCESSFUL: 72/0, 245/0, 59/0 (финальное дерево, 16:21).
- `sgw (JDK 27)` ktlintMainSourceSetCheck: designsystem — SUCCESS; мои файлы quest-authoring/lesson-runner presentation — чисты; корзины атрибутированы (triage log).
- grep-сверка testTag/stringResource до/после — diff пуст (байт-в-байт).

**Residual risks:** визуальный ритм диалогов (surface-4/radius-20) на устройствах и uppercase-отображение длинных заголовков чипов — пункт осмотра рига 9.9; ktlint-корзины вне диффа — владельцу; app-shell-гейт добавлен в обязательные после эпизода с апострофом (рекомендация).
