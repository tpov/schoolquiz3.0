---
name: quest-seed
description: Сборка и заливка квеста в Firestore проекта school-quiz-89336951 — проверка инвариантов, подключение в каталог school, сидер с ключом из окружения, проверка по базе. Использовать только после аудита и явного «заливай» от пользователя.
when_to_use: «залей», «интегрируй в Firebase», «сид», «выложи квест».
---

# Заливка квеста

Предусловия: аудит темы за темой завершён; `node format-for-firebase.js data/school/quest-<subj>.js` → `7 sections, 140 lessons, 5600 questions, ALL INVARIANTS OK`; квест подключён в `data/school.js` (`require('./school/quest-<subj>')`, id уникален).

Ключ сервис-аккаунта: переменная `SCHOOL_QUIZ_SERVICE_ACCOUNT` (путь к `school-quiz-89336951-firebase-adminsdk-*.json`, обычно в `~/Downloads`). `shared.js` откажется работать, если `project_id` ключа ≠ `school-quiz-89336951` — не подставлять ключи других проектов.

```
SCHOOL_QUIZ_SERVICE_ACCOUNT=~/Downloads/school-quiz-89336951-firebase-adminsdk-<id>.json \
SEED_CATALOGS=school SEED_QUEST_IDS=qb-school-<subj>[,…] node seed-bulk-quests.js
```
Ожидаемый вывод: `[school] done: N ops | quests=… lessons=140 questions=5600`. Запись идёт через `set(..., {merge:true})` — ничего не удаляет; повторный запуск идемпотентен.

Проверка: по каждому квесту сравнить число документов `sections/themes/lessons/questions` с локальным квестом и `payload` выборочных вопросов с `JSON.stringify({...payload, id})` (сидер дописывает `id` в payload). `verify-seeded-quest.js` для школьных квестов НЕПРИГОДЕН — в нём зашит префикс курса english-tech.

Заливать ТОЛЬКО по явной команде пользователя — это внешнее действие.
