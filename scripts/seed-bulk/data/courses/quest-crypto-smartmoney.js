'use strict';

// ===========================================================================
// Crypto Smart Money — full course (hand-authored, research-grounded).
// Dynamic assembler: reads the grounded syllabus (titles) + per-lesson question
// modules and builds the quest -> section -> theme -> lesson -> question tree.
// 7 modules (sections) x 4 themes x 5 lessons x 40 questions = 140 lessons / 5600 Q.
// Same seed-bulk format / validator as the english courses.
// Not wired into courses.js until all 140 lesson files exist and validation passes.
// ===========================================================================

const { buildLesson } = require('./crypto-sm/_helpers');
const SYLLABUS = require('./crypto-sm/syllabus');

const sections = SYLLABUS.modules.map((mod, mi) => {
  const s = mi + 1;
  return {
    id: `sb-courses-crypto-sm-${s}`,
    title: mod.title,
    themes: mod.themes.map((th, ti) => {
      const t = ti + 1;
      return {
        id: `tb-courses-crypto-sm-${s}-${t}`,
        title: th.title,
        lessons: th.lessons.map((lessonTitle, li) => {
          const l = li + 1;
          const questions = require(`./crypto-sm/lessons/${s}-${t}-${l}`);
          return buildLesson(s, t, l, `lb-courses-crypto-sm-${s}-${t}-${l}`, lessonTitle, questions);
        }),
      };
    }),
  };
});

module.exports = { id: SYLLABUS.id, title: SYLLABUS.title, sections };
