'use strict';
// Аудит темы history 2-3 (ревизор). Уникальное имя: audit-history-2-3.js
// Проверки:
//  1) fb-вопросы, повторяющие sc/mc того же урока (нормализация русских основ + Jaccard по вариантам);
//  2) пересечения ord (по годам и по совпадению пунктов) — ≥75% или вложенность;
//  3) позиционные ссылки (кириллический регэксп, свой);
//  4) оформление: ключ sc заметно длиннее дистракторов.
const path = require('path');
const base = path.join(__dirname, 'data', 'school', 'history');

const lessons = [1, 2, 3, 4, 5].map((l) => {
  delete require.cache[require.resolve(path.join(base, 'lessons', `2-3-${l}.js`))];
  return { l, qs: require(path.join(base, 'lessons', `2-3-${l}.js`)) };
});

// ---------- утилиты ----------
const words = (s) => String(s || '').toLowerCase().replace(/[^а-яёa-z0-9]+/gi, ' ').split(' ').filter(Boolean);
// грубая нормализация русской основы: обрезаем окончания 2+ символов
const stem = (w) => (w.length > 5 ? w.slice(0, w.length - 2) : w);
const normSet = (s) => new Set(words(s).map(stem));
function jac(a, b) {
  const A = normSet(a), B = normSet(b);
  if (!A.size || !B.size) return 0;
  let inter = 0; for (const x of A) if (B.has(x)) inter++;
  return inter / (A.size + B.size - inter);
}
const years = (s) => {
  // диапазон «1096–1099» — одно событие, берём только начальный год
  const m = String(s).match(/\d{3,4}(?:\s*[–—-]\s*\d{3,4})?/g) || [];
  const out = new Set();
  for (const x of m) { const [a] = x.split(/[–—-]/).map((y) => +y.trim()); out.add(a); }
  return out;
};
const stages = (s) => {
  const m = String(s).match(/[IVX]+\s*вв?\.?/g) || [];
  return m.map((x) => x.replace(/\s+/g, ''));
};

// ---------- 1) дубли fb <-> sc/mc ----------
console.log('=== 1. Дубли fb <-> sc/mc (стем-смысл >= 0.55 ИЛИ совпадение варианта-ключа при близком стеме) ===');
for (const { l, qs } of lessons) {
  const keyOf = (q) => {
    if (q.type === 'SingleChoice') {
      return (q.options || []).filter((o) => o.id === q.correctOptionId).map((o) => o.text).join(' ');
    }
    if (q.type === 'MultipleChoice') {
      const ids = q.correctOptionIds || [];
      return (q.options || []).filter((o) => ids.includes(o.id)).map((o) => o.text).join(' ');
    }
    return '';
  };
  const scmc = qs.map((q, i) => ({ i, q, key: keyOf(q) })).filter((x) => x.key);
  qs.forEach((q, i) => {
    if (q.type !== 'FillBlank') return;
    const correct = (q.candidates || []).filter((c) => (q.blanks || []).some((b) => b.correctCandidateId === c.id)).map((c) => c.text).join(' ');
    const cands = (q.candidates || []).map((c) => c.text).join(' ');
    for (const { i: j, q: r, key } of scmc) {
      const stemSim = jac(q.text, r.text);
      const optSim = jac(cands, (r.options || []).map((o) => o.text).join(' '));
      const keyWordHit = stemSim > 0.35 && words(correct).some((w) => words(key).some((k) => stem(k) === stem(w)));
      if (stemSim >= 0.55 || (keyWordHit && optSim >= 0.5) || (keyWordHit && stemSim >= 0.45)) {
        console.log(`  урок ${l}: fb q${i} <-> ${r.type === 'SingleChoice' ? 'sc' : 'mc'} q${j}  stem=${stemSim.toFixed(2)} opts=${optSim.toFixed(2)}`);
      }
    }
  });
}

// ---------- 2) пересечения ord внутри урока и темы ----------
console.log('\n=== 2. Пересечения ord (доля общих пунктов; >=0.75 или вложенность — дефект) ===');
const ords = [];
for (const { l, qs } of lessons) qs.forEach((q, i) => { if (q.type === 'Ordering') ords.push({ l, i, items: (q.items || []).map((it) => it.text) }); });
const overlap = (a, b) => {
  const A = new Set(a.map((x) => x.toLowerCase().replace(/\s+/g, ' ').trim()));
  const B = new Set(b.map((x) => x.toLowerCase().replace(/\s+/g, ' ').trim()));
  let inter = 0; for (const x of A) if (B.has(x)) inter++;
  return inter / Math.min(A.size, B.size);
};
const yearOverlap = (a, b) => {
  const A = new Set([...years(a.join(' ')), ...stages(a.join(' '))]);
  const B = new Set([...years(b.join(' ')), ...stages(b.join(' '))]);
  let inter = 0; for (const x of A) if (B.has(x)) inter++;
  const min = Math.min(A.size, B.size);
  return min ? inter / min : 0;
};
for (let i = 0; i < ords.length; i++) for (let j = i + 1; j < ords.length; j++) {
  const a = ords[i], b = ords[j];
  const same = overlap(a.items, b.items), yr = yearOverlap(a.items, b.items);
  if (same >= 0.75 || yr >= 0.75) console.log(`  l${a.l} q${a.i} <-> l${b.l} q${b.i}: text=${same.toFixed(2)} years=${yr.toFixed(2)}`);
}

// ---------- 3) позиционные ссылки (кириллица, свой регэксп) ----------
console.log('\n=== 3. Позиционные ссылки в info/вариантах ===');
const POS = new RegExp('(?:[А-Яа-яЁё]*перв|[А-Яа-яЁё]*втор|[А-Яа-яЁё]*трет|четв[её]рт|пят|последн|предпоследн|остальн|оставш)[а-яё]*\\s+(?:вариант|пункт|шаг|ответ|утверждени|запис|строк|событ|элемент|позици|граф|столбц)[а-яё]*', 'i');
const POS2 = /соответственн[а-яё]*|первые\s+три|последние\s+(?:два|три|четыре|пять)|в\s+четвёрт[а-яё]*\s+(?:запис|строк|пункт|вариант)/i;
for (const { l, qs } of lessons) qs.forEach((q, i) => {
  const info = String(q.info || '');
  const opt = (q.options || []).map((o) => o.text).join(' ');
  if (POS.test(info) || POS2.test(info)) console.log(`  l${l} q${i} info: «${(info.match(POS) || info.match(POS2) || [''])[0]}»`);
  if (POS.test(opt)) console.log(`  l${l} q${i} options: «${opt.match(POS)[0]}»`);
});

// ---------- 4) оформление: ключ sc длиннее дистракторов ----------
console.log('\n=== 4. Ключ sc заметно длиннее дистракторов (>1.5x к самому длинному) ===');
for (const { l, qs } of lessons) qs.forEach((q, i) => {
  if (q.type !== 'SingleChoice') return;
  const corr = (q.options || []).filter((o) => o.id === q.correctOptionId);
  const wrong = (q.options || []).filter((o) => o.id !== q.correctOptionId);
  if (!corr.length || !wrong.length) return;
  const klen = Math.max(...corr.map((o) => o.text.length));
  const wmax = Math.max(...wrong.map((o) => o.text.length));
  if (klen > wmax * 1.5) console.log(`  l${l} q${i}: ключ ${klen} симв. vs макс. дистрактор ${wmax} — «${corr[0].text.slice(0, 60)}…»`);
});

// ---------- 5) хронология внутри ord (годы должны возрастать) ----------
console.log('\n=== 5. Монотонность лет в ord (по напечатанным датам) ===');
for (const { l, qs } of lessons) qs.forEach((q, i) => {
  if (q.type !== 'Ordering') return;
  const ys = (q.items || []).map((it) => {
    const m = String(it.text).match(/(\d{3,4})/); return m ? +m[1] : null;
  });
  const nums = ys.filter((y) => y !== null);
  for (let k = 1; k < nums.length; k++) if (nums[k] < nums[k - 1]) console.log(`  l${l} q${i}: год убывает: ${nums[k - 1]} -> ${nums[k]}`);
});
console.log('\n(audit-history-2-3.js: готово)');
