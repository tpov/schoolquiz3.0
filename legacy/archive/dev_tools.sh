#!/bin/bash
set -e

# SchoolQuiz 3.0 Developer Tools
# Многофункциональный скрипт для разработки, тестирования и сборки проекта

# ASCII-арт логотип TPOV
show_logo() {
    echo -e "\033[1;36m"
    echo "  "
    echo -e "   \033[1;34m████████╗\033[1;31m██████╗  \033[1;33m██████╗ \033[1;32m██╗   ██╗"
    echo -e "   \033[1;34m╚══██╔══╝\033[1;31m██╔══██╗ \033[1;33m██╔══██╗\033[1;32m██║   ██║"
    echo -e "   \033[1;34m   ██║   \033[1;31m██████╔╝ \033[1;33m██║  ██║\033[1;32m██║   ██║"
    echo -e "   \033[1;34m   ██║   \033[1;31m██╔═══╝  \033[1;33m██║  ██║\033[1;32m╚██╗ ██╔╝"
    echo -e "   \033[1;34m   ██║   \033[1;31m██║      \033[1;33m██████╔╝\033[1;32m ╚████╔╝ "
    echo -e "   \033[1;34m   ╚═╝   \033[1;31m╚═╝      \033[1;33m╚═════╝ \033[1;32m  ╚═══╝  "
    echo "  "
    echo -e "\033[0m"
}

# Приветствие
show_welcome() {
    clear
    show_logo
    echo -e "\033[1;32m"
    echo "======================================================="
    echo "        SchoolQuiz 3.0 Developer Tools v1.0            "
    echo "======================================================="
    echo -e "\033[0m"
    echo "Добро пожаловать в инструментарий разработчика SchoolQuiz 3.0!"
    echo "Этот скрипт поможет вам с настройкой среды, сборкой и тестированием."
    echo
    echo "Нажмите Enter для продолжения..."
    read -r
}

# Сообщение об успешной сборке
show_success_message() {
    local build_type="$1"

    echo -e "\033[1;32m"
    echo "======================================================="
    echo "                  СБОРКА УСПЕШНА!                      "
    echo "======================================================="
    echo -e "\033[0m"

    # Случайное мотивирующее сообщение
    local messages=(
        "Отличная работа! Ваша $build_type версия готова к покорению мира!"
        "Успех! $build_type версия собрана без единой ошибки. Вы великолепны!"
        "Сборка завершена успешно! Ваш код работает как швейцарские часы."
        "Поздравляем! $build_type версия собрана. Тестировщики будут в восторге!"
        "Успех! Вы только что создали новую прекрасную $build_type версию SchoolQuiz!"
    )

    # Выбираем случайное сообщение
    local random_index=$((RANDOM % ${#messages[@]}))
    echo "${messages[$random_index]}"
    echo
    show_logo
}

# Сообщение о неудачной сборке
show_failure_message() {
    local error_code="$1"

    echo -e "\033[1;31m"
    echo "======================================================="
    echo "                  СБОРКА НЕ УДАЛАСЬ                    "
    echo "                  Код ошибки: $error_code              "
    echo "======================================================="
    echo -e "\033[0m"

    # Случайное мотивирующее сообщение при ошибке
    local messages=(
        "Не отчаивайтесь! Каждая ошибка - это шаг к совершенству."
        "Ошибки - это часть процесса. Великие разработчики никогда не сдаются!"
        "Временная неудача. Проанализируйте ошибки и попробуйте снова."
        "Даже лучшие разработчики сталкиваются с ошибками. Это нормально!"
        "Не получилось? Отлично! Теперь вы знаете, как НЕ надо делать."
    )

    # Выбираем случайное сообщение
    local random_index=$((RANDOM % ${#messages[@]}))
    echo "${messages[$random_index]}"
    echo
    echo "Совет: Проверьте лог ошибок и используйте пункт меню \"Анализ отчета об ошибках\"."
    echo
}

# Установка недостающих инструментов
install_dependencies() {
    echo "Установка недостающих зависимостей..."
    OS=$(uname -s 2>/dev/null || echo "Windows")

    # Проверка и установка Java
    if ! command -v java &> /dev/null; then
        echo "Java не найдена. Установка..."

        if [ "$OS" = "Darwin" ]; then  # macOS
            if command -v brew &> /dev/null; then
                brew install --cask adoptopenjdk
            else
                echo "Homebrew не найден. Установка Homebrew..."
                /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
                brew install --cask adoptopenjdk
            fi
        elif [ "$OS" = "Linux" ]; then  # Linux
            if command -v apt-get &> /dev/null; then
                sudo apt-get update
                sudo apt-get install -y openjdk-17-jdk
            elif command -v dnf &> /dev/null; then
                sudo dnf install -y java-17-openjdk
            else
                echo "Не удалось установить Java. Пожалуйста, установите Java вручную."
                return 1
            fi
        elif [[ "$OS" == MINGW* ]] || [[ "$OS" == MSYS* ]] || [ "$OS" = "Windows" ]; then  # Windows
            echo "На Windows рекомендуется установить Java через официальный установщик."
            echo "Посетите https://adoptium.net/ для загрузки и установки JDK."
            echo "После установки перезапустите скрипт."
            return 1
        else
            echo "Неподдерживаемая операционная система. Пожалуйста, установите Java вручную."
            return 1
        fi
    fi

    # Проверка и установка Node.js (для Firebase)
    if ! command -v node &> /dev/null; then
        echo "Node.js не найден. Установка..."

        if [ "$OS" = "Darwin" ]; then  # macOS
            if command -v brew &> /dev/null; then
                brew install node
            else
                echo "Homebrew не найден. Node.js не был установлен."
                echo "Сборка продолжится без поддержки Firebase функций."
            fi
        elif [ "$OS" = "Linux" ]; then  # Linux
            if command -v apt-get &> /dev/null; then
                curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
                sudo apt-get install -y nodejs
            elif command -v dnf &> /dev/null; then
                curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
                sudo dnf install -y nodejs
            else
                echo "Не удалось установить Node.js. Firebase функции не будут доступны."
            fi
        elif [[ "$OS" == MINGW* ]] || [[ "$OS" == MSYS* ]] || [ "$OS" = "Windows" ]; then  # Windows
            echo "На Windows рекомендуется установить Node.js через официальный установщик."
            echo "Посетите https://nodejs.org/ для загрузки и установки Node.js."
            echo "После установки перезапустите скрипт."
        else
            echo "Неподдерживаемая операционная система для автоматической установки Node.js."
        fi
    fi

    # Проверка и установка ADB (Android Debug Bridge)
    if ! command -v adb &> /dev/null; then
        echo "ADB не найден. Установка Android platform-tools..."

        if [ "$OS" = "Darwin" ]; then  # macOS
            if command -v brew &> /dev/null; then
                brew install --cask android-platform-tools
            else
                echo "Homebrew не найден. ADB не был установлен."
                echo "Вы не сможете автоматически устанавливать APK на устройства."
            fi
        elif [ "$OS" = "Linux" ]; then  # Linux
            if command -v apt-get &> /dev/null; then
                sudo apt-get update
                sudo apt-get install -y android-tools-adb
            elif command -v dnf &> /dev/null; then
                sudo dnf install -y android-tools
            else
                echo "Не удалось установить ADB. Пожалуйста, установите Android platform-tools вручную."
            fi
        elif [[ "$OS" == MINGW* ]] || [[ "$OS" == MSYS* ]] || [ "$OS" = "Windows" ]; then  # Windows
            echo "На Windows рекомендуется установить Android SDK Platform Tools через Android Studio."
            echo "Посетите https://developer.android.com/studio для загрузки Android Studio."
            echo "Или скачайте platform-tools отдельно: https://developer.android.com/tools/releases/platform-tools"
        else
            echo "Неподдерживаемая операционная система для автоматической установки ADB."
        fi
    fi

    echo "Установка зависимостей завершена."
    return 0
}

# Проверка необходимых инструментов
check_dependencies() {
    echo "Проверка наличия необходимых инструментов..."
    MISSING_DEPS=false

    # Проверка Gradle
    if ! command -v ./gradlew &> /dev/null; then
        if [ ! -f "./gradlew" ]; then
            echo "ОШИБКА: Gradle wrapper (gradlew) не найден!"
            return 1
        fi
    fi

    # Проверка Java
    if ! command -v java &> /dev/null; then
        echo "ПРЕДУПРЕЖДЕНИЕ: Java не найдена."
        MISSING_DEPS=true
    fi

    # Проверка Node.js (для Firebase)
    if ! command -v node &> /dev/null; then
        echo "ПРЕДУПРЕЖДЕНИЕ: Node.js не найден. Firebase функции не будут доступны."
        HAS_NODE=false
        MISSING_DEPS=true
    else
        HAS_NODE=true
    fi

    # Проверка ADB
    if ! command -v adb &> /dev/null; then
        echo "ПРЕДУПРЕЖДЕНИЕ: ADB (Android Debug Bridge) не найден. Установка приложения на устройство будет недоступна."
        HAS_ADB=false
        MISSING_DEPS=true
    else
        HAS_ADB=true
    fi

    # Если есть отсутствующие зависимости, предлагаем их установить
    if [ "$MISSING_DEPS" = true ]; then
        echo "Обнаружены отсутствующие зависимости. Хотите установить их автоматически? [y/N]"
        read -r answer
        if [[ "$answer" =~ ^[Yy]$ ]]; then
            install_dependencies
            # После установки повторно проверяем зависимости
            if ! command -v node &> /dev/null; then
                HAS_NODE=false
            else
                HAS_NODE=true
            fi
            if ! command -v adb &> /dev/null; then
                HAS_ADB=false
            else
                HAS_ADB=true
            fi
        fi
    fi

    return 0
}

# Проверка наличия необходимых ключей и сертификатов
check_keys() {
    echo "Проверка наличия ключей и сертификатов..."

    # Если keystore файл отсутствует
    if [ ! -f "./app/keystore.jks" ] && [ ! -f "$HOME/.android/debug.keystore" ]; then
        echo "ПРЕДУПРЕЖДЕНИЕ: Файл keystore не найден. Будет использована мок-версия."
        HAS_KEYS=false
    else
        HAS_KEYS=true
    fi

    # Проверка Firebase конфигурации
    if [ ! -f "./app/google-services.json" ]; then
        echo "ПРЕДУПРЕЖДЕНИЕ: Firebase конфигурация не найдена. Будет использована мок-версия."
        HAS_FIREBASE_CONFIG=false
    else
        HAS_FIREBASE_CONFIG=true
    fi

    return 0
}

# Проверка наличия локально опубликованного логгер-плагина
check_logger_plugin() {
    echo "Проверка наличия и актуальности логгер-плагина..."
    
    # Проверяем наличие директории плагина
    if [ ! -d "logger-gradle-plugin" ]; then
        echo "ОШИБКА: Директория логгер-плагина не найдена!"
        return 1
    fi

    # Извлекаем текущую версию плагина из файла build.gradle.kts
    local source_version=""
    if [ -f "logger-gradle-plugin/build.gradle.kts" ]; then
        source_version=$(grep -F "version = " "logger-gradle-plugin/build.gradle.kts" | sed -e 's/version = "\(.*\)"/\1/')
    else
        echo "ОШИБКА: Файл build.gradle.kts логгер-плагина не найден!"
        return 1
    fi
    
    if [ -z "$source_version" ]; then
        echo "ОШИБКА: Не удалось определить версию логгер-плагина!"
        return 1
    fi
    
    echo "Версия логгер-плагина в исходном коде: $source_version"
    
    # Проверяем наличие в локальном maven репозитории
    local plugin_path="$HOME/.m2/repository/com/tpov/logger/logger-gradle-plugin"
    if [ ! -d "$plugin_path" ]; then
        echo "Логгер-плагин не найден в локальном репозитории. Требуется публикация."
        return 1
    fi
    
    # Проверяем, есть ли директория с текущей версией
    local version_path="$plugin_path/$source_version"
    if [ ! -d "$version_path" ]; then
        echo "Версия $source_version не найдена в локальном репозитории. Требуется публикация."
        return 1
    fi
    
    # Дополнительная проверка файла pom, чтобы убедиться, что публикация завершена
    if [ ! -f "$version_path/logger-gradle-plugin-$source_version.pom" ]; then
        echo "Файл POM для версии $source_version не найден. Требуется публикация."
        return 1
    fi
    
    echo "Логгер-плагин версии $source_version найден в локальном репозитории."
    echo "Плагин актуален, повторная публикация не требуется."
    return 0
}

# Настройка среды разработки
setup_environment() {
    echo "Настройка среды разработки..."

    # Очистка проекта
    ./gradlew clean

    # Публикация логгер-плагина локально, если его нет
    if ! check_logger_plugin; then
        ./gradlew :logger-gradle-plugin:publishToMavenLocal
        if [ $? -ne 0 ]; then
            echo "ОШИБКА: Не удалось опубликовать логгер-плагин!"
            return 1
        fi
    fi

    # Установка зависимостей Firebase функций
    if [ "$HAS_NODE" = true ] && [ -d "./functions" ]; then
        echo "Установка зависимостей Firebase функций..."
        (cd functions && npm install)
    fi

    echo "Среда разработки настроена успешно!"
    return 0
}

# Определение типа сборки на основе наличия ключей
determine_build_type() {
    if [ "$HAS_KEYS" = true ]; then
        echo "release"
    else
        echo "debug"
        echo "ПРЕДУПРЕЖДЕНИЕ: Отсутствуют ключи подписи. Будет создана debug-версия вместо release."
    fi
}

# Установка приложения на устройство
install_app_to_device() {
    local build_type=$1
    local package_name="com.tpov.schoolquiz"
    local main_activity=".ui.MainActivity"

    echo "Установка приложения на устройство..."

    # Проверка подключенных устройств
    local devices=$(adb devices | grep -v "List" | grep "device" | wc -l)
    if [ "$devices" -eq 0 ]; then
        echo "ОШИБКА: Нет подключенных Android устройств."
        return 1
    fi

    # Установка APK или AAB в зависимости от типа сборки
    if [ "$build_type" = "debug" ]; then
        ./gradlew :app:installDebug

        if [ $? -eq 0 ]; then
            echo "Запуск приложения..."
            adb shell am start -n "$package_name/$main_activity"
        else
            echo "ОШИБКА: Не удалось установить приложение."
            return 1
        fi
    else
        echo "Для установки Release-версии используйте сгенерированный AAB файл через Google Play Console"
        echo "или конвертируйте AAB в APK с помощью bundletool:"
        echo "java -jar bundletool.jar build-apks --bundle=app/build/outputs/bundle/release/app-release.aab --output=app-release.apks --mode=universal"
        echo "После конвертации можно установить APK командой:"
        echo "adb install app-release.apks"
    fi

    return 0
}

# Создание отчета о сборке
generate_build_report() {
    local build_type="$1"
    local mode="$2" # tester или developer
    local success="$3"
    local report_file="/tmp/build_report_$(date +%Y%m%d_%H%M%S).txt"

    echo "Создание отчета о сборке..."

    # Заголовок отчета
    cat > "$report_file" << EOF
============================================================
    ОТЧЕТ О СБОРКЕ SCHOOLQUIZ 3.0
    Дата: $(date "+%d.%m.%Y %H:%M:%S")
    Тип сборки: $build_type
    Режим: $mode
    Результат: $([ "$success" = true ] && echo "УСПЕШНО" || echo "НЕУДАЧНО")
============================================================

EOF

    # Информация о системе
    {
        echo "ИНФОРМАЦИЯ О СИСТЕМЕ"
        echo "------------------------------------------------------------"
        echo "Операционная система: $(uname -s) $(uname -r)"
        echo "Архитектура: $(uname -m)"
        if command -v java &> /dev/null; then
            echo "Версия Java: $(java -version 2>&1 | head -n 1)"
        else
            echo "Java: не установлена"
        fi
        if command -v node &> /dev/null; then
            echo "Версия Node.js: $(node --version)"
        else
            echo "Node.js: не установлен"
        fi
        echo "Версия Gradle: $(./gradlew --version | grep Gradle | head -n 1)"
        echo
    } >> "$report_file"

    # Параметры сборки и результаты
    {
        echo "ПАРАМЕТРЫ И РЕЗУЛЬТАТЫ СБОРКИ"
        echo "------------------------------------------------------------"
        echo "Режим сборки: $mode"
        echo "Тип сборки: $build_type"
        echo "Проверки качества кода: $([ "$mode" = "developer" ] && echo "Выполнены" || echo "Пропущены")"
        echo "Тесты: $([ "$mode" = "developer" ] && echo "Запущены" || echo "Пропущены")"
        echo "Результат: $([ "$success" = true ] && echo "УСПЕШНО" || echo "НЕУДАЧНО")"
        echo
    } >> "$report_file"

    # Результаты тестов (только для разработчика и если доступны)
    if [ "$mode" = "developer" ] && [ -d "app/build/reports/tests" ]; then
        {
            echo "РЕЗУЛЬТАТЫ ТЕСТОВ"
            echo "------------------------------------------------------------"

            # Поиск отчетов о результатах тестов
            find app/build/reports/tests -name "index.html" -type f | while read -r test_report; do
                test_dir=$(dirname "$test_report")
                # Парсинг HTML-отчета тестов для извлечения базовых результатов
                if command -v grep &> /dev/null; then
                    tests_count=$(grep -F "tests=" "$test_report" | sed -E 's/.*tests="([0-9]+)".*/\1/')
                    failures=$(grep -F "failures=" "$test_report" | sed -E 's/.*failures="([0-9]+)".*/\1/')
                    
                    echo "Набор тестов: $(basename "$test_dir")"
                    if [ -n "$tests_count" ] && [ -n "$failures" ]; then
                        echo "  Всего тестов: $tests_count"
                        echo "  Неудачных тестов: $failures"
                        echo "  Успешных тестов: $((tests_count - failures))"
                        
                        # Если есть ошибки, собираем информацию об упавших тестах
                        if [ "$failures" -gt 0 ]; then
                            # Извлекаем информацию о неудачных тестах из HTML
                            local failed_tests=$(grep -A 5 "failureName" "$test_report")
                            
                            # Преобразуем в кликабельные ссылки и добавляем в отчет
                            echo "  Детали неудачных тестов:" 
                            echo "$failed_tests" | while IFS= read -r line; do
                                # Упрощенное извлечение для каждого расширения
                                for ext in ".kt" ".java"; do
                                    if echo "$line" | grep -F "$ext:" > /dev/null; then
                                        # Извлекаем путь к файлу и номер строки
                                        file_path=$(echo "$line" | sed -E "s/^.*([^ ]+$ext):.*/\1/")
                                        line_num=$(echo "$line" | sed -E "s/^.*$file_path:([0-9]+).*/\1/")
                                        
                                        if [ -n "$file_path" ] && [ -n "$line_num" ]; then
                                            # Создаем кликабельную ссылку для Android Studio с скрытым URL
                                            full_path="$(pwd)/$file_path"
                                            rel_path=$(echo "$file_path" | sed "s|^.*/||g")
                                            echo -en "    \033[8mfile://$full_path:$line_num\033[0m"
                                            echo -e "\033[1;36m$rel_path:$line_num\033[0m"
                                        fi
                                    fi
                                done
                            done
                        fi
                    else
                        echo "  Результаты недоступны"
                    fi
                else
                    echo "Детальная информация о тестах недоступна (grep не найден)"
                fi
            done
            
            echo
        } >> "$report_file"
    fi

    # Список артефактов сборки
    {
        echo "РЕЗУЛЬТАТЫ СБОРКИ"
        echo "------------------------------------------------------------"

        if [ "$build_type" = "debug" ]; then
            echo "Созданные APK файлы:"
            find app/build/outputs -name "*.apk" -type f | while read -r apk; do
                echo "  - $apk ($(du -h "$apk" | awk '{print $1}'))"
            done
        else
            echo "Созданные AAB файлы:"
            find app/build/outputs -name "*.aab" -type f | while read -r aab; do
                echo "  - $aab ($(du -h "$aab" | awk '{print $1}'))"
            done
        fi

    } >> "$report_file"

    # Автоматически открываем отчет только при неудачной сборке
    if [ "$success" = false ]; then
        echo -e "\033[1;33mСборка завершилась с ошибками. Вот ссылки на проблемные файлы:\033[0m"
        
        # Показываем ссылки на первые ошибки для быстрого доступа
        echo -e "\n\033[1;36mКликабельные ссылки на ошибки (работают прямо в терминале Android Studio):\033[0m"
        
        # Выводим до 5 ошибок с сокращенными путями
        local error_links=()
        WORKSPACE_PATH="$(pwd)"
        
        # Собираем ссылки на ошибки из отчета
        while IFS= read -r line; do
            if echo "$line" | grep -F "file://" > /dev/null; then
                local file_url=$(echo "$line" | grep -o 'file://[^ ]*')
                local file_path=$(echo "$file_url" | sed 's|file://||')
                local rel_path=$(echo "$file_path" | sed "s|$WORKSPACE_PATH/||")
                local line_num=$(echo "$file_url" | grep -o ':[0-9]*$' | sed 's/://')
                
                if [ -n "$file_path" ] && [ -n "$line_num" ]; then
                    # Составляем краткое описание ошибки
                    local error_desc=""
                    if echo "$line" | grep -o "\[.*\]" > /dev/null; then
                        error_desc=$(echo "$line" | grep -o "\[.*\]")
                    fi
                    
                    # Форматирование с скрытием полного URL
                    error_links+=("\033[8m$file_url\033[0m\033[1;36m${rel_path}:${line_num}\033[0m $error_desc")
                fi
            fi
        done < "$report_file"
        
        # Выводим до 5 ссылок
        for ((i=0; i<${#error_links[@]} && i<5; i++)); do
            echo -e "   ${error_links[$i]}"
        done
    fi

    # Выводим сводку результатов
    local errors_count=$(grep -c "file://" "$report_file")
    if [ "$success" = false ] && [ "$errors_count" -gt 0 ]; then
        echo -e "\n\033[1;33mВсего обнаружено ошибок: $errors_count\033[0m"
    fi

    # Удаляем временный файл отчета
    rm -f "$report_file"
    
    return 0
}

# Функция для запуска команд с компактным выводом
run_with_compact_output() {
    local cmd="$1"
    local description="$2"
    local log_file="/tmp/log_$(date +%s).txt"
    local status_file="/tmp/status_$(date +%s).txt"
    
    echo -e "\033[1;34m➔ $description\033[0m"
    echo "   Запуск: $cmd"
    echo "   Выполняется..."
    
    # Запускаем команду и сохраняем вывод в файл,
    # одновременно отслеживая прогресс для отображения индикатора
    (eval "$cmd" > "$log_file" 2>&1; echo $? > "$status_file") &
    local pid=$!
    
    # Показываем анимированный индикатор выполнения
    local chars=( "⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏" )
    local i=0
    local last_line=""
    while kill -0 $pid 2> /dev/null; do
        char="${chars[$i]}"
        i=$(( (i+1) % ${#chars[@]} ))
        
        # Получаем последнюю информативную строку из лога
        if [ -f "$log_file" ]; then
            new_last_line=$(tail -1 "$log_file" | grep -v "^$" | cut -c 1-60)
            if [ -n "$new_last_line" ]; then
                last_line="$new_last_line"
                if [ ${#last_line} -gt 60 ]; then
                    last_line="${last_line:0:57}..."
                fi
            fi
        fi
        
        echo -ne "\r   $char $last_line                                       "
        sleep 0.1
    done
    
    # Очищаем строку с индикатором
    echo -ne "\r                                                                               \r"
    
    # Получаем статус завершения
    local status=$(cat "$status_file")
    
    # Показываем результат
    if [ "$status" = "0" ]; then
        echo -e "   \033[1;32m✓ Выполнено успешно\033[0m"
        
        # При успешном выполнении просто удаляем временный лог
        rm -f "$log_file" "$status_file"
    else
        # При неудаче обрабатываем ошибки
        WORKSPACE_PATH="$(pwd)"
        
        # Вывод ошибок с более чистым форматированием
        echo -e "   \033[1;31m✗ Ошибка (код $status)\033[0m"
        echo -e "   Последние строки лога:"
        tail -n 5 "$log_file" | sed 's/^/   | /'
        
        # Выводим ссылки на ошибки прямо в терминал для Android Studio
        echo -e "   \033[1;32mСсылки на ошибки (кликабельные в Android Studio):\033[0m"
        
        # Показываем ссылки на первые 3 ошибки с сокращенными путями для наглядности
        local error_count=0
        cat "$log_file" | while IFS= read -r line; do
            # Поиск файлов с ошибками
            for ext in ".kt" ".java" ".xml" ".gradle"; do
                if echo "$line" | grep -F "$ext:" > /dev/null; then
                    # Извлекаем путь к файлу
                    local file_path=""
                    
                    # Пытаемся найти абсолютный путь
                    if echo "$line" | grep -F "$WORKSPACE_PATH" > /dev/null; then
                        file_path=$(echo "$line" | grep -o "$WORKSPACE_PATH[^:]*$ext")
                    else
                        # Иначе пробуем найти относительный путь
                        file_path=$(echo "$line" | grep -o "[^ :]*$ext")
                        if [ -n "$file_path" ] && [ -f "$file_path" ]; then
                            file_path="$WORKSPACE_PATH/$file_path"
                        fi
                    fi
                    
                    if [ -n "$file_path" ] && [ -f "$file_path" ]; then
                        # Извлекаем номер строки
                        line_num=$(echo "$line" | sed -E "s/^.*$ext:([0-9]+).*/\1/")
                        if [ -n "$line_num" ]; then
                            # Получаем относительный путь для отображения
                            rel_path=$(echo "$file_path" | sed "s|$WORKSPACE_PATH/||")
                            
                            # Извлекаем короткое описание ошибки
                            error_desc=""
                            if [[ "$line" =~ \[.*\] ]]; then
                                error_desc=$(echo "$line" | grep -o '\[.*\]')
                            else
                                error_desc=$(echo "$line" | grep -o ':.*' | cut -c 2- | cut -c -40)
                                if [ ${#error_desc} -gt 40 ]; then
                                    error_desc="${error_desc}..."
                                fi
                            fi
                            
                            # Форматирование цветных ссылок с скрытием полного URL
                            # Используем ANSI escape sequence для скрытия URL (сделать его прозрачным)
                            # \033[8m делает текст "невидимым" (тот же цвет что и фон)
                            echo -en "   \033[8mfile://$file_path:$line_num\033[0m"
                            echo -e "\033[1;36m${rel_path}:${line_num}\033[0m - $error_desc"
                            
                            # Ограничиваем до 3 ошибок
                            ((error_count++))
                            if [ "$error_count" -ge 3 ]; then
                                break 2
                            fi
                        fi
                    fi
                fi
            done
        done
        
        # Удаляем временные файлы
        rm -f "$log_file" "$status_file"
    fi
    
    return $status
}

# Групповой запуск команд с компактным выводом
run_build_sequence() {
    local description="$1"
    local build_type="$2"
    local mode="$3"
    local success=true
    
    echo -e "\033[1;36m╔═════════════════════════════════════════════════════════╗\033[0m"
    echo -e "\033[1;36m║ $description\033[0m"
    echo -e "\033[1;36m╚═════════════════════════════════════════════════════════╝\033[0m"
    
    # Очистка проекта
    run_with_compact_output "./gradlew clean" "Очистка проекта" || success=false
    
    # Публикация логгер-плагина, если его нет
    if ! check_logger_plugin; then
        run_with_compact_output "./gradlew :logger-gradle-plugin:publishToMavenLocal" "Публикация логгер-плагина" || success=false
    fi
    
    # Для режима разработчика запускаем проверки качества кода и тесты
    if [ "$mode" = "developer" ]; then
        # Проверка качества кода
        local lint_result=0
        run_with_compact_output "./gradlew detekt ktlintCheck" "Проверка качества кода" || lint_result=$?
        
        if [ $lint_result -ne 0 ]; then
            echo -e "\033[1;33mОбнаружены проблемы с качеством кода. Продолжить сборку? [y/N]\033[0m"
            read -n 1 -s answer
            echo
            if [[ ! "$answer" =~ ^[Yy]$ ]]; then
                echo -e "\033[1;31mСборка отменена.\033[0m"
                show_failure_message $lint_result
                generate_build_report "$build_type" "$mode" false
                return 1
            fi
        fi
        
        # Запуск тестов
        local test_result=0
        run_with_compact_output "./gradlew test" "Запуск тестов" || test_result=$?
        
        if [ $test_result -ne 0 ]; then
            echo -e "\033[1;33mНе все тесты прошли успешно. Продолжить сборку? [y/N]\033[0m"
            read -n 1 -s answer
            echo
            if [[ ! "$answer" =~ ^[Yy]$ ]]; then
                echo -e "\033[1;31mСборка отменена.\033[0m"
                show_failure_message $test_result
                generate_build_report "$build_type" "$mode" false
                return 1
            fi
        fi
        
        # Для разработчика также собираем Firebase функции
        if [ "$HAS_NODE" = true ] && [ -d "./functions" ]; then
            run_with_compact_output "(cd functions && npm install)" "Установка зависимостей Firebase" || true
            run_with_compact_output "(cd functions && npm run build)" "Сборка Firebase функций" || true
        fi
    fi
    
    # Определяем какую команду сборки запускать
    local build_command=""
    if [ "$build_type" = "debug" ]; then
        build_command="./gradlew :app:assembleDebug"
    else
        build_command="./gradlew :app:bundleRelease"
    fi
    
    # Запускаем сборку
    local build_result=0
    run_with_compact_output "$build_command" "Сборка $build_type версии приложения" || build_result=$?
    
    if [ $build_result -ne 0 ]; then
        success=false
        show_failure_message $build_result
    else
        # Показываем результаты сборки
        echo -e "\033[1;32m✓ $build_type версия собрана успешно\033[0m"
        if [ "$build_type" = "debug" ]; then
            echo -e "\033[1;32m  Созданные APK файлы:\033[0m"
            find app/build/outputs -name "*.apk" -type f | while read -r apk; do
                echo "  - $apk ($(du -h "$apk" | awk '{print $1}'))"
            done
        else
            echo -e "\033[1;32m  Созданные AAB файлы:\033[0m"
            find app/build/outputs -name "*.aab" -type f | while read -r aab; do
                echo "  - $aab ($(du -h "$aab" | awk '{print $1}'))"
            done
        fi
    fi
    
    return $build_result
}

# Сборка проекта для тестировщика
build_for_tester() {
    echo "Сборка проекта для тестировщика..."
    echo "Этот режим пропускает проверки качества кода и публикацию Firebase."
    
    local start_time=$(date +%s)
    
    # Создание мок-конфигурации Firebase, если она отсутствует
    if [ ! -f "./app/google-services.json" ]; then
        echo "Firebase конфигурация не найдена. Создание мок-версии..."
        create_mock_config
    fi
    
    # Определение типа сборки на основе наличия ключей
    local build_type=$(determine_build_type)
    
    # Устанавливаем автоматическую публикацию плагина при необходимости
    local publish_plugin=false
    if ! check_logger_plugin; then
        publish_plugin=true
        echo "Логгер-плагин отсутствует или устарел, будет выполнена автоматическая публикация."
    fi
    
    # Запускаем сборку с компактным выводом логов
    local success=true
    
    # Очистка проекта
    run_with_compact_output "./gradlew clean" "Очистка проекта" || success=false
    
    # Публикация логгер-плагина, если его нет или версия изменилась
    if [ "$publish_plugin" = true ]; then
        run_with_compact_output "./gradlew :logger-gradle-plugin:publishToMavenLocal" "Публикация логгер-плагина" || success=false
    fi
    
    # Определяем какую команду сборки запускать
    local build_command=""
    if [ "$build_type" = "debug" ]; then
        build_command="./gradlew :app:assembleDebug"
    else
        build_command="./gradlew :app:bundleRelease"
    fi
    
    # Запускаем сборку
    local build_result=0
    run_with_compact_output "$build_command" "Сборка $build_type версии приложения" || build_result=$?
    
    if [ $build_result -ne 0 ]; then
        success=false
        show_failure_message $build_result
    else
        # Показываем результаты сборки
        echo -e "\033[1;32m✓ $build_type версия собрана успешно\033[0m"
        if [ "$build_type" = "debug" ]; then
            echo -e "\033[1;32m  Созданные APK файлы:\033[0m"
            find app/build/outputs -name "*.apk" -type f | while read -r apk; do
                echo "  - $apk ($(du -h "$apk" | awk '{print $1}'))"
            done
        else
            echo -e "\033[1;32m  Созданные AAB файлы:\033[0m"
            find app/build/outputs -name "*.aab" -type f | while read -r aab; do
                echo "  - $aab ($(du -h "$aab" | awk '{print $1}'))"
            done
        fi
    fi
    
    local end_time=$(date +%s)
    local build_duration=$((end_time - start_time))
    echo "Сборка для тестировщика завершена за $(($build_duration / 60)) минут и $(($build_duration % 60)) секунд."
    
    # Создание отчета о сборке
    generate_build_report "$build_type" "tester" "$success"
    
    if [ "$success" = true ]; then
        show_success_message "$build_type"
        
        if [ "$HAS_ADB" = true ]; then
            echo "Хотите установить приложение на подключенное устройство? [y/N]"
            read -r answer
            if [[ "$answer" =~ ^[Yy]$ ]]; then
                install_app_to_device "$build_type"
            fi
        fi
    fi
    
    return 0
}

# Полная сборка проекта для разработчика
build_for_developer() {
    echo "Полная сборка проекта для разработчика..."
    echo "Этот режим включает все проверки кода и тесты."
    
    local start_time=$(date +%s)
    
    # Очистка временных файлов
    cleanup_temp_files
    
    # Определение типа сборки на основе наличия ключей
    local build_type=$(determine_build_type)
    
    # Устанавливаем автоматическую публикацию плагина при необходимости
    local publish_plugin=false
    if ! check_logger_plugin; then
        publish_plugin=true
        echo "Логгер-плагин отсутствует или устарел, будет выполнена автоматическая публикация."
    fi
    
    # Спрашиваем о развертывании Firebase функций
    local deploy_firebase=false
    if [ "$HAS_NODE" = true ] && [ -d "./functions" ]; then
        echo -e "\033[1;33mВопрос: Разворачивать ли Firebase функции после сборки? [y/N]\033[0m"
        read -r answer
        if [[ "$answer" =~ ^[Yy]$ ]]; then
            deploy_firebase=true
            echo "Firebase функции будут развернуты после сборки."
            
            # Предупреждение для релизной сборки
            if [ "$build_type" = "release" ]; then
                echo -e "\033[1;31mПРЕДУПРЕЖДЕНИЕ!\033[0m Вы собираетесь развернуть Firebase функции при релизной сборке!"
                echo "Это может повлиять на рабочую среду. Рекомендуется использовать отдельный проект Firebase для тестирования."
                echo -e "\033[1;31mПродолжить? [y/N]\033[0m"
                read -r confirm
                if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
                    deploy_firebase=false
                    echo "Развертывание Firebase функций отменено."
                fi
            fi
        else
            echo "Развертывание Firebase функций будет пропущено."
        fi
    fi
    
    # Запускаем полную сборку с компактным выводом логов
    local success=true
    
    # Очистка проекта
    run_with_compact_output "./gradlew clean" "Очистка проекта" || success=false
    
    # Публикация логгер-плагина, если его нет или версия изменилась
    if [ "$publish_plugin" = true ]; then
        run_with_compact_output "./gradlew :logger-gradle-plugin:publishToMavenLocal" "Публикация логгер-плагина" || success=false
        
        # Проверяем, что публикация прошла успешно
        if [ $? -eq 0 ]; then
            echo "Логгер-плагин успешно опубликован в локальном репозитории."
        else
            echo "ОШИБКА: Не удалось опубликовать логгер-плагин!"
            success=false
        fi
    fi
    
    # Проверка качества кода
    local lint_result=0
    run_with_compact_output "./gradlew detekt ktlintCheck" "Проверка качества кода" || lint_result=$?
    
    if [ $lint_result -ne 0 ]; then
        echo -e "\033[1;33mОбнаружены проблемы с качеством кода. Продолжить сборку? [y/N]\033[0m"
        read -n 1 -s answer
        echo
        if [[ ! "$answer" =~ ^[Yy]$ ]]; then
            echo -e "\033[1;31mСборка отменена.\033[0m"
            show_failure_message $lint_result
            generate_build_report "$build_type" "developer" false
            return 1
        fi
    fi
    
    # Запуск тестов
    local test_result=0
    run_with_compact_output "./gradlew test" "Запуск тестов" || test_result=$?
    
    if [ $test_result -ne 0 ]; then
        echo -e "\033[1;33mНе все тесты прошли успешно. Продолжить сборку? [y/N]\033[0m"
        read -n 1 -s answer
        echo
        if [[ ! "$answer" =~ ^[Yy]$ ]]; then
            echo -e "\033[1;31mСборка отменена.\033[0m"
            show_failure_message $test_result
            generate_build_report "$build_type" "developer" false
            return 1
        fi
    fi
    
    # Для разработчика также собираем Firebase функции
    if [ "$HAS_NODE" = true ] && [ -d "./functions" ]; then
        run_with_compact_output "(cd functions && npm install)" "Установка зависимостей Firebase" || true
        run_with_compact_output "(cd functions && npm run build)" "Сборка Firebase функций" || true
    fi
    
    # Определяем какую команду сборки запускать
    local build_command=""
    if [ "$build_type" = "debug" ]; then
        build_command="./gradlew :app:assembleDebug"
    else
        build_command="./gradlew :app:bundleRelease"
    fi
    
    # Запускаем сборку
    local build_result=0
    run_with_compact_output "$build_command" "Сборка $build_type версии приложения" || build_result=$?
    
    if [ $build_result -ne 0 ]; then
        success=false
        show_failure_message $build_result
    else
        # Показываем результаты сборки
        echo -e "\033[1;32m✓ $build_type версия собрана успешно\033[0m"
        if [ "$build_type" = "debug" ]; then
            echo -e "\033[1;32m  Созданные APK файлы:\033[0m"
            find app/build/outputs -name "*.apk" -type f | while read -r apk; do
                echo "  - $apk ($(du -h "$apk" | awk '{print $1}'))"
            done
        else
            echo -e "\033[1;32m  Созданные AAB файлы:\033[0m"
            find app/build/outputs -name "*.aab" -type f | while read -r aab; do
                echo "  - $aab ($(du -h "$aab" | awk '{print $1}'))"
            done
        fi
        
        # Если пользователь запросил развертывание Firebase функций
        if [ "$deploy_firebase" = true ]; then
            echo -e "\n\033[1;33mРазвертывание Firebase функций...\033[0m"
            run_with_compact_output "(cd functions && npm run deploy)" "Развертывание Firebase функций" || echo -e "\033[1;31mОшибка при развертывании Firebase функций\033[0m"
        fi
    fi
    
    local end_time=$(date +%s)
    local build_duration=$((end_time - start_time))
    echo "Полная сборка для разработчика завершена за $(($build_duration / 60)) минут и $(($build_duration % 60)) секунд."
    
    # Создание отчета о сборке с дополнительной информацией о тестах и проверке кода
    generate_build_report "$build_type" "developer" "$success"
    
    if [ "$success" = true ]; then
        show_success_message "$build_type"
        
        if [ "$HAS_ADB" = true ]; then
            echo "Хотите установить приложение на подключенное устройство? [y/N]"
            read -r answer
            if [[ "$answer" =~ ^[Yy]$ ]]; then
                install_app_to_device "$build_type"
            fi
        fi
    fi
    
    return 0
}

# Запуск тестов
run_tests() {
    echo "Запуск всех тестов..."

    ./gradlew test

    echo "Результаты тестов:"
    find . -path "*/build/reports/tests" | xargs ls -la

    return 0
}

# Проверка качества кода
check_code_quality() {
    echo "Проверка качества кода..."

    # Запуск detekt
    ./gradlew detekt

    # Запуск ktlint
    ./gradlew ktlintCheck

    echo "Проверка завершена, отчеты доступны в build/reports/"
    return 0
}

# Запуск Firebase эмуляторов
run_firebase_emulators() {
    echo "Запуск Firebase эмуляторов..."
    echo "Firebase эмуляторы - это локальные версии Firebase сервисов (Firestore, Functions, Auth и т.д.), которые позволяют тестировать приложение без использования реальных облачных ресурсов."

    if [ "$HAS_NODE" = false ]; then
        echo "ОШИБКА: Node.js не установлен. Невозможно запустить Firebase эмуляторы."
        return 1
    fi

    if [ ! -d "./functions" ]; then
        echo "ОШИБКА: Директория functions не найдена."
        return 1
    fi

    # Сборка и запуск Firebase функций
    (cd functions && npm run build && npm run serve)

    return 0
}

# Создание мок-конфигурации для Firebase
create_mock_config() {
    echo "Создание мок-конфигурации Firebase..."

    if [ -f "./app/google-services.json" ]; then
        echo "ВНИМАНИЕ: Файл google-services.json уже существует. Вы хотите заменить его мок-версией? [y/N]"
        read -r answer
        if [[ ! "$answer" =~ ^[Yy]$ ]]; then
            echo "Операция отменена."
            return 0
        fi
    fi

    cat > ./app/google-services.json << EOF
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "mock-project",
    "storage_bucket": "mock-project.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.tpov.schoolquiz"
        }
      },
      "api_key": [
        {
          "current_key": "mock_api_key"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ]
}
EOF

    echo "Мок-конфигурация Firebase создана."
    return 0
}

# Очистка временных файлов и логов
cleanup_temp_files() {
    echo "Очистка временных файлов и логов..."

    # Удаление временных файлов логов
    find . -name "gradle_output.txt" -type f -delete
    find . -name "gradle_version.txt" -type f -delete
    find . -name "build_log.txt" -type f -delete
    find . -name "build_output.txt" -type f -delete

    # Удаление macOS специфичных файлов
    find . -name ".DS_Store" -type f -delete

    # Удаление временных файлов Gradle
    find . -name "*.log" -type f -delete
    find . -path "*/build/tmp/*" -delete 2>/dev/null

    echo "Временные файлы очищены."
    return 0
}

# Парсинг и обработка отчетов об ошибках
parse_error_report() {
    echo "Анализ отчета об ошибках..."
    
    # Создаем временный файл для отчета
    local error_file="/tmp/error_report_$(date +%s).txt"

    # Если аргумент передан, используем его как имя файла
    if [ -n "$1" ] && [ -f "$1" ]; then
        cat "$1" > "$error_file"
    else
        # Запрашиваем вставку отчета об ошибках
        echo "Вставьте отчет об ошибках (введите 'EOF' на новой строке или нажмите Cmd+D/Ctrl+D для завершения ввода):"
        cat > "$error_file"
    fi

    # Проверяем, что файл не пуст
    if [ ! -s "$error_file" ]; then
        echo "ОШИБКА: Пустой отчет об ошибках."
        rm -f "$error_file"
        return 1
    fi

    # Обрабатываем файл и группируем ошибки по файлам
    echo "Обработка ошибок..."
    echo "==========================================="
    echo "Сводка ошибок:"
    echo "==========================================="

    # Рабочая директория проекта
    local WORKSPACE_PATH="$(pwd)"
    
    # Извлекаем уникальные файлы с ошибками
    declare -A unique_files_map
    
    # Поиск по каждому расширению отдельно
    for ext in ".kt" ".java" ".xml" ".gradle"; do
        while read -r line; do
            if [ -z "$line" ]; then continue; fi
            
            # Пытаемся извлечь полный путь к файлу, если он присутствует
            local file_path=""
            if echo "$line" | grep -F "$WORKSPACE_PATH" > /dev/null; then
                file_path=$(echo "$line" | grep -o "$WORKSPACE_PATH[^:]*$ext")
            else
                # Иначе пробуем найти относительный путь
                file_path=$(echo "$line" | grep -o "[^ :]*$ext")
                if [ -n "$file_path" ] && [ -f "$file_path" ]; then
                    file_path="$WORKSPACE_PATH/$file_path"
                fi
            fi
            
            if [ -n "$file_path" ] && [ -f "$file_path" ]; then
                rel_path=$(echo "$file_path" | sed "s|$WORKSPACE_PATH/||")
                unique_files_map["$file_path"]="$rel_path"
            fi
        done < <(grep -F "$ext:" "$error_file")
    done
    
    # Формируем список файлов с ошибками
    declare -a file_paths=()
    declare -a rel_paths=()
    for file_path in "${!unique_files_map[@]}"; do
        file_paths+=("$file_path")
        rel_paths+=("${unique_files_map[$file_path]}")
    done
    local file_count=${#file_paths[@]}

    echo "Найдено $file_count файлов с проблемами:"

    # Для каждого файла показываем количество проблем
    for ((i=0; i<file_count; i++)); do
        file_path="${file_paths[$i]}"
        rel_path="${rel_paths[$i]}"
        
        # Считаем количество проблем в файле - безопасный поиск по фиксированной строке
        local error_count=$(grep -F "$rel_path" "$error_file" | wc -l)

        echo "[$((i+1))] $rel_path ($error_count проблем)"
    done

    # Прямо выводим в терминал кликабельные ссылки
    echo "==========================================="
    echo -e "\033[1;36mКликабельные ссылки для навигации к ошибкам (прямо в Android Studio):\033[0m"
    
    # Показываем кликабельные ссылки для первых ошибок каждого файла
    for ((i=0; i<file_count && i<3; i++)); do
        file_path="${file_paths[$i]}"
        rel_path="${rel_paths[$i]}"
        
        # Находим первую строку с ошибкой в этом файле
        local first_error=$(grep -F "$rel_path" "$error_file" | head -1)
        
        # Извлекаем номер строки
        local line_num=""
        if echo "$first_error" | grep -o "$rel_path:[0-9]\+" > /dev/null; then
            line_num=$(echo "$first_error" | grep -o "$rel_path:[0-9]\+" | sed -E "s/^.*:([0-9]+)/\1/")
        fi
        
        # Извлекаем краткое описание ошибки
        local error_desc=""
        if [[ "$first_error" =~ \[.*\] ]]; then
            error_desc=$(echo "$first_error" | grep -o '\[.*\]')
        fi
        
        if [ -n "$line_num" ]; then
            # Скрываем полный URL и показываем относительный путь
            echo -en "\033[8mfile://$file_path:$line_num\033[0m"
            echo -e "\033[1;36m$rel_path:$line_num\033[0m $error_desc"
        else
            # Если нет номера строки, просто выводим файл
            echo -en "\033[8mfile://$file_path\033[0m"
            echo -e "\033[1;36m$rel_path\033[0m"
        fi
    done
    
    echo "==========================================="
    echo "Выберите файл для просмотра проблем (0 для выхода):"
    read -r choice

    if [ "$choice" = "0" ] || [ -z "$choice" ]; then
        rm -f "$error_file"
        return 0
    fi

    # Проверяем, что выбор корректен
    if ! [[ "$choice" =~ ^[0-9]+$ ]] || [ "$choice" -lt 1 ] || [ "$choice" -gt "$file_count" ]; then
        echo "Неверный выбор."
        rm -f "$error_file"
        return 1
    fi

    # Получаем выбранный файл
    local selected_idx=$((choice-1))
    local selected_file="${file_paths[$selected_idx]}"
    local selected_rel_path="${rel_paths[$selected_idx]}"

    # Показываем проблемы выбранного файла
    echo "==========================================="
    echo "Проблемы в файле $selected_rel_path:"
    echo "==========================================="

    # Собираем проблемы для этого файла
    declare -a problem_lines=()
    declare -a problem_numbers=()
    
    while read -r line; do
        if [ -z "$line" ]; then continue; fi
        
        # Извлекаем номер строки
        local line_num=""
        if echo "$line" | grep -o "$selected_rel_path:[0-9]\+" > /dev/null; then
            line_num=$(echo "$line" | grep -o "$selected_rel_path:[0-9]\+" | sed -E "s/^.*:([0-9]+)/\1/")
        fi
        
        if [ -n "$line_num" ]; then
            problem_lines+=("$line")
            problem_numbers+=("$line_num")
        fi
    done < <(grep -F "$selected_rel_path" "$error_file")
    
    # Нумеруем и отображаем проблемы
    local problem_count=${#problem_lines[@]}
    
    for ((i=0; i<problem_count; i++)); do
        local line="${problem_lines[$i]}"
        local line_num="${problem_numbers[$i]}"
        
        # Извлекаем краткое описание ошибки
        local error_desc=""
        if [[ "$line" =~ \[.*\] ]]; then
            error_desc=$(echo "$line" | grep -o '\[.*\]')
        else
            error_desc=$(echo "$line" | sed 's/^.*:[0-9][0-9]*:[0-9][0-9]*: //')
            if [ ${#error_desc} -gt 50 ]; then
                error_desc="${error_desc:0:47}..."
            fi
        fi
        
        # Создаем кликабельную ссылку с кратким описанием, скрывая полный URL
        if [ -n "$line_num" ]; then
            echo -en "[$((i+1))] \033[8mfile://$selected_file:$line_num\033[0m"
            echo -e "\033[1;36m$selected_rel_path:$line_num\033[0m - $error_desc"
        else
            echo "[$((i+1))] Строка неизвестна: $error_desc"
        fi
    done

    echo "==========================================="
    echo "Выберите действие:"
    echo "1. Открыть файл в редакторе (если доступно)"
    echo "2. Показать контекст кода вокруг ошибки"
    echo "3. Предложить исправление (если возможно)"
    echo "0. Назад"
    echo "==========================================="
    read -r action_choice

    # Запрашиваем выбор номера проблемы, если это не выход
    local selected_problem_line=""
    if [ "$action_choice" != "0" ]; then
        echo "Выберите номер проблемы (1-$problem_count, или 0 для всех):"
        read -r problem_choice
        
        if [ "$problem_choice" = "0" ]; then
            # Для операций со всем файлом номер строки не важен
            selected_problem_line=""
        elif [[ "$problem_choice" =~ ^[0-9]+$ ]] && [ "$problem_choice" -gt 0 ] && [ "$problem_choice" -le "$problem_count" ]; then
            # Получаем номер строки выбранной проблемы
            selected_problem_line="${problem_numbers[$((problem_choice-1))]}"
        else
            echo "Неверный выбор проблемы."
            rm -f "$error_file"
            return 1
        fi
    else
        # Выход из меню
        rm -f "$error_file"
        return 0
    fi

    case $action_choice in
        1)
            # Пытаемся открыть файл в редакторе
            if [ -n "$selected_problem_line" ]; then
                echo -e "\033[1;32mВы можете нажать на эту ссылку для открытия файла в Android Studio:\033[0m"
                # Используем скрытый полный URL для кликабельной ссылки
                echo -en "\033[8mfile://$selected_file:$selected_problem_line\033[0m"
                echo -e "\033[1;36m$selected_rel_path:$selected_problem_line\033[0m"
            fi
            
            if command -v code &> /dev/null; then
                # VS Code
                if [ -n "$selected_problem_line" ]; then
                    code -g "$selected_file:$selected_problem_line"
                    echo "Открыт файл в Visual Studio Code на строке $selected_problem_line"
                else
                    code "$selected_file"
                    echo "Открыт файл в Visual Studio Code"
                fi
            elif command -v vim &> /dev/null; then
                # Vim
                if [ -n "$selected_problem_line" ]; then
                    vim "+$selected_problem_line" "$selected_file"
                else
                    vim "$selected_file"
                fi
            elif command -v nano &> /dev/null; then
                # Nano
                if [ -n "$selected_problem_line" ]; then
                    nano "+$selected_problem_line" "$selected_file"
                else
                    nano "$selected_file"
                fi
            else
                echo "Не найден подходящий редактор. Установите VS Code, Vim или Nano."
                # Показываем контекст кода в любом случае
                show_code_context "$selected_file" "$selected_problem_line"
            fi
            ;;
        2)
            # Показываем контекст кода
            show_code_context "$selected_file" "$selected_problem_line"
            ;;
        3)
            # Предлагаем исправление в зависимости от типа проблемы
            local selected_problem="${problem_lines[$((problem_choice-1))]}"
            suggest_fix "$selected_problem" "$selected_file" "$selected_problem_line"
            ;;
        0|*)
            # Возврат к списку проблем
            echo "Возврат в меню..."
            ;;
    esac

    rm -f "$error_file"
    return 0
}

# Показать контекст кода вокруг ошибки
show_code_context() {
    local file_path="$1"
    local line_number="$2"
    local context=5  # строк до и после
    local WORKSPACE_PATH="$(pwd)"

    # Проверяем существование файла
    if [ ! -f "$file_path" ]; then
        echo "ОШИБКА: Файл не найден: $file_path"
        return 1
    fi

    # Получаем относительный путь к файлу для отображения
    local rel_path=$(echo "$file_path" | sed "s|$WORKSPACE_PATH/||")

    # Вычисляем диапазон строк для отображения
    local start_line=$((line_number - context))
    if [ $start_line -lt 1 ]; then
        start_line=1
    fi

    local end_line=$((line_number + context))
    local file_length=$(wc -l < "$file_path")
    if [ $end_line -gt $file_length ]; then
        end_line=$file_length
    fi

    echo "==========================================="
    echo "Контекст кода в файле $rel_path (строки $start_line-$end_line):"
    echo "==========================================="

    # Отображаем строки с номерами, выделяя проблемную строку
    local current_line=$start_line
    while [ $current_line -le $end_line ]; do
        local line_content=$(sed -n "${current_line}p" "$file_path")
        
        # Выделяем проблемную строку
        if [ "$current_line" = "$line_number" ]; then
            echo -e "\033[1;31m$current_line: $line_content\033[0m   ← ПРОБЛЕМА ЗДЕСЬ"
        else
            echo -e "\033[0;37m$current_line: $line_content\033[0m"
        fi
        
        current_line=$((current_line + 1))
    done

    echo "==========================================="
    
    # Создаем кликабельную ссылку для этой строки с скрытым URL
    if [ -n "$line_number" ]; then
        echo -e "\033[1;33mКликабельная ссылка для перехода в Android Studio:\033[0m"
        echo -en "\033[8mfile://$file_path:$line_number\033[0m"
        echo -e "\033[1;36m$rel_path:$line_number\033[0m"
    fi
    
    echo "Нажмите Enter для продолжения..."
    read -r
}

# Предложить исправление на основе типа ошибки
suggest_fix() {
    local error_line="$1"
    local file_path="$2"
    local line_number="$3"
    local WORKSPACE_PATH="$(pwd)"
    local rel_path=$(echo "$file_path" | sed "s|$WORKSPACE_PATH/||")

    # Извлекаем тип ошибки
    local error_type=$(echo "$error_line" | grep -o '\[[^]]*\]' | tr -d '[]')

    echo "==========================================="
    echo -e "\033[1;32mПредлагаемое исправление для проблемы типа $error_type:\033[0m"
    echo "==========================================="

    # В зависимости от типа ошибки предлагаем разные исправления
    case $error_type in
        "MagicNumber")
            # Показываем код с магическим числом
            show_code_context "$file_path" "$line_number"

            # Определяем магическое число из контекста
            local line_content=$(sed -n "${line_number}p" "$file_path")
            local magic_numbers=$(echo "$line_content" | grep -o '[0-9]\+' | sort -u)

            echo "В строке найдены следующие числовые значения: $magic_numbers"
            echo "Рекомендации по исправлению:"
            echo "1. Создайте именованную константу для числа в начале класса/файла:"
            echo "   private const val НАЗВАНИЕ_КОНСТАНТЫ = значение"
            echo "2. Замените магическое число на константу в коде"
            echo
            echo "Пример:"
            echo "private const val DEFAULT_PADDING = 16"
            echo "..."
            echo "val padding = DEFAULT_PADDING"
            ;;

        "NewLineAtEndOfFile")
            echo "В файле отсутствует пустая строка в конце."
            echo "Исправление: добавьте пустую строку в конец файла."
            echo
            echo "Команда для исправления:"
            echo "echo >> \"$rel_path\""
            ;;

        "UnusedPrivateMember")
            echo "Обнаружена неиспользуемая приватная функция или переменная."
            echo "Рекомендации по исправлению:"
            echo "1. Если функция действительно не используется, удалите её"
            echo "2. Если функция будет использоваться в будущем, добавьте комментарий @Suppress(\"UnusedPrivateMember\")"
            echo "3. Проверьте, возможно, имя функции неправильно указано в местах использования"
            ;;
            
        "LongParameterList")
            echo "Функция имеет слишком много параметров."
            echo "Рекомендации по исправлению:"
            echo "1. Сгруппируйте связанные параметры в отдельный класс или data class"
            echo "2. Используйте паттерн Builder для создания объектов с множеством параметров"
            echo "3. Используйте именованные аргументы для улучшения читаемости"
            echo "4. Рассмотрите вариант разделения функции на несколько меньших функций"
            ;;
            
        "LongMethod")
            echo "Функция слишком длинная."
            echo "Рекомендации по исправлению:"
            echo "1. Разделите функцию на несколько меньших функций с четкой ответственностью"
            echo "2. Выделите повторяющиеся блоки кода в отдельные вспомогательные функции"
            echo "3. Используйте функциональное программирование (map, filter и т.д.) вместо явных циклов"
            echo "4. Если изменение невозможно, добавьте аннотацию @Suppress(\"LongMethod\")"
            ;;

        *)
            echo "Для данного типа ошибки нет автоматических рекомендаций."
            echo "Общая рекомендация: обратитесь к документации по стилю кода Kotlin."
            ;;
    esac

    echo "==========================================="
    echo "Нажмите Enter для продолжения..."
    read -r
}

# Показать главное меню
show_menu() {
    clear
    show_logo
    echo "======================================================="
    echo "        SchoolQuiz 3.0 Developer Tools v1.0            "
    echo "======================================================="
    echo "1. Настроить среду разработки"
    echo "2. 🧪 СБОРКА ДЛЯ ТЕСТИРОВЩИКА (быстрая без проверок)"
    echo "3. 💻 СБОРКА ДЛЯ РАЗРАБОТЧИКА (полная с проверками)"
    echo "4. Установить APK на устройство"
    echo "5. Запустить все тесты"
    echo "6. Проверить качество кода"
    echo "7. Запустить Firebase эмуляторы"
    echo "8. Создать мок-конфигурацию Firebase"
    echo "9. Быстрая разработка (настроить + сборка для тестировщика)"
    echo "10. Очистить временные файлы"
    echo "11. Анализ отчета об ошибках"
    echo "0. Выход"
    echo "======================================================="
    echo -n "Выберите действие: "
}

# Инициализация глобальных переменных
HAS_NODE=true
HAS_KEYS=true
HAS_FIREBASE_CONFIG=true
HAS_ADB=true

# Главный цикл
main() {
    # Показываем приветственное сообщение при первом запуске
    show_welcome

    # Проверка наличия необходимых инструментов
    if ! check_dependencies; then
        echo "КРИТИЧЕСКАЯ ОШИБКА: Отсутствуют необходимые зависимости. Скрипт завершает работу."
        exit 1
    fi

    # Проверка наличия ключей
    check_keys

    while true; do
        show_menu
        read -r choice

        case $choice in
            1)
                setup_environment
                ;;
            2)
                build_for_tester
                ;;
            3)
                build_for_developer
                ;;
            4)
                # Определяем тип сборки на основе наличия ключей
                build_type=$(determine_build_type)
                install_app_to_device "$build_type"
                ;;
            5)
                run_tests
                ;;
            6)
                check_code_quality
                ;;
            7)
                run_firebase_emulators
                ;;
            8)
                create_mock_config
                ;;
            9)
                setup_environment && build_for_tester
                ;;
            10)
                cleanup_temp_files
                ;;
            11)
                parse_error_report
                ;;
            0)
                echo "Завершение работы. До свидания!"
                exit 0
                ;;
            *)
                echo "Неверный выбор. Пожалуйста, выберите действие из списка."
                ;;
        esac

        echo
        read -p "Нажмите Enter для продолжения..."
    done
}

# Запуск главной функции
main
