'use strict';

// Factory that assembles a school-subject quest from its syllabus + lesson modules.
// Each subject quest file is a one-liner: module.exports = require('./_build-quest')('physics');
//
// Structure: 7 sections x 4 themes x 5 lessons x 40 questions = 140 lessons / 5600 Q.
// visibleOn: ['home'] and archived: false are set explicitly so the quest lands on the
// HOME screen regardless of the seeder's per-catalog defaults (seed-bulk-quests.js
// honours quest.visibleOn / quest.archived when they are present).

module.exports = function buildQuest(subject) {
  const { buildLesson } = require(`./${subject}/_helpers`);
  const SYLLABUS = require(`./${subject}/syllabus`);
  const base = SYLLABUS.idBase;

  const sections = SYLLABUS.modules.map((mod, mi) => {
    const s = mi + 1;
    return {
      id: `sb-${base}-${s}`,
      title: mod.title,
      themes: mod.themes.map((th, ti) => {
        const t = ti + 1;
        return {
          id: `tb-${base}-${s}-${t}`,
          title: th.title,
          lessons: th.lessons.map((lessonTitle, li) => {
            const l = li + 1;
            const questions = require(`./${subject}/lessons/${s}-${t}-${l}`);
            return buildLesson(s, t, l, `lb-${base}-${s}-${t}-${l}`, lessonTitle, questions);
          }),
        };
      }),
    };
  });

  return {
    id: SYLLABUS.id,
    title: SYLLABUS.title,
    visibleOn: ['home'],
    archived: false,
    sections,
  };
};
