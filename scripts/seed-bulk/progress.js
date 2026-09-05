'use strict';
// Прогресс авторинга: какие уроки предмета существуют и валидны (40 вопросов).
//   node progress.js <subject> | --all
const path = require('path'); const fs = require('fs');
const subjects = process.argv[2] === '--all' ? ['math','physics','chemistry','biology','history','geography'] : [process.argv[2]];
for (const subject of subjects) {
  const base = path.join(__dirname, 'data', 'school', subject); let ok = 0; const missing = [], broken = [], noSpec = [];
  for (let s = 1; s <= 7; s++) for (let t = 1; t <= 4; t++) for (let l = 1; l <= 5; l++) {
    const c = `${s}-${t}-${l}`;
    if (!fs.existsSync(path.join(base, 'focus', `${c}.json`))) noSpec.push(c);
    const f = path.join(base, 'lessons', `${c}.js`);
    if (!fs.existsSync(f)) { missing.push(c); continue; }
    try { const a = require(f); if (Array.isArray(a) && a.length === 40) ok++; else broken.push(c); } catch (e) { broken.push(c); }
  }
  console.log(`${subject.padEnd(10)} уроков ${ok}/140 | спек ${140 - noSpec.length}/140` + (missing.length ? ` | нет: ${missing.slice(0, 10).join(',')}${missing.length > 10 ? '…' : ''}` : '') + (broken.length ? ` | битые: ${broken.join(',')}` : ''));
}
