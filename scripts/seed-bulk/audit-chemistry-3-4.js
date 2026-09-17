'use strict';
// Ревизор темы chemistry 3-4. Уникальное имя (параллельные агенты).
const path = require('path');
const base = path.join(__dirname, 'data', 'school', 'chemistry', 'lessons');
const lessons = [1, 2, 3, 4, 5].map((l) => {
  delete require.cache[require.resolve(path.join(base, `3-4-${l}.js`))];
  return { l, qs: require(path.join(base, `3-4-${l}.js`)) };
});

const norm = (x) => String(x || '').toLowerCase().replace(/ё/g, 'е').replace(/\s+/g, ' ').trim();
const opts = (q) => ['options', 'items', 'candidates'].flatMap((a) => (q[a] || []).map((e) => e.text));

console.log('=== A. ПОЧТИ-ДУБЛИ: одинаковый текст вопроса ===');
const byStem = new Map();
for (const { l, qs } of lessons) qs.forEach((q, i) => {
  const k = norm(q.text);
  if (!byStem.has(k)) byStem.set(k, []);
  byStem.get(k).push(`3-4-${l} q${i} (${q.type}/${q.difficulty})`);
});
for (const [k, v] of byStem) if (v.length > 1) console.log(`STEM DUP [${k}] → ${v.join(' | ')}`);

console.log('\n=== A2. ПОЧТИ-ДУБЛИ: пересечение наборов вариантов/пунктов/кандидатов (Jaccard) ===');
const all = [];
for (const { l, qs } of lessons) qs.forEach((q, i) => all.push({ tag: `3-4-${l} q${i}`, l, i, q }));
for (let x = 0; x < all.length; x++) for (let y = x + 1; y < all.length; y++) {
  const A = all[x], B = all[y];
  if (A.q.type !== B.q.type) continue;
  const sa = new Set(opts(A.q).map(norm)), sb = new Set(opts(B.q).map(norm));
  let inter = 0; sa.forEach((t) => { if (sb.has(t)) inter++; });
  const union = new Set([...sa, ...sb]).size;
  const jac = inter / union;
  const stemSame = norm(A.q.text) === norm(B.q.text);
  if (jac >= 0.6 || (stemSame && jac >= 0.4)) console.log(`${A.tag} ~ ${B.tag}: J=${jac.toFixed(2)} stemSame=${stemSame} stem=[${norm(A.q.text).slice(0, 70)}]`);
}

console.log('\n=== B. ПОЗИЦИОННЫЕ ССЫЛКИ (широкий фильтр, info + варианты) ===');
const RX = [
  /\bперв\w*\s+(?:\d+\s+)?(?:вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|част)/i,
  /\bвтор\w*\s+(?:\d+\s+)?(?:вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|част)/i,
  /\bтрет\w*\s+(?:\d+\s+)?(?:вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|част)/i,
  /\bчетвёрт\w*\s+(?:\d+\s+)?(?:вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|част)/i,
  /\bпят\w*\s+(?:\d+\s+)?(?:вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|част)/i,
  /\b(?:последн|предпоследн)\w*\s+(?:\d+\s+)?(?:вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|част)/i,
  /\b(?:два|три|четыре|пять)\s+последн\w*/i,
  /\b(?:первые|последние)\s+(?:два|три|четыре|пять)\s+(?:вариант|пункт|шаг|утверждени|запис|строк|случа|объект|элемент)/i,
  /\bвариант\w*\s*[«"']?[абвгдеabcdef][»"']?/i,
  /\bпункт\w*\s*[«"']?[абвгдеabcdef][»"']?/i,
  /\b(?:предыдущ|следующ)\w*\s+(?:вариант|пункт|шаг|утверждени|запис|строк|уравнени|формул)/i,
];
for (const { tag, q } of all) {
  const fields = [['info', q.info]];
  for (const [name, arr] of [['options', q.options], ['items', q.items], ['candidates', q.candidates]]) (arr || []).forEach((e, j) => fields.push([`${name}[${j}]`, e.text]));
  for (const [f, txt] of fields) {
    const s = String(txt || '');
    for (const rx of RX) if (rx.test(s)) { console.log(`${tag} ${f}: «${s.slice(0, 110)}»`); break; }
  }
}

console.log('\n=== C. ORD: числа в скобках и монотонность ===');
for (const { tag, q } of all) {
  if (q.type !== 'Ordering') continue;
  const nums = (q.items || []).map((it) => {
    const m = String(it.text).match(/(-?\d+(?:[.,]\d+)?)\s*°?C/);
    return m ? parseFloat(m[1].replace(',', '.')) : null;
  });
  const defined = nums.filter((n) => n !== null);
  const inc = defined.every((n, i) => i === 0 || n > defined[i - 1]);
  const dec = defined.every((n, i) => i === 0 || n < defined[i - 1]);
  console.log(`${tag}: [${q.items.map((i) => i.text).join(' → ')}] nums=${JSON.stringify(nums)} inc=${inc} dec=${dec}`);
}

console.log('\n=== D. FB: кандидаты, совпадающие по смыслу (лексическое сходство) ===');
for (const { tag, q } of all) {
  if (q.type !== 'FillBlank') continue;
  const corr = q.blanks.map((b) => q.candidates.find((c) => c.id === b.correctCandidateId).text);
  const stem = norm(q.text);
  console.log(`${tag}: «${q.text.slice(0, 80)}» корр=${JSON.stringify(corr)} канд=[${q.candidates.map((c) => c.text).join('; ')}]`);
}
