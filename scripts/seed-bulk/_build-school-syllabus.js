'use strict';
// Build <subject>/syllabus.js + lesson-index.json from the designed focus/*.json specs.
// Usage: node _build-school-syllabus.js <subject>          (math|physics|chemistry|biology|history|geography)
//        node _build-school-syllabus.js --all
const fs = require('fs');
const path = require('path');

const META = {
  math:      { id: 'qb-school-math-full',  idBase: 'school-math-full',  title: 'Математика: полный школьный курс' },
  physics:   { id: 'qb-school-physics',    idBase: 'school-physics',    title: 'Физика: полный школьный курс' },
  chemistry: { id: 'qb-school-chemistry',  idBase: 'school-chemistry',  title: 'Химия: полный школьный курс' },
  biology:   { id: 'qb-school-biology',    idBase: 'school-biology',    title: 'Биология: полный школьный курс' },
  history:   { id: 'qb-school-history',    idBase: 'school-history',    title: 'Всемирная история: полный школьный курс' },
  geography: { id: 'qb-school-geography',  idBase: 'school-geography',  title: 'География: полный школьный курс' },
};

function build(subject) {
  const meta = META[subject];
  if (!meta) throw new Error('unknown subject: ' + subject);
  const baseDir = path.join(__dirname, 'data', 'school', subject);
  const focusDir = path.join(baseDir, 'focus');
  if (!fs.existsSync(focusDir)) return { subject, ok: false, reason: 'no focus dir' };

  const modules = [];
  const index = [];
  const missing = [];
  let lessonCount = 0;

  for (let s = 1; s <= 7; s++) {
    const themes = [];
    let sectionTitle = null;
    for (let t = 1; t <= 4; t++) {
      const titles = [];
      let themeTitle = null;
      for (let l = 1; l <= 5; l++) {
        const p = path.join(focusDir, `${s}-${t}-${l}.json`);
        if (!fs.existsSync(p)) { missing.push(`${s}-${t}-${l}`); titles.push(`Урок ${s}.${t}.${l}`); continue; }
        let j;
        try { j = JSON.parse(fs.readFileSync(p, 'utf8')); }
        catch (e) { missing.push(`${s}-${t}-${l}(badjson)`); titles.push(`Урок ${s}.${t}.${l}`); continue; }
        lessonCount += 1;
        if (!sectionTitle && j.sectionTitle) sectionTitle = j.sectionTitle;
        if (!themeTitle && j.themeTitle) themeTitle = j.themeTitle;
        const title = j.lessonTitle || `Урок ${s}.${t}.${l}`;
        titles.push(title);
        index.push({ s, t, l, sectionTitle: j.sectionTitle, themeTitle: j.themeTitle, title });
      }
      themes.push({ title: themeTitle || `Тема ${s}.${t}`, lessons: titles });
    }
    modules.push({ title: sectionTitle || `Раздел ${s}`, themes });
  }

  const syllabus = { id: meta.id, idBase: meta.idBase, title: meta.title, modules };
  fs.writeFileSync(path.join(baseDir, 'syllabus.js'), 'module.exports = ' + JSON.stringify(syllabus, null, 2) + ';\n');
  fs.writeFileSync(path.join(baseDir, 'lesson-index.json'), JSON.stringify(index, null, 2));
  return { subject, ok: missing.length === 0, lessons: lessonCount, missing };
}

const arg = process.argv[2];
const subjects = arg === '--all' ? Object.keys(META) : [arg];
for (const s of subjects) {
  try {
    const r = build(s);
    if (r.ok === false && r.reason) { console.log(`${s.padEnd(10)} — ${r.reason}`); continue; }
    console.log(`${s.padEnd(10)} lessons=${r.lessons}/140 ${r.missing.length ? 'MISSING(' + r.missing.length + '): ' + r.missing.slice(0, 12).join(',') : 'OK'}`);
  } catch (e) { console.log(`${s.padEnd(10)} ERROR ${e.message}`); }
}
