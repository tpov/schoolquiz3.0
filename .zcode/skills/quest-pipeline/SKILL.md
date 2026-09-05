---
name: quest-pipeline
description: Контракт и конвейер школьных квестов SchoolQuiz (scripts/seed-bulk/data/school). Загружать ПЕРВЫМ при любой задаче «сгенерировать / проверить / залить квест»: формат урока, инварианты ADR-0003, id-схема, порядок блоков, гейты, этапы дизайн → авторинг → аудит → сборка → заливка.
when_to_use: Любая работа с квестами, уроками, вопросами, focus-спеками, каталогом school, сидером Firebase.
---

# Конвейер квестов SchoolQuiz

**Репозиторий: `/Users/tpov/schoolquiz3.0`.** Если сессия открыта не из него — первым делом `cd /Users/tpov/schoolquiz3.0/scripts/seed-bulk`; все пути ниже относительно `scripts/seed-bulk/`.

Рабочий каталог: `scripts/seed-bulk/` (все команды ниже — из него). Один предмет = квест: **7 разделов × 4 темы × 5 уроков × 40 вопросов = 140 уроков / 5600 вопросов**.

## Файлы предмета `data/school/<subj>/`
- `focus/<s>-<t>-<l>.json` — спека урока (дизайн). Строгий JSON: `coords{s,t,l}`, `sectionTitle`, `themeTitle`, `lessonTitle`, `focus`, `teaching_points` (5–9), `formulas` или `key_facts`, `example`, `level`.
- `lessons/<s>-<t>-<l>.js` — 40 вопросов (авторинг). См. скилл `quest-author`.
- `_helpers.js` — билдеры `sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5, buildLesson`; id-префикс `qsb-<idBase>-…`.
- `syllabus.js`, `lesson-index.json` — генерируются: `node _build-school-syllabus.js <subj>` (новый предмет — добавить в `META` этого скрипта: `id`, `idBase`, `title`).
- `data/school/quest-<subj>.js` — однострочник `require('./_build-quest')('<subj>')`. Квест получает `visibleOn:['home']`, `archived:false` → домашний экран.
- `data/school.js` — каталог `school`; квест подключается сюда **только после аудита**.

Id-схема: `qb-<idBase>` (квест), `sb-<idBase>-<s>`, `tb-<idBase>-<s>-<t>`, `lb-<idBase>-<s>-<t>-<l>`, `qsb-<idBase>-<s>-<t>-<l>-<sc|mc|ord|fb>-<e|h>-<n>`. У математики idBase `school-math-full` (плоский `school-math` занят пилотом).

## Инварианты (ADR-0003, `shared/core/question-schema/.../QuestionContent.kt`)
- SingleChoice / MultipleChoice: варианты 2..8, у mc ≥2 верных.
- Ordering: пункты 2..8, записаны в ПРАВИЛЬНОМ порядке.
- FillBlank: пропусков 1..3, кандидатов РОВНО 5 или 10, в тексте столько `___`, сколько пропусков.
- Каждый вопрос: `text`, `difficulty` EASY|HARD, `info` (пояснение), `imageUrl` (обычно null).
- Урок: ровно 40 = по 10 sc/mc/ord/fb, 20 EASY + 20 HARD; **строгий порядок индексов 0..39**: 5 sc E, 5 sc H, 5 mc E, 5 mc H, 5 ord E, 5 ord H, 5 fb E, 5 fb H.

## Факты о раннере, которые определяют правила контента
- **Раннер тасует варианты, пункты и кандидатов при показе** (`RunnerStateMapper.kt`, `shuffledForDisplay`). Поэтому: (1) выравнивать распределение ключей по позициям НЕ нужно; (2) любые позиционные ссылки в `info` («первый вариант», «два последних», «вариант б», «третий шаг», «соответственно») — ДЕФЕКТ, писать по содержанию.
- Оценка — доля верного (`Scoring.kt`): частичный зачёт за mc/ord/fb есть.
- Картинок в контенте нет — графики, чертежи, карты описываются словами.
- Язык контента: русский (вопросы и `info`), термины и формулы в оригинале.

## Гейты (обязательны, гонять до чистого)
```
node gate-lesson.js <subj> <s>-<t>        # тема: бакеты по 5, bad=0 fbbad=0 seq=0 pos=0 ERR=0 → GATE: CLEAN
node gate-lesson.js <subj> --all          # весь предмет
node format-for-firebase.js data/school/quest-<subj>.js   # ждём: 7 sections, 140 lessons, 5600 questions, ALL INVARIANTS OK
node progress.js --all                    # что уже написано
```

## Этапы
1. **Дизайн** (`quest-design`): силлабус 7×4 → согласовать с пользователем → 140 focus-спек → `_build-school-syllabus.js`.
2. **Авторинг** (`quest-author`): 1 агент = 1 тема (5 уроков); только недостающие файлы; гейт темы до чистого.
3. **Аудит** (`quest-audit`): 1 ревизор = 1 тема; содержательные дефекты; гейт до чистого.
4. **Сборка**: `format-for-firebase.js` по квесту → подключить в `data/school.js`.
5. **Заливка** (`quest-seed`): сидер с ключом из окружения, проверка по Firestore.

## Правила параллельной работы
- Временные файлы и скрипты называть уникально: `<роль>-<subj>-<s>-<t>.*` — каталог общий.
- Агент пишет файлы САМ (Write) — атомарно по уроку: файл либо целый на 40 вопросов, либо отсутствует. При обрыве перезапуск дописывает только недостающее.
- Никогда не переписывать готовый валидный урок «заодно».

## Массовые прогоны (много тем сразу)
`./run-wave.sh <audit|author> <subj> [-j N] [--all | <s>-<t> …]` — запускает N параллельных headless-сессий ZCode (по одной на тему), пишет `run/<subj>/<mode>-<s>-<t>.log` и сводку гейтов. Внутри интерактивной сессии для многих тем используй субагентов; из терминала — этот скрипт.
