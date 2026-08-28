'use strict';

// ===========================================================================
// German A0-C2 — full general-German course (hand-authored, CEFR-aligned).
// Dynamic assembler: reads the designed syllabus (titles) + per-lesson question
// modules and builds the quest -> section -> theme -> lesson -> question tree.
// 7 levels (A0..C2) x 4 themes x 5 lessons x 40 questions = 140 lessons / 5600 Q.
// German is taught in the question text; explanations (info) are in Russian.
// Not wired into courses.js until all 140 lesson files exist and validation passes.
// ===========================================================================

const { buildLesson } = require('./german/_helpers');
const SYLLABUS = require('./german/syllabus');

const sections = SYLLABUS.modules.map((mod, mi) => {
  const s = mi + 1;
  return {
    id: `sb-courses-german-${s}`,
    title: `${mod.cefr} — ${mod.title}`,
    themes: mod.themes.map((th, ti) => {
      const t = ti + 1;
      return {
        id: `tb-courses-german-${s}-${t}`,
        title: th.title,
        lessons: th.lessons.map((lessonTitle, li) => {
          const l = li + 1;
          const questions = require(`./german/lessons/${s}-${t}-${l}`);
          return buildLesson(s, t, l, `lb-courses-german-${s}-${t}-${l}`, lessonTitle, questions);
        }),
      };
    }),
  };
});

module.exports = { id: SYLLABUS.id, title: SYLLABUS.title, sections };
