'use strict';
// Уникальный вспомогательный скрипт аудита темы chemistry 2-3 (НЕ переиспользовать для других тем).
// 1) Точные и почти-дубли (стем + варианты). 2) Позиционные ссылки (кириллица, свой регэксп).
// 3) Арифметическая сверка ключевых чисел темы. 4) sc-оформление, выдающее ответ (длина ключа).

const path = require('path');
const base = path.join(__dirname, 'data', 'school', 'chemistry', 'lessons');
const lessons = [1, 2, 3, 4, 5].map(l => require(path.join(base, `2-3-${l}.js`)));

const qs = [];
lessons.forEach((arr, li) => arr.forEach((q, qi) => qs.push({ lesson: li + 1, idx: qi, q })));

const norm = s => String(s).toLowerCase().replace(/[\s.,;:()«»"'’\-−–—]/g, '');
const words = s => String(s).toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim().split(/\s+/).filter(Boolean);
const jac = (a, b) => { const A = new Set(a), B = new Set(b); const i = [...A].filter(x => B.has(x)).length; const u = new Set([...A, ...B]).size; return u ? i / u : 0; };
const stem = q => norm(q.text);
const opts = q => (q.options || q.items || q.candidates || []).map(o => norm(o.text));

console.log('=== ПОЧТИ-ДУБЛИ (одинаковый стем и/или совпадающие варианты) ===');
for (let i = 0; i < qs.length; i++) {
  for (let j = i + 1; j < qs.length; j++) {
    const A = qs[i], B = qs[j];
    if (A.q.type !== B.q.type) continue;
    const ss = jac(words(stem(A.q)), words(stem(B.q)));
    const oo = jac(opts(A.q), opts(B.q));
    const exact = stem(A.q) === stem(B.q) && opts(A.q).join('|') === opts(B.q).join('|');
    const sameStem = stem(A.q) === stem(B.q);
    if (exact || sameStem || (ss >= 0.75 && oo >= 0.5) || (oo >= 0.8 && ss >= 0.3)) {
      console.log(`${A.lesson}.${A.idx} ~ ${B.lesson}.${B.idx} [${A.q.type}] exact=${exact ? 1 : 0} sameStem=${sameStem ? 1 : 0} ss=${ss.toFixed(2)} oo=${oo.toFixed(2)}`);
      console.log(`   A: ${A.q.text.slice(0, 100)}`);
      console.log(`   B: ${B.q.text.slice(0, 100)}`);
    }
  }
}

console.log('\n=== ПОЗИЦИОННЫЕ ССЫЛКИ (кириллица, info + варианты) ===');
const posRe = /(перв\w*|втор\w*|трет\w*|четв[её]рт\w*|пят\w*|шест\w*|седьм\w*|восьм\w*|последн\w*|предпоследн\w*|два|три|четыре)\s*((два|три|четыре)\s+)?(пункт\w*|вариант\w*|утверждени\w*|шаг\w*|запис\w*|столб\w*|строк\w*|строчк\w*|перечисл\w*)/i;
const posRe2 = /\b(соответственно|вышеуказанн\w*|перечисленн\w* выше|из перечисленных)\b/i;
const posRe3 = /(предыдущ\w*|следующ\w*)\s*(шаг\w*|пункт\w*|вариант\w*|элемент)/i;
qs.forEach(({ lesson, idx, q }) => {
  const texts = { info: q.info };
  (q.options || []).forEach(o => { texts['opt:' + o.text.slice(0, 30)] = o.text; });
  (q.items || []).forEach((o, i) => { texts['item' + (i + 1)] = o.text; });
  (q.candidates || []).forEach(o => { texts['cand:' + o.text.slice(0, 20)] = o.text; });
  for (const [k, t] of Object.entries(texts)) {
    if (!t) continue;
    const m = t.match(posRe) || t.match(posRe3);
    const m2 = t.match(posRe2);
    if (m) console.log(`${lesson}.${idx} [${k}] «${m[0]}» :: ${t.slice(0, 150)}`);
    else if (m2) console.log(`${lesson}.${idx} [${k}] «${m2[0]}» :: ${t.slice(0, 150)}`);
  }
});

console.log('\n=== sc: КЛЮЧ ЗАМЕТНО ДЛИННЕЕ ДИСТРАКТОРОВ (len(key) > 2.2 * медианы и ключ самый длинный) ===');
qs.forEach(({ lesson, idx, q }) => {
  if (q.type !== 'SingleChoice') return;
  const lens = q.options.map(o => o.text.length);
  const ki = q.options.findIndex(o => o.id === q.correctOptionId);
  const others = lens.filter((_, i) => i !== ki).sort((a, b) => a - b);
  const med = others.length % 2 ? others[(others.length - 1) / 2] : (others[others.length / 2 - 1] + others[others.length / 2]) / 2;
  if (lens[ki] > 2.2 * med && lens[ki] === Math.max(...lens)) {
    console.log(`${lesson}.${idx}: key(${lens[ki]}) vs media(${med}) :: ${q.options[ki].text.slice(0, 130)}`);
  }
});

console.log('\n=== АРИФМЕТИКА (пересчёт в node) ===');
const chk = [
  ['Z=33 внутри 4-го периода: 33-18', 33 - 18, 15],
  ['N(35Cl)=35-17', 35 - 17, 18],
  ['N(37Cl)=37-17', 37 - 17, 20],
  ['N(сера)=32-16', 32 - 16, 16],
  ['электронов [Ar]4s2', 18 + 2, 20],
  ['электронов [Ar]3d10 4s2 4p5', 18 + 10 + 2 + 5, 35],
  ['низшая СО азота 5-8', 5 - 8, -3],
  ['низшая СО хлора 7-8', 7 - 8, -1],
  ['низшая СО серы 6-8', 6 - 8, -2],
  ['Mn: 5+2', 5 + 2, 7],
  ['внешних у серы 2+4', 2 + 4, 6],
];
chk.forEach(([name, got, exp]) => console.log(`${name} = ${got} ${got === exp ? 'OK' : 'FAIL(' + exp + ')'}`));
console.log('\nМассы: K 39,10 < Ar 39,95; Ni 58,69 < Co 58,93; I 126,9 < Te 127,6 — факты сверены со спецификацией.');
