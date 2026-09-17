'use strict';
// Audit helper for theme physics 2-1 (unique name to avoid clashes with parallel agents).

const lessons = [1, 2, 3, 4, 5].map(l => ({
  l,
  qs: require(`./data/school/physics/lessons/2-1-${l}.js`),
}));

const all = [];
for (const { l, qs } of lessons) {
  qs.forEach((q, i) => all.push({ l, i, q }));
}
console.log('total questions:', all.length);

function textsOf(q) {
  if (q.options) return q.options.map(o => o.text);
  if (q.items) return q.items.map(o => o.text);
  if (q.candidates) return q.candidates.map(o => o.text);
  return [];
}

// ---- 1. Duplicate detection: text alone and text+variants ----
console.log('\n=== EXACT TEXT DUPS (text equal, any type) ===');
const byText = new Map();
for (const { l, i, q } of all) {
  const key = q.text.trim();
  if (!byText.has(key)) byText.set(key, []);
  byText.get(key).push(`${l}-${i}`);
}
for (const [k, v] of byText) if (v.length > 1) console.log('TEXT DUP:', v, '|', k.slice(0, 80));

console.log('\n=== FULL DUPS (text + sorted variant texts) ===');
const byFull = new Map();
for (const { l, i, q } of all) {
  const key = q.text.trim() + ' || ' + textsOf(q).map(t => t.trim()).sort().join(' | ');
  if (!byFull.has(key)) byFull.set(key, []);
  byFull.get(key).push(`${l}-${i}(${q.type})`);
}
for (const [k, v] of byFull) if (v.length > 1) console.log('FULL DUP:', v, '|', k.slice(0, 100));

console.log('\n=== NEAR DUPS (same type, normalized text: lowercase, no spaces/punct) ===');
const norm = s => s.toLowerCase().replace(/[^a-zа-яё0-9]/g, '');
const byNorm = new Map();
for (const { l, i, q } of all) {
  if (q.type !== 'SingleChoice' && q.type !== 'MultipleChoice') continue;
  const key = q.type + ' ' + norm(q.text) + ' ' + textsOf(q).map(norm).sort().join(',');
  if (!byNorm.has(key)) byNorm.set(key, []);
  byNorm.get(key).push(`${l}-${i}`);
}
for (const [k, v] of byNorm) if (v.length > 1) console.log('NEAR DUP:', v, '|', k.slice(0, 100));

// ---- 2. Positional references scan ----
console.log('\n=== POSITIONAL WORDS in info/options/items/candidates/text ===');
const re = /(первых?\s(двух|трёх|четырёх|пяти)|последн\w*|предпоследн\w*|предыдущ\w*|следующ\w*|\bв?\s?(первом|первая|первое|первый)\s(варианте|пункте|шаге|случае|столбце)|\bво?\s?(втором|вторая|второе|второй|третьем|третья|третье|третий|четвёртом|четвёртая|четвёртое|четвёртый|пятом|пятая|пятое|пятый)\s(варианте|пункте|шаге|случае|столбце)|два\sпоследних|три\sпоследних|вариант\w*\s[а-я]\b)/i;
for (const { l, i, q } of all) {
  const spots = [['text', q.text], ['info', q.info]];
  for (const o of (q.options || [])) spots.push(['opt:' + o.id, o.text]);
  for (const o of (q.items || [])) spots.push(['item:' + o.id, o.text]);
  for (const o of (q.candidates || [])) spots.push(['cand:' + o.id, o.text]);
  for (const [where, s] of spots) {
    const m = s && s.match(re);
    if (m) console.log(`POS? ${l}-${i} ${where}: ...«${m[0]}» in «${s.slice(0, 120)}»`);
  }
}

// ---- 3. Numeric re-checks of every computed key ----
console.log('\n=== NUMERIC RECHECKS ===');
const eq = (a, b, eps = 1e-9) => {
  const parse = x => { if (typeof x === 'string') { try { return JSON.parse(x); } catch { return x; } } return x; };
  a = parse(a); b = parse(b);
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((x, i) => eq(x, b[i], eps));
  return Math.abs(a - b) < eps;
};
const checks = [];
const C = (label, actual, expect) => checks.push({ label, ok: eq(actual, expect), actual, expect });

// 2-1-1
C('1-1 q3 R=6+8', 6 + 8, 14);
C('1-1 q4 6N at 2N/cm', 6 / 2, 3);
C('1-1 q5 sqrt(8^2+6^2)', Math.hypot(8, 6), 10);
C('1-1 q6 m2/m1=v1/v2', 0.6 / 0.2, 3);
C('1-1 q7 R=10+4-6', 10 + 4 - 6, 8);
C('1-1 q8 sqrt(12^2+5^2)', Math.hypot(12, 5), 13);
C('1-1 q12 range 3..4', Math.hypot(3, 4), 5);
C('1-1 q15 sqrt(9^2+12^2)', Math.hypot(9, 12), 15);
C('1-1 q16 v2=0.9*1/3', 0.9 * 1 / 3, 0.3);
C('1-1 q18 R=12-5-3', 12 - 5 - 3, 4);
C('1-1 q19 12/4, 5*4', 12 / 4, 3);
C('1-1 q19b 5*4', 5 * 4, 20);
C('1-1 q21 6-8 opp', Math.abs(8 - 6), 2);
C('1-1 q21b perp', Math.hypot(6, 8), 10);
C('1-1 q25 cases', JSON.stringify([Math.hypot(3, 4), Math.hypot(6, 8), Math.hypot(5, 12), Math.hypot(9, 12), Math.hypot(8, 15)]), JSON.stringify([5, 10, 13, 15, 17]));
C('1-1 q26 m1/m2=0.2/0.6', 0.2 / 0.6, 1 / 3);
C('1-1 q28 |12-5|', Math.abs(12 - 5), 7);
C('1-1 q28b perp', Math.hypot(5, 12), 13);
C('1-1 q29 tg', 6 / 8, 0.75);

// 2-1-2
C('2-1 q6 mu=5/20', 5 / 20, 0.25);
C('2-1 q6b N=2*10', 2 * 10, 20);
C('2-1 q7 F=80*10', 80 * 10, 800);
C('2-1 q9 N=2*10', 2 * 10, 20);
C('2-1 q15 mu', 5 / 20, 0.25);
C('2-1 q18 F=80*10', 80 * 10, 800);
C('2-1 q26 mg cases', JSON.stringify([0, 1 * 10, 3 * 10, 5 * 10]), JSON.stringify([0, 10, 30, 50]));
C('2-1 q28 cases', JSON.stringify([2 * 10, 8 * 10, 60 * 10, 80 * 10]), JSON.stringify([20, 80, 600, 800]));

// 2-1-3
C('2-3 q2 a=10/2', 10 / 2, 5);
C('2-3 q3 F=3*4', 3 * 4, 12);
C('2-3 q5 a=(12-4)/4', (12 - 4) / 4, 2);
C('2-3 q6 s=2*25/2', 2 * 5 ** 2 / 2, 25);
C('2-3 q7 F=1200*2.5', 1200 * 2.5, 3000);
C('2-3 q8 a=2/0.5', 2 / 0.5, 4);
C('2-3 q9 R=30-10', 30 - 10, 20);
C('2-3 q9b a=20/5', 20 / 5, 4);
C('2-3 q9c v=4*4', 4 * 4, 16);
C('2-3 q13 a=6/2', 6 / 2, 3);
C('2-3 q13b v=3*4', 3 * 4, 12);
C('2-3 q15 a=(12-4)/4', (12 - 4) / 4, 2);
C('2-3 q15b s', 2 * 5 ** 2 / 2, 25);
C('2-3 q15c v=2*5', 2 * 5, 10);
C('2-3 q16 a=10/2', 10 / 2, 5);
C('2-3 q16b F=0.5*5', 0.5 * 5, 2.5);
C('2-3 q16c s=5*4/2', 5 * 2 ** 2 / 2, 10);
C('2-3 q17 a=20/8', 20 / 8, 2.5);
C('2-3 q17b F=1200*2.5', 1200 * 2.5, 3000);
C('2-3 q17c s=(20+0)/2*8', (20 + 0) / 2 * 8, 80);
C('2-3 q19 R=hypot(6,8)', Math.hypot(6, 8), 10);
C('2-3 q19b a=10/5', 10 / 5, 2);
C('2-3 q21 cases', JSON.stringify([12 / 12, 12 / 6, 12 / 4, 12 / 2]), JSON.stringify([1, 2, 3, 6]));
C('2-3 q22 cases', JSON.stringify([2 * 0.5, 2 * 2, 2 * 5, 2 * 10]), JSON.stringify([1, 4, 10, 20]));
C('2-3 q26 cases', JSON.stringify([5 / 10, 6 / 4, 10 / 4, 12 / 3, 20 / 2]), JSON.stringify([0.5, 1.5, 2.5, 4, 10]));
C('2-3 q27 a=20/8', 20 / 8, 2.5);
C('2-3 q28 a=10/5', 10 / 5, 2);
C('2-3 q29 cases', JSON.stringify([1 * (2 / 2), 2 * (4 / 2), 3 * (6 / 2), 5 * (8 / 2)]), JSON.stringify([1, 4, 9, 20]));

// 2-1-4
C('2-4 q5 a=10/5', 10 / (2 + 3), 2);
C('2-4 q6 N=3*2', 3 * 2, 6);
C('2-4 q6b check 10-6=2*2', (10 - 6) - (2 * 2), 0);
C('2-4 q7 F=2*10', 2 * 10, 20);
C('2-4 q8 a=15/5', 15 / (1 + 4), 3);
C('2-4 q8b N=4*3', 4 * 3, 12);
C('2-4 q9 N=1*3', 1 * 3, 3);
C('2-4 q13 mg=1*10', 1 * 10, 10);
C('2-4 q15 N=3*2', 3 * 2, 6);
C('2-4 q16 N=4*3', 4 * 3, 12);
C('2-4 q16b 15-12=1*3', (15 - 12) - (1 * 3), 0);
C('2-4 q23 cases', JSON.stringify([0.5 * 10, 2 * 10, 7 * 10, 12 * 10]), JSON.stringify([5, 20, 70, 120]));
C('2-4 q26 cases', JSON.stringify([1 * 2, 2 * 2, 3 * 2, 4 * 2]), JSON.stringify([2, 4, 6, 8]));
C('2-4 q29 cases', JSON.stringify([5 / 5, 10 / 5, 15 / 5, 20 / 5, 30 / 5]), JSON.stringify([1, 2, 3, 4, 6]));
C('2-4 q35 a=10/5', 10 / 5, 2);
C('2-4 q36 N=3*2', 3 * 2, 6);
C('2-4 q37 N=4*3', 4 * 3, 12);

// 2-1-5
const atwA = (m1, m2, g = 10) => (m1 - m2) * g / (m1 + m2);
const atwT = (m1, m2, g = 10) => 2 * m1 * m2 * g / (m1 + m2);
C('2-5 q5 a(3,2)', atwA(3, 2), 2);
C('2-5 q6 T(3,2)', atwT(3, 2), 24);
C('2-5 q6b check 24-20=2*2', (24 - 20) - (2 * 2), 0);
C('2-5 q7 a(4,1)', atwA(4, 1), 6);
C('2-5 q8 T(4,1)', atwT(4, 1), 16);
C('2-5 q9 a table', 2 * 10 / (3 + 2), 4);
C('2-5 q15 a(3,2)', atwA(3, 2), 2);
C('2-5 q15b T(3,2)', atwT(3, 2), 24);
C('2-5 q16 a(4,1)', atwA(4, 1), 6);
C('2-5 q16b T(4,1)', atwT(4, 1), 16);
C('2-5 q17 a table', 2 * 10 / 5, 4);
C('2-5 q17b T=3*4', 3 * 4, 12);
C('2-5 q17c 20-12=2*4', (20 - 12) - (2 * 4), 0);
C('2-5 q18 T=5*12', 5 * (10 + 2), 60);
C('2-5 q22 cases', JSON.stringify([atwA(2, 2), atwA(6, 4), atwA(3, 1), atwA(4, 1)]), JSON.stringify([0, 2, 5, 6]));
C('2-5 q24 cases', JSON.stringify([4 * (10 - 3), 4 * 10, 4 * (10 + 2), 4 * (10 + 5)]), JSON.stringify([28, 40, 48, 60]));
C('2-5 q25 T(3,2)', atwT(3, 2), 24);
C('2-5 q26 cases', JSON.stringify([atwT(4, 1), atwT(3, 2), atwT(7, 3), atwT(6, 4)]), JSON.stringify([16, 24, 42, 48]));
C('2-5 q28 cases', JSON.stringify([2 * 10 / 10, 2 * 10 / 5, 3 * 10 / 5, 4 * 10 / 5]), JSON.stringify([2, 4, 6, 8]));
C('2-5 q35 a(3,2)', atwA(3, 2), 2);
C('2-5 q36 T(3,2)', atwT(3, 2), 24);
C('2-5 q37 a(4,1)', atwA(4, 1), 6);
C('2-5 q39 T=5*12', 5 * 12, 60);

let bad = 0;
for (const c of checks) {
  if (!c.ok) { bad++; console.log('NUMERIC FAIL:', c.label, 'actual=', c.actual, 'expect=', c.expect); }
}
console.log(`numeric checks: ${checks.length}, failed: ${bad}`);
