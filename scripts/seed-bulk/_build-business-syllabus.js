'use strict';
// One-off: turn the business grounding-workflow output JSON into syllabus.js + per-lesson grounding files.
// Usage: node _build-business-syllabus.js <workflow-output.json>
const fs = require('fs');
const path = require('path');

const src = process.argv[2];
if (!src) { console.error('need output json path'); process.exit(1); }
const raw = JSON.parse(fs.readFileSync(src, 'utf8'));
const data = raw.modules ? raw : (raw.result && raw.result.modules ? raw.result : null);
if (!data) { console.error('could not find .modules in output'); process.exit(1); }

const modules = data.modules;
if (modules.length !== 7) { console.error('expected 7 modules, got', modules.length); process.exit(1); }

const baseDir = path.join(__dirname, 'data', 'courses', 'business');
const groundDir = path.join(baseDir, 'grounding');
fs.mkdirSync(groundDir, { recursive: true });

const syllabusModules = [];
const index = [];
let lessonCount = 0;
const problems = [];

modules.forEach((m, mi) => {
  const s = mi + 1;
  if (!m.themes || m.themes.length !== 4) problems.push(`S${s}: themes=${m.themes ? m.themes.length : 0}`);
  const themes = [];
  (m.themes || []).forEach((th, ti) => {
    const t = ti + 1;
    const lessons = (th.lessons || []);
    if (lessons.length !== 5) problems.push(`S${s}T${t}: lessons=${lessons.length}`);
    const titles = [];
    lessons.forEach((L, li) => {
      const l = li + 1;
      lessonCount += 1;
      const title = (L && L.title) ? String(L.title) : `Урок ${s}.${t}.${l}`;
      titles.push(title);
      const grounding = {
        coords: { s, t, l },
        subjectTitle: m.title,
        themeTitle: th.title,
        lessonTitle: title,
        focus: L.focus || '',
        teaching_points: L.teaching_points || [],
        frameworks: L.frameworks || [],
        example: L.example || '',
        sources: L.sources || [],
      };
      fs.writeFileSync(path.join(groundDir, `${s}-${t}-${l}.json`), JSON.stringify(grounding, null, 2));
      index.push({ s, t, l, subjectTitle: m.title, themeTitle: th.title, title });
    });
    themes.push({ title: th.title, lessons: titles });
  });
  syllabusModules.push({ title: m.title, themes });
});

const syllabus = {
  id: 'qb-courses-business',
  title: 'Основы бизнеса: программа уровня Harvard Business School (MBA core)',
  modules: syllabusModules,
};

fs.writeFileSync(path.join(baseDir, 'syllabus.js'), 'module.exports = ' + JSON.stringify(syllabus, null, 2) + ';\n');
fs.writeFileSync(path.join(baseDir, 'lesson-index.json'), JSON.stringify(index, null, 2));

console.log('lessons:', lessonCount, '| grounding files ->', groundDir);
console.log('problems:', problems.length ? problems.join('; ') : 'none');
