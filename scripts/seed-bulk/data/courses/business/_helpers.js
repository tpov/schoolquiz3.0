'use strict';

// Shared builders for the Business (HBS-MBA-core-aligned) course lesson modules.
// A lesson file exports an array of 40 question objects built with these.
// Mirrors english-tech/_helpers.js, with the business id prefix.

function sc(idx, difficulty, text, options, correctOptionId, info) {
  return { type: 'SingleChoice', difficulty, text, imageUrl: null, options, correctOptionId, info };
}
function mc(idx, difficulty, text, options, correctOptionIds, info) {
  return { type: 'MultipleChoice', difficulty, text, imageUrl: null, options, correctOptionIds, info };
}
function ord(idx, difficulty, text, items, info) {
  return { type: 'Ordering', difficulty, text, imageUrl: null, items, info };
}
function fb(idx, difficulty, text, blanks, candidates, info) {
  return { type: 'FillBlank', difficulty, text, imageUrl: null, blanks, candidates, info };
}

const op4 = (a, b, c, d) => ([{ id: 'a', text: a }, { id: 'b', text: b }, { id: 'c', text: c }, { id: 'd', text: d }]);
const op5 = (a, b, c, d, e) => ([{ id: 'a', text: a }, { id: 'b', text: b }, { id: 'c', text: c }, { id: 'd', text: d }, { id: 'e', text: e }]);
const op6 = (a, b, c, d, e, f) => ([{ id: 'a', text: a }, { id: 'b', text: b }, { id: 'c', text: c }, { id: 'd', text: d }, { id: 'e', text: e }, { id: 'f', text: f }]);
const items4 = (a, b, c, d) => ([{ id: 'i1', text: a }, { id: 'i2', text: b }, { id: 'i3', text: c }, { id: 'i4', text: d }]);
const items5 = (a, b, c, d, e) => ([{ id: 'i1', text: a }, { id: 'i2', text: b }, { id: 'i3', text: c }, { id: 'i4', text: d }, { id: 'i5', text: e }]);
const cand5 = (a, b, c, d, e) => ([{ id: 'c1', text: a }, { id: 'c2', text: b }, { id: 'c3', text: c }, { id: 'c4', text: d }, { id: 'c5', text: e }]);

// Wrap a raw question array into a lesson with stable business ids.
function buildLesson(s, t, l, lessonId, lessonTitle, questions) {
  const TYPE_PREFIX = { SingleChoice: 'sc', MultipleChoice: 'mc', Ordering: 'ord', FillBlank: 'fb' };
  const DIFF_PREFIX = { EASY: 'e', HARD: 'h' };
  const counters = {};
  const wrapped = questions.map((q, order) => {
    const key = `${TYPE_PREFIX[q.type]}-${DIFF_PREFIX[q.difficulty]}`;
    counters[key] = (counters[key] || 0) + 1;
    const id = `qsb-courses-business-${s}-${t}-${l}-${TYPE_PREFIX[q.type]}-${DIFF_PREFIX[q.difficulty]}-${counters[key]}`;
    return { id, order, text: q.text, payload: q };
  });
  return { id: lessonId, title: lessonTitle, questions: wrapped };
}

module.exports = { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5, buildLesson };
