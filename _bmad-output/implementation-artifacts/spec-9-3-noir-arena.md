---
title: '9.3 noir-arena — джорни арены на NOIR при неизменных селекторах (CAP-17)'
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

**Problem:** Инвентарь 9.1: вход в арену и её каталог — последние сырые material3-поверхности джорни (ArenaEntryContent — Card/OutlinedButton, ArenaEntryDialogs — material3.AlertDialog, ArenaLessonCatalogScreen — Button/Card, ArenaRunnerContent — Text/TextButton в результате/внимании). UX-9 требует NOIR-семью при сохранении авторитетных путей подготовки/резервов/восстановления.

**Approach:** Переодеть поверхности в noir-примитивы (NoirGroup/NoirButton/NoirDialog из 9.1/NoirType), сохранив (1) ВСЕ testTag байт-в-байт — риг-джорни 3.x управляются этими селекторами; (2) все строки без изменений — квитанции арены читает риг по текстам; (3) семантику событий. Один primary на экран: загрузка/обновление каталога; подтверждение диалога — primary диалога.

## Boundaries & Constraints

**Always:**
- testTag каждого селектора сохранён: arena_entry, arena_wallet_unknown/nolics, arena_wallet_refresh, arena_random, arena_tickets_load, arena_preparations_load, arena_recovery_load, arena_entry_busy, arena_entry_notice, arena_preparation_<id>, arena_retry_prepare_<id>, arena_ticket_<id>, arena_start_<id>, arena_open_<id>, arena_cancel_<id>, arena_action_*_<id>, arena_catalog, arena_back, arena_refresh, arena_loading, arena_notice, arena_count, arena_empty, arena_lesson_<id>, arena_select_<id>, arena_more, arena_entry_dialog, arena_consent_price, arena_difficulty_EASY/HARD, arena_entry_confirm.
- Строки ресурсов не меняются; кнопки отображают UPPERCASE (семья NoirButton, §6.1).
- Домен/данные/functions не трогаются; политика подготовки/резервов не трогается (UX-9, AD-13/31).

**Never:**
- Никаких новых локальных цветов; только токены noir-пакета.
- Не менять тексты квитанций/статусов (рид-поверхности рига).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Шапка входа с кошельком | wallet известен | Строки ноликов/жизней/времени наблюдения + все кнопки-селекторы на месте | Нет |
| Кошелёк неизвестен | wallet = null | Честная строка «неизвестно» под своим тегом | Нет |
| Экран занят | busy = true | Тег arena_entry_busy на спиннере (акцентный тон) | Нет |
| Диалог подготовки | Prepare | Цена/заряд/сложность + выбор сложности (теги EASY/HARD) + confirm | События ChangeDifficulty/Confirm/Dismiss с теми же id |
| Диалог отмены тикета | Cancel | Замороженная цена + предупреждение + confirm/dismiss | Те же события |
| Каталог | список уроков | Карточки NoirGroup, «выбрать» ghost с тегом arena_select_<id> | Нет |
| Пустой каталог | totalLessons = 0 | Строка пустоты под тегом arena_empty | Нет |
| Результат попытки | ArenaResult | Квитанция теми же строками; кнопки sync/back ghost | Нет |

</frozen-after-approval>

## Code Map

- `android/feature/quizzes-screen/presentation/.../screen/ArenaEntryContent.kt` — Card → NoirGroup, OutlinedButton → NoirButton ghost (тег в modifier), спиннер акцентный, тексты NoirType.
- `.../screen/ArenaEntryDialogs.kt` — material3.AlertDialog → NoirDialog/NoirDialogAction; сложность — ghost-кнопки с тегами; confirm — primary с тегом.
- `.../screen/ArenaLessonCatalogScreen.kt` — заголовок/карточки → NoirType/NoirGroup; back/more/select ghost, refresh primary; спиннер акцентный.
- `android/feature/lesson-runner/presentation/.../ui/ArenaRunnerContent.kt` — Result/Attention/refresh-кнопка: TextButton → NoirButton ghost, тексты NoirType, спиннеры акцентные; Question-ветка (QuestionProgressHeader/QuestionTypeContent) не тронута — уже на noir-семье.
- Тест: `.../quizzes-screen/presentation/src/test/.../screen/NoirArenaEntryContentTest.kt` (4 кейса: селекторы шапки, wallet_unknown, busy-тег, диалог подготовки с проводкой ChangeDifficulty/Confirm); test-зависимости модуля (robolectric-набор) + `robolectric.properties` sdk=34.
- Do not touch: ArenaRunnerView.kt (component-сторона), arena-домен, скрипты рига, strings.xml.

## Tasks & Acceptance

**Execution:**
- [x] ArenaEntryContent/ArenaEntryDialogs/ArenaLessonCatalogScreen/ArenaRunnerContent → NOIR.
- [x] Compose-тест селекторов и проводки диалога; test-зависимости модуля.
- [ ] Гейты: sgw (JDK 27) тесты quizzes-screen/lesson-runner/designsystem; ktlint-атрибуция.

**Acceptance Criteria:**
- Given сессия арены и известный кошелёк, when шапка входа открыта, then присутствуют все селекторы рига (список Code Map) и строки честные.
- Given busy, when экран занят, then arena_entry_busy существует.
- Given диалог подготовки, when выбрать HARD и подтвердить, then ровно два события ChangeDifficulty(HARD) и Confirm(dialogId) с теми же id.
- Given catalog/диалоги, when отрисованы, then ни одна строка ресурса не изменилась и ни один testTag не переименован.

## Implementation Notes

- Baseline: 75193bfa (night/e7-commerce).
- robolectric-набор модуля добавлен по образцу settings; пин sdk=34 — вместе с корневым апгрейдом robolectric до 4.14.1 в каталоге (см. 9.2 Spec Change Log: предсуществующий красный targetSdk35-vs-robolectric4.11.1, затрагивавший settings/economy/app-shell).
- UPPERCASE кнопок — отображение, не смена строк; uiautomator-драйвер рига целится по resource-id (testTag), не по тексту.

## Spec Change Log

- 2026-10-28, верификация: тот же пяти-модульный sgw-прогон (см. 9.2) — **BUILD SUCCESSFUL**: quizzes-screen/presentation testDebugUnitTest **191/0** (включая 4 новых NoirArenaEntryContentTest: селекторы шапки, wallet_unknown, busy-тег, проводка диалога ChangeDifficulty(HARD)+Confirm), lesson-runner/presentation 240/0. ktlint модулей: чисто (единственное нарушение — предсуществующий DefaultMyQuestsComponent.kt:118, см. 9.2). Гонка с соседним WIP в общем дереве — гейт добивался ретраями, финальный прогон на финальном коде. Лов петли: импорт LocalNoirAccent и ArenaEntryCallbacks (пакет screen, не component) пойманы компиляцией до лендинга.

## Design Notes

- Почему refresh — единственный primary каталога: он — главное действие экрана; выбор конкретного урока — ghost (§6.1: одна залитая кнопка на действие).
- Почему диалоговые сложность/confirm различаются стилем: сложность — переключение замысла (ghost), confirm — необратимая трата резервов (primary).
