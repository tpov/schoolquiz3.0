---
title: '9.5 noir-competitions — джорни соревнований на NOIR при неизменных селекторах (CAP-17)'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '7dd3ed61'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** Верификация волны-1 (вторая смена 2026-10-28) показала: все 8 экранов модуля tournament импортируют designsystem.noir (поэтому метод-инвентарь 9.1 их не пометил), но их тела рендерят сырые material3-примитивы — `Button`/`OutlinedButton`/`AlertDialog`/`OutlinedTextField`/`TextButton` и дефолтные спиннеры поверх noir-типографики. Джорни соревнований (эпик 5, истории 5.9–5.11) говорил на NOIR наполовину; это нарушает Done-when 1/4 эпика (финальный DESIGN на каждом джорни; единая shared-реализация без локальных визуальных форков).

**Approach:** Механический переезд на существующие примитивы designsystem/noir по образцу 9.3/9.6: кнопки → NoirButton (primary — главное действие текущего состояния экрана; ghost — второстепенные, опции, служебные строки), согласие организатора → NoirDialog/NoirDialogAction (примитив 9.1), поля ввода → NOIR-цвета (§6.7, паттерн 9.6), индикаторы загрузки → акцентный тон. Геометрия сохраняется (`fillWidth = false` там, где m3-кнопка прижималась к содержимому). Все testTag — байт-в-байт (соревновательные поверхности читаются ригом по resource-id), все строки ресурсов и литералы («[x] »/«[ ] ») — без изменений, семантика/contentDescription — без изменений.

## Boundaries & Constraints

**Always:**
- Только presentation-слой модуля tournament; домен/данные/functions не трогаются.
- testTag каждого селектора сохранён дословно (tournament_admission_*, tournament_agreement_*, tournament_bracket_*, tournament_browser_*, tournament_match_play_*, tournament_qualification_*, tournament_results_*, tournament_organizer_*, tournament_consent_*, tournament_escrow_*, tournament_ranking_*, tournament_detail_*).
- Правило §6.1 «рядом только одна залитая»: propose=Primary + ready=Ghost в одном ряду; download=Ghost при continue=Primary; confirm диалога — primary диалога.
- Спиннеры — m3 CircularProgressIndicator с `color = LocalNoirAccent.current` (паттерн 9.2/9.3/9.6).

**Never:**
- Не трогать quest-authoring (9.4 — соседний прогон эпика-4), соц-поверхности (9.8 — соседний эпик-8 в полёте), revenue-строки магазина.
- Не менять тексты ресурсов и in-code литералы («[x] »/«[ ] », строки статусов) — рид-поверхности.
- Не заводить новых локальных цветов/шрифтов — только токены noir-пакета.
- Не трогать уже-noir app-shell экраны TournamentEventScreen/TournamentEventDetailScreen (проверены телами — NoirButton/NoirGroup/акцентные прогресс-бары).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Экран допуска, событие не загружено | event == null | Строка пустоты NoirT3 + back ghost с тегом и contentDescription | Нет |
| Экран допуска, окно открыто | downloadFitsEvent | download ghost (второстепенное), continue primary с тегом и contentDescription | Нет |
| Согласование, слот не согласован | meeting != null | propose primary + ready ghost в одном ряду; confirm строки ghost с отставкой по rowConfirmEnabled | Нет |
| Согласование, матч начат | SESSION_STARTED | play primary; propose/ready рядом остаются одна залитая | Нет |
| Сетка, событие завершено | FINISHED + placements | results primary (единственная залитая доски) | Нет |
| Деталь браузера | detail != null | enter primary; back/refresh/first_load/open/load_more ghost | Нет |
| Игра вопроса (матч/квалификация) | payload.options | опции — ghost-кнопки с литералом «[x] »/«[ ] » и тегом option_<id>; blanks — NOIR-поле | awaitingDelivery отключает опции и submit |
| Согласие организатора | state.consent | NoirDialog (тег tournament_consent_dialog): confirm primary (тег), close ghost; busy отключает обе | onDismissRequest гасится при busy |
| Любой экран | state.busy | CircularProgressIndicator акцентного тона | Нет |

## Code Map

- `android/feature/tournament/presentation/.../screen/TournamentAdmissionScreen.kt` — back/download ghost, continue primary, спиннер акцент.
- `.../screen/TournamentAgreementScreen.kt` — back/confirm/load_more/ready ghost, propose/play primary, slot-поле NOIR-цвета, спиннер акцент.
- `.../screen/TournamentBracketScreen.kt` — back/refresh/first_load/open/load_more/agree ghost, results primary, спиннер акцент.
- `.../screen/TournamentBrowserScreen.kt` — back/refresh/first_load/open/load_more ghost, enter primary, спиннер акцент.
- `.../screen/TournamentMatchPlayScreen.kt` — back/recheck×2/опции ghost, submit primary, blanks NOIR-поле, спиннер акцент.
- `.../screen/TournamentOrganizerScreen.kt` — prepare primary, show_consent/retry/refresh/new_create/create_retry ghost, source-поле NOIR-цвета, согласие → NoirDialog/NoirDialogAction, спиннер акцент.
- `.../screen/TournamentQualificationScreen.kt` — back/refresh/pick/load_more/recheck×2/опции ghost, start/submit primary, link/blanks NOIR-поля, спиннер акцент.
- `.../screen/TournamentResultsScreen.kt` — back ghost, спиннер акцент.
- Не тронуто: `android/feature/app-shell/.../ui/TournamentEventScreen.kt`, `TournamentEventDetailScreen.kt` (уже noir), строки, домен, скрипты рига.

## Tasks & Acceptance

**Execution:**
- [x] 8 экранов модуля tournament → noir-семья (кнопки/диалог/поля/спиннеры).
- [x] testTag/строки/семантика — байт-в-байт (сверка grep-ом до/после).
- [x] Гейт: sgw (JDK 27) testDebugUnitTest + ktlintMain/TestSourceSetCheck tournament:presentation.

**Acceptance Criteria:**
- Given любой из 8 экранов, when отрисован, then ни один testTag не переименован и ни одна строка ресурса/литерал не изменены.
- Given ряд с двумя кнопками, when обе видимы, then ровно одна залитая (§6.1).
- Given согласие организатора, when state.consent != null, then диалог — NoirDialog surface-4/radius-20 с mono-действиями, confirm — primary с тегом tournament_consent_confirm.
- Given busy, when экран ждёт, then индикатор акцентного тона.
- Given модуль, when гейт, then testDebugUnitTest зелёный и ktlint файлов истории чист.

## Implementation Notes

- Baseline: 7dd3ed61 (night/e7-commerce, после лендинга 4.25).
- NoirButton upper-case отображение — правило семьи (§6.1/§4: кнопки mono); риг целится по resource-id (testTag), тексты не менялись.
- Опции вопроса — NoirButton Ghost: общий шар-примитив; lesson-runner`овский AnswerOptionSurface internal и вне classpath tournament. Uppercase-отображение длинных опций — пункт осмотра для 9.9 (риг).
- Геометрия: `fillWidth = false` везде, где m3-кнопка прижималась к содержимому (ряды SpaceBetween, ряды spacedBy(8.dp), строки списков).
- Слепая зона метода 9.1 (файлы с noir+m3 импортами одновременно) задокументирована в tickets.toml (шапка волны-2) — метод свипа для остатка эпика: grep тел, не импортов.

## Spec Change Log

- 2026-10-28, волна-2: история переоткрыта (заявление волны-1 «новой работы нет» не подтвердилось: raw m3-вызовы в телах 8 экранов при noir-импортах) и закрыта рестайлом. Гейт (sgw, JDK 27, `--no-configuration-cache`): прогон 1 — ktlint поймал мою строку `rowConfirmEnabled(...)` >120 (перенесена из HEAD:149 в новый блок без сокращения) + 2 чужих; после переноса аргументов: прогон 2 `:android:feature:tournament:presentation:testDebugUnitTest :…:ktlintTestSourceSetCheck` → **BUILD SUCCESSFUL** (компиляция main+test, все модульные юнит-тесты компонентов 5.x зелёные); прогон 3 `:…:ktlintMainSourceSetCheck` — единственные оставшиеся нарушения **предсуществующие**: TournamentAgreementScreen 94/200 (HEAD-строки 87:130 и 185:136, блоки Text deadline/ready_row — grep по diff = 0 касаний) и DefaultTournamentQualificationComponent.kt:19 unused import (файл вне diff). Мои файлы ktlint-чисты; корзина tournament атрибутируется владельцу (в известную корзину quest-authoring:data не входит — новая находка). testTag/строки — байт-в-байт (grep-сверки до/после).
