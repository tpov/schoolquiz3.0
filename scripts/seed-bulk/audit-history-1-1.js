'use strict';
// audit-history-1-1.js — попарная сверка 200 вопросов темы history 1-1:
// 1) почти-дубли (общий стем + пересечение вариантов), 2) позиционные ссылки
// (кириллический регэксп), 3) оформление ключа (длина vs дистракторы), 4) fb-структура.
const norm = (s) => String(s || '')
  .toLowerCase()
  .replace(/[«»"'(),.;:!?–—-]/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const stemOf = (t) => {
  const n = norm(t);
  // обрезаем типовые префиксы-команды, оставляя суть задания
  return n.replace(/^расположите\s+(события|даты|этапы|звенья|шаги|периоды|эпохи|века|формы|стадии|группы|типы|памятники)?\s*(этих)?\s*/, 'расположите ');
};

const texts = (q) => {
  const p = q.payload || q;
  if (p.type === 'SingleChoice' || p.type === 'MultipleChoice') return p.options.map((o) => norm(o.text));
  if (p.type === 'Ordering') return p.items.map((o) => norm(o.text));
  if (p.type === 'FillBlank') return p.candidates.map((o) => norm(o.text));
  return [];
};

const lessons = [1, 2, 3, 4, 5].map((l) => ({
  l,
  qs: require(`./data/school/history/lessons/1-1-${l}.js`),
}));

const flat = [];
for (const { l, qs } of lessons) for (let i = 0; i < qs.length; i++) flat.push({ l, i, q: qs[i] });

// 1) попарные дубли: доля общих вариантов + совпадение стемов
console.log('=== ПОПАРНАЯ СВЕРКА ВАРИАНТОВ (порог overlap>=0.5 или точный дубликат) ===');
for (let x = 0; x < flat.length; x++) {
  for (let y = x + 1; y < flat.length; y++) {
    const A = flat[x], B = flat[y];
    const ta = texts(A.q), tb = texts(B.q);
    const setB = new Set(tb);
    const shared = ta.filter((t) => setB.has(t)).length;
    const minLen = Math.min(ta.length, tb.length);
    const ovA = shared / ta.length, ovB = shared / tb.length;
    const overlap = Math.max(ovA, ovB);
    const sameStem = stemOf(A.q.text) === stemOf(B.q.text);
    const sameText = norm(A.q.text) === norm(B.q.text);
    if (shared > 0 && (overlap >= 0.5 || (sameStem && shared >= 2))) {
      console.log(
        `[${A.l}-${A.i}] x [${B.l}-${B.i}] types=${A.q.type}/${B.q.type} shared=${shared}/${ta.length},${tb.length}` +
        ` ovA=${ovA.toFixed(2)} ovB=${ovB.toFixed(2)} sameStem=${sameStem} sameText=${sameText}` +
        ` | ${A.q.text.slice(0, 60)}`
      );
    }
    if (sameText && A.q.type === B.q.type && shared >= Math.max(2, Math.floor(minLen * 0.5))) {
      console.log(`  !! сильное совпадение: [${A.l}-${A.i}] и [${B.l}-${B.i}]`);
    }
  }
}

// 2) позиционные ссылки — свой регэксп для кириллицы
console.log('=== ПОЗИЦИОННЫЕ ССЫЛКИ ===');
const posRe = new RegExp(
  '(перв|втор|трет|четв|пят|последн|остальн|предпоследн|соседн|следующ|предыдущ)[а-яё]*' +
  '\\s+(вариант|пункт|утверждени|шаг|строк|запис|элемент|позиции|граф|столбец|колонк)' , 'i'
);
const posRe2 = new RegExp('соответственно|в\\s+порядке\\s+перечислен', 'i');
let posCount = 0;
for (const { l, i, q } of flat) {
  const chunks = [q.text, q.info];
  const p = q.payload || q;
  if (p.options) p.options.forEach((o) => chunks.push(o.text));
  if (p.items) p.items.forEach((o) => chunks.push(o.text));
  for (const c of chunks) {
    if (c && (posRe.test(c) || posRe2.test(c))) {
      posCount++;
      console.log(`[${l}-${i}] ${c.slice(0, 100)}`);
    }
  }
}
if (!posCount) console.log('нет');

// 3) оформление ключа sc: длина ключа vs дистракторы, скобки
console.log('=== ОФОРМЛЕНИЕ SC-КЛЮЧА (длина >1.5x средней дистрактора или скобки только у ключа) ===');
let fmt = 0;
for (const { l, i, q } of flat) {
  const p = q.payload || q;
  if (p.type !== 'SingleChoice') continue;
  const keyIdx = 'abcde'.indexOf(p.correctOptionId);
  const key = p.options[keyIdx].text;
  const others = p.options.filter((o, j) => j !== keyIdx).map((o) => o.text);
  const avg = others.reduce((s, t) => s + t.length, 0) / others.length;
  const keyParen = /\([^)]+\)/.test(key);
  const anyOtherParen = others.some((t) => /\([^)]+\)/.test(t));
  if (key.length > avg * 1.5 && key.length - avg > 25) {
    fmt++;
    console.log(`[${l}-${i}] key(${key.length}) avg(${avg.toFixed(0)}): ${key}`);
  }
  if (keyParen && !anyOtherParen) {
    fmt++;
    console.log(`[${l}-${i}] скобки только у ключа: ${key}`);
  }
}
if (!fmt) console.log('нет');

// 4) fb: 5 или 10 кандидатов, пропусков=1, подстановка каждого кандидата
console.log('=== FB СТРУКТУРА ===');
let fbBad = 0;
for (const { l, i, q } of flat) {
  const p = q.payload || q;
  if (p.type !== 'FillBlank') continue;
  const blanks = (p.text.match(/___/g) || []).length;
  const n = p.blanks.length;
  if (p.candidates.length !== 5 && p.candidates.length !== 10) { fbBad++; console.log(`[${l}-${i}] кандидатов ${p.candidates.length}`); }
  if (blanks !== n) { fbBad++; console.log(`[${l}-${i}] пропусков ${blanks} vs ${n}`); }
}
if (!fbBad) console.log('ok');

console.log('=== ИТОГО вопросов:', flat.length, '===');
