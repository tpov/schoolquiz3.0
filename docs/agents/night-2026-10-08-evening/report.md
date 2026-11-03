# Отчёт смены 2026-10-08 (вечер) — night/e3-e8-inception

Смена по второй партии решений владельца 2026-10-08
(`_bmad-output/mvp-candidate/owner-decisions-2026-10-08.md`, раздел «Вторая партия»).
Ветка `night/e3-e8-inception` (продолжение `night/epic2-impl`, черновики влиты из
`night/e3-e8-drafts`). Файлы владельца не тронуты; гейты — по реестру проверок истории.

## 1. hitl 2.7/1.3/1.5 (первый приоритет владельца) — NOT RUN, device двойной проверен

`adb devices` пуст на сервере (включая рестарт adb-сервера) И на adb-сервере Mac владельца
через поднявшийся к концу смены reverse-форвард `[::1]:5037` — телефон не присоединён/не
авторизован на стороне Mac, туннель исправен. По оговорке владельца («Если adb devices пуст —
пометь и продолжай») истории помечены blocked с указанием попытки; следующий контакт —
`ADB_SERVER_SOCKET='tcp:[::1]:5037' adb devices -l`. Evidence:
`docs/agents/evidence/2026-10-08-hitl-device-attempt/`.

## 2. Инцепции эпиков 3/4/7/8 — ГОТОВЫ (36 историй, канонические tickets.toml)

Черновики трека D переведены в канонические breakdown'ы без изменения набора историй; статусы
выставлены по факту. Диспетчируемость (scope-правило эпика-2: код + JVM/Node-тесты, включая
Node-контракты против demo-backend):

- **epic-3 arena-completion (12):** все blocked; открытая цепочка — серверная половина 3.3
  (демо-фикстуры ролей/банков + прокси) → 3.11 → 3.12; ONLINE-O2-предпосылка 3.2 закрыта
  (2.5/2.6/2.9).
- **epic-4 authoring-review (10):** ready 4.1 (TZ06A верификация), 4.2 (TZ06B views), 4.4
  (UX-O6 контракт); остальные hitl/after-цепочки.
- **epic-7 commerce (8):** ready 7.7 (purchase provenance), 7.8 (TZ10B); 7.1 — внешний вход
  владельца (Play Console), блокирует только 7.6.
- **epic-8 social-features (6):** ready 8.1 (UX-O7 контракты); 8.6 ждёт TZ06B (4.2) + контракт
  UX-O10/O12 + hitl.

## 3. Реализация: 7.8 tz10b-dependency-audit — DONE

- Спека `_bmad-output/implementation-artifacts/spec-7-tz10b-dependency-audit.md`; отдельный
  dependency-only commit `7f2af2fa`, docs `37120fb4`.
- Аудит: functions 19→8 уязвимостей (оба critical websocket-driver ушли in-range; admin
  13.8.0→13.10.0, functions 7.2.5→7.4.0, google-auth 10.6.2→10.9.1), scripts 18→13
  (critical ушёл), noir — lockfile создан, 0 уязвимостей. Residual (uuid GHSA-w5hq-g745-h8pq,
  @grpc/grpc-js) чистится только мажорами — не форсировано.
- Чистые установки (`rm -rf node_modules && npm ci`) из закоммиченных lockfile'ов; гейты после
  обновления: functions 92/92 (no-env) + 92/92 (demo-backend), scripts `npm test` + `npm run
  lint` зелёные; baseline functions 92/92 зафиксирован ДО изменений. Гигиена demo: 23
  mutation_keys, 0 probe. Evidence: `docs/agents/evidence/2026-10-08-tz10b-audit/`.

## 4. Широкий гейт (решение владельца №3)

Известные красные семейства (O6 JDK-27 env, fixture pins platform:firebase ×2, detekt
jvm-target, dex-merge heap cap) не затронуты этой сменой: Node-поверхность 7.8 ими не
накрывается, остальные шаги — docs/tickets. Атрибуция close-out 963afef1 действует.

## Открытое для следующей смены

- hitl 1.3/1.5/2.7 — при появлении устройства (порядок владельца: hitl вперёд).
- Ready-очередь: 8.1 (UX-O7 контракты) и 4.4 (UX-O6 контракт) — контрактные истории; 4.2 (TZ06B
  views, серверная реализация + роль-фикстуры); 4.1 (TZ06A верификация по integration-ветке);
  7.7 (purchase provenance — крупнейшая); цепочка эпика-3 через серверную половину 3.3.
- Открытый вопрос из черновика эпика-8 (остаётся владельцу): контракт TZ06B как база авторских
  «Начислений» 8.6 — подтверждение не требуется, пока 4.2 не выполнена.
