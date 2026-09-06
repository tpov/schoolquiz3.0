'use strict';
// Снимок темы history 3-1 (5 уроков) перед правками ревизора-b. Уникальное имя.
const fs = require('fs');
const path = require('path');
const BASE = path.join(__dirname, 'data', 'school', 'history');
const out = {};
for (let l = 1; l <= 5; l++) {
  const p = path.join(BASE, 'lessons', `3-1-${l}.js`);
  out[`3-1-${l}`] = fs.readFileSync(p, 'utf8');
}
fs.writeFileSync(path.join(__dirname, 'snap-history-3-1-b.json'), JSON.stringify(out, null, 1));
console.log('snap-history-3-1-b.json written:', Object.keys(out).join(', '));
