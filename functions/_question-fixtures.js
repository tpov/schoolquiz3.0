"use strict";

/**
 * Production-shaped question payloads, one per content type, shared by `question-key-store.test.js`
 * and `catalog-redaction-plan.test.js`.
 *
 * The planner suite asserts that the planner hands the key store exactly the questions publication
 * would, and that assertion only means something if both suites are talking about the same
 * questions. Other suites in this directory carry payloads of their own for their own purposes;
 * these two share theirs because one is proved against the other.
 *
 * Payloads only. Each suite wraps them in whatever document shape its module under test reads.
 */

const SINGLE = {
  type: "SingleChoice",
  text: "Which keyword declares a read-only binding?",
  imageUrl: null,
  options: [{id: "a", text: "var"}, {id: "b", text: "val"}, {id: "c", text: "lateinit"}],
  correctOptionId: "b",
  info: "val is the read-only one",
};

const MULTIPLE = {
  type: "MultipleChoice",
  text: "Which of these run on the JVM?",
  imageUrl: null,
  options: [
    {id: "a", text: "Kotlin"},
    {id: "b", text: "Swift"},
    {id: "c", text: "Scala"},
    {id: "d", text: "Rust"},
  ],
  correctOptionIds: ["a", "c"],
};

const ORDERING = {
  type: "Ordering",
  text: "Put the build steps in order",
  imageUrl: null,
  items: [
    {id: "i1", text: "compile"},
    {id: "i2", text: "test"},
    {id: "i3", text: "package"},
    {id: "i4", text: "deploy"},
  ],
};

const FILL_BLANK = {
  type: "FillBlank",
  text: "___ compiles to bytecode and ___ to machine code.",
  imageUrl: null,
  blanks: [
    {id: "b1", correctCandidateId: "c1"},
    {id: "b2", correctCandidateId: "c2"},
  ],
  candidates: [
    {id: "c1", text: "Kotlin"},
    {id: "c2", text: "Rust"},
    {id: "c3", text: "Elm"},
  ],
  protectedTextSegments: ["Kotlin"],
};

const SURVEY = {
  type: "Survey",
  text: "Which editor do you use?",
  imageUrl: null,
  options: [{id: "a", text: "IntelliJ"}, {id: "b", text: "VS Code"}],
  allowMultiple: false,
};

/**
 * The dialect the `question` literal in `scripts/seed-hierarchy.js` writes and
 * `KotlinxSerializationQuestionContentParser.kt` still reads. Already a string: it is the one
 * shape here that is defined by its bytes rather than by an object the redactor understands.
 */
const LEGACY_SINGLE_CHOICE = '{"type":"single-choice","options":["a","b"],"correctIndex":0}';

module.exports = {SINGLE, MULTIPLE, ORDERING, FILL_BLANK, SURVEY, LEGACY_SINGLE_CHOICE};
