---
phase: 08
role: backend-dev
---

# Phase 08 — Backend Tasks

## Pattern Invariants

- `firestore.rules` — только добавление `catalogs` block; существующие rules не меняются
- `allow read` только для `request.auth != null` — не публичный доступ
- `catalogs` — read-only для клиентов; write только через Firebase Admin SDK (сервер)

---

## 1. UPDATE firestore.rules — catalogs read block

- **Файл:** `firestore.rules`
- **Тип:** Firebase Security Rules update
- **Сигнатура:** добавить `match /catalogs/{catalogId} { allow read: if request.auth != null; }`
- **Вход:** существующий `firestore.rules`; найти правильное место для добавления
- **Поведение / Выход:**
  - Аутентифицированные пользователи могут читать документы из коллекции `catalogs`
  - Запись (`write`, `create`, `update`, `delete`) — не разрешена клиентам
  - Ограничение `request.auth != null` — соответствует существующей политике для `user_stats`
- **Edge cases:**
  - Если в `firestore.rules` уже есть catch-all правило `allow read, write: if false` — добавляемый rule должен быть ВЫШЕ него (rules применяются от первого совпадающего)
  - Если путь `/catalogs/{catalogId}` требует match по document ID — убедиться что `{catalogId}` — wildcard, не конкретный документ
  - Firebase deploy потребуется вручную (`firebase deploy --only firestore:rules`) — не автоматизируется в `./gradlew`
- **Depends on:** Phase 05 (`FirebaseCatalogRemoteDataSource` читает из `catalogs` collection)
- **Canonical reference:** `04-testing.md §5` (упомянуто как Firebase Rules в integration test plan), `2-grounding.md` Problem 4
- **Rationale:** без rules клиент получит `PERMISSION_DENIED` при первом `fetchAll()` в production; spec требует чтение каталогов для всех авторизованных пользователей
