'use strict';
// Аудит темы chemistry 6-4 (ревизор). Уникальное имя — каталог общий.
// 1) снапшот всех вопросов темы -> snap-chemistry-6-4.json
// 2) точные и почти-дубли (гейт-style + Jaccard по вариантам + одинаковые стемы)
// 3) позиционные ссылки своим кириллическим регэкспом (гейт-регэксп мёртв для \b/\w на кириллице)
// 4) оформление sc: ключ заметно длиннее дистракторов
const fs = require('fs');
const path = require('path');
const BASE = path.join(__dirname, 'data', 'school', 'chemistry');
const LESSONS = [1, 2, 3, 4, 5];

const norm = (x) => String(x || '').toLowerCase().replace(/\s+/g, ' ').trim();
const opts = (q) => ['options', 'items', 'candidates'].flatMap((a) => (q[a] || []).map((e) => String(e.text)));

const all = [];
for (const l of LESSONS) {
  delete require.cache[require.resolve(path.join(BASE, 'lessons', `6-4-${l}.js`))];
  const qs = require(path.join(BASE, 'lessons', `6-4-${l}.js`));
  qs.forEach((q, i) => all.push({ lesson: l, i, q }));
}
fs.writeFileSync(path.join(__dirname, 'snap-chemistry-6-4.json'), JSON.stringify(all.map(({ lesson, i, q }) => ({ id: `6-4-${lesson}-q${i}`, ...pick(q) })), null, 1));
function pick(q) {
  return {
    type: q.type, difficulty: q.difficulty, text: q.text,
    options: q.options || q.items || q.candidates,
    key: q.correctOptionId || q.correctOptionIds || (q.blanks ? q.blanks.map((b) => b.correctCandidateId) : undefined),
    info: q.info,
  };
}

console.log('=== 1. ТОЧНЫЕ ДУБЛИ (гейт-style) ===');
{
  const map = new Map();
  for (const { lesson, i, q } of all) {
    const body = opts(q).map(norm).sort().join('|');
    const k = norm(q.text) + '::' + body;
    if (map.has(k)) console.log(`6-4-${lesson} q${i} == ${map.get(k)} (текст + варианты)`);
    else map.set(k, `6-4-${lesson} q${i}`);
  }
  console.log('(пусто = точных дублей нет)');
}

console.log('\n=== 2. ОДИНАКОВЫЕ СТЕМЫ (нормализованный текст вопроса) ===');
{
  const map = new Map();
  for (const { lesson, i, q } of all) {
    const k = norm(q.text).replace(/___/g, '@');
    if (map.has(k)) console.log(`6-4-${lesson} q${i} ~ ${map.get(k)} :: «${q.text}»`);
    else map.set(k, `6-4-${lesson} q${i}`);
  }
  console.log('(пусто = одинаковых стемов нет)');
}

console.log('\n=== 3. ПОЧТИ-ДУБЛИ: пересечение вариантов (Jaccard>=0.35) или подмножество >=3 ===');
{
  for (let a = 0; a < all.length; a++) for (let b = a + 1; b < all.length; b++) {
    const A = all[a], B = all[b];
    if (A.q.type !== B.q.type) continue;
    const sa = new Set(opts(A.q).map(norm)), sb = new Set(opts(B.q).map(norm));
    let inter = 0; for (const x of sa) if (sb.has(x)) inter++;
    const jac = inter / (sa.size + sb.size - inter);
    const subset = (inter === Math.min(sa.size, sb.size)) && Math.min(sa.size, sb.size) >= 3;
    if (jac >= 0.35 || subset) {
      console.log(`6-4-${A.lesson} q${A.i} ~ 6-4-${B.lesson} q${B.i} :: jac=${jac.toFixed(2)} inter=${inter} [${sa.size}vs${sb.size}] :: «${A.q.text.slice(0, 60)}» / «${B.q.text.slice(0, 60)}»`);
    }
  }
  console.log('(конец списка)');
}

console.log('\n=== 4. fb: одинаковые наборы кандидатов ===');
{
  const fbs = all.filter(({ q }) => q.type === 'FillBlank');
  for (let a = 0; a < fbs.length; a++) for (let b = a + 1; b < fbs.length; b++) {
    const A = fbs[a], B = fbs[b];
    const sa = opts(A.q).map(norm).sort().join('|'), sb = opts(B.q).map(norm).sort().join('|');
    if (sa === sb) console.log(`6-4-${A.lesson} q${A.i} ~ 6-4-${B.lesson} q${B.i} :: одинаковые кандидаты :: «${A.q.text}» / «${B.q.text}»`);
  }
  console.log('(конец списка)');
}

console.log('\n=== 5. ПОЗИЦИОННЫЕ ССЫЛКИ (кириллический регэксп) ===');
const ORD1 = /(?<![А-Яа-яЁё])(?:перв|втор|трет|четвёрт|пят|шест|седьм|восьм|последн|предпоследн|остальн|оставш)[а-яё]*\s+(?:вариант|пункт|шаг|утверждени|запис|уравнени|формул|строк|действи|наблюдени|этап|стади|проб|ответ|позици|часть|половин|групп)/gi;
const NUMORD = /(?<![А-Яа-яЁё])(?:два|две|три|четыре|пять)\s+(?:последн|перв|втор|остальн|оставш|друг)[а-яё]*\s+(?:вариант|пункт|шаг|утверждени|запис|уравнени|строк|проб|раствор|веществ)/gi;
const LETTER = /(?:вариант|пункт|шаг|столбец)[а-яё]*\s+[«"']?[абвгдеabcdef][»"']?(?![а-яё])/gi;
const STEPNUM = /(?:шаг|пункт|действи|стадия|этап)[а-яё]*\s*[№]?\s*\d/gi;
for (const { lesson, i, q } of all) {
  const fields = [['info', q.info], ...opts(q).map((t, j) => [`opt${j}`, t])];
  for (const [f, txt] of fields) {
    if (!txt) continue;
    for (const re of [ORD1, NUMORD, LETTER, STEPNUM]) {
      re.lastIndex = 0;
      let m;
      while ((m = re.exec(String(txt)))) {
        console.log(`6-4-${lesson} q${i} [${f}] ${re.source.slice(0, 30)}… → «${String(txt).slice(Math.max(0, m.index - 30), m.index + 45).replace(/\n/g, ' ')}»`);
        break;
      }
    }
  }
}
console.log('(конец списка)');

console.log('\n=== 6. ОФОРМЛЕНИЕ sc: ключ заметно длиннее дистракторов (ratio>=1.7) ===');
for (const { lesson, i, q } of all) {
  if (q.type !== 'SingleChoice') continue;
  const correct = q.options.find((o) => o.id === q.correctOptionId);
  const distr = q.options.filter((o) => o.id !== q.correctOptionId);
  const cl = correct.text.length;
  const dmax = Math.max(...distr.map((o) => o.text.length));
  if (cl >= dmax * 1.7) {
    console.log(`6-4-${lesson} q${i} :: ключ ${cl} симв., макс. дистрактор ${dmax} :: «${correct.text}»`);
  }
}
console.log('(конец списка)');
