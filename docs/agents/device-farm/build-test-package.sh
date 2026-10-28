#!/usr/bin/env bash
# Сборка тест-пакета для AWS Device Farm (custom environment + Maestro).
#
# Запуск на VPS из корня репо:
#   bash docs/agents/device-farm/build-test-package.sh
# Предпосылка: run/device-farm/android-next-mvp.apk и android-next-mvpb.apk
# уже собраны (README, §«Сборка APK»). APK в .gitignore (*.apk) и в Git не попадают.
#
# Результат: run/device-farm/devicefarm-test-package.zip с макетом
#   apks/android-next-mvp.apk     — клиент A (com.tpov.schoolquiz.mvp)
#   apks/android-next-mvpb.apk    — клиент B (com.tpov.schoolquiz.mvpb), 4.26
#   flows/*.yaml                  — флоу из .maestro/mvp (01–07)
set -euo pipefail

root=$(cd "$(dirname "$0")/../../.." && pwd)
out="$root/run/device-farm/pkg"
rm -rf "$out"
mkdir -p "$out/apks" "$out/flows"

for apk in android-next-mvp.apk android-next-mvpb.apk; do
  if [ -f "$root/run/device-farm/$apk" ]; then
    cp "$root/run/device-farm/$apk" "$out/apks/"
  else
    echo "WARN: run/device-farm/$apk отсутствует — в пакет не попадёт" >&2
  fi
done

cp "$root"/.maestro/mvp/*.yaml "$out/flows/"

(
  cd "$out"
  # zip в системе нет; python3 -m zipfile делает то же без новых зависимостей
  python3 -m zipfile -c devicefarm-test-package.zip apks flows
)

echo "--- содержимое пакета ---"
python3 -m zipfile -l "$out/devicefarm-test-package.zip"
echo "--- sha256 ---"
sha256sum "$out/devicefarm-test-package.zip"
