# SchoolQuiz 4.0 — текущая структура проекта

> Этот файл описывает **текущее состояние репозитория**.
> Сейчас новый проект в основном представляет собой **чистый каркас модулей**: структура, Gradle-модули и `src`-деревья уже созданы, но бизнес-логика в большинстве модулей ещё не реализована.

---

## 1. Что сейчас считается активным проектом

В активный чистый проект входят только эти основные зоны:

- `apps/` — приложения-входные точки
- `shared/` — KMP/shared-часть: core + domain/data по фичам
- `android/` — Android-only presentation/UI слой
- `platform/` — адаптеры к платформенным SDK и внешним сервисам
- `server/` — серверные JVM-модули
- `docs/` — документация по архитектуре/продукту
- `infra/` — инфраструктурные заготовки
- `scripts/` — служебные скриптовые каталоги
- `legacy/` — **всё старое и всё, что не относится к чистому проекту**

`settings.gradle` сейчас подключает только новый каркас. Из приложений там остался только `:apps:android-next`.

---

## 2. Корневая структура репозитория

```text
.
├── android/                # Android-only слой: navigation, design system, userguide, presentation-модули
├── apps/                   # Точки входа приложений
├── docs/                   # Документация по продукту и архитектуре
├── infra/                  # Инфраструктурные каталоги (docker/env/firebase/github-actions)
├── legacy/                 # Весь legacy и все убранные/нечистые артефакты
├── platform/               # Адаптеры к Firebase/Billing/Telegram/Crypto/Android services
├── scripts/                # Папки под служебные скрипты
├── server/                 # Серверные JVM-модули и воркеры
├── shared/                 # Общая KMP/shared-архитектура
├── build.gradle            # Корневой Gradle build (активный)
├── settings.gradle         # Главная карта подключённых модулей
├── gradle.properties       # Gradle-настройки проекта
├── gradlew / gradlew.bat   # Gradle wrapper
├── detekt.yml              # Конфигурация Detekt
├── LICENSE                 # Лицензия
├── local.properties        # Локальные настройки SDK/машины
└── build/, .gradle/, .idea/, .vscode/, .venv/   # Технические/локальные служебные директории
```

### Важно
- `build/`, `.gradle/`, `.idea/`, `.vscode/`, `.venv/` — **не часть архитектуры продукта**, а технические каталоги среды и сборки.
- `legacy/` специально оставлен отдельно, чтобы старый код не смешивался с новым чистым проектом.

---

## 3. `apps/` — приложения

```text
apps/
└── android-next/           # Новое Android-приложение; единственная активная app-entrypoint в Gradle
```

Комментарий:
- `android-next` — будущий основной Android-клиент нового SchoolQuiz 4.0.
- `android-legacy` уже исключён из активной структуры.

---

## 4. `shared/` — общая KMP/shared-архитектура

`shared/` — это основа новой архитектуры: общие модели, контракты, домен и data-слой по фичам.

```text
shared/
├── core/
│   ├── foundation/         # Базовые shared-фундаменты/общие примитивы
│   ├── logger/             # Общий логгер для нового проекта
│   ├── model/              # Общие модели/типы
│   ├── question-schema/    # Схемы и описание типов вопросов
│   ├── persistence/        # Общие контракты/слой хранения
│   ├── network/            # Общие сетевые контракты/модели
│   ├── preferences/        # Общие настройки/preferences контракты
│   ├── sync/               # Общие синхронизационные контракты
│   └── test/               # Shared test-модуль/тестовая база
│
└── feature/
    ├── app-shell/
    │   ├── domain/         # Домен приложения: старт, shell, orchestration
    │   └── data/           # Data-слой app-shell
    │
    ├── quiz/
    │   ├── domain/         # Единый модуль квизов: catalog -> quizzes -> questions + режим редактирования
    │   └── data/
    │
    ├── local/
    │   └── settings/       # Настройки локального режима
    │       ├── domain/
    │       └── data/
    │
    ├── internet/
    │   ├── auth/           # Авторизация
    │   │   ├── domain/
    │   │   └── data/
    │   ├── profile/        # Профиль
    │   │   ├── domain/
    │   │   └── data/
    │   ├── social/         # Социальные функции
    │   │   ├── domain/
    │   │   └── data/
    │   └── leaderboard/    # Рейтинг/лидерборд
    │       ├── domain/
    │       └── data/
    │
    ├── qualification/
    │   ├── domain/         # Логика квалификаций, ролей, очередей задач и правил доступа
    │   └── data/
    │
    └── economy/
        ├── domain/         # Единый модуль экономики: кошелёк, магазин, награды
        └── data/
```

### Комментарий по `shared/`
- Это **не UI**, а общая продуктовая логика, модели и data/domain-слой.
- Почти все leaf-модули здесь оформлены как **KMP-модули**.
- Внутри них обычно есть `src/commonMain`, `src/androidMain`, `src/jvmMain`, тестовые source set'ы.

### Техническое замечание
Промежуточные каталоги в дереве сами по себе **не считаются отдельными Gradle-модулями**.
Активными считаются только leaf-модули, которые перечислены в `settings.gradle`.

---

## 5. `android/` — Android-only слой

Это слой Android UI и presentation-модулей. Он не дублирует shared domain/data, а опирается на них.

```text
android/
├── core/
│   ├── navigation/         # Android-навигация
│   ├── designsystem/       # Общая UI-система/компоненты/темизация
│   └── userguide/          # Android userguide/onboarding/guide-слой
│
└── feature/
    ├── app-shell/
    │   └── presentation/   # UI оболочки приложения
    │
    ├── quiz/
    │   └── presentation/   # Единый Android UI-модуль квизов: catalog -> quizzes -> questions + режим редактирования
    │
    ├── local/
    │   └── settings/presentation/
    │
    ├── internet/
    │   ├── auth/presentation/
    │   ├── profile/presentation/
    │   ├── social/presentation/
    │   └── leaderboard/presentation/
    │
    ├── qualification/
    │   └── presentation/   # UI квалификаций и квалификационных задач
    │
    └── economy/
        └── presentation/   # Единый UI-модуль экономики: кошелёк, магазин, награды
```

### Комментарий по `android/`
- Это Android-specific presentation-слой.
- Здесь не должна жить основная бизнес-логика — только UI, screen orchestration, Android integration.

### Техническое замечание
Промежуточные каталоги внутри `android/feature/` сами по себе **не являются отдельными Gradle-модулями**.
Активными считаются только leaf-модули, перечисленные в `settings.gradle`.

---

## 6. `platform/` — адаптеры внешних платформ

```text
platform/
├── android-services/       # Android platform services/adapters
├── firebase/               # Firebase integration adapter
├── billing/                # Billing adapter
├── crypto/                 # Crypto/token adapter
└── telegram/               # Telegram integration adapter
```

Комментарий:
- Здесь должна находиться интеграция с внешними SDK/API.
- Эти модули отделены от domain-слоя, чтобы не смешивать бизнес-правила и конкретные платформенные реализации.

---

## 7. `server/` — серверная зона

```text
server/
├── functions/              # Серверные functions
├── workers/
│   ├── sync/               # Фоновая синхронизация
│   ├── leaderboard/        # Пересчёт/обновление рейтингов
│   ├── rewards/            # Серверная выдача наград
│   ├── review-collisions/  # Разбор конфликтов ревью/коллизий
│   └── notifications/      # Фоновые уведомления
├── bot-telegram/           # Telegram bot серверная часть
├── ai-gateway/             # Шлюз к AI-сервисам
└── admin-tools/            # Админские инструменты
```

Комментарий:
- Это отдельная серверная часть внутри того же репозитория.
- Идея соответствует твоему требованию держать клиент, shared-логику и сервер рядом в одном проекте.

---

## 8. `docs/`, `infra/`, `scripts/`

### `docs/`

```text
docs/
├── agents/                 # Документация для AI/агентов
├── api/                    # API-описания
├── architecture/           # Архитектурные документы
└── product/                # Продуктовые документы
```

### `infra/`

```text
infra/
├── docker/                 # Docker-заготовки
├── env/                    # env-шаблоны/окружения
├── firebase/               # Firebase infra-конфиги/заготовки
└── github-actions/         # CI/CD заготовки
```

### `scripts/`

```text
scripts/
├── deploy/                 # Скрипты деплоя
└── dev/                    # Dev-скрипты
```

Комментарий:
- Эти каталоги относятся к обслуживанию проекта, а не к бизнес-логике приложения.

---

## 9. `legacy/` — всё старое и вынесенное из чистого проекта

```text
legacy/
├── app/                    # Старое Android-приложение
├── common/                 # Старый общий модуль
├── core/                   # Старый core
├── network/                # Старый network-модуль
├── settings/               # Старый settings-модуль
├── shop/                   # Старый shop-модуль
├── userguide/              # Старый userguide-модуль
├── log-api/                # Старый log-api
├── logger-compiler-plugin/ # Старый compiler plugin логгера
├── logger-gradle-plugin/   # Старый Gradle plugin логгера
├── logger-processor/       # Старый annotation/log processor
├── test-app/               # Старое тестовое приложение
└── archive/                # Вынесенные root-артефакты и служебный старый мусор
    ├── .github/
    ├── build-logic/
    ├── functions/
    ├── node_modules/
    └── ... другие старые файлы (README, firebase.json, package.json, html/json/csv/log и т.д.)
```

### Комментарий по `legacy/`
- Здесь лежит **всё, что не должно смешиваться с новым чистым проектом**.
- `legacy/` **не является частью новой активной архитектуры**.
- Это архивная зона для старого кода и старых артефактов, чтобы их можно было при необходимости посмотреть отдельно.

---

## 10. Что реально подключено в Gradle сейчас

По `settings.gradle.kts` в активной сборке участвуют **57 модулей**:

- `:apps:android-next` (1)
- `:shared:core:*` (9): foundation, logger, model, question-schema, persistence, network, preferences, sync, test
- `:shared:feature:*` (20): app-shell, quiz, local:settings, internet:{auth,profile,social,leaderboard}, qualification, economy, **minigame** — у каждой фичи domain + data
- `:android:core:*` (3): navigation, designsystem, userguide
- `:android:feature:*` (10): presentation-модули всех фич, включая **minigame**
- `:platform:*` (5): android-services, firebase, billing, crypto, telegram
- `:server:*` (9): functions, workers:{sync,leaderboard,rewards,review-collisions,notifications}, bot-telegram, ai-gateway, admin-tools

Не участвуют в активной сборке:
- содержимое `legacy/`
- технические каталоги вроде `.gradle/`, `build/`, `.idea/`, `.vscode/`, `.venv/`

## 10.1. Build-система

- **Gradle version catalog** — `gradle/libs.versions.toml`, единый источник версий и зависимостей.
- **`buildSrc/` с convention plugins** — 4 штуки:
  - `schoolquiz.android.application` — для `apps/*`
  - `schoolquiz.android.library` — для `android/*`, `platform/*`
  - `schoolquiz.kmp.library` — для `shared/*` (KMP: androidTarget + jvm, готово к добавлению iOS)
  - `schoolquiz.jvm.library` — для `server/*`
- **Всё на Kotlin DSL** (`.gradle.kts`). Корневой `build.gradle.kts` — минимальный, глобальных инъекций зависимостей нет.
- Каждый module-level `build.gradle.kts` = 5–10 строк: применение convention plugin + namespace + точечные зависимости.

---

## 11. Архитектурные решения (ADR)

В `docs/architecture/` зафиксированы принятые архитектурные решения:

- **ADR-0001** — карта модулей (5 зон, почему именно так)
- **ADR-0002** — KMP-стратегия (androidTarget + jvm сейчас, iOS добавляется одним плагином)
- **ADR-0003** — схема вопроса (4 типа: SingleChoice/MultipleChoice/Ordering/FillBlank; Difficulty EASY/HARD; философия «EASY = обучение, HARD = оценка»)
- **ADR-0004** — sync-контракт (offline-first, per-entity version+updatedAt, Firestore listeners + FCM + pull, оптимистичная блокировка, tombstones, retry без hard rollback)
- **ADR-0005** — жизненный цикл квеста (REGULAR/COURSE треки, параллельные ревью-флажки, автопромо по серверным константам, CompletionEffect, QuizSessionMode LEARNING/EXAM)
- **ADR-0006** — роли и квалификации (числовые уровни, 6 квалификаций + USER, серверная защита)
- **ADR-0007** — сертификаты (один курс = один сертификат, верификация через сервер, независимо от квалификаций)
- **ADR-0008** — навигация (Decompose, Single-Activity, 4 bottom-вкладки с nested stacks, drawer-per-tab, deep links)
- **ADR-0009** — dependency injection (Koin, per-feature DI модули, KMP+JVM)
- **ADR-0010** — designsystem (Compose + Material3 + брендовая палитра из legacy: `#000000/#242429/#4285F4/#FFD700`, 16dp corners, 0dp elevation, dark-only)

## 12. Краткий вывод

Текущее состояние проекта:

1. **Чистая архитектура разложена по зонам**: `apps / shared / android / platform / server`.
2. **Build-система приведена в порядок**: version catalog + buildSrc convention plugins, всё на Kotlin DSL.
3. **Legacy вынесен** в `legacy/`, не участвует в активной сборке.
4. **Большинство модулей — каркас**, ждут наполнения.
5. **Ключевые архитектурные решения задокументированы в ADR** (0001–0010). Архитектура модульности закрыта.

## 13. Если дальше продолжать работу

Архитектура модульности закрыта. Следующие шаги — наполнение кода:

1. `shared/core/model` — базовые ID/типы (value classes для QuestId, UserId и т.п.).
2. `shared/core/question-schema` согласно ADR-0003 (sealed Question + 4 типа).
3. `shared/core/sync` согласно ADR-0004 (контракт Syncable + MutationOp).
4. `shared/feature/quiz/domain` согласно ADR-0005 (Quest, QuestPhase, QuestChecks, CompletionEffect).
5. Параллельно — `shared/feature/qualification/domain` (ADR-0006) и `shared/feature/internet/profile/domain` (ADR-0007).
6. `android/core/designsystem` — Material3 тема + брендовая палитра + wrapper-компоненты (ADR-0010).
7. `shared/feature/app-shell/domain` + `android/feature/app-shell/presentation` — RootComponent и Scaffold с 4 вкладками (ADR-0008).
8. Первый vertical slice: главный экран (любая вкладка) с 1-2 реальными фичами.
9. Постепенно наполнять data-слой и presentation фич.
