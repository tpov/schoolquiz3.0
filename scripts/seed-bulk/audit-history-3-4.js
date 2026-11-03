'use strict';
// Audit tool for history theme 3-4 (unique to this task; other agents use their own files).
// Usage:
//   node audit-history-3-4.js snap    — write BEFORE snapshot to snap-history-3-4.json
//   node audit-history-3-4.js check   — analysis: fb/sc-mc dups, ord overlap, positional refs, key length
//   node audit-history-3-4.js diff    — compare current lessons vs snapshot (changed questions only)

const fs = require('fs');
const path = require('path');

const LESSONS_DIR = path.join(__dirname, 'data', 'school', 'history', 'lessons');
const SNAP = path.join(__dirname, 'snap-history-3-4.json');
const LESSON_IDS = ['3-4-1', '3-4-2', '3-4-3', '3-4-4', '3-4-5'];

function loadLesson(id) {
  const mod = require(path.join(LESSONS_DIR, id + '.js'));
  return mod.map((q, i) => ({ i, q }));
}

function loadAll() {
  const out = {};
  for (const id of LESSON_IDS) out[id] = loadLesson(id);
  return out;
}

// ---- normalization -------------------------------------------------------

const FILLER = new Set([
  'в', 'во', 'и', 'с', 'со', 'а', 'но', 'да', 'или', 'либо', 'что', 'как', 'это',
  'из', 'от', 'до', 'по', 'за', 'на', 'о', 'об', 'при', 'для', 'у', 'к', 'ко',
  'не', 'ни', 'бы', 'же', 'ли', 'то', 'так', 'такой', 'такая', 'такое', 'своей',
  'своего', 'свою', 'который', 'которая', 'которое', 'которых', 'их', 'его', 'её',
  'опять', 'кто', 'кого', 'чем', 'том', 'та', 'те', 'этот', 'эта', 'эти', 'этим',
  'был', 'была', 'было', 'были', 'стал', 'стала', 'стало', 'стали', 'называется',
  'называются', 'называли', 'называл', 'когда', 'какой', 'какая', 'какие', 'где',
  'куда', 'почему', 'зачем', 'чем', 'про', 'под', 'над', 'без', 'через', 'между',
  'самый', 'самая', 'самое', 'все', 'всё', 'всех', 'наиболее', 'более', 'менее',
  'один', 'одна', 'одно', 'два', 'три', 'year',
]);

function words(s) {
  return String(s)
    .toLowerCase()
    .replace(/[«»"'().,;:!?—–\-]/g, ' ')
    .split(/\s+/)
    .map((w) => w.replace(/ё/g, 'е'))
    .filter((w) => w.length >= 3 && !FILLER.has(w));
}

function stemKey(s) {
  const ws = words(s);
  // crude stemming for Russian: cut common adjective/noun endings
  return ws
    .map((w) => w.replace(/(ами|ями|ов|ев|ах|ях|ой|ий|ый|ая|ое|ые|ых|ому|ему|ым|им|ом|ем|ую|юю|ию|ия|ье|ья|ии|ей|ов|у|ю|е|а|я|и|ы)$/, ''))
    .join('|');
}

function jaccard(aSet, bSet) {
  const a = new Set(aSet);
  const b = new Set(bSet);
  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  const uni = new Set([...a, ...b]).size;
  return uni === 0 ? 0 : inter / uni;
}

function textsOf(q) {
  if (q.options) return q.options.map((o) => o.text);
  if (q.items) return q.items.map((o) => o.text);
  if (q.candidates) return q.candidates.map((o) => o.text);
  return [];
}

function correctKeys(q) {
  if (q.type === 'SingleChoice') return [q.options.find((o) => o.id === q.correctOptionId).text];
  if (q.type === 'MultipleChoice') return q.correctOptionIds.map((id) => q.options.find((o) => o.id === id).text);
  return [];
}

function posRefHits(s) {
  const re = /(?:перв|втор|трет|четв|последн|остальн)[а-яё]*\s*(?:два|три|четыре|пять)?\s*(?:вариант|пункт|утверждени|строк|шаг|позици|пара)[а-яё]*/gi;
  const hits = s.match(re) || [];
  const sootv = /соответственн[а-яё]*/i.test(s) ? ['соответственно'] : [];
  return [...hits, ...sootv];
}

// ---- analysis ------------------------------------------------------------

function analyze() {
  const data = loadAll();

  console.log('=== 1. fb <-> sc/mc near-duplicates (same lesson) ===');
  for (const lid of LESSON_IDS) {
    const qs = data[lid];
    const fbs = qs.filter(({ q }) => q.type === 'FillBlank');
    const upper = qs.filter(({ q }) => q.type === 'SingleChoice' || q.type === 'MultipleChoice');
    for (const fb of fbs) {
      const fbCands = fb.q.candidates.map((c) => c.text.toLowerCase());
      const fbCorrect = fb.q.blanks[0].correctCandidateId;
      const fbCorrectText = fb.q.candidates.find((c) => c.id === fbCorrect).text.toLowerCase();
      for (const up of upper) {
        const keys = correctKeys(up.q).map((t) => t.toLowerCase());
        const keyContainsFbCorrect = keys.some(
          (k) => k.includes(fbCorrectText) || fbCorrectText.includes(k) || (words(k).length && words(fbCorrectText).length && stemKey(k) === stemKey(fbCorrectText))
        );
        const optJ = jaccard(fbCands.map(stemKey), up.q.options.map((o) => stemKey(o.text)));
        const stemJ = jaccard(stemKey(fb.q.text).split('|'), stemKey(up.q.text).split('|'));
        const stemFull = jaccard(words(fb.q.text), words(up.q.text));
        if (keyContainsFbCorrect && (optJ > 0.3 || stemJ > 0.35 || stemFull > 0.4)) {
          console.log(
            `[${lid}] fb#${fb.i} vs ${up.q.type === 'SingleChoice' ? 'sc' : 'mc'}#${up.i} ` +
              `optJ=${optJ.toFixed(2)} stemJ=${stemJ.toFixed(2)} full=${stemFull.toFixed(2)}\n` +
              `   FB: ${fb.q.text}\n   UP: ${up.q.text}`
          );
        }
      }
    }
  }

  console.log('\n=== 2. ord pairwise item overlap (>= 75%), dates stripped ===');
  for (const lid of LESSON_IDS) {
    const ords = data[lid].filter(({ q }) => q.type === 'Ordering');
    for (let a = 0; a < ords.length; a++) {
      for (let b = a + 1; b < ords.length; b++) {
        const strip = (it) => stemKey(String(it.text).replace(/\([^)]*\)/g, ' ').replace(/\b\d[\d–—-]*\b/g, ' '));
        const A = ords[a].q.items.map(strip);
        const B = ords[b].q.items.map(strip);
        let inter = 0;
        for (const x of A) if (B.includes(x)) inter++;
        const ratio = inter / Math.min(A.length, B.length);
        if (ratio >= 0.75) {
          console.log(`[${lid}] ord#${ords[a].i} vs ord#${ords[b].i} overlap=${(ratio * 100) | 0}% (${inter}/${Math.min(A.length, B.length)})`);
        } else {
          // fuzzy event-level pass: items match if word-set Jaccard >= 0.5
          const W = (it) => new Set(stemKey(String(it.text).replace(/\([^)]*\)/g, ' ')).split('|').filter(Boolean));
          const AW = ords[a].q.items.map(W);
          const BW = ords[b].q.items.map(W);
          const used = new Set();
          let finter = 0;
          for (const wa of AW) {
            for (let bi = 0; bi < BW.length; bi++) {
              if (used.has(bi)) continue;
              let inter = 0;
              for (const w of wa) if (BW[bi].has(w)) inter++;
              const uni = new Set([...wa, ...BW[bi]]).size;
              if (uni > 0 && inter / uni >= 0.5) { finter++; used.add(bi); break; }
            }
          }
          const fratio = finter / Math.min(AW.length, BW.length);
          if (fratio >= 0.75) {
            console.log(`[${lid}] ord#${ords[a].i} vs ord#${ords[b].i} FUZZY overlap=${(fratio * 100) | 0}% (${finter}/${Math.min(AW.length, BW.length)})`);
          }
        }
      }
    }
  }

  console.log('\n=== 3. positional references (own cyrillic regex) ===');
  for (const lid of LESSON_IDS) {
    for (const { i, q } of data[lid]) {
      const hits = posRefHits(q.info || '');
      const stemHits = posRefHits(q.text || '');
      if (hits.length) console.log(`[${lid}] q#${i} info: ${hits.join(', ')} :: ${(q.info || '').slice(0, 140)}`);
      if (stemHits.length) console.log(`[${lid}] q#${i} stem: ${stemHits.join(', ')} :: ${(q.text || '').slice(0, 140)}`);
    }
  }

  console.log('\n=== 4. key length ratio (sc/mc) ===');
  for (const lid of LESSON_IDS) {
    for (const { i, q } of data[lid]) {
      if (q.type === 'SingleChoice') {
        const key = q.options.find((o) => o.id === q.correctOptionId).text;
        const maxD = Math.max(...q.options.filter((o) => o.id !== q.correctOptionId).map((o) => o.text.length));
        if (key.length / Math.max(maxD, 1) >= 1.5) {
          console.log(`[${lid}] sc#${i} key(${key.length}) vs maxDist(${maxD}): "${key}"`);
        }
      }
      if (q.type === 'MultipleChoice') {
        for (const cid of q.correctOptionIds) {
          const key = q.options.find((o) => o.id === cid).text;
          const others = q.options.filter((o) => !q.correctOptionIds.includes(o.id)).map((o) => o.text);
          const maxD = Math.max(...others.map((o) => o.length), 1);
          if (key.length / maxD >= 1.6) {
            console.log(`[${lid}] mc#${i} key(${key.length}) vs maxDist(${maxD}): "${key}"`);
          }
        }
      }
    }
  }

  console.log('\n=== 5. fb: more than one fitting candidate ===');
  for (const lid of LESSON_IDS) {
    for (const { i, q } of data[lid]) {
      if (q.type !== 'FillBlank') continue;
      const m = q.text.match(/___/g);
      const blanks = m ? m.length : 0;
      if (blanks !== q.blanks.length) console.log(`[${lid}] fb#${i}: blanks=${blanks} vs ${q.blanks.length}`);
    }
  }

  console.log('\n=== 6. mc duplicate option meanings (identical stems) ===');
  for (const lid of LESSON_IDS) {
    for (const { i, q } of data[lid]) {
      if (q.type !== 'MultipleChoice') continue;
      for (let a = 0; a < q.options.length; a++) {
        for (let b = a + 1; b < q.options.length; b++) {
          const j = jaccard(words(q.options[a].text), words(q.options[b].text));
          if (j >= 0.75) {
            console.log(`[${lid}] mc#${i} opts ${q.options[a].id}/${q.options[b].id} J=${j.toFixed(2)}: "${q.options[a].text}" | "${q.options[b].text}"`);
          }
        }
      }
    }
  }
}

// ---- snapshot / diff -----------------------------------------------------

function snapshot() {
  const data = loadAll();
  const out = {};
  for (const lid of LESSON_IDS) {
    out[lid] = data[lid].map(({ i, q }) => ({
      i,
      type: q.type,
      difficulty: q.difficulty,
      text: q.text,
      options: textsOf(q),
      correct: q.correctOptionId || q.correctOptionIds || null,
      info: q.info,
    }));
  }
  fs.writeFileSync(SNAP, JSON.stringify(out, null, 1));
  console.log('snapshot written:', SNAP);
}

function diff() {
  const before = JSON.parse(fs.readFileSync(SNAP, 'utf8'));
  const now = loadAll();
  let total = 0;
  for (const lid of LESSON_IDS) {
    for (const { i, q } of now[lid]) {
      const b = before[lid].find((x) => x.i === i);
      const cur = {
        i,
        type: q.type,
        difficulty: q.difficulty,
        text: q.text,
        options: textsOf(q),
        correct: q.correctOptionId || q.correctOptionIds || null,
        info: q.info,
      };
      if (JSON.stringify(b) !== JSON.stringify(cur)) {
        total++;
        console.log(`[${lid}] q#${i} CHANGED (type=${q.type}, difficulty=${q.difficulty})`);
      }
    }
    for (const b of before[lid]) {
      if (!now[lid].some(({ i }) => i === b.i)) console.log(`[${lid}] q#${b.i} REMOVED`);
    }
  }
  console.log('changed questions:', total);
}

const mode = process.argv[2] || 'check';
if (mode === 'snap') snapshot();
else if (mode === 'diff') diff();
else analyze();
