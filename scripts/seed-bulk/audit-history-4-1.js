'use strict';
// Audit script for history theme 4-1 (revizor). Unique filenames per instructions.
const fs = require('fs');
const path = require('path');

const DIR = path.join(__dirname, 'data', 'school', 'history', 'lessons');
const LESSONS = ['4-1-1', '4-1-2', '4-1-3', '4-1-4', '4-1-5'];
const SNAP = path.join(__dirname, 'snap-history-4-1.json');

const lessons = LESSONS.map((l) => {
  const q = require(path.join(DIR, l + '.js'));
  return { lesson: l, questions: q.map((qq, idx) => ({ idx, lesson: l, q: qq })) };
});
const ALL = [].concat(...lessons.map((l) => l.questions));

// ---------- snapshot ----------
fs.writeFileSync(SNAP, JSON.stringify(lessons, null, 1));
console.log('snapshot written:', SNAP, 'questions:', ALL.length);

// ---------- normalization ----------
const ENDINGS = ['ами','ями','иями','ого','его','ому','ему','ыми','ими','иях','ах','ях','ам','ям','ов','ев','ей','ий','ый','ой','ая','яя','ое','ее','ые','ие','ии','ия','ие','ью','ом','ем','ут','ют','ат','ят','ет','ла','ло','ли','л','ть','ы','и','а','я','е','у','ю','ь'];
function stemTok(w) {
  if (w.length <= 4) return w;
  for (const e of ENDINGS) {
    if (w.length - e.length >= 3 && w.endsWith(e)) return w.slice(0, w.length - e.length);
  }
  return w;
}
function norm(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/«|»|["„“]/g, '')
    .replace(/[^а-яa-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => (/^\d+$/.test(w) ? w : stemTok(w)));
}
function setOf(s) { return new Set(norm(s)); }
function jac(a, b) {
  if (!a.size || !b.size) return 0;
  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  return inter / (a.size + b.size - inter);
}
// remove years and parentheticals for event-level comparison of ord items
function eventNorm(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/\([^)]*\)/g, ' ')
    .replace(/\d{3,4}\s*(г|гг|года|год)?\.?/g, ' ')
    .replace(/[^а-яa-z\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 1)
    .map((w) => (/^\d+$/.test(w) ? w : stemTok(w)))
    .filter((w) => w.length > 3);
}
function evSet(s) { return new Set(eventNorm(s)); }
function evJac(a, b) {
  if (!a.size || !b.size) return 0;
  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  return inter / (a.size + b.size - inter);
}

const KIND = { SingleChoice: 'sc', MultipleChoice: 'mc', Ordering: 'ord', FillBlank: 'fb' };

// ---------- 1. all-pairs near-duplicate detection (stems + option/key sets) ----------
console.log('\n===== A. ПОХОЖИЕ ВОПРОСЫ (стем Jaccard >= 0.55 или совпадение набора вариантов >= 0.6) =====');
for (let i = 0; i < ALL.length; i++) {
  for (let j = i + 1; j < ALL.length; j++) {
    const A = ALL[i], B = ALL[j];
    const ta = KIND[A.q.type], tb = KIND[B.q.type];
    if (!ta || !tb) continue;
    if (A.q.type === 'Ordering' && B.q.type === 'Ordering') continue; // template stems; covered by section C
    const stemA = setOf(A.q.text), stemB = setOf(B.q.text);
    const sj = jac(stemA, stemB);
    // option sets
    const optsA = (A.q.options || A.q.items || A.q.candidates || []).map((o) => o.text);
    const optsB = (B.q.options || B.q.items || B.q.candidates || []).map((o) => o.text);
    const sets = optsA.map((o) => setOf(o));
    let optOverlap = 0;
    for (const o of optsB) {
      const so = setOf(o);
      const best = Math.max(0, ...sets.map((s) => jac(s, so)));
      if (best >= 0.6) optOverlap++;
    }
    const optJac = optsA.length && optsB.length ? optOverlap / Math.min(optsA.length, optsB.length) : 0;
    if (sj >= 0.55 || (optJac >= 0.6 && sj >= 0.35)) {
      console.log(`${ta}-${A.idx}(${A.lesson})  <->  ${tb}-${B.idx}(${B.lesson})  stem=${sj.toFixed(2)} opt=${optJac.toFixed(2)}`);
      console.log('   A:', A.q.text.slice(0, 100));
      console.log('   B:', B.q.text.slice(0, 100));
    }
  }
}

// ---------- 1b. fb vs sc/mc: same stem meaning + same key (main historical defect class) ----------
console.log('\n===== A2. FB vs SC/MC: стем-смысл и ключ =====');
for (const L of lessons) {
  const fbs = L.questions.filter((x) => x.q.type === 'FillBlank');
  const scmcs = L.questions.filter((x) => x.q.type === 'SingleChoice' || x.q.type === 'MultipleChoice');
  for (const f of fbs) {
    const fbKeyCands = f.q.candidates.filter((c) => f.q.blanks.some((b) => b.correctCandidateId === c.id));
    for (const r of scmcs) {
      const sj = jac(setOf(f.q.text), setOf(r.q.text));
      const refKeys = (r.q.correctOptionIds || [r.q.correctOptionId]).map((k) => r.q.options.find((o) => o.id === k).text);
      const keyMatch = fbKeyCands.some((c) => refKeys.some((rk) => jac(setOf(c.text), setOf(rk)) >= 0.6 || rk.toLowerCase().includes(c.text.toLowerCase()) || c.text.toLowerCase().includes(rk.toLowerCase())));
      const candSet = setOf(f.q.candidates.map((c) => c.text).join(' '));
      const optSet = setOf(r.q.options.map((o) => o.text).join(' '));
      const optSim = jac(candSet, optSet);
      if ((sj >= 0.4 && keyMatch) || (keyMatch && optSim >= 0.5)) {
        console.log(`fb-${f.idx} ~ ${KIND[r.q.type]}-${r.idx} (${L.lesson}) stem=${sj.toFixed(2)} opt=${optSim.toFixed(2)} keyMatch=${keyMatch}`);
        console.log('   FB:', f.q.text.slice(0, 110), '→ ключ:', fbKeyCands.map((c) => c.text).join(','));
        console.log('   REF:', r.q.text.slice(0, 110), '→ ключ:', refKeys.join(' | '));
      }
    }
  }
}

// ---------- 2. fb answer embedded in another question's stem/key ----------
console.log('\n===== B. ОТВЕТ fb ВСТРЕЧАЕТСЯ В КЛЮЧЕ/СТЕМЕ другого вопроса того же урока =====');
for (const L of lessons) {
  const fbs = L.questions.filter((x) => x.q.type === 'FillBlank');
  const others = L.questions.filter((x) => x.q.type !== 'FillBlank');
  for (const f of fbs) {
    const correct = f.q.candidates.filter((c) => f.q.blanks.some((b) => b.correctCandidateId === c.id));
    for (const c of correct) {
      const cs = setOf(c.text);
      if (!cs.size) continue;
      for (const o of others) {
        const oStem = setOf(o.q.text);
        const oOpts = (o.q.options || []).map((x) => setOf(x.text));
        const inStem = jac(cs, oStem) >= 0.6;
        const inOpt = oOpts.some((s) => jac(cs, s) >= 0.75);
        if (inStem || inOpt) {
          console.log(`${KIND[o.q.type]}-${o.idx}(${o.lesson}) ${inStem ? 'в СТЕМЕ' : 'в ВАРИАНТЕ-КЛЮЧЕ'} содержит ответ fb-${f.idx}: «${c.text}»`);
          console.log('    stem:', o.q.text.slice(0, 100));
        }
      }
    }
  }
}

// ---------- 3. ord overlaps (theme-wide, >=75% = defect, 60-75% = review) ----------
console.log('\n===== C. ORD-ПЕРЕКРЫТИЯ (совпадение пунктов по смыслу, в % от меньшего набора) =====');
const ords = ALL.filter((x) => x.q.type === 'Ordering');
for (let i = 0; i < ords.length; i++) {
  for (let j = i + 1; j < ords.length; j++) {
    const A = ords[i], B = ords[j];
    const itemsA = A.q.items.map((it) => evSet(it.text));
    const itemsB = B.q.items.map((it) => evSet(it.text));
    const usedB = new Set();
    let shared = 0;
    const sharedTxt = [];
    for (let ai = 0; ai < itemsA.length; ai++) {
      const sa = itemsA[ai];
      for (let bi = 0; bi < itemsB.length; bi++) {
        if (usedB.has(bi)) continue;
        if (evJac(sa, itemsB[bi]) >= 0.5) { shared++; sharedTxt.push(A.q.items[ai].text + ' == ' + B.q.items[bi].text); usedB.add(bi); break; }
      }
    }
    const pct = shared / Math.min(itemsA.length, itemsB.length);
    if (pct >= 0.6) {
      console.log(`ord-${A.idx}(${A.lesson}) <-> ord-${B.idx}(${B.lesson})  shared=${shared}/${Math.min(itemsA.length, itemsB.length)} (${(pct * 100).toFixed(0)}%)`);
      sharedTxt.forEach((t) => console.log('    -', t));
    }
  }
}

// ---------- 3b. ord subsets: items of one ord verbatim inside another ----------
console.log('\n===== C2. ORD-ПОДМНОЖЕСТВА =====');
for (let i = 0; i < ords.length; i++) {
  for (let j = 0; j < ords.length; j++) {
    if (i === j) continue;
    const A = ords[i], B = ords[j];
    const normItemsA = A.q.items.map((it) => it.text.toLowerCase().replace(/ё/g, 'е').replace(/\s+/g, ' ').trim());
    const normItemsB = new Set(B.q.items.map((it) => it.text.toLowerCase().replace(/ё/g, 'е').replace(/\s+/g, ' ').trim()));
    const allIn = normItemsA.every((t) => normItemsB.has(t));
    if (allIn) console.log(`ord-${A.idx}(${A.lesson}) ПОЛНОСТЬЮ входит в ord-${B.idx}(${B.lesson})`);
  }
}

// ---------- 4. ord chronology (years must be non-decreasing) ----------
console.log('\n===== D. ORD-ХРОНОЛОГИЯ (годы) =====');
for (const o of ords) {
  const years = o.q.items.map((it) => {
    const m = it.text.match(/(\d{3,4})\s*(г|гг|года|год)/) || it.text.match(/(\d{3,4})/);
    return m ? parseInt(m[1], 10) : null;
  });
  for (let k = 1; k < years.length; k++) {
    if (years[k - 1] != null && years[k] != null && years[k] < years[k - 1]) {
      console.log(`ord-${o.idx}(${o.lesson}): год убывает: ${o.q.items[k - 1].text} -> ${o.q.items[k].text}`);
    }
  }
}

// ---------- 5. positional references (Cyrillic-safe regex, \b useless for Russian) ----------
console.log('\n===== E. ПОЗИЦИОННЫЕ ССЫЛКИ =====');
const POS_RE = new RegExp('(перв|втор|трет|четв|пят|последн|остальн|предпоследн|средн|предыдущ|следующ)[а-яё]*\\s+(вариант|пункт|утвержден|шаг|строк|элемент|из\\s+них|позиции|часть)', 'i');
const POS_RE2 = new RegExp('вариант\\s+[абвгд]', 'i');
const POS_RE3 = new RegExp('соответственно', 'i');
const POS_RE4 = new RegExp('в (перв|втор|трет|последн)[а-яё]* (пункт|вариант|строке|шаге)', 'i');
for (const x of ALL) {
  const texts = [x.q.info, ...((x.q.options || x.q.items || []).map((o) => o.text)), ...(x.q.candidates || []).map((c) => c.text)];
  for (const t of texts) {
    if (!t) continue;
    const m = t.match(POS_RE) || t.match(POS_RE2) || t.match(POS_RE3) || t.match(POS_RE4);
    if (m) console.log(`${KIND[x.q.type]}-${x.idx}(${x.idx >= 20 && x.idx < 30 ? 'ord' : ''}${x.lesson}): «${m[0]}» в: ${t.slice(0, 90)}`);
  }
}

// ---------- 6. key length giveaway (sc) ----------
console.log('\n===== F. SC: КЛЮЧ ДЛИННЕЕ ДИСТРАКТОРОВ >1.5x =====');
for (const x of ALL.filter((q) => q.q.type === 'SingleChoice')) {
  const key = x.q.options.find((o) => o.id === x.q.correctOptionId);
  const dis = x.q.options.filter((o) => o.id !== x.q.correctOptionId).map((o) => o.text.length);
  const ratio = key.text.length / Math.max(...dis);
  if (ratio > 1.5) console.log(`sc-${x.idx}(${x.lesson}): key=${key.text.length} maxDis=${Math.max(...dis)} ratio=${ratio.toFixed(2)} «${key.text.slice(0, 80)}»`);
  // скобки только у ключа
  if (/\(/.test(key.text) && dis.every((d) => true) && x.q.options.filter((o) => o.id !== x.q.correctOptionId).every((o) => !/\(/.test(o.text))) {
    console.log(`sc-${x.idx}(${x.lesson}): скобки только у ключа: «${key.text}»`);
  }
}

// ---------- 7. duplicate options inside mc ----------
console.log('\n===== G. MC: ДУБЛИ ВАРИАНТОВ / НЕОДНОЗНАЧНОСТИ =====');
for (const x of ALL.filter((q) => q.q.type === 'MultipleChoice')) {
  const seen = {};
  for (const o of x.q.options) {
    const k = norm(o.text).join(' ');
    if (seen[k]) console.log(`mc-${x.idx}(${x.lesson}): дубли вариантов «${o.text}» и «${seen[k]}»`);
    else seen[k] = o.text;
  }
}

// ---------- 8. fb: duplicate candidates ----------
console.log('\n===== H. FB: ДУБЛИ КАНДИДАТОВ =====');
for (const x of ALL.filter((q) => q.q.type === 'FillBlank')) {
  const seen = {};
  for (const c of x.q.candidates) {
    const k = norm(c.text).join(' ');
    if (seen[k]) console.log(`fb-${x.idx}(${x.lesson}): дубли кандидатов «${c.text}» и «${seen[k]}»`);
    else seen[k] = c.text;
  }
  const nBlanks = (x.q.text.match(/___/g) || []).length;
  if (nBlanks !== x.q.blanks.length) console.log(`fb-${x.idx}(${x.lesson}): пропусков ${nBlanks}, blanks ${x.q.blanks.length}`);
}

console.log('\n===== done =====');
