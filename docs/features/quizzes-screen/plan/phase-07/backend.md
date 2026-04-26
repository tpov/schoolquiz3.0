---
phase: 07
role: backend-dev
---

# Phase-07 Backend Tasks: Koin Factory Updates

### Pattern Invariants

- Scaffold ownership: `QuestPresentationModule.kt` и `AppShellPresentationModule.kt` — backend-dev ownership (Gradle/DI wiring). Frontend-dev не модифицирует Koin module файлы напрямую. Ref: `CLAUDE.md` (Scaffold File Ownership section).
- Koin `factory<T>` с `parametersOf(...)` — существующий паттерн. Verify текущую сигнатуру в `QuestPresentationModule.kt:25-41` перед изменением.
- Порядок Koin modules в `startKoin` определяет availability — `quizzesPresentationModule` уже добавлен в Phase-03 (`AppApplication.kt`). Verify что `QuestRepository`, `SectionRepository`, `ThemeRepository`, `LessonRepository` registrations предшествуют `quizzesPresentationModule`.
- Koin `single<>` для singleton scope (DefaultRootComponent) vs `factory<>` для per-use (child components). Verify DefaultRootComponent scoping в `AppShellPresentationModule.kt`.

---

## Update QuestPresentationModule — HomeQuestsComponent factory

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/.../di/QuestPresentationModule.kt`
- **Тип:** Koin module (modification)
- **Сигнатура:** existing `val questPresentationModule = module { ... }`
- **Вход:** N/A (modification)
- **Поведение / Выход:**
  - Обновить `factory<HomeQuestsComponent>` — добавить `onCatalogDrillDown: (CatalogId, String) -> Unit` как параметр через `parametersOf(...)`:
    - `factory<HomeQuestsComponent> { (ctx: ComponentContext, onCatalogDrillDown: (CatalogId, String) -> Unit) -> DefaultHomeQuestsComponent(componentContext=ctx, observeCatalogs=get(), onCatalogDrillDown=onCatalogDrillDown) }`
  - Verify существующий паттерн — если factory сейчас принимает только `ComponentContext`, нужно добавить второй параметр
  - `CatalogId` — из `shared/core/catalog/domain`; verify import
- **Edge cases:**
  - Если `DefaultHomeQuestsComponent` сейчас принимает `Navigator` как параметр — проверить нужен ли он после добавления `onCatalogDrillDown` (возможно Navigator уже не нужен для onCatalogClick path)
  - Koin `parametersOf` — порядок параметров должен совпадать с порядком при вызове из DefaultRootComponent
- **Depends on:** `DefaultHomeQuestsComponent.kt` (Phase-07 frontend), `QuestPresentationModule.kt` (existing), `CatalogId`
- **Canonical reference:** `06-api-contract.md:164, §16`
- **Rationale:** Factory должен принимать `onCatalogDrillDown` для передачи в DefaultHomeQuestsComponent constructor.

---

## Update QuestPresentationModule — MyQuestsComponent factory

- **Файл:** `android/feature/quest/presentation/src/main/kotlin/.../di/QuestPresentationModule.kt`
- **Тип:** Koin module (modification — same file as above)
- **Сигнатура:** existing `factory<MyQuestsComponent>` entry
- **Вход:** N/A (modification)
- **Поведение / Выход:**
  - Обновить `factory<MyQuestsComponent>` — добавить `onQuestDrillDown: (QuestDisplayItem) -> Unit` как параметр:
    - `factory<MyQuestsComponent> { (ctx: ComponentContext, nav: Navigator, onQuestDrillDown: (QuestDisplayItem) -> Unit) -> DefaultMyQuestsComponent(componentContext=ctx, authRepo=get(), observeMyQuests=get(), observeCatalogs=get(), navigator=nav, onQuestDrillDown=onQuestDrillDown) }`
  - `QuestDisplayItem` — из `android/core/designsystem/model`; уже в зависимостях quest/presentation
- **Edge cases:**
  - `Navigator` параметр — verify существующая фабрика использует его; не убирать
  - Порядок параметров в `parametersOf(...)` — согласовать с DefaultRootComponent call site (Phase-07 frontend)
- **Depends on:** `DefaultMyQuestsComponent.kt` (Phase-07 frontend), `QuestDisplayItem`
- **Canonical reference:** `06-api-contract.md:199, §16`
- **Rationale:** Consistent с HomeQuestsComponent factory update.

---

## Verify AppShellPresentationModule — quizzesComponent registration

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/.../di/AppShellPresentationModule.kt`
- **Тип:** Koin module (verify + possibly modify)
- **Сигнатура:** existing `val appShellPresentationModule = module { ... }`
- **Вход:** N/A (verify)
- **Поведение / Выход:**
  - Verify: `QuizzesComponent` factory уже зарегистрирован через `quizzesPresentationModule` (Phase-03 AppApplication). Если `DefaultRootComponent` injections проходят через `appShellPresentationModule`, нужно убедиться что `get<QuizzesComponent>(parameters = { parametersOf(childContext) })` разрешается.
  - Если `DefaultRootComponent` создаёт `quizzesComponent` **напрямую через Koin** (`get<QuizzesComponent>(parameters = { parametersOf(childContext("quizzes")) })`), убедиться что `quizzesPresentationModule` modules list предшествует `appShellPresentationModule` или хотя бы загружен до первого `get<QuizzesComponent>()`.
  - Если `DefaultRootComponent` создаёт `quizzesComponent` **без Koin** (через direct constructor call `DefaultQuizzesComponent(...)`), то AppShellPresentationModule изменения не нужны.
  - Frontend-dev (Phase-07) определяет подход — backend-dev verify DI consistency.
- **Edge cases:**
  - Verify что нет duplicate `single<QuizzesComponent>` регистраций (один в `quizzesPresentationModule`, один возможно в `appShellPresentationModule`) — duplicate → Koin exception
- **Depends on:** `AppShellPresentationModule.kt` (existing), `quizzesPresentationModule` (Phase-03)
- **Canonical reference:** `06-api-contract.md:742`
- **Rationale:** DI consistency — один owner для QuizzesComponent registration.
