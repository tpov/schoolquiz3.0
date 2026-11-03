# INTEGRATION.md — механический хенд-офф для агента в репозитории

> Для Claude Code / Codex. Правки на 2-3 минуты каждая, без дизайна.

## Контекст
Проект: KMP + Decompose + Compose + Koin. Канон дизайна — `noir-design-system/NOIR-SPEC.md` + экспорт `~/Downloads/Web-Prototype` (актуальный набор 25.08). План с решениями — `/Users/tpov/Downloads/Web-Prototype/noir-integration-plan.md` §6 (F1 — оставить таймер, F2 — жизнь за подсказку, F3-F7 — по макету).

Текущий HEAD уже содержит: шрифты (`NoirFonts.kt`), i18n (`values/strings.xml` + `values-en`), Shell/Drawer/Runner/Result/Profile/NFT, токен-кодоген `NoirColorTokens`. Остались точечные расхождения.

## Задачи (делать по порядку, каждая — один коммит)

### 1. Lesson List — чип Easy зеленый `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/screen/LessonItemCard.kt:61`
Сейчас `NoirChipTone.Accent` (azure). По макету Easy = `NoirChipTone.Ok` (success, `#5CC97A`). Hard уже красный — не трогать.
Проверка: визуально + `./gradlew :android:feature:quizzes-screen:presentation:compileDebugKotlin --no-configuration-cache`

### 2. Shop — state-строка `android/feature/economy/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/economy/presentation/view/NoirShopStore.kt:242`
Добавить `next 2000` для `STANDARD_HEART_SLOT`: `standardHeartCost(balance.standardHearts + 1)` форматировать через `groupedByThousands()`. При `maxed` не показывать.

### 3. Shop — рамка Beta-чипа `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/noir/NoirComponents.kt:563`
Заменить `Color.White.copy(alpha=.065)` на `NoirOutline` (`#44444E`). Токен уже есть.

## Верификация после всех правок
```bash
./gradlew ciCheck --no-configuration-cache
./gradlew :apps:android-next:assembleDebug --no-configuration-cache -q
```

## Примечания
- Не трогать `local.properties`, `scripts/seed-bulk/data`, `.claude/`, `run/`, `noir-design-system/` (утracked).
- Строки — только через `stringResource`, новые ключи `shop_*/quizzes_*` в `res/values*/strings.xml` обоих локалей.
- Коммиты — `style: краткое сообщение` (см. `git log --oneline -8`).
