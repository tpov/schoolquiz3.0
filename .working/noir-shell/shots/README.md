# NOIR шелл — скриншоты «до/после» (schoolquiz-emu, redroid, Android 12, 720×1280)

Ветка `noir/shell-screens`, коммиты e72eb413…5faba21e (2026-10-13).
Эталон: DESIGN.md/EXPERIENCE.md (ux-schoolquiz3.0-2026-09-28), ux-constraints UX-1/3/4/9/13, эпик epic-ux-noir (AD-25).

## Пары

| Экран | До | После | Что изменилось |
| --- | --- | --- | --- |
| LOCAL / HOME QUESTS | before-1-local-home-quests.png | after-1-local-home-quests.png | статусбар фиолетовый → chrome #08080A; легаси-подложка шелла → чистый чёрный + wash-home от экрана; низ/верх на chrome |
| EVENTS / QUALIFIER TOURNAMENT | before-2-events-qualifier.png | after-2-events-qualifier.png | azure-подложка → wash-arena (#1D1530); панели на chrome |
| SHOP | before-3-shop.png | after-3-shop.png | убрана вторая подложка; панели chrome (wash-shop не заходит на навигацию) |
| SHOP → РЕФЕРАЛЫ | before-4-shop-referrals.png | after-4-shop-referrals.png | легаси (Google-blue, #242429 карточки, emoji-иконки) → семейство магазина: wash-shop, NoirGlassCard, mono-числа, нолики серые, золото золотое, действия accent-mono |
| ARENA (INTERNET) | before-5-arena-quests.png | after-5-arena.png | панели chrome; wash-арена появляется на списке квестов арены (режим QuestListMode.Arena; сам хаб — см. «Ограничения») |
| Drawer | before-6-drawer.png | after-6-drawer.png | статусбар/жест-зона chrome |
| LOCAL → MY QUESTS | — (легаси-экран не снимался отдельно) | after-7-my-quests.png | вторая смена: BrandCard/QuestCard/CatalogSpinner → wash-home, NOIR-селект категории, строки-карточки, FAB на NoirFab |
| Drawer → ABOUT | — | after-8-about-dialog.png | вторая смена: m3-дефолт → NoirS4/NoirShapeXl, accent-кнопка |

Не попало в кадры (нужны данные/состояние, которых нет на пустом стенде): gift-box FAB и диалог (нужны коробки — 10 дней стрика), список квестов арены с поиском-FAB (пустой каталог + расхождение секции, см. ниже), лидерборд/участники (wash-arena — change 5ec51214).

## Ограничения стенда

- «До» снималось с чистой установки ветки до правок; «после» — после `pm clear`. Содержимое каталогов может отличаться (локальный сев выполняется при первом старте) — сравнивать систему (панели/фон/компоненты), не состав данных.
- Претрейновое расхождение (не из этого дифа): пункт drawer «Арена» на INTERNET меняет заголовок на ARENA, но тело остаётся профилем (секция/стек расходятся). Воспроизводится и до правок (before-5). Требует отдельного разбора навигации.
- На without-GMS стенде цены магазина не приходят из Play — «Prices are loading…» и замки это ожидаемое состояние.

## Гейты

- `assembleDebug` — зелёный (финальная сборка HEAD).
- `ktlintCheck` затронутых модулей — зелёный.
- `testDebugUnitTest`: designsystem/quest/quizzes-screen/economy — зелёные; app-shell 45/51, 6 падений `DrawerFooterSyncStatusTest` (`RoboCookieManager`, Robolectric-среда) — воспроизведены на чистом дереве (без дифа) и не связаны с правками.
- `detekt` в репо падает и на чистом дереве: `Invalid value (25) passed to --jvm-target` (detekt-CLI не знает JVM 25 после uplift тулчейна) — претрейновая проблема среды, требует отдельного решения владельца.

## Вторая смена (2026-10-13, fb92ad2b…9d37097c)

Досняты MY QUESTS (последний полностью легаси-экран шелла) и About-диалог; бонусом m3 DropdownMenu в списке квестов переведён на NoirS4 (в кадр не попал — нужен длинный тап по квесту, на пустом стенде нет данных). Общий NoirFab вынесен в дизайн-систему (fb92ad2b), home-коробка и «+» переведены на него.
