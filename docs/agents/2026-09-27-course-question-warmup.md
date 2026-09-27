# Отчёт: фоновый прогрев вопросов обычного урока

Дата: 2026-09-27

Стартовый HEAD: `15fedc22880d18830086be60b1ec6b4265470239`

## Изменение

При показе списка уроков курса приложение теперь начинает в фоне загрузку вопросов только для первого доступного урока. Синхронизация запускается после публикации UI-состояния, не блокирует переход в урок и не занимает общие ворота загрузкой остальных уроков курса. Когда следующий урок становится доступным, он также может быть прогрет. Обычный холодный путь загрузки в runner сохранён как запасной вариант.

## Изменённые файлы

- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponent.kt`
- `android/feature/quizzes-screen/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultQuizzesComponent.kt`
- `android/feature/quizzes-screen/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/quizzes_screen/presentation/component/DefaultLessonListComponentTest.kt`
- `_bmad-output/implementation-artifacts/spec-course-question-warmup.md`

## Проверки

- `:android:feature:quizzes-screen:presentation:testDebugUnitTest` — PASS на сервере. Новый тест использует отдельные планировщики UI и фоновой работы, проверяет немедленную навигацию и прогрев только доступного урока.
- `:apps:android-next:assembleMvp` — PASS на сервере, OpenJDK 27, Gradle 9.8, один worker, heap 2 ГБ. Изолированный Android user home задан явно для подписи тестовой сборки.
- APK SHA-256: `80c747c82e6e0116e9e381331b9293d42992c3de11d288c5c939b419df211c6f`. Сертификат: `73d39c9caeac685788d5f466dc7668e60be42445879f2883a124c32230db81d9`.
- Samsung Galaxy A14 `RF8W80MBR3Z`: APK установлен командой `adb install -r`, без очистки данных; SHA-256 установленного `base.apk` совпал с APK-кандидатом.
- Maestro 2.0.10, flow `.maestro/mvp/06-open-ordinary-lesson-uk.yaml`: все обязательные команды завершились, открыт первый вопрос, screenshot сохранён в `docs/agents/evidence/2026-09-27-course-question-warmup/`.

## Ограничения и остаток

Maestro-сценарий проходит двухвопросный smoke-урок, содержимое которого уже было закэшировано предыдущими проверками. Поэтому этот прогон подтверждает отсутствие регрессии UI и совместимость установленной сборки, но не доказывает ускорение холодного открытия конкретного пользовательского курса. Полный `ciCheck` не запускался. Следующая проверка для этой проблемы — измерить первое холодное открытие реального урока курса и повторный запуск после фонового прогрева, не очищая пользовательскую базу.

## Коммит

Коммит этого среза указан в сообщении о сдаче отчёта.
