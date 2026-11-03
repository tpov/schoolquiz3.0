'use strict';

// Аудит темы chemistry 6-1: почти-дубли, позиционные ссылки, fb-кандидаты, длина ключа.
// Уникальное имя файла (параллельные агенты в общем каталоге).

const lessons = [1, 2, 3, 4, 5].map(l => require(`./data/school/chemistry/lessons/6-1-${l}.js`));

const label = (li, qi) => `6-1-${li + 1} q${qi}`;
const optionsOf = q => {
  if (q.options) return q.options.map(o => o.text);
  if (q.items) return q.items.map(o => o.text);
  if (q.candidates) return q.candidates.map(o => o.text);
  return [];
};
const keyOf = q => {
  if (q.type === 'SingleChoice') return [q.correctOptionId];
  if (q.type === 'MultipleChoice') return q.correctOptionIds;
  return null;
};

// --- 1. Дубли и почти-дубли ---
// Нормализация: цифры и латиница/греческие индексы (₂₃₄₅₇) сохраняем, убираем пунктуацию/регистр/ё.
const norm = s => s.toLowerCase().replace(/ё/g, 'е').replace(/[^\p{L}\p{N}]/gu, '');
const setOf = arr => new Set(arr.map(norm).filter(Boolean));
function jaccard(a, b) {
  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  return inter / (a.size + b.size - inter || 1);
}

const all = [];
lessons.forEach((qs, li) => qs.forEach((q, qi) => all.push({ li, qi, q })));

console.log('=== ТОЧНЫЕ ДУБЛИ (stem+options) ===');
const seen = new Map();
for (const { li, qi, q } of all) {
  const sig = norm(q.text) + '|' + optionsOf(q).map(norm).sort().join('#');
  if (seen.has(sig)) console.log(`DUP: ${label(seen.get(sig).li, seen.get(sig).qi)} == ${label(li, qi)}`);
  else seen.set(sig, { li, qi });
}

console.log('\n=== ПОЧТИ-ДУБЛИ (совпадение стема >=80% ИЛИ стем равен, варианты совпадают >=50%) ===');
for (let i = 0; i < all.length; i++) {
  for (let j = i + 1; j < all.length; j++) {
    const A = all[i], B = all[j];
    const ta = norm(A.q.text), tb = norm(B.q.text);
    const sameType = A.q.type === B.q.type;
    if (!sameType) continue;
    const stemSim = jaccard(new Set(ta.split('')), new Set(tb.split(''))); // грубая близость по символам
    const optSim = jaccard(setOf(optionsOf(A.q)), setOf(optionsOf(B.q)));
    const textSim = jaccard(new Set(ta.match(/.{1,4}/g) || []), new Set(tb.match(/.{1,4}/g) || []));
    if ((stemSim > 0.8 && optSim >= 0.5) || (textSim >= 0.85 && optSim >= 0.5)) {
      console.log(`NEAR: ${label(A.li, A.qi)} ~ ${label(B.li, B.qi)} (opts ${optSim.toFixed(2)}) :: "${A.q.text.slice(0, 60)}" vs "${B.q.text.slice(0, 60)}"`);
    }
  }
}

// --- 2. Позиционные ссылки (кириллический регэксп; \b и \w не работают для русского) ---
console.log('\n=== ПОЗИЦИОННЫЕ ССЫЛКИ в info ===');
const CY = '[А-Яа-яЁё]';
const posPatterns = [
  [/(перв|втор|трет|четв|пят)[а-яё]*\s+(вариант|пункт|строк|шаг|утвержден|ответ)/gi, 'вариант/пункт по номеру'],
  [/(два|три|четыре|первые|последн)[а-яё]*\s+(варианта?|пункт[а-яё]*|утвержден[а-яё]*|записи)/gi, 'счётные позиции'],
  [/последн[а-яё]*\s+(вариант|пункт|шаг|запись)/gi, 'последний …'],
  [/вариант[а-яё]*\s*[абвгде]/gi, 'вариант а/б/в…'],
  [/пункт[а-яё]*\s*[1-5и]/gi, 'пункт 1/2…'],
];
for (const { li, qi, q } of all) {
  const info = q.info || '';
  for (const [re, why] of posPatterns) {
    const m = info.match(re);
    if (m) console.log(`POS ${label(li, qi)} [${why}]: ${JSON.stringify(m.slice(0, 3))} :: ${info.slice(0, 90)}`);
  }
}

console.log('\n=== ПОЗИЦИОННЫЕ ССЫЛКИ в вариантах/пунктах (соседи, суммы) ===');
for (const { li, qi, q } of all) {
  const opts = optionsOf(q);
  const re = new RegExp(`(предыдущ|следующ|остальн|сумм[аеу]|разность|разниц[ау] Costco)`, 'gi');
  opts.forEach((o, oi) => {
    if (/предыдущ|следующ|остальн/i.test(o) && !/из предыдущего|из предыдущ/i.test(o))
      console.log(`POSOPT ${label(li, qi)} opt#${oi}: ${o.slice(0, 80)}`);
  });
}

// --- 3. fb: дубликаты кандидатов, пустые тексты ---
console.log('\n=== fb: повторы кандидатов внутри вопроса ===');
for (const { li, qi, q } of all) {
  
  if (q.type !== "FillBlank") continue;
  const cands = q.candidates.map(c => c.text);
  const dup = cands.filter((c, i) => cands.indexOf(c) !== i);
  if (dup.length) console.log(`FBDUP ${label(li, qi)}: ${dup}`);
  const blanks = (q.text.match(/___/g) || []).length;
  if (blanks !== q.blanks.length) console.log(`FBBLANKS ${label(li, qi)}: blanks=${blanks} vs ${q.blanks.length}`);
}

// --- 4. Оформление: длина ключа vs дистракторов (sc) ---
console.log('\n=== sc: ключ заметно длиннее дистракторов (>2.2x avg и >40 симв) ===');
for (const { li, qi, q } of all) {
  
  if (q.type !== "SingleChoice") continue;
  const lens = q.options.map(o => o.text.length);
  const ki = q.options.findIndex(o => o.id === q.correctOptionId);
  const avgOther = (lens.reduce((s, l) => s + l, 0) - lens[ki]) / (lens.length - 1);
  if (lens[ki] > 2.2 * avgOther && lens[ki] > 40)
    console.log(`LONGKEY ${label(li, qi)}: key(${lens[ki]}) vs avgOther(${avgOther.toFixed(0)}): ${q.options[ki].text.slice(0, 70)}`);
}

console.log('\nDONE');
