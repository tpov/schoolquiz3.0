# ADR-0010: Designsystem

## Status
Accepted — 2026-04-16

## Context

В legacy UI был построен на XML + ViewBinding + Material Components (Material2). Визуальный стиль трёх опорных экранов (shop, referrals, settings) — тёмный flat-дизайн с брендовыми акцентами:

- Background: чистый `#000000`
- Cards: `#242429` с 1dp stroke вместо теней
- Primary accent: `#4285F4` (Google Play Blue)
- Gold для премиум: `#FFD700`
- Violet для donate: `#2A1B3D`
- Rounded corners: 16dp
- Elevation: 0 везде
- Цветные иконки по категориям (синий, зелёный, оранжевый для разных групп)
- Только dark theme (`values-night/` пусты — всегда тёмный)

Юзер хочет сохранить визуальную атмосферу legacy, но обновить под современный движок.

## Decision

### Движок: Jetpack Compose + Material3

Базируемся на **Jetpack Compose + Material3**:

- Compose — уже принятое решение (ADR-0008).
- Material3 — современный Google движок, даёт готовые анимации, accessibility, motion, dynamic typography. Брендовая палитра живёт как кастомный `ColorScheme`, поверх Material3-компонентов.

Никакого designsystem с нуля — 90% кастомизации делается через `MaterialTheme` с брендовыми shapes/colors/typography, плюс несколько wrapper-компонентов.

### Цветовая схема (брендовая)

Переносим палитру legacy, обёрнутую в Material3 `ColorScheme`:

```
// dark-only colorScheme (пока, light может быть добавлен позже)
primary        = #4285F4   // Google Play Blue
onPrimary      = #FFFFFF
primaryContainer = #1A3C7A
onPrimaryContainer = #D3E3FF

secondary      = #FFD700   // Gold, для премиум/достижений
onSecondary    = #000000
secondaryContainer = #3A2F0A   // приглушённый gold для cards
onSecondaryContainer = #FFE58F

tertiary       = #7D4FAB   // Violet, для donate/спецкарточек
onTertiary     = #FFFFFF
tertiaryContainer = #2A1B3D
onTertiaryContainer = #E0BDF5

background     = #000000
onBackground   = #FFFFFF
surface        = #242429   // базовая карточка
onSurface      = #FFFFFF
surfaceVariant = #1A1A1F
onSurfaceVariant = #B8B8B8

error          = #FF3B30
onError        = #FFFFFF

outline        = #3A3A40   // для 1dp stroke'ов карточек
```

### Shapes

```
extraSmall = 4.dp
small      = 8.dp
medium     = 12.dp
large      = 16.dp       // основные карточки
extraLarge = 24.dp       // диалоги / bottom sheets
```

### Elevation

**Глобально elevation = 0.dp**. Вместо теней — 1dp stroke в цвете `outline`. Это прямо отражает legacy-стиль («flat с бордерами»).

Исключение: Navigation Bar (bottom tabs) имеет небольшой surface tonal elevation для визуального отделения.

### Типографика

Material3 default типографика (Roboto/System). Кастомные шрифты сейчас не добавляем — в legacy их тоже не было. Если позже появится брендовый font — добавляется в `android/core/designsystem/src/main/res/font/`.

### Компоненты

Большинство UI строится из стандартных Material3 (`Button`, `OutlinedButton`, `Text`, `TextField`). Для типичных паттернов legacy — лёгкие wrapper'ы в `android/core/designsystem`:

- **`BrandCard(content)`** — `Surface` с `MaterialTheme.colorScheme.surface`, border 1dp outline, 16dp corners. Заменяет legacy `CardView` с layered background.
- **`BrandPrimaryButton`** / **`BrandSecondaryButton`** — стандартные Material3 кнопки с брендовым colorScheme.
- **`BrandProgressBar`** — линейный прогресс с цветным fill (для daily streak, прогресса квеста).
- **`BrandCircleIconButton`** — круглая кнопка с иконкой и 1dp stroke (как в `circle_button_background.xml` legacy).
- **`CategoryIcon`** — квадратная «цветная иконка» (синий/зелёный/оранжевый/gold/violet), который использовался в shop/settings/referrals.

Все — тонкие обёртки 10-30 строк, не перепридумывают стандартные контролы.

### Только Dark theme (пока)

Legacy был dark-only. В первой итерации тоже только dark. Light theme добавляется позже, если появится продуктовое требование. Структура `colorScheme` уже готова к расширению.

### Иконки

Используем `androidx.compose.material:material-icons-extended` как основу. Брендовые иконки (логотип квеста, gold medal, donate heart) — SVG → VectorDrawable → Composable через `painterResource`. Живут в `android/core/designsystem/src/main/res/drawable/`.

### Preview и каталог компонентов

В `android/core/designsystem` добавляется **каталог компонентов** — набор Composable Preview'ев (`@Preview`), где каждая компонента нарисована в нескольких состояниях. Это:
- документация для разработчика
- быстрый визуальный тест при изменении палитры
- источник для скриншотов в дизайнерских дискуссиях

Каталог виден только в IDE preview + в debug-сборке (отдельный экран «Design catalog» в сайд-меню, скрыт в release).

### Роль модулей

- **`android/core/designsystem`** — `MaterialTheme` (colorScheme, shapes, typography), обёртки-компоненты, брендовые drawable'ы, каталог.
- **`android/feature/*/presentation`** — используют `SchoolQuizTheme { ... }` и строят экраны из Material3 + wrapper'ов.

## Consequences

### Плюсы
- Современный движок (Material3) с бесплатными обновлениями от Google.
- Визуальная преемственность с legacy (та же палитра, тот же flat-стиль, те же 16dp corners).
- Минимум кастомного кода: 5-7 wrapper'ов поверх стандартных Material3.
- Dark-only упрощает первую итерацию, расширяется до dual-theme без ломки.
- Preview-каталог документирует всё в одном месте.

### Минусы
- **Dark-only может разочаровать** юзеров, привыкших к light UI. Митигация: добавить переключатель в настройки, когда появится готовый light colorScheme.
- **Material3 компоненты не 100% совпадают** с legacy (например, у M3 FilterChip другая геометрия, чем у MaterialButton). В местах, где это важно, — применяем wrapper.
- Compose BOM обновляется часто — придётся следить за release notes при апгрейдах.

### Правила
1. Все экраны оборачиваются в `SchoolQuizTheme { ... }`, этот composable — единственная точка входа в дизайн.
2. Никаких hardcoded цветов в presentation-коде. Только `MaterialTheme.colorScheme.X`.
3. Никаких hardcoded shapes. Только `MaterialTheme.shapes.X`.
4. Компоненты, которые переиспользуются в 2+ фичах — поднимаются в `android/core/designsystem`.
5. Компоненты с 1-2 использованиями остаются в своей feature-presentation.
6. Добавление компонента в designsystem — сопровождается `@Preview` в каталоге.

## Mapping из legacy

| Legacy (XML + Material2) | Новый (Compose + Material3) |
|---|---|
| `CardView` с `cardBackgroundColor="#242429"` + 16dp radius | `BrandCard` (Surface + outline border) |
| `MaterialButton` с blue tint | `BrandPrimaryButton` (M3 Button с брендовым colorScheme) |
| `#4285F4` everywhere | `MaterialTheme.colorScheme.primary` |
| `#FFD700` gold accents | `MaterialTheme.colorScheme.secondary` |
| `circle_button_background.xml` | `BrandCircleIconButton` |
| `values-night/` пусты, только dark | `darkColorScheme { ... }` в theme |
| `res/layout/shop_fragment.xml` | `ShopScreen()` composable в `shop presentation` |

## Notes

Тема и компоненты живут в `android/core/designsystem/src/main/java/com/tpov/schoolquiz/android/core/designsystem/`. Зависимости (из `libs.versions.toml`): `libs.bundles.compose.ui`, `libs.androidx.activity.compose`, при необходимости `libs.compose.material-icons-extended`. Brand drawable'ы — `android/core/designsystem/src/main/res/drawable/`.
