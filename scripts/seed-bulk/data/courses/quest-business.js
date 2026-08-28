'use strict';

// ===========================================================================
// Business (HBS MBA core) — full course (hand-authored, research-grounded).
// Dynamic assembler: reads the grounded syllabus (titles) + per-lesson question
// modules and builds the quest -> section -> theme -> lesson -> question tree.
// 7 subjects (sections) x 4 themes x 5 lessons x 40 questions = 140 lessons / 5600 Q.
// Grounded in authoritative sources (HBS/HBS Online, HBR, standard textbooks);
// teaches standard concepts/frameworks, NOT HBS proprietary cases.
// Not wired into courses.js until all 140 lesson files exist and validation passes.
// ===========================================================================

const { buildLesson } = require('./business/_helpers');
const SYLLABUS = require('./business/syllabus');

const sections = SYLLABUS.modules.map((mod, mi) => {
  const s = mi + 1;
  return {
    id: `sb-courses-business-${s}`,
    title: mod.title,
    themes: mod.themes.map((th, ti) => {
      const t = ti + 1;
      return {
        id: `tb-courses-business-${s}-${t}`,
        title: th.title,
        lessons: th.lessons.map((lessonTitle, li) => {
          const l = li + 1;
          const questions = require(`./business/lessons/${s}-${t}-${l}`);
          return buildLesson(s, t, l, `lb-courses-business-${s}-${t}-${l}`, lessonTitle, questions);
        }),
      };
    }),
  };
});

module.exports = { id: SYLLABUS.id, title: SYLLABUS.title, sections };
