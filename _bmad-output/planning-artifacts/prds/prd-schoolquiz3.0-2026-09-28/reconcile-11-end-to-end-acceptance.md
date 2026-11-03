# Input reconciliation — 11-END-TO-END-ACCEPTANCE

**Статус remedies, 2026-09-28: оба gaps устранены в плановом тексте.** Gap1 → [совместимый rollout/финансовый rollback](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:131); gap2 → [отдельно разрешаемые KMP/public условия](/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd-consolidated-addendum.md:133). Они не стали текущим blocker Android MVP и не разрешают выполнение сейчас. Ниже сохранён исходный анализ сверенного снимка.

Дата: 2026-09-28. Planning-only, read-only сверка исходников; runtime, сборки, устройства, магазин и финансовые прогоны не запускались. Авторитет — текущий `prd.md` (FR-1…55), последние решения владельца и PRODUCT-CONTRACT; `prd-consolidated.md` не использован как authority. Сверены `prd.md`, `addendum.md`, `prd-consolidated-addendum.md`, `evidence-inventory.md`. Основные документы и код не изменены. HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.

Уточнения владельца сохранены: первоначальная подача 1000 ноликов, частичная повторная 100×число затронутых вопросов без доплат и потолка; автор платит при принятии, сетевой retry не новая попытка; вознаграждение личный уровень/10 за урок после всего цикла, включая обоснованный отказ, с пропорциональным общим пределом полученной платы и сжиганием остатка; review fee не возвращается. Проверка онлайн с серверным прогрессом и паузой. Тариф metadata-only отложен до реализации соответствующего пути и не блокирует весь PRD. Эти правила не отменяют Play refunds, отмену резервов и возврат фонда турнира.

Исходный вход: `_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/11-END-TO-END-ACCEPTANCE.md:1–72`.

## Сохранено

- Единый идентифицируемый code/backend/APK candidate, HEAD/branch/dirty/schema/config/flags/tool versions/checksum/certificate, сохранение данных/export и отсутствие «очистки для зелёного» сохранены: оригинал:7–9 → PRD:441–471,490,494–497; consolidated addendum:70,73–75.
- Полные текущие server/Kotlin/static/ciCheck/HTTP/rules/Room/UI gates, unavailable=not-run, counts/freshness/skips/исторический run отдельно и no hash=fresh PASS сохранены: PRD:469–471,496,513–515; evidence inventory:24–28,84,92–94.
- Arena money/author/reserve/replay/season/premium/tournament/large-cohort матрица, one ticket на двух устройствах максимум один settlement против двух законных different tickets, frozen provenance/escrow/ledger сохранены: оригинал:15–24 → PRD:457–467,504–509. Детализации исходной07/10матрицы перечислены в соответствующих отдельных reconciliation, а не считаются новой областью11.
- Настоящий offline prepare→start→restart/rotation/background→finish→delivery, отсутствие подходящего inventory/version, очередь120, version checks вместе с записью, response loss after commit, A→B→A/new epoch/stale cursor/late/background и обновление непустой установки без uninstall/destructive migration/legacy-as-new сохранены: оригинал:28–32 → PRD:435–451,505,509; consolidated addendum:29–39,70–74.
- Все четыре пользовательских пути, fixtures только для условий, реальная UI semantic automation/bounded waits/clearState:false/уникальные logs и UI+server proof, старые red logs, inventory прежде device roles, nine roles не serial assignments, realPlay не Redroid, ручной владелец не Maestro сохранены: оригинал:36–62 → PRD:494–515; consolidated addendum:70–75; evidence inventory:92–94.
- ACCEPTANCE по требованиям, отдельный MANUAL-CHECK, hashes/logs/redacted identities/UI/migration/limits, открытый money/data-loss/rights defect блокирует готовность: оригинал:72 → PRD:471,487–490,513–515. Нового текущего общего PASS здесь нет.

## Gaps — 2

### 1. Недостаточно точно сохранён порядок совместимости и rollback подтверждённых денег

Источник: оригинал:68. PRD:451,469 и consolidated addendum:65 сохраняют данные при migration/rollback, но не удерживают явно исходный порядок server→migrations/new client→flags→scenarios→owner→KMP→separate public и запрет восстанавливать старую БД поверх подтверждённой новой финансовой истории. Откат только lock/image без удаления данных не покрывает такой случай восстановления БД.

Минимальное исправление в technical/acceptance приложение: вернуть указанный порядок и «rollback сохраняет новую финансовую историю; старая БД не восстанавливается поверх подтверждённых денег; выключение новых продаж не выключает обработку старых оплаченных операций/возвратов». Не разрешать rollout, деньги, миграцию либо rollback сейчас.

### 2. Поздние KMP/public этапы обозначены, но часть их обязательных условий исчезла

Источник: оригинал:64–66. PRD:24,481 и consolidated addendum:66 верно оставляют перенос и публикацию отдельными разрешениями; однако не сохраняют полный checklist: перед KMP — проверить его актуальное состояние и новые пользовательские изменения, сохранить их, сделать точечный merge принятого состояния и повторить gates; перед public — фактически опубликованные versionCode, Billing/SKU/подпись, privacy/возраст/UGC/Families вместе с актуальной официальной target API проверкой.

Минимальное исправление: вернуть эти условия в раздел «после отдельного разрешения». Историческое target API36 оставить только происхождением старого плана, не текущим store/legal proof. Не получать новые store requirements сейчас и не превращать public/KMP работы в условие завершения нынешнего Android MVP до отдельной стадии.

## Решения/зависимости

Новые продуктовые вопросы не требуются. Устройства/окружение/Play/final APK/backend и ручная приёмка остаются будущими фактическими зависимостями, а не доказанно отсутствующими ресурсами. Metadata-only review tariff отложен до реализации соответствующего пути, не блокирует весь PRD. Новые принятые online review и денежные правила входят в текущую матрицу PRD:502 и не ослабляют offline Арены/обычных уроков.

## Снимок сверенных документов

- prd.md: SHA-256 8a2e57611bf0ff844999ea7b665b67320fa33a6637f67400fcfc42d50fa311d7
- addendum.md: SHA-256 c3bbdde5f371cb0a112cdafa4569176b7e5116612461b32c33dd3cc4c86745fd
- prd-consolidated-addendum.md: SHA-256 b527705569a3a8f09b83bf9f5eac0f8772e414adb3751ecbac1979702a6fe1cb
- evidence-inventory.md: SHA-256 27339c262c18bfff37f10a020f2585b9ff59500c3348339c8bfadbeb8b2a5a89
