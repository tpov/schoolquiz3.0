#!/usr/bin/env bash
# Seed Firestore `catalogs` collection с стартовыми каталогами.
#
# Требования:
#   1. firebase login (интерактивно один раз)
#   2. firebase use school-quiz-89336951
#
# Запуск:
#   ./scripts/seed-catalogs.sh
set -euo pipefail

PROJECT_ID="school-quiz-89336951"
SEED_FILE="$(dirname "$0")/seed-catalogs.json"

if ! command -v firebase >/dev/null; then
  echo "ERROR: firebase CLI не установлен. npm i -g firebase-tools" >&2
  exit 1
fi

if ! firebase projects:list >/dev/null 2>&1; then
  echo "ERROR: не залогинен в Firebase. Запусти: firebase login" >&2
  exit 1
fi

# Каждый ключ верхнего уровня в seed-catalogs.json становится document ID.
# Нет batch import в firebase CLI — пишем по одному.
node -e "
const fs = require('fs');
const data = JSON.parse(fs.readFileSync('$SEED_FILE','utf8')).catalogs;
for (const [id, fields] of Object.entries(data)) {
  console.log('Writing catalog/' + id + ': ' + JSON.stringify(fields));
}
"

echo ""
echo "Применяю через firebase firestore:write (project=$PROJECT_ID)..."

# Альтернатива через firebase CLI (требует --data inline):
node -e "
const fs = require('fs');
const data = JSON.parse(fs.readFileSync('$SEED_FILE','utf8')).catalogs;
const { execSync } = require('child_process');
for (const [id, fields] of Object.entries(data)) {
  const dataJson = JSON.stringify(fields).replace(/'/g, \"\\\\'\");
  const cmd = \`firebase firestore:write 'catalogs/\${id}' '\${dataJson}' --project=$PROJECT_ID\`;
  console.log('>', cmd);
  execSync(cmd, { stdio: 'inherit' });
}
"

echo ""
echo "Done. Проверь в Firebase Console:"
echo "https://console.firebase.google.com/project/$PROJECT_ID/firestore/data/~2Fcatalogs"
