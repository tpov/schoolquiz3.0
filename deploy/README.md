# Деплой SchoolQuiz (firebase-эмуляторы)

Два контейнера-эмулятора (auth, firestore, functions) для MVP-проверок.
Параметры сняты с живых контейнеров 24.09.2026.

- `mvp-firebase` — фоновый эмулятор MVP (без портов наружу).
- `tz06c-e2e` — стенд e2e-прогона tz06c; порты на 127.0.0.1: 19099 (auth),
  18080 (firestore), 15001 (functions). Артефакты прогона в /srv/...tz06c-full-e2e.1xsjgy.

Данные эмуляторов и инструменты (firebase-cli, jdk21) лежат в /srv/schoolquiz-mvp-* —
это исключённые каталоги (см. workspace/documents/DISK-MAP.md), в git не входят.

Запуск:  docker compose -f deploy/docker-compose.yml up -d
Остановка: docker compose -f deploy/docker-compose.yml down
Контейнеры заведены с restart=no — после перезагрузки сервера поднимать вручную.
