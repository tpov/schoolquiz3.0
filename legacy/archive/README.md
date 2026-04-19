### SchoolQuiz 3.0
SchoolQuiz 3.0 is a mobile app for hosting quizzes. Users can choose from various question themes such as Geography, Space, Mathematics, and many more. The app also features a rating system and the ability to watch ads for bonuses.

![Diagram](https://github.com/tpov/schoolquiz3.0/assets/33009369/c28a62e5-76bd-458e-8bc3-bde8f59ceb8d)

#### Main Features:

- Quizzes on various topics.
- Rating system to track progress.
- Earn bonuses by watching ads.
- User profile with diverse statistics.
- Create your own quiz questions.
- Chat to interact with other users.
- Qualifications for additional features and roles.
- Automatic question translation for international users.

#### Libraries Used:

[- Userguide](https://github.com/tpov/Userguide) - Our own library for creating user guides and instructions.

#### How to Start:

Clone the repository:
```bash
git clone https://github.com/tpov/schoolquiz3.0.git
```

Navigate to the project directory:
```bash
cd schoolquiz3.0
```

(If needed) Install all required dependencies.

#### Google Play Link:
<p align="left">
  <a href="https://play.google.com/store/apps/details?id=com.tpov.schoolquiz">
    <img src="https://github-production-user-asset-6210df.s3.amazonaws.com/33009369/273196784-cb73ab71-3377-4053-8cad-4061fec9b2bd.png" width="300px" alt="schoolquiz app logo" />
  </a>
</p>

## Лицензия

Этот проект распространяется по лицензии MIT. При использовании, публикации или модификации кода обязательно указывайте источник и автора: **TPOV**.

Полный текст лицензии — в файле [LICENSE](./LICENSE).

## CI/CD и автоматизация

### Android CI
- Каждый push или pull request в ветку `main` автоматически собирает проект и запускает unit-тесты через GitHub Actions.
- Workflow: `.github/workflows/android-ci.yml`

### Firebase Functions CI/CD
- При каждом push в ветку `main` функции из папки `functions` автоматически деплоятся на сервер Firebase.
- Workflow: `.github/workflows/functions-deploy.yml`
- Для работы CI/CD нужно добавить секрет `FIREBASE_TOKEN` в GitHub (Settings → Secrets → Actions → New repository secret).

#### Как добавить секрет FIREBASE_TOKEN
1. Получи токен командой:
   ```bash
   firebase login:ci
   ```
2. Добавь его в GitHub: Settings → Secrets → Actions → New repository secret → Name: `FIREBASE_TOKEN`.

---

## Как добавить или изменить функцию Firebase
1. Создай новый файл в `functions/src/`, например, `myFunction.ts`.
2. Экспортируй функцию и добавь её в `src/index.ts`.
3. Запусти `npm run build` в папке `functions` для локальной проверки.
4. Сделай commit и push — CI/CD всё задеплоит.

---

## Best practices для open source
- Вся логика и инфраструктура вынесена в отдельные модули.
- Все временные и сгенерированные файлы игнорируются через корневой `.gitignore`.
- Документация и лицензия присутствуют.
- CI/CD автоматизирует сборку и деплой.

---
