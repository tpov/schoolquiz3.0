# Repository preparation

This is the canonical SchoolQuiz product root. `sq-merge` is a linked worktree, not another product. The separate `/srv/schoolquiz-mvp-20260906/integration` history and its seven worktrees are preserved pending an evidence-based merge; do not delete or silently select a codebase. Existing `AGENTS.md` remains authoritative. A clean checkout requires the Android SDK, JDK, Firebase test configuration, and Gradle wrapper described by the project. Production credentials and emulator state stay outside Git.

## Закрытый перенос одной папкой

На исходном сервере из корня этой папки: `python3 scripts/transfer.py check`, затем `sudo python3 scripts/transfer.py export`. В `exports/<UTC>/` появятся `manifest.json`, `state.tar.zst` и согласованные снимки SQLite; каталог имеет права 0700 и исключён из Git. Проверьте пакет командой `python3 scripts/transfer.py verify exports/<UTC>`. Копируйте всю папку продукта, включая `.git` и выбранный каталог `exports/<UTC>`, по защищённому каналу.

На новом компьютере запустите `python3 scripts/transfer.py restore exports/<UTC> /пустой/закрытый/staging`. Проверка SHA-256 и SQLite выполняется до извлечения, staging становится 0700. Затем установите зависимости и перенесите содержимое staging на целевые пути из `manifest.json` при остановленных службах. Запуск служб — отдельный этап после проверки владельцев файлов и конфигурации. Если нужны общие аккаунты или gateway, это отдельные явно названные зависимости; один боевой session нельзя запускать в двух ботах.

### Локальный Firebase-эмулятор

`deploy/docker-compose.yml` собирает собственный образ из зафиксированных
`deploy/firebase-cli/package-lock.json` и `functions/package-lock.json` и Java 21.
Все входные данные и результаты находятся в `.local/firebase` и включаются
в закрытый экспорт этой папки. Это только ручной тестовый профиль:
`docker compose -f deploy/docker-compose.yml --profile manual build`, затем
`docker compose -f deploy/docker-compose.yml --profile manual up -d` при
осознанном тестировании. Сам скрипт установки не запускает эмулятор.
Архивы историй и уникальных worktree-файлов находятся в `.local/history`;
внешний `/srv/schoolquiz-mvp-20260906/integration` после переноса не нужен
для восстановления истории этого репозитория. Ветки архивов доступны в
`refs/archive/integration/*` и `refs/archive/worktrees/*`.

### Исторические исходники и проверка Android

Основной переносимый исходник — Git этой папки. Все ветки прежнего
`/srv/schoolquiz-mvp-20260906/integration`, снимки связанных worktree и
28 ранее недостижимых commits доступны под `refs/archive/integration/*`.
Их несохранённые правки и untracked-файлы лежат в закрытом
`.local/history/worktrees-20260926`, в том числе manifest с исходными путями.
Полный прежний `.git` со всеми dangling blob/tree сохранён в
`.local/history/original-integration-git-20260926.tar.zst`. Игнорируемые файлы
внешних worktree, включая потенциально уникальные Kotlin-тесты, сохранены
в `.local/history/ignored-worktrees-20260926/*.tar.zst`; списки файлов рядом.
Исторические архивы входят в закрытый `scripts/transfer.py export` и не должны
публиковаться в Git.

Для воспроизводимой проверки основного Android-приложения установите JDK 17,
Android SDK и выполните `ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk17
./gradlew :apps:android-next:assembleDebug --no-daemon`. `local.properties`
создаётся локально при необходимости и никогда не входит в Git/образ.

Для debug Android-сборки требуется собственный
`apps/android-next/google-services.json`. Он исключён из Git, включён в
закрытый export/restore вместе с `local.properties` и должен быть восстановлен
до Gradle-сборки. После переноса обновите `sdk.dir` в `local.properties`
под новый Android SDK либо задайте `ANDROID_HOME`; не переносите чужой Firebase
проект в тестовую среду. Приватные файлы должны иметь доступ только владельца.

После `scripts/transfer.py restore` перенастройте только машинный путь SDK:
`ANDROID_HOME=/path/to/sdk python3 scripts/prepare-android-config.py`.
Скрипт сохраняет все прочие приватные параметры `local.properties`, задаёт
права 0600 и не выводит их значения. При отсутствии файла он создаёт его.
