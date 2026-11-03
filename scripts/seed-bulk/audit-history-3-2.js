'use strict';
// Ревизия темы history 3-2 (уникальное имя — каталог общий).
// Проверки: (а) fb↔sc/mc дубли (стем+ключ), (б/в) перекрытие ord-пунктов внутри темы,
// (г) позиционные ссылки (кириллический регэксп), (д) длина ключа sc vs дистракторы,
// (и) хронология внутри ord, (з) дубли опций mc / пустые тексты.
const path = require('path');

const LESSONS = ['3-2-1', '3-2-2', '3-2-3', '3-2-4', '3-2-5'];
const base = __dirname;
const mode = process.argv[2] || 'audit';
const snapPath = path.join(base, `snap-history-3-2.json`);

const norm = (s) => String(s || '').toLowerCase().replace(/[«»"()]/g, ' ').replace(/[ё]/g, 'е').replace(/[^a-zа-я0-9\s-]/gi, ' ').replace(/\s+/g, ' ').trim();
const STOP = new Set(['в','на','за','из','по','для','от','до','и','или','а','но','не','был','была','было','были','это','этот','эта','этих','своей','своего','тому','того','же','ли','об','о','у','с','со','при','после','перед','между','год','года','году','годы','годов','гг','г','ок','около','что','какой','какая','какие','который','которые','кто','где','когда','чем','как','каким','какого','из','под','над','через','их','его','её','ее','они','они','ним','ней','все','все','первый','известный','известна','известно','как','называют','называется','относится','относятся','верно','верны','верное','относится']);
const stem = (w) => w.slice(0, Math.max(4, Math.min(w.length, 6)));
const tokens = (s) => norm(s).split(' ').filter((w) => w.length > 1 && !STOP.has(w)).map(stem);
const jac = (a, b) => { const A = new Set(a), B = new Set(b); if (!A.size || !B.size) return 0; let n = 0; for (const x of A) if (B.has(x)) n++; return n / (A.size + B.size - n); };
const sim = (x, y) => jac(tokens(x), tokens(y));

function loadAll() {
  const out = [];
  for (const l of LESSONS) {
    delete require.cache[require.resolve(path.join(base, 'data/school/history/lessons', `${l}.js`))];
    const a = require(path.join(base, 'data/school/history/lessons', `${l}.js`));
    a.forEach((q, i) => out.push({ lesson: l, idx: i, q }));
  }
  return out;
}

const all = loadAll();
const findings = { fbDup: [], ordOverlap: [], ordSubset: [], pos: [], keyLen: [], mcDupOpt: [], chron: [], empty: [] };

// --- (а) fb против sc/mc того же урока: сходный стем + совпадающий ключ
for (const fb of all.filter((x) => x.q.type === 'FillBlank')) {
  const fbStem = fb.q.text.replace(/___/g, ' ');
  const fbKey = fb.q.candidates.find((c) => c.id === fb.q.blanks[0].correctCandidateId);
  const fbKeyText = String(fbKey ? fbKey.text : '');
  for (const other of all.filter((x) => x.lesson === fb.lesson && x.idx < 30 && (x.q.type === 'SingleChoice' || x.q.type === 'MultipleChoice'))) {
    const stemSim = sim(fbStem, other.q.text);
    if (stemSim < 0.34) continue;
    const keyHit = other.q.type === 'SingleChoice'
      ? (() => { const o = other.q.options.find((op) => op.id === other.q.correctOptionId); return o ? sim(fbKeyText, o.text) : 0; })()
      : Math.max(...other.q.options.filter((op) => other.q.correctOptionIds.includes(op.id)).map((op) => sim(fbKeyText, op.text)), 0);
    findings.fbDup.push(`${fb.lesson} fb#${fb.idx} ↔ ${other.q.type === 'SingleChoice' ? 'sc' : 'mc'}#${other.idx} stem=${stemSim.toFixed(2)} key=${keyHit.toFixed(2)} | fbKey="${fbKeyText.slice(0, 40)}"`);
  }
}

// --- (б/в) перекрытие пунктов ord внутри темы (пары ≥75 % и полные подмножества)
const ords = all.filter((x) => x.q.type === 'Ordering');
const matchItems = (A, B) => A.map((a) => B.some((b) => sim(a, b) >= 0.5));
for (let i = 0; i < ords.length; i++) for (let j = i + 1; j < ords.length; j++) {
  const A = ords[i], B = ords[j];
  if (A.lesson === B.lesson && A.idx === B.idx) continue;
  const aT = A.q.items.map((x) => x.text), bT = B.q.items.map((x) => x.text);
  const aM = matchItems(aT, bT), bM = matchItems(bT, aT);
  const shared = Math.max(aM.filter(Boolean).length, bM.filter(Boolean).length);
  const pct = shared / Math.min(aT.length, bT.length);
  if (pct >= 0.75) findings.ordOverlap.push(`${A.lesson} ord#${A.idx} ∩ ${B.lesson} ord#${B.idx}: ${shared}/${Math.min(aT.length, bT.length)} = ${(pct * 100) | 0}% (a→b ${aM.filter(Boolean).length}/${aT.length}, b→a ${bM.filter(Boolean).length}/${bT.length})`);
  if (aM.every(Boolean) && aT.length <= bT.length) findings.ordSubset.push(`${A.lesson} ord#${A.idx} ⊆ ${B.lesson} ord#${B.idx}`);
  if (bM.every(Boolean) && bT.length < aT.length) findings.ordSubset.push(`${B.lesson} ord#${B.idx} ⊆ ${A.lesson} ord#${A.idx}`);
}

// --- (г) позиционные ссылки (свой регэксп с [А-Яа-яЁё] — гейтовский мёртв для кириллицы)
const POS_RE = new RegExp('[Пп]ерв(ый|ая|ое|ые|ых|ом|ую)|[Вв]тор(ой|ая|ое|ые|ых|ом|ую)|[Тт]рет(ий|ья|ье|ьи|ьих|ьем|ью)|[Чч]етв[её]рт\\w*|[Пп]ят\\w*|[Пп]оследн\\w*|[Пп]редпоследн\\w*|[Оо]стальн\\w*|[Оо]ставш\\w*|[Сс]оответственн\\w*', 'g');
const POS_NOUN = /(вариант|пункт|шаг|ответ|утверждени|запис|строк|уравнени|формул|случа|объект|элемент|позици|список|произведени|событи|документ)/;
const LET_REF = /[Вв]ариант[а-я]*\s*[«"']?[абвгд][»"']?|[Пп]ункт[а-я]*\s*[«"']?[абвгд][»"']?/;
for (const { lesson, idx, q } of all) {
  const fields = [['text', q.text], ['info', q.info]];
  (q.options || []).forEach((o) => fields.push(['opt:' + o.id, o.text]));
  (q.items || []).forEach((o) => fields.push(['item:' + o.id, o.text]));
  (q.candidates || []).forEach((o) => fields.push(['cand:' + o.id, o.text]));
  for (const [name, val] of fields) {
    const s = String(val || '');
    if (!s) { findings.empty.push(`${lesson} q${idx} ${name}`); continue; }
    for (const m of s.matchAll(POS_RE)) {
      const tail = s.slice(m.index, m.index + 70);
      if (POS_NOUN.test(tail)) { findings.pos.push(`${lesson} q${idx} [${name}]: …${tail.replace(/\s+/g, ' ')}`); break; }
    }
    if (LET_REF.test(s)) findings.pos.push(`${lesson} q${idx} [${name}]: letter-ref …${s.slice(0, 60)}`);
  }
}

// --- (д) ключ sc длиннее дистракторов в 1.5+ раза (или скобки только у ключа)
for (const { lesson, idx, q } of all.filter((x) => x.q.type === 'SingleChoice')) {
  const key = q.options.find((o) => o.id === q.correctOptionId);
  const dis = q.options.filter((o) => o.id !== q.correctOptionId);
  const kl = key.text.length;
  const maxDis = Math.max(...dis.map((o) => o.text.length));
  const ratio = kl / Math.max(1, maxDis);
  if (ratio >= 1.5) findings.keyLen.push(`${lesson} sc#${idx}: key ${kl} vs maxDistractor ${maxDis} (×${ratio.toFixed(2)}) key="${key.text.slice(0, 70)}"`);
}

// --- (з) одинаковые / почти одинаковые опции внутри mc
for (const { lesson, idx, q } of all.filter((x) => x.q.type === 'MultipleChoice')) {
  for (let i = 0; i < q.options.length; i++) for (let j = i + 1; j < q.options.length; j++) {
    if (sim(q.options[i].text, q.options[j].text) >= 0.75) findings.mcDupOpt.push(`${lesson} mc#${idx}: "${q.options[i].text}" ≈ "${q.options[j].text}"`);
  }
}

// --- (и) хронология внутри ord (по первому году в пункте)
const YEAR = /(\d{3,4})/;
for (const { lesson, idx, q } of ords) {
  let prev = -1, ok = true;
  for (const it of q.items) {
    const m = YEAR.exec(it.text);
    if (!m) { findings.chron.push(`${lesson} ord#${idx}: пункт без года: "${it.text}"`); ok = false; continue; }
    const y = +m[1];
    if (y <= prev) { findings.chron.push(`${lesson} ord#${idx}: хронология нарушена (${prev} → ${y})`); ok = false; }
    prev = y;
  }
}

// --- снимок
const snap = {};
for (const l of LESSONS) {
  delete require.cache[require.resolve(path.join(base, 'data/school/history/lessons', `${l}.js`))];
  snap[l] = require(path.join(base, 'data/school/history/lessons', `${l}.js`)).map((q) => ({ t: q.type, d: q.difficulty, text: q.text, o: (q.options || q.items || q.candidates || []).map((x) => x.text), k: q.correctOptionId || q.correctOptionIds || (q.blanks ? q.blanks[0].correctCandidateId + ':' + q.candidates.find((c) => c.id === q.blanks[0].correctCandidateId).text : null), info: q.info }));
}
require('fs').writeFileSync(snapPath, JSON.stringify(snap, null, 1));

// --- отчёт
console.log(`== fb↔sc/mc пары со схожим стемом (≥0.34; подозрительные — с key≥0.5) ==`);
for (const f of findings.fbDup) console.log('  ' + f);
console.log(`== ord-пары с перекрытием ≥75% ==`);
for (const f of findings.ordOverlap) console.log('  ' + f);
console.log(`== ord-подмножества ==`);
for (const f of findings.ordSubset) console.log('  ' + f);
console.log(`== позиционные ссылки ==`);
for (const f of findings.pos) console.log('  ' + f);
console.log(`== sc: ключ ≥1.5× длиннее самого длинного дистрактора ==`);
for (const f of findings.keyLen) console.log('  ' + f);
console.log(`== mc: почти совпадающие опции ==`);
for (const f of findings.mcDupOpt) console.log('  ' + f);
console.log(`== ord: хронология/даты ==`);
for (const f of findings.chron) console.log('  ' + f);
if (findings.empty.length) { console.log(`== пустые тексты ==`); for (const f of findings.empty) console.log('  ' + f); }
console.log(`snap → ${snapPath}`);
