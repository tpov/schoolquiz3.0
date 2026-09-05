---
description: Залить проверенные квесты в Firestore (school-quiz-89336951) и проверить по базе. Только после аудита.
argument-hint: <quest-id[,quest-id…]>
skills: quest-pipeline, quest-seed
---
Залить: $ARGUMENTS

Загрузи скилл quest-seed. Перед заливкой: инварианты каждого квеста чисты, квест подключён в `data/school.js`, ключ найден по `SCHOOL_QUIZ_SERVICE_ACCOUNT` (ищи `~/Downloads/school-quiz-89336951-firebase-adminsdk-*.json`). Выполни сидер, затем проверку по Firestore, и отчитайся числами: документов по уровням, совпадение payload выборки.
