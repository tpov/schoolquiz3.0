'use strict';
// One-off: turn the german design-workflow output JSON into syllabus.js + per-lesson focus files.
// Usage: node _build-german-syllabus.js <workflow-output.json>
const fs = require('fs');
const path = require('path');

const src = process.argv[2];
if (!src) { console.error('need output json path'); process.exit(1); }
const raw = JSON.parse(fs.readFileSync(src, 'utf8'));
const data = raw.modules ? raw : (raw.result && raw.result.modules ? raw.result : null);
if (!data) { console.error('could not find .modules in output'); process.exit(1); }

const modules = data.modules;
if (modules.length !== 7) { console.error('expected 7 modules, got', modules.length); process.exit(1); }

const baseDir = path.join(__dirname, 'data', 'courses', 'german');
const focusDir = path.join(baseDir, 'focus');
fs.mkdirSync(focusDir, { recursive: true });

const syllabusModules = [];
const index = [];
let lessonCount = 0;
const problems = [];

modules.forEach((m, mi) => {
  const s = mi + 1;
  if (!m.themes || m.themes.length !== 4) problems.push(`M${s}: themes=${m.themes ? m.themes.length : 0}`);
  const themes = [];
  (m.themes || []).forEach((th, ti) => {
    const t = ti + 1;
    const lessons = (th.lessons || []);
    if (lessons.length !== 5) problems.push(`${m.cefr}-T${t}: lessons=${lessons.length}`);
    const titles = [];
    lessons.forEach((L, li) => {
      const l = li + 1;
      lessonCount += 1;
      const title = (L && L.title) ? String(L.title) : `Урок ${s}.${t}.${l}`;
      titles.push(title);
      const focus = {
        coords: { s, t, l },
        cefr: m.cefr,
        sectionTitle: m.title,
        themeTitle: th.title,
        lessonTitle: title,
        focus: L.focus || '',
        grammar_points: L.grammar_points || [],
        vocab: L.vocab || [],
        examples: L.examples || [],
      };
      fs.writeFileSync(path.join(focusDir, `${s}-${t}-${l}.json`), JSON.stringify(focus, null, 2));
      index.push({ s, t, l, cefr: m.cefr, sectionTitle: m.title, themeTitle: th.title, title });
    });
    themes.push({ title: th.title, lessons: titles });
  });
  syllabusModules.push({ cefr: m.cefr, title: m.title, themes });
});

const syllabus = {
  id: 'qb-courses-german',
  title: 'Немецкий A0–C2: общий курс для русскоязычных',
  modules: syllabusModules,
};

fs.writeFileSync(path.join(baseDir, 'syllabus.js'), 'module.exports = ' + JSON.stringify(syllabus, null, 2) + ';\n');
fs.writeFileSync(path.join(baseDir, 'lesson-index.json'), JSON.stringify(index, null, 2));

console.log('lessons:', lessonCount, '| focus files ->', focusDir);
console.log('problems:', problems.length ? problems.join('; ') : 'none');
