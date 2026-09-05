# ZCode — инструкции для этого репозитория

Проект: SchoolQuiz (KMP + Decompose + Compose + Koin). Общие правила кода — в корневом `AGENTS.md`.

## Контент квестов
Всё, что касается генерации, проверки и заливки квестов, живёт в `scripts/seed-bulk/` и описано скиллами:
- `quest-pipeline` — контракт и конвейер (загружать первым);
- `quest-design` → `quest-author` → `quest-audit` → `quest-seed` — этапы.

Команды: `/quest:new`, `/quest:from-material`, `/quest:audit`, `/quest:seed`, `/quest:status`.

Правила, которые нельзя нарушать:
- Силлабус нового квеста и заливка в Firebase — только после явного согласия пользователя.
- Готовые валидные уроки (40 вопросов, гейт чистый) не переписывать.
- Каждая тема закрывается гейтом `node gate-lesson.js <subj> <s>-<t>` → `GATE: CLEAN`; квест — `node format-for-firebase.js … → ALL INVARIANTS OK`.
- Параллельные субагенты: 1 = 1 тема (или 1 раздел на дизайне); временные файлы с уникальными именами.
- Ключ Firebase только через `SCHOOL_QUIZ_SERVICE_ACCOUNT`; чужие ключи не подставлять.
