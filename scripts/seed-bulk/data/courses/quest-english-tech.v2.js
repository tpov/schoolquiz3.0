'use strict';

// ===========================================================================
// Technical English A0-C2 — REWRITE (v2).
// Hand-authored content (NOT procedural templates). Same seed-bulk format as
// quest-english.js: 40 questions per lesson (10 sc / 10 mc / 10 ord / 10 fb,
// 20 EASY + 20 HARD), each with a Russian `info` explanation.
//
// STATUS: pilot. Only A1 / Theme 1 / Lesson 1 is authored as the quality
// reference. Full planned structure (7 levels x 4 themes x 5 lessons) is listed
// at the bottom as PLAN. Not wired into courses.js until the rewrite is done.
// ===========================================================================

// --- Builders (mirror quest-english.js, with invariant-friendly shapes) -----
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
const cand5 = (a, b, c, d, e) => ([{ id: 'c1', text: a }, { id: 'c2', text: b }, { id: 'c3', text: c }, { id: 'c4', text: d }, { id: 'c5', text: e }]);

// ---------------------------------------------------------------------------
// LESSON A1.1.1 — Pronouns and the verb "be" (представить себя и команду)
// ---------------------------------------------------------------------------
const lesson_A1_1_1 = [
  // ---- SingleChoice EASY ----
  sc(0, 'EASY', '___ am a developer.', op4('I', 'You', 'He', 'We'), 'a',
    'Форма "am" используется только с местоимением "I": I am.'),
  sc(1, 'EASY', 'She ___ a tester on our team.', op4('am', 'is', 'are', 'be'), 'b',
    'С he / she / it используется "is": she is.'),
  sc(2, 'EASY', 'We ___ in the same team.', op4('am', 'is', 'are', 'be'), 'c',
    'С we / you / they используется "are": we are.'),
  sc(3, 'EASY', 'They ___ backend engineers.', op4('is', 'are', 'am', 'be'), 'b',
    'They — множественное число, поэтому "are".'),
  sc(4, 'EASY', 'Replace "Tom": "Tom is a developer." → "___ is a developer."',
    op4('He', 'She', 'It', 'They'), 'a',
    'Tom — мужчина в единственном числе, заменяется местоимением "He".'),
  // ---- SingleChoice HARD ----
  sc(5, 'HARD', 'I ___ not the code owner.', op4('am', 'is', 'are', 'be'), 'a',
    'Отрицание с "I" строится как "I am not".'),
  sc(6, 'HARD', '___ you the reviewer of this PR?', op4('Are', 'Is', 'Am', 'Be'), 'a',
    'В вопросе с "you" глагол "are" выносится вперёд: Are you...?'),
  sc(7, 'HARD', 'It ___ a small bug, not a big one.', op4('is', 'are', 'am', 'be'), 'a',
    'It — единственное число → "is".'),
  sc(8, 'HARD', '"We\'re on call this week." "We\'re" means:',
    op4('We are', 'We is', 'We am', 'We be'), 'a',
    'Сокращение "we\'re" = "we are".'),
  sc(9, 'HARD', 'Anna and I ___ in the meeting now.', op4('am', 'is', 'are', 'be'), 'c',
    '"Anna and I" = "we" → используется "are".'),

  // ---- MultipleChoice EASY ----
  mc(10, 'EASY', 'Mark the correct sentences with "be":',
    op6('I am a dev.', 'I is a dev.', 'She is a QA.', 'She are a QA.', 'We are a team.', 'We is a team.'),
    ['a', 'c', 'e'],
    'Правильно: I am, She is, We are.'),
  mc(11, 'EASY', 'Which pronouns go with "are"?',
    op6('you', 'we', 'they', 'I', 'he', 'she'), ['a', 'b', 'c'],
    '"Are" используется с you, we, they.'),
  mc(12, 'EASY', 'Which of these are subject pronouns?',
    op6('I', 'me', 'he', 'him', 'they', 'them'), ['a', 'c', 'e'],
    'Подлежащные местоимения: I, he, they. Me, him, them — объектные.'),
  mc(13, 'EASY', 'Mark the correct self-introductions:',
    op5('I am Oleg.', 'Me is Oleg.', 'I am a developer.', 'I developer.', 'My name is Oleg.'),
    ['a', 'c', 'e'],
    'Корректно: I am Oleg, I am a developer, My name is Oleg.'),
  mc(14, 'EASY', 'Which sentences use "is" correctly?',
    op5('The server is down.', 'The server are down.', 'The build is green.', 'The tests is green.', 'It is ready.'),
    ['a', 'c', 'e'],
    'Единственное число (server, build, it) → "is".'),
  // ---- MultipleChoice HARD ----
  mc(15, 'HARD', 'Mark the grammatically correct negatives:',
    op6('I am not ready.', "I amn't ready.", "She isn't here.", 'She not is here.', "They aren't merged.", "They isn't merged."),
    ['a', 'c', 'e'],
    'Правильно: I am not, She isn\'t, They aren\'t. Формы "amn\'t" не существует.'),
  mc(16, 'HARD', 'Mark the correct questions:',
    op6('Are you the author?', 'Be you the author?', 'Is it fixed?', 'Am it fixed?', 'Am I on the list?', 'Is you on the list?'),
    ['a', 'c', 'e'],
    'Вопрос = форма be вперёд + подлежащее: Are you...?, Is it...?, Am I...?'),
  mc(17, 'HARD', 'Which contractions are correct?',
    op6("I'm = I am", "She's = She is", "They're = They are", "We's = We is", "He're = He are", "It's = It is"),
    ['a', 'b', 'c', 'f'],
    'Корректно: I\'m, She\'s, They\'re, It\'s. "We\'s" и "He\'re" не существуют.'),
  mc(18, 'HARD', 'Mark the correct sentences with compound subjects:',
    op5('Tom and I are here.', 'Tom and I is here.', 'You and she are paired.', 'You and she is paired.', 'He and I am ready.'),
    ['a', 'c'],
    'Составное подлежащее = множественное число → "are".'),
  mc(19, 'HARD', 'Which sentences correctly use "be" + state?',
    op5('He is on vacation.', 'He are on vacation.', 'The PR is open.', 'The PR are open.', 'The pipelines are red.'),
    ['a', 'c', 'e'],
    'He/The PR → "is"; the pipelines (мн. ч.) → "are".'),

  // ---- Ordering EASY ----
  ord(20, 'EASY', 'Build a sentence: "I am a junior developer".',
    items4('I', 'am', 'a junior', 'developer'),
    'Порядок: подлежащее + be + дополнение.'),
  ord(21, 'EASY', 'Build: "She is our team lead".',
    items4('She', 'is', 'our', 'team lead'),
    'She → is, далее именная группа.'),
  ord(22, 'EASY', 'Build: "We are in the same repo".',
    items4('We', 'are', 'in the', 'same repo'),
    'We → are, затем обстоятельство места.'),
  ord(23, 'EASY', 'Build: "They are on the call".',
    items4('They', 'are', 'on the', 'call'),
    'С they используется are.'),
  ord(24, 'EASY', 'Build: "He is a good reviewer".',
    items4('He', 'is', 'a good', 'reviewer'),
    'He → is + именная группа с артиклем.'),
  // ---- Ordering HARD ----
  ord(25, 'HARD', 'Build: "I am not the code owner".',
    items4('I', 'am not', 'the code', 'owner'),
    'Отрицание: подлежащее + am not + дополнение.'),
  ord(26, 'HARD', 'Build a question: "Are you the author of this PR?".',
    items4('Are you', 'the author', 'of this', 'PR?'),
    'Вопрос начинается с "Are you", дальше именная группа.'),
  ord(27, 'HARD', 'Build: "Tom and I are pair programming".',
    items4('Tom and I', 'are', 'pair', 'programming'),
    'Составное подлежащее → are + Continuous.'),
  ord(28, 'HARD', 'Build a question: "Is it ready for review?".',
    items4('Is it', 'ready', 'for', 'review?'),
    'Вопрос с "it": Is it ready...?'),
  ord(29, 'HARD', 'Build: "We are not blocked anymore".',
    items4('We', 'are not', 'blocked', 'anymore'),
    'Отрицание во множественном: are not.'),

  // ---- FillBlank EASY ----
  fb(30, 'EASY', 'I ___ a developer.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('is', 'am', 'are', 'be', 'do'),
    'С "I" — только "am".'),
  fb(31, 'EASY', 'She ___ a designer.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('am', 'are', 'is', 'be', 'was'),
    'С she используется is.'),
  fb(32, 'EASY', 'We ___ a small team.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('am', 'is', 'are', 'be', 'were'),
    'С we используется are.'),
  fb(33, 'EASY', 'They ___ in the office today.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('is', 'am', 'are', 'be', 'does'),
    'С they используется are.'),
  fb(34, 'EASY', 'It ___ a simple task.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('am', 'are', 'is', 'be', 'do'),
    'С it используется is.'),
  // ---- FillBlank HARD ----
  fb(35, 'HARD', 'I ___ not sure about this bug.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('is', 'are', 'am', 'was', 'be'),
    'Отрицание с "I": I am not.'),
  fb(36, 'HARD', '___ you the owner of this ticket?',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Is', 'Am', 'Are', 'Be', 'Do'),
    'Вопрос с "you": Are you...?'),
  fb(37, 'HARD', 'He ___ not online right now.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('am', 'are', 'is', 'was', 'be'),
    'С he используется is (в отрицании is not).'),
  fb(38, 'HARD', 'My teammate and I ___ on the same task.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('am', 'is', 'are', 'was', 'be'),
    '"My teammate and I" — это we, поэтому are.'),
  fb(39, 'HARD', '___ it ready to merge?',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Are', 'Am', 'Is', 'Be', 'Does'),
    'Вопрос с "it": Is it...?'),
];

// ---------------------------------------------------------------------------
// LESSON A1.1.2 — Present Simple для рабочих рутин
// ---------------------------------------------------------------------------
const lesson_A1_1_2 = [
  // ---- SingleChoice EASY ----
  sc(0, 'EASY', 'I ___ my email every morning.', op4('check', 'checks', 'checking', 'am check'), 'a',
    'С "I" глагол стоит в базовой форме без -s: I check.'),
  sc(1, 'EASY', 'She ___ the build before lunch.', op4('start', 'starts', 'starting', 'is start'), 'b',
    'С she/he/it в Present Simple к глаголу добавляется -s: she starts.'),
  sc(2, 'EASY', 'We ___ a standup at 10 every day.', op4('has', 'haves', 'have', 'having'), 'c',
    'С we глагол в базовой форме: we have (не "has").'),
  sc(3, 'EASY', 'He ___ his code to GitHub.', op4('push', 'pushing', 'pushes', 'are push'), 'c',
    'He → третье лицо ед. числа, поэтому push + es = pushes.'),
  sc(4, 'EASY', 'They ___ tickets in the morning.', op4('reads', 'read', 'reading', 'is read'), 'b',
    'They — множественное число, глагол без -s: they read.'),
  // ---- SingleChoice HARD ----
  sc(5, 'HARD', 'My teammate ___ the logs when a test fails.', op4('check', 'checks', 'are checking', 'do check'), 'b',
    '"My teammate" = he/she (ед. число) → checks с окончанием -s.'),
  sc(6, 'HARD', 'The pipeline ___ the app automatically.', op4('build', 'are building', 'builds', 'build it'), 'c',
    'The pipeline = it, поэтому build + s = builds.'),
  sc(7, 'HARD', 'I usually ___ to a new branch before I code.', op4('switches', 'switch', 'am switch', 'switching'), 'b',
    'С "I" глагол без -s даже при наличии наречия usually: I switch.'),
  sc(8, 'HARD', 'She ___ the *PR* and leaves comments.', op4('open', 'opens', 'is open', 'opening'), 'b',
    'She → третье лицо ед. числа: opens. *PR* — термин, не переводится.'),
  sc(9, 'HARD', 'It rarely ___ on the first try.', op4('work', 'working', 'works', 'are work'), 'c',
    'It → третье лицо ед. числа, поэтому works с -s.'),

  // ---- MultipleChoice EASY ----
  mc(10, 'EASY', 'Mark the correct present simple sentences:',
    op6('She tests the app.', 'She test the app.', 'I write code.', 'I writes code.', 'They deploy on Friday.', 'They deploys on Friday.'),
    ['a', 'c', 'e'],
    'Правильно: she tests (-s в 3 л.), I write и they deploy (без -s).'),
  mc(11, 'EASY', 'Which subjects take the -s form of the verb? (he ___s)',
    op6('he', 'she', 'it', 'we', 'they', 'I'), ['a', 'b', 'c'],
    'Окончание -s в Present Simple берут только he, she, it.'),
  mc(12, 'EASY', 'Mark the correct verb forms for "He ___":',
    op6('He runs the tests.', 'He run the tests.', 'He fixes bugs.', 'He fix bugs.', 'He checks the logs.', 'He check the logs.'),
    ['a', 'c', 'e'],
    'С he нужна форма с -s: runs, fixes, checks.'),
  mc(13, 'EASY', 'Which sentences describe a daily routine correctly?',
    op5('I start work at nine.', 'I starts work at nine.', 'We read the chat first.', 'We reads the chat first.', 'He closes old tickets.'),
    ['a', 'c', 'e'],
    'Корректно: I start, we read (без -s) и he closes (с -s).'),
  mc(14, 'EASY', 'Which adverbs of frequency are spelled correctly?',
    op5('always', 'alway', 'usually', 'usualy', 'every day'), ['a', 'c', 'e'],
    'Правильное написание: always, usually, every day.'),
  // ---- MultipleChoice HARD ----
  mc(15, 'HARD', 'Mark the correct negative sentences:',
    op6("I don't merge to main.", "I doesn't merge to main.", "She doesn't review on Fridays.", "She don't review on Fridays.", "We don't deploy at night.", "We doesn't deploy at night."),
    ['a', 'c', 'e'],
    'Отрицание: I/we/they → don\'t, а he/she/it → doesn\'t. Глагол после них без -s.'),
  mc(16, 'HARD', 'Mark the correct questions in present simple:',
    op6('Does the test pass?', 'Do the test pass?', 'Do you write tests?', 'Does you write tests?', 'Does she deploy daily?', 'Do she deploy daily?'),
    ['a', 'c', 'e'],
    'Does — с he/she/it (the test, she), do — с I/you/we/they; смысловой глагол без -s.'),
  mc(17, 'HARD', 'Which sentences keep the verb in its base form correctly?',
    op6("He doesn't deploy on Mondays.", "He doesn't deploys on Mondays.", 'Does it build cleanly?', 'Does it builds cleanly?', "They don't review my code.", "They don't reviews my code."),
    ['a', 'c', 'e'],
    'После doesn\'t / does и don\'t глагол стоит в базовой форме, без -s.'),
  mc(18, 'HARD', 'Mark the sentences where the adverb of frequency is in a natural position:',
    op5('She usually checks email first.', 'She checks usually email first.', 'I always run the tests.', 'I run always the tests.', 'We rarely deploy on Friday.'),
    ['a', 'c', 'e'],
    'Наречие частоты ставится перед смысловым глаголом: usually checks, always run, rarely deploy.'),
  mc(19, 'HARD', 'Which third-person forms are spelled correctly?',
    op6('she watches', 'she watchs', 'it finishes', 'it finishs', 'he tries', 'he trys'), ['a', 'c', 'e'],
    'После -ch/-sh добавляется -es (watches, finishes); -y после согласной → -ies (tries).'),

  // ---- Ordering EASY ----
  ord(20, 'EASY', 'Build a sentence: "I read the tickets every morning".',
    items4('I', 'read', 'the tickets', 'every morning'),
    'Порядок: подлежащее + глагол + дополнение + обстоятельство времени.'),
  ord(21, 'EASY', 'Build: "She writes tests every day".',
    items4('She', 'writes', 'tests', 'every day'),
    'She → глагол с -s (writes), затем дополнение и время.'),
  ord(22, 'EASY', 'Build: "We start work at nine".',
    items4('We', 'start', 'work', 'at nine'),
    'We → глагол без -s (start), далее дополнение и время.'),
  ord(23, 'EASY', 'Build: "He deploys the app on Friday".',
    items4('He', 'deploys', 'the app', 'on Friday'),
    'He → форма с -s (deploys), потом дополнение и обстоятельство.'),
  ord(24, 'EASY', 'Build: "They close old tickets".',
    items4('They', 'close', 'old', 'tickets'),
    'They → глагол без -s (close) + именная группа.'),
  // ---- Ordering HARD ----
  ord(25, 'HARD', 'Build: "She always checks the logs first".',
    items4('She', 'always', 'checks', 'the logs first'),
    'Наречие always стоит перед глаголом с -s: She always checks.'),
  ord(26, 'HARD', 'Build a negative: "I do not merge to main".',
    items4('I', 'do not', 'merge', 'to main'),
    'Отрицание: подлежащее + do not + глагол в базовой форме.'),
  ord(27, 'HARD', 'Build a question: "Does the test pass on CI?".',
    items4('Does', 'the test', 'pass', 'on CI?'),
    'Вопрос в 3 л. начинается с Does + подлежащее + глагол без -s.'),
  ord(28, 'HARD', 'Build a negative: "He does not review on Fridays".',
    items4('He', 'does not', 'review', 'on Fridays'),
    'He → does not + глагол без -s (review).'),
  ord(29, 'HARD', 'Build a question: "Do you usually work from home?".',
    items4('Do you', 'usually', 'work', 'from home?'),
    'Вопрос с you: Do you + наречие частоты + глагол.'),

  // ---- FillBlank EASY ----
  fb(30, 'EASY', 'She ___ the logs every morning.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('check', 'checks', 'checking', 'is check', 'are check'),
    'She → третье лицо ед. числа, нужна форма с -s: checks.'),
  fb(31, 'EASY', 'I ___ to a new branch before I start.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5('switches', 'is switch', 'switching', 'switch', 'are switch'),
    'С "I" глагол без -s: switch.'),
  fb(32, 'EASY', 'We ___ a standup at 10.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('has', 'having', 'have', 'is have', 'haves'),
    'We → глагол в базовой форме: have.'),
  fb(33, 'EASY', 'He ___ his code to GitHub.',
    [{ id: 'b1', correctCandidateId: 'c5' }], cand5('push', 'pushing', 'are push', 'is push', 'pushes'),
    'He → третье лицо ед. числа: push + es = pushes.'),
  fb(34, 'EASY', 'They ___ the *README* on Mondays.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('updates', 'update', 'updating', 'is update', 'has update'),
    'They — множественное число, глагол без -s: update. *README* — термин, не переводится.'),
  // ---- FillBlank HARD ----
  fb(35, 'HARD', 'She ___ review my code on Fridays.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5("don't", 'not', 'do not', "doesn't", 'is not'),
    'Отрицание с she строится через doesn\'t + глагол без -s.'),
  fb(36, 'HARD', '___ the test pass on CI?',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Do', 'Is', 'Does', 'Are', 'Has'),
    'Вопрос с "the test" (it) в 3 л. начинается с Does.'),
  fb(37, 'HARD', 'I ___ merge to main on Friday.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5("doesn't", "don't", 'not', 'is not', 'am not'),
    'Отрицание с "I": don\'t + глагол в базовой форме.'),
  fb(38, 'HARD', 'He ___ runs the tests before a release.',
    [{ id: 'b1', correctCandidateId: 'c5' }], cand5('are', 'do', 'is', 'does', 'always'),
    'Наречие частоты always ставится перед смысловым глаголом: He always runs.'),
  fb(39, 'HARD', '___ you usually work on the backend?',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Does', 'Do', 'Are', 'Is', 'Have'),
    'Вопрос с you в Present Simple начинается с Do.'),
];

// ---------------------------------------------------------------------------
// LESSON A1.1.3 — There is / There are
// ---------------------------------------------------------------------------
const lesson_A1_1_3 = [
  // ---- SingleChoice EASY ----
  sc(0, 'EASY', 'There ___ a bug in the login screen.', op4('is', 'are', 'am', 'be'), 'a',
    'С единственным числом (a bug) используется "there is".'),
  sc(1, 'EASY', 'There ___ two open pull requests.', op4('is', 'are', 'am', 'be'), 'b',
    'С множественным числом (two pull requests) используется "there are".'),
  sc(2, 'EASY', 'There ___ a *README* in the repo.', op4('are', 'is', 'am', 'be'), 'b',
    'Один файл README — единственное число, поэтому "there is".'),
  sc(3, 'EASY', 'There ___ three tasks in my sprint.', op4('is', 'am', 'are', 'be'), 'c',
    'Three tasks — множественное число, нужно "there are".'),
  sc(4, 'EASY', 'There ___ a new ticket for me today.', op4('are', 'am', 'is', 'be'), 'c',
    'A new ticket — единственное число, поэтому "there is".'),
  // ---- SingleChoice HARD ----
  sc(5, 'HARD', 'There ___ not a test for this function yet.', op4('is', 'are', 'am', 'be'), 'a',
    'Отрицание в единственном числе: "there is not" (there isn\'t).'),
  sc(6, 'HARD', '___ there any blockers on your side?', op4('Is', 'Are', 'Am', 'Be'), 'b',
    'С "any blockers" (множественное число) вопрос строится как "Are there...?".'),
  sc(7, 'HARD', 'There ___ a few errors in the build log.', op4('is', 'are', 'am', 'be'), 'b',
    '"A few errors" — множественное число, поэтому "there are".'),
  sc(8, 'HARD', '"There isn\'t a deadline." This sentence is:', op4('negative', 'a question', 'positive', 'plural'), 'a',
    '"Isn\'t" = is not, поэтому предложение отрицательное.'),
  sc(9, 'HARD', 'There ___ some merge conflicts in this branch.', op4('is', 'am', 'are', 'be'), 'c',
    '"Some merge conflicts" — множественное число, нужно "there are".'),

  // ---- MultipleChoice EASY ----
  mc(10, 'EASY', 'Mark the correct sentences:',
    op6('There is a bug.', 'There are a bug.', 'There are two bugs.', 'There is two bugs.', 'There is one ticket.', 'There are one ticket.'),
    ['a', 'c', 'e'],
    'Единственное число → "there is", множественное → "there are".'),
  mc(11, 'EASY', 'Which nouns go with "There is"?',
    op6('a bug', 'three tasks', 'a *commit*', 'two files', 'a deadline', 'some errors'),
    ['a', 'c', 'e'],
    '"There is" используется с единственным числом: a bug, a commit, a deadline.'),
  mc(12, 'EASY', 'Which nouns go with "There are"?',
    op6('a server', 'two tests', 'a branch', 'three pull requests', 'a file', 'five tickets'),
    ['b', 'd', 'f'],
    '"There are" используется с множественным числом: two tests, three pull requests, five tickets.'),
  mc(13, 'EASY', 'Mark the correct ways to describe a project:',
    op5('There is a *README*.', 'There are a *README*.', 'There are two branches.', 'There is two branches.', 'There is one test.'),
    ['a', 'c', 'e'],
    'Один README и один test → "there is"; две ветки → "there are".'),
  mc(14, 'EASY', 'Which sentences use "there are" correctly?',
    op5('There are many users.', 'There are a user.', 'There are some bugs.', 'There are a deadline.', 'There are four commits.'),
    ['a', 'c', 'e'],
    '"There are" сочетается с множественным числом: many users, some bugs, four commits.'),
  // ---- MultipleChoice HARD ----
  mc(15, 'HARD', 'Mark the grammatically correct negatives:',
    op6("There isn't a test.", "There aren't a test.", "There aren't any errors.", "There isn't any errors.", "There isn't a deadline.", "There aren't a deadline."),
    ['a', 'c', 'e'],
    'Единственное число → "there isn\'t", множественное → "there aren\'t".'),
  mc(16, 'HARD', 'Mark the correct questions:',
    op6('Is there a deadline?', 'Are there a deadline?', 'Are there any blockers?', 'Is there any blockers?', 'Is there a meeting today?', 'Are there a meeting today?'),
    ['a', 'c', 'e'],
    'Вопрос: "Is there..." для ед. числа, "Are there..." для мн. числа.'),
  mc(17, 'HARD', 'Which sentences correctly use "any" and "some"?',
    op6("There aren't any tests.", "There aren't some tests.", 'There are some open PRs.', 'There are any open PRs.', 'Is there any documentation?', 'Is there some documentation?'),
    ['a', 'c', 'e'],
    '"Any" — в отрицаниях и вопросах; "some" — в утверждениях.'),
  mc(18, 'HARD', 'Mark the correct sentences about a codebase:',
    op5("There aren't any merge conflicts.", "There isn't any merge conflicts.", 'There is one failing test.', 'There are one failing test.', 'There are several warnings.'),
    ['a', 'c', 'e'],
    'Conflicts/warnings (мн.) → aren\'t/are; one failing test (ед.) → is.'),
  mc(19, 'HARD', 'Which short answers are correct?',
    op5('Yes, there is.', 'Yes, there are some.', 'Yes, there am.', 'No, there be not.', "No, there aren't."),
    ['a', 'b', 'e'],
    'Краткие ответы: "there is", "there are (some)", "there aren\'t".'),

  // ---- Ordering EASY ----
  ord(20, 'EASY', 'Build a sentence: "There is a bug in the code".',
    items4('There is', 'a bug', 'in the', 'code'),
    'Порядок: "There is" + единственное число + обстоятельство места.'),
  ord(21, 'EASY', 'Build: "There are two open tickets".',
    items4('There are', 'two', 'open', 'tickets'),
    '"There are" + множественное число (two ... tickets).'),
  ord(22, 'EASY', 'Build: "There is a README file".',
    items4('There is', 'a', 'README', 'file'),
    'Один файл → "There is" + a README file.'),
  ord(23, 'EASY', 'Build: "There are many users online".',
    items4('There are', 'many', 'users', 'online'),
    '"Many users" — множественное число → "There are".'),
  ord(24, 'EASY', 'Build: "There is a test for this".',
    items4('There is', 'a test', 'for', 'this'),
    'Один тест → "There is" + a test.'),
  // ---- Ordering HARD ----
  ord(25, 'HARD', 'Build: "There is not a test for it yet".',
    items4('There is not', 'a test', 'for it', 'yet'),
    'Отрицание в ед. числе: "There is not" + a test ... yet.'),
  ord(26, 'HARD', 'Build a question: "Are there any blockers today?".',
    items4('Are there', 'any', 'blockers', 'today?'),
    'Вопрос с мн. числом: "Are there" + any blockers.'),
  ord(27, 'HARD', 'Build a question: "Is there a deadline for this?".',
    items4('Is there', 'a deadline', 'for', 'this?'),
    'Вопрос с ед. числом: "Is there" + a deadline.'),
  ord(28, 'HARD', 'Build: "There are some errors in the log".',
    items4('There are', 'some errors', 'in the', 'log'),
    '"Some errors" (мн.) → "There are".'),
  ord(29, 'HARD', 'Build: "There aren\'t any open issues".',
    items4("There aren't", 'any', 'open', 'issues'),
    'Отрицание во мн. числе: "There aren\'t" + any open issues.'),

  // ---- FillBlank EASY ----
  fb(30, 'EASY', 'There ___ a bug in the login screen.',
    [{ id: 'b1', correctCandidateId: 'c1' }], cand5('is', 'are', 'am', 'be', 'was'),
    'Единственное число (a bug) → "there is".'),
  fb(31, 'EASY', 'There ___ two open pull requests.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('is', 'are', 'am', 'be', 'were'),
    'Множественное число (two pull requests) → "there are".'),
  fb(32, 'EASY', 'There ___ a *commit* on this branch.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('are', 'am', 'is', 'be', 'do'),
    'Один commit → "there is".'),
  fb(33, 'EASY', 'There ___ three tests in this file.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5('is', 'am', 'be', 'are', 'was'),
    'Three tests (мн.) → "there are".'),
  fb(34, 'EASY', 'There ___ a deadline on Friday.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('are', 'is', 'am', 'be', 'were'),
    'A deadline (ед.) → "there is".'),
  // ---- FillBlank HARD ----
  fb(35, 'HARD', 'There ___ any tests for this function.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5("isn't", 'is', "aren't", 'am not', 'be'),
    'С "any tests" (мн.) в отрицании → "there aren\'t".'),
  fb(36, 'HARD', '___ there a deadline for this ticket?',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Are', 'Is', 'Am', 'Be', 'Do'),
    'Вопрос с ед. числом (a deadline) → "Is there...?".'),
  fb(37, 'HARD', 'There ___ any open issues right now.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5("isn't", 'is not', 'am not', "aren't", 'be'),
    'Отрицание с "any open issues" (мн.) → "there aren\'t".'),
  fb(38, 'HARD', '___ there any blockers on your side?',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Is', 'Am', 'Are', 'Be', 'Does'),
    'Вопрос с "any blockers" (мн.) → "Are there...?".'),
  fb(39, 'HARD', 'There ___ some merge conflicts to fix.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('is', 'are', 'am', 'be', 'was'),
    '"Some merge conflicts" (мн.) в утверждении → "there are".'),
];

// ---------------------------------------------------------------------------
// LESSON A1.1.4 — Can / cannot (способность и разрешение)
// ---------------------------------------------------------------------------
const lesson_A1_1_4 = [
  // ---- SingleChoice EASY ----
  sc(0, 'EASY', 'I ___ review your PR after lunch.', op4('can', 'cans', 'am can', 'to can'), 'a',
    'После "can" идёт инфинитив без to и без -s: I can review.'),
  sc(1, 'EASY', 'She can ___ the project on her laptop.', op4('builds', 'building', 'build', 'to build'), 'c',
    'После модального "can" глагол стоит в базовой форме: can build.'),
  sc(2, 'EASY', 'We ___ deploy on Fridays — it is not allowed.', op4('can', "can't", 'can not to', 'no can'), 'b',
    'Запрет (нет разрешения) выражается через "can\'t": we can\'t deploy.'),
  sc(3, 'EASY', '"Can you fix this bug today?" "Yes, ___."', op4('I can', 'I do', 'can I', 'I am'), 'a',
    'Краткий положительный ответ на вопрос с can: "Yes, I can".'),
  sc(4, 'EASY', '___ you help me with the merge?', op4('Do', 'Are', 'Can', 'Is'), 'c',
    'Просьба о помощи строится с "can" в начале: Can you help...?'),
  // ---- SingleChoice HARD ----
  sc(5, 'HARD', 'You ___ push directly to *main* — it is protected.', op4('can', 'can to', "can't", 'cannot to'), 'c',
    'Отсутствие разрешения: "can\'t" (или cannot) + базовый глагол push.'),
  sc(6, 'HARD', '"Can he deploy the build?" "No, ___."', op4("he can't", "he doesn't", "he isn't", "can't he"), 'a',
    'Краткий отрицательный ответ: "No, he can\'t".'),
  sc(7, 'HARD', 'Only admins ___ delete a repository.', op4('cans', 'can', 'are can', 'can to'), 'b',
    'Разрешение для группы лиц: admins can delete (одна форма can для всех).'),
  sc(8, 'HARD', '"Cannot" is the same as:', op4("can't", "can'nt", 'can no', "ca'nt"), 'a',
    'Полная форма "cannot" равна сокращению "can\'t".'),
  sc(9, 'HARD', 'Interns ___ merge to production without a review.', op4('can', "can't", "can't to", 'not can'), 'b',
    'Нет разрешения мержить без ревью → "can\'t".'),

  // ---- MultipleChoice EASY ----
  mc(10, 'EASY', 'Mark the correct sentences with "can":',
    op6('I can code.', 'I can to code.', 'She can test.', 'She cans test.', 'We can deploy.', 'We can deploys.'),
    ['a', 'c', 'e'],
    'После "can" — базовый глагол без to и без -s: can code / can test / can deploy.'),
  mc(11, 'EASY', 'Which are correct short answers to "Can you do it?"',
    op6('Yes, I can.', "No, I can't.", 'Yes, I do.', 'Yes, I am.', "No, I can't do.", 'No, I not can.'),
    ['a', 'b'],
    'Краткие ответы на вопрос с can: "Yes, I can" / "No, I can\'t".'),
  mc(12, 'EASY', 'Which sentences ask for permission or help correctly?',
    op6('Can I join the call?', 'Can you review this?', 'You can I deploy?', 'Can deploy I?', 'Can we merge now?', 'Merge can we?'),
    ['a', 'b', 'e'],
    'Вопрос с can: "Can" + подлежащее + глагол (Can I/you/we...?).'),
  mc(13, 'EASY', 'Mark the things a developer can do:',
    op5('I can write code.', 'I can fix bugs.', 'I can to deploy.', 'I can reviewing PRs.', 'I can open a ticket.'),
    ['a', 'b', 'e'],
    'Корректно только базовый глагол после can: write / fix / open.'),
  mc(14, 'EASY', 'Which negatives are correct?',
    op5("I can't help now.", "She can't merge.", 'We can not to deploy.', "They can't to push.", "He can't access the repo."),
    ['a', 'b', 'e'],
    'Отрицание = can\'t + базовый глагол: can\'t help / merge / access.'),
  // ---- MultipleChoice HARD ----
  mc(15, 'HARD', 'Mark the grammatically correct sentences:',
    op6('You can deploy after the demo.', 'You can deploys after the demo.', "He can't access prod.", "He can'ts access prod.", 'Can they restart the server?', 'They can restart the server.'),
    ['a', 'c', 'e', 'f'],
    'Can/can\'t не меняются по лицам и берут базовый глагол; вопрос — can вперёд.'),
  mc(16, 'HARD', 'Which sentences correctly describe ability with "be able to"?',
    op6('I am able to read logs.', 'I am able read logs.', 'She is able to debug it.', 'She is able to debugging it.', 'We were able to fix it yesterday.', 'We are able fix it.'),
    ['a', 'c', 'e'],
    '"be able to" требует to + глагол: am/is/are able to + базовый глагол.'),
  mc(17, 'HARD', 'Mark the correct permission rules for a protected branch:',
    op6("You can't push to main.", 'You can open a PR.', 'You can to push to main.', "Reviewers can't approve their own PR.", 'You can merge without review.', 'Admins can override the lock.'),
    ['a', 'b', 'd', 'f'],
    'Логично и грамматически верно: запреты через can\'t, разрешения через can + базовый глагол.'),
  mc(18, 'HARD', 'Which are correct short answers?',
    op5("Yes, we can.", "No, they can't.", "Yes, she cans.", "No, he can't.", "Yes, I can to."),
    ['a', 'b', 'd'],
    'Краткий ответ: подлежащее + can / can\'t, без -s и без to.'),
  mc(19, 'HARD', 'Mark the sentences where "can" means PERMISSION (not ability):',
    op5('You can leave early after the release.', 'She can write Python.', 'Guests can read the wiki, but not edit it.', 'He can lift the server rack.', 'Interns can deploy only to staging.'),
    ['a', 'c', 'e'],
    'Разрешение = правило команды (leave / read / deploy to staging); ability — личное умение.'),

  // ---- Ordering EASY ----
  ord(20, 'EASY', 'Build a sentence: "I can review your PR".',
    items4('I', 'can', 'review', 'your PR'),
    'Порядок: подлежащее + can + базовый глагол + дополнение.'),
  ord(21, 'EASY', 'Build: "She can fix the bug".',
    items4('She', 'can', 'fix', 'the bug'),
    'She + can + глагол: can не меняется по лицам.'),
  ord(22, 'EASY', 'Build a question: "Can you deploy the app?".',
    items4('Can', 'you', 'deploy', 'the app?'),
    'Вопрос начинается с "Can" + подлежащее + глагол.'),
  ord(23, 'EASY', 'Build: "We can merge it now".',
    items4('We', 'can', 'merge', 'it now'),
    'We + can + базовый глагол merge.'),
  ord(24, 'EASY', 'Build a negative: "He cannot access the repo".',
    items4('He', 'cannot', 'access', 'the repo'),
    'Отрицание: подлежащее + cannot + базовый глагол.'),
  // ---- Ordering HARD ----
  ord(25, 'HARD', 'Build: "You cannot push to main directly".',
    items4('You cannot', 'push', 'to main', 'directly'),
    'Запрет: подлежащее + cannot + глагол + обстоятельство.'),
  ord(26, 'HARD', 'Build a question: "Can you review my code after lunch?".',
    items4('Can you', 'review', 'my code', 'after lunch?'),
    'Вежливая просьба: Can you + глагол + объект + время.'),
  ord(27, 'HARD', 'Build: "Only admins can delete the branch".',
    items4('Only admins', 'can', 'delete', 'the branch'),
    'Разрешение для группы: подлежащее + can + базовый глагол.'),
  ord(28, 'HARD', 'Build: "I am able to read the logs".',
    items4('I am', 'able to', 'read', 'the logs'),
    '"be able to" = am + able to + базовый глагол (синоним can).'),
  ord(29, 'HARD', 'Build a short answer: "No, I cannot do it today".',
    items4('No,', 'I cannot', 'do it', 'today'),
    'Отрицательный краткий ответ: No, + подлежащее + cannot + глагол.'),

  // ---- FillBlank EASY ----
  fb(30, 'EASY', 'I ___ help you with the deploy.',
    [{ id: 'b1', correctCandidateId: 'c1' }], cand5('can', 'cans', 'am can', 'can to', 'to can'),
    'Способность/готовность помочь: "can" + базовый глагол.'),
  fb(31, 'EASY', 'She can ___ the tests locally.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5('runs', 'running', 'to run', 'run', 'ran'),
    'После "can" глагол в базовой форме: can run.'),
  fb(32, 'EASY', '___ you open the ticket for me?',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Are', 'Do', 'Can', 'Is', 'Will to'),
    'Просьба строится с "Can" в начале: Can you open...?'),
  fb(33, 'EASY', 'We ___ deploy on weekends — it is forbidden.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('can', "can't", 'can to', 'cans', 'are can'),
    'Запрет (нет разрешения): "can\'t" deploy.'),
  fb(34, 'EASY', '"Can you join the standup?" "Yes, I ___."',
    [{ id: 'b1', correctCandidateId: 'c5' }], cand5('do', 'am', 'will', "can't", 'can'),
    'Краткий положительный ответ: "Yes, I can".'),
  // ---- FillBlank HARD ----
  fb(35, 'HARD', 'You ___ push directly to *main*; it is protected.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('can', 'cannot', 'can to', 'not can', 'cans'),
    'Нет разрешения пушить в main → "cannot" + базовый глагол.'),
  fb(36, 'HARD', 'I am ___ to read the production logs now.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5('can', 'able', 'able to to', 'able to', 'can to'),
    'Конструкция "be able to": am able to + глагол (после "I am" нужно "able to").'),
  fb(37, 'HARD', '"Can he deploy the build?" "No, he ___."',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('cans', 'do', "can't", "doesn't", 'not'),
    'Краткий отрицательный ответ: "No, he can\'t".'),
  fb(38, 'HARD', 'Only reviewers ___ approve a pull request.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('cans', 'can', 'can to', 'are can', 'to can'),
    'Разрешение: can не меняется по лицам → reviewers can approve.'),
  fb(39, 'HARD', '___ we restart the server after the release?',
    [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Do', 'Are', 'Is', 'Does', 'Can'),
    'Вопрос-разрешение начинается с "Can": Can we restart...?'),
];

// ---------------------------------------------------------------------------
// LESSON A1.1.5 — Артикли a / an / the с техническими существительными
// ---------------------------------------------------------------------------
const lesson_A1_1_5 = [
  // ---- SingleChoice EASY ----
  sc(0, 'EASY', 'I opened ___ ticket this morning.', op4('a', 'an', 'the', '—'), 'a',
    'Первое упоминание исчисляемого существительного с согласным звуком — "a ticket".'),
  sc(1, 'EASY', 'There is ___ error in the log.', op4('a', 'an', 'the', '—'), 'b',
    '"Error" начинается с гласного звука, поэтому артикль "an".'),
  sc(2, 'EASY', 'I found a bug. ___ bug is in the parser.', op4('A', 'An', 'The', '—'), 'c',
    'Баг уже упоминался, теперь он известен — используется "the".'),
  sc(3, 'EASY', 'We use ___ Git for version control.', op4('a', 'an', 'the', '—'), 'd',
    'Названия инструментов как общее понятие идут без артикля — "we use Git".'),
  sc(4, 'EASY', 'She wrote ___ unit test for the function.', op4('a', 'an', 'the', '—'), 'a',
    '"Unit" звучит как "ю" (согласный звук), поэтому "a unit test".'),
  // ---- SingleChoice HARD ----
  sc(5, 'HARD', 'Send the request to ___ API.', op4('a', 'an', 'the', '—'), 'b',
    'Аббревиатура "API" начинается с гласного звука "эй", поэтому "an API".'),
  sc(6, 'HARD', 'The job takes about ___ hour to finish.', op4('a', 'an', 'the', '—'), 'b',
    'В слове "hour" буква h не читается, звук гласный — "an hour".'),
  sc(7, 'HARD', 'Open ___ terminal and run the build.', op4('a', 'an', 'the', '—'), 'a',
    'Первое упоминание исчисляемого "terminal" с согласным звуком — "a terminal".'),
  sc(8, 'HARD', 'Please review ___ code I pushed yesterday.', op4('a', 'an', 'the', '—'), 'c',
    'Речь о конкретном, известном коде ("который я запушил") — "the code".'),
  sc(9, 'HARD', 'You need ___ URL to open the page.', op4('a', 'an', 'the', '—'), 'a',
    '"URL" звучит как "ю" (согласный звук), поэтому "a URL".'),

  // ---- MultipleChoice EASY ----
  mc(10, 'EASY', 'Mark every phrase that correctly uses "a":',
    op6('a bug', 'a issue', 'a branch', 'a error', 'a server', 'a hour'),
    ['a', 'c', 'e'],
    '"A" ставится перед согласным звуком: a bug, a branch, a server.'),
  mc(11, 'EASY', 'Which phrases correctly use "an"?',
    op6('an app', 'an function', 'an icon', 'an URL', 'an array', 'an link'),
    ['a', 'c', 'e'],
    '"An" — перед гласным звуком: an app, an icon, an array.'),
  mc(12, 'EASY', 'Which sentences need NO article (zero article)?',
    op5('I write code every day.', 'We use Git.', 'I opened a ticket.', 'I love Python.', 'There is a bug.'),
    ['a', 'b', 'd'],
    'Неисчисляемые и общие понятия (code, Git, Python) идут без артикля.'),
  mc(13, 'EASY', 'Mark the correct first-mention sentences:',
    op5('I created a repo.', 'I created an repo.', 'There is an error.', 'There is a error.', 'I see a warning.'),
    ['a', 'c', 'e'],
    'a repo (согл. звук), an error (гласный), a warning (согл.) — все верны.'),
  mc(14, 'EASY', 'Where should we use "the" (specific / already known)?',
    op5('Close the ticket we discussed.', 'Open a new ticket.', 'Restart the server now.', 'I need a break.', 'Check the log from this morning.'),
    ['a', 'c', 'e'],
    '"The" — для конкретных, известных из контекста вещей.'),
  // ---- MultipleChoice HARD ----
  mc(15, 'HARD', 'Mark the phrases where "an" is correct (by sound):',
    op6('an API', 'an URL', 'an SQL query', 'an UI', 'an hour', 'an user'),
    ['a', 'c', 'e'],
    'an API, an SQL ("эс"), an hour — гласный звук. URL/UI/user звучат как "ю".'),
  mc(16, 'HARD', 'Mark the phrases where "a" is correct (by sound):',
    op6('a URL', 'a icon', 'a UI', 'a hour', 'a user', 'a API'),
    ['a', 'c', 'e'],
    'a URL ("ю"), a UI ("ю"), a user ("ю") — согласный звук.'),
  mc(17, 'HARD', 'Which sentences are grammatically correct?',
    op6('I sent an email.', 'I sent a email.', 'It is an honest mistake.', 'It is a honest mistake.', 'We hit an edge case.', 'We hit a edge case.'),
    ['a', 'c', 'e'],
    'email, honest (h немое), edge — гласный звук, поэтому "an".'),
  mc(18, 'HARD', 'Choose the correct uses of "the" for unique/specific things:',
    op6('the database is down', 'the Git is down', 'the main branch', 'the JavaScript', 'the production server', 'the code in general'),
    ['a', 'c', 'e'],
    'the — для конкретных объектов (database, main branch, production server), не для общих понятий.'),
  mc(19, 'HARD', 'Pick the correctly written commit/ticket lines:',
    op5('Fix a typo in the README.', 'Fix the typo in README.', 'Add an endpoint for users.', 'Add a endpoint for users.', 'Update the docs after the release.'),
    ['a', 'c', 'e'],
    'a typo (согл.), an endpoint (гласный), the docs/the release (конкретные) — верны.'),

  // ---- Ordering EASY ----
  ord(20, 'EASY', 'Build a sentence: "I found a bug today".',
    items4('I found', 'a', 'bug', 'today'),
    'Первое упоминание "bug" с согласным звуком → "a bug".'),
  ord(21, 'EASY', 'Build: "There is an error here".',
    items4('There is', 'an', 'error', 'here'),
    '"error" начинается с гласного звука → "an error".'),
  ord(22, 'EASY', 'Build: "Please restart the server".',
    items4('Please', 'restart', 'the', 'server'),
    'Конкретный, известный сервер → "the server".'),
  ord(23, 'EASY', 'Build: "We write code every day".',
    items4('We', 'write', 'code', 'every day'),
    '"code" — неисчисляемое, общее понятие, без артикля.'),
  ord(24, 'EASY', 'Build: "She opened a new ticket".',
    items4('She', 'opened', 'a new', 'ticket'),
    'Новый, впервые упомянутый тикет → "a new ticket".'),
  // ---- Ordering HARD ----
  ord(25, 'HARD', 'Build: "I need an hour to fix it".',
    items4('I need', 'an hour', 'to fix', 'it'),
    'В "hour" h немое, звук гласный → "an hour".'),
  ord(26, 'HARD', 'Build: "The bug is in the parser".',
    items4('The bug', 'is', 'in the', 'parser'),
    'Оба объекта известны/конкретны из контекста → "the".'),
  ord(27, 'HARD', 'Build: "This endpoint returns a URL".',
    items4('This endpoint', 'returns', 'a', 'URL'),
    '"URL" звучит как "ю" (согласный звук) → "a URL".'),
  ord(28, 'HARD', 'Build: "We added an API for payments".',
    items4('We added', 'an API', 'for', 'payments'),
    '"API" начинается с гласного звука → "an API".'),
  ord(29, 'HARD', 'Build: "I pushed the fix to main".',
    items4('I pushed', 'the fix', 'to', 'main'),
    'Конкретное, известное исправление → "the fix".'),

  // ---- FillBlank EASY ----
  fb(30, 'EASY', 'I opened ___ pull request.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('an', 'a', 'the', 'is', '—'),
    'Первое упоминание "pull request" (согл. звук) → "a".'),
  fb(31, 'EASY', 'There is ___ error on line 10.',
    [{ id: 'b1', correctCandidateId: 'c3' }], cand5('a', 'the', 'an', 'is', '—'),
    '"error" — гласный звук → "an".'),
  fb(32, 'EASY', 'The test failed. ___ test is flaky.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5('A', 'An', 'a', 'The', '—'),
    'Тест уже упомянут, теперь известен → "The".'),
  fb(33, 'EASY', 'We use ___ Docker for our builds.',
    [{ id: 'b1', correctCandidateId: 'c5' }], cand5('a', 'an', 'the', 'is', '—'),
    'Название инструмента как общее понятие → без артикля.'),
  fb(34, 'EASY', 'She added ___ button to the page.',
    [{ id: 'b1', correctCandidateId: 'c1' }], cand5('a', 'an', 'the', 'is', '—'),
    'Новая, впервые упомянутая кнопка (согл. звук) → "a".'),
  // ---- FillBlank HARD ----
  fb(35, 'HARD', 'The call takes ___ hour.',
    [{ id: 'b1', correctCandidateId: 'c4' }], cand5('a', 'the', '—', 'an', 'is'),
    'В "hour" h немое, звук гласный → "an".'),
  fb(36, 'HARD', 'The form sends ___ SQL query to the database.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('a', 'an', 'the', '—', 'is'),
    '"SQL" читается с гласного звука "эс" → "an".'),
  fb(37, 'HARD', 'It is just ___ UI change, not logic.',
    [{ id: 'b1', correctCandidateId: 'c1' }], cand5('a', 'an', 'the', '—', 'is'),
    '"UI" звучит как "ю" (согл. звук) → "a".'),
  fb(38, 'HARD', 'Read ___ README before you start.',
    [{ id: 'b1', correctCandidateId: 'c5' }], cand5('a', 'an', '—', 'is', 'the'),
    'Конкретный README этого проекта → "the".'),
  fb(39, 'HARD', 'We added ___ endpoint for the payment flow.',
    [{ id: 'b1', correctCandidateId: 'c2' }], cand5('a', 'an', 'the', '—', 'is'),
    '"endpoint" начинается с гласного звука → "an".'),
];

// --- Lesson assembler (same id scheme as quest-english.js) ------------------
function buildLesson(s, t, l, lessonId, lessonTitle, questions) {
  const TYPE_PREFIX = { SingleChoice: 'sc', MultipleChoice: 'mc', Ordering: 'ord', FillBlank: 'fb' };
  const DIFF_PREFIX = { EASY: 'e', HARD: 'h' };
  const counters = {};
  const wrapped = questions.map((q, order) => {
    const key = `${TYPE_PREFIX[q.type]}-${DIFF_PREFIX[q.difficulty]}`;
    counters[key] = (counters[key] || 0) + 1;
    const id = `qsb-courses-english-tech-v2-${s}-${t}-${l}-${TYPE_PREFIX[q.type]}-${DIFF_PREFIX[q.difficulty]}-${counters[key]}`;
    return { id, order, text: q.text, payload: q };
  });
  return { id: lessonId, title: lessonTitle, questions: wrapped };
}

// --- A0 (section 1), Theme 1 ---
const lesson_A0_1_1 = require('./english-tech/lessons/1-1-1');
const lesson_A0_1_2 = require('./english-tech/lessons/1-1-2');
const lesson_A0_1_3 = require('./english-tech/lessons/1-1-3');
const lesson_A0_1_4 = require('./english-tech/lessons/1-1-4');
const lesson_A0_1_5 = require('./english-tech/lessons/1-1-5');
// A0 Theme 2 (Objects in an app)
const lesson_A0_2_1 = require('./english-tech/lessons/1-2-1');
const lesson_A0_2_2 = require('./english-tech/lessons/1-2-2');
const lesson_A0_2_3 = require('./english-tech/lessons/1-2-3');
const lesson_A0_2_4 = require('./english-tech/lessons/1-2-4');
const lesson_A0_2_5 = require('./english-tech/lessons/1-2-5');
// A0 Theme 3 (Tiny actions)
const lesson_A0_3_1 = require('./english-tech/lessons/1-3-1');
const lesson_A0_3_2 = require('./english-tech/lessons/1-3-2');
const lesson_A0_3_3 = require('./english-tech/lessons/1-3-3');
const lesson_A0_3_4 = require('./english-tech/lessons/1-3-4');
const lesson_A0_3_5 = require('./english-tech/lessons/1-3-5');
// A0 Theme 4 (First phrases at work)
const lesson_A0_4_1 = require('./english-tech/lessons/1-4-1');
const lesson_A0_4_2 = require('./english-tech/lessons/1-4-2');
const lesson_A0_4_3 = require('./english-tech/lessons/1-4-3');
const lesson_A0_4_4 = require('./english-tech/lessons/1-4-4');
const lesson_A0_4_5 = require('./english-tech/lessons/1-4-5');

// Theme 2 (Developer tools) lessons live in their own files.
const lesson_A1_2_1 = require('./english-tech/lessons/2-2-1');
const lesson_A1_2_2 = require('./english-tech/lessons/2-2-2');
const lesson_A1_2_3 = require('./english-tech/lessons/2-2-3');
const lesson_A1_2_4 = require('./english-tech/lessons/2-2-4');
const lesson_A1_2_5 = require('./english-tech/lessons/2-2-5');
// Theme 3 (Team basics)
const lesson_A1_3_1 = require('./english-tech/lessons/2-3-1');
const lesson_A1_3_2 = require('./english-tech/lessons/2-3-2');
const lesson_A1_3_3 = require('./english-tech/lessons/2-3-3');
const lesson_A1_3_4 = require('./english-tech/lessons/2-3-4');
const lesson_A1_3_5 = require('./english-tech/lessons/2-3-5');
// Theme 4 (Numbers, time and status)
const lesson_A1_4_1 = require('./english-tech/lessons/2-4-1');
const lesson_A1_4_2 = require('./english-tech/lessons/2-4-2');
const lesson_A1_4_3 = require('./english-tech/lessons/2-4-3');
const lesson_A1_4_4 = require('./english-tech/lessons/2-4-4');
const lesson_A1_4_5 = require('./english-tech/lessons/2-4-5');
// --- A2 (section 3), Theme 1 ---
const lesson_A2_1_1 = require('./english-tech/lessons/3-1-1');
const lesson_A2_1_2 = require('./english-tech/lessons/3-1-2');
const lesson_A2_1_3 = require('./english-tech/lessons/3-1-3');
const lesson_A2_1_4 = require('./english-tech/lessons/3-1-4');
const lesson_A2_1_5 = require('./english-tech/lessons/3-1-5');
// A2 Theme 2 (API, data and tests)
const lesson_A2_2_1 = require('./english-tech/lessons/3-2-1');
const lesson_A2_2_2 = require('./english-tech/lessons/3-2-2');
const lesson_A2_2_3 = require('./english-tech/lessons/3-2-3');
const lesson_A2_2_4 = require('./english-tech/lessons/3-2-4');
const lesson_A2_2_5 = require('./english-tech/lessons/3-2-5');
// A2 Theme 3 (Updates and collaboration)
const lesson_A2_3_1 = require('./english-tech/lessons/3-3-1');
const lesson_A2_3_2 = require('./english-tech/lessons/3-3-2');
const lesson_A2_3_3 = require('./english-tech/lessons/3-3-3');
const lesson_A2_3_4 = require('./english-tech/lessons/3-3-4');
const lesson_A2_3_5 = require('./english-tech/lessons/3-3-5');
// A2 Theme 4 (Past and future of tasks)
const lesson_A2_4_1 = require('./english-tech/lessons/3-4-1');
const lesson_A2_4_2 = require('./english-tech/lessons/3-4-2');
const lesson_A2_4_3 = require('./english-tech/lessons/3-4-3');
const lesson_A2_4_4 = require('./english-tech/lessons/3-4-4');
const lesson_A2_4_5 = require('./english-tech/lessons/3-4-5');
// --- B1 (section 4), Theme 1 ---
const lesson_B1_1_1 = require('./english-tech/lessons/4-1-1');
const lesson_B1_1_2 = require('./english-tech/lessons/4-1-2');
const lesson_B1_1_3 = require('./english-tech/lessons/4-1-3');
const lesson_B1_1_4 = require('./english-tech/lessons/4-1-4');
const lesson_B1_1_5 = require('./english-tech/lessons/4-1-5');
// B1 Theme 2 (Architecture and platforms)
const lesson_B1_2_1 = require('./english-tech/lessons/4-2-1');
const lesson_B1_2_2 = require('./english-tech/lessons/4-2-2');
const lesson_B1_2_3 = require('./english-tech/lessons/4-2-3');
const lesson_B1_2_4 = require('./english-tech/lessons/4-2-4');
const lesson_B1_2_5 = require('./english-tech/lessons/4-2-5');
// B1 Theme 3 (Debugging and releases)
const lesson_B1_3_1 = require('./english-tech/lessons/4-3-1');
const lesson_B1_3_2 = require('./english-tech/lessons/4-3-2');
const lesson_B1_3_3 = require('./english-tech/lessons/4-3-3');
const lesson_B1_3_4 = require('./english-tech/lessons/4-3-4');
const lesson_B1_3_5 = require('./english-tech/lessons/4-3-5');
// B1 Theme 4 (Explaining and discussing)
const lesson_B1_4_1 = require('./english-tech/lessons/4-4-1');
const lesson_B1_4_2 = require('./english-tech/lessons/4-4-2');
const lesson_B1_4_3 = require('./english-tech/lessons/4-4-3');
const lesson_B1_4_4 = require('./english-tech/lessons/4-4-4');
const lesson_B1_4_5 = require('./english-tech/lessons/4-4-5');
// --- B2 (section 5), Theme 1 ---
const lesson_B2_1_1 = require('./english-tech/lessons/5-1-1');
const lesson_B2_1_2 = require('./english-tech/lessons/5-1-2');
const lesson_B2_1_3 = require('./english-tech/lessons/5-1-3');
const lesson_B2_1_4 = require('./english-tech/lessons/5-1-4');
const lesson_B2_1_5 = require('./english-tech/lessons/5-1-5');
// B2 Theme 2 (Quality attributes)
const lesson_B2_2_1 = require('./english-tech/lessons/5-2-1');
const lesson_B2_2_2 = require('./english-tech/lessons/5-2-2');
const lesson_B2_2_3 = require('./english-tech/lessons/5-2-3');
const lesson_B2_2_4 = require('./english-tech/lessons/5-2-4');
const lesson_B2_2_5 = require('./english-tech/lessons/5-2-5');
// B2 Theme 3 (Professional communication)
const lesson_B2_3_1 = require('./english-tech/lessons/5-3-1');
const lesson_B2_3_2 = require('./english-tech/lessons/5-3-2');
const lesson_B2_3_3 = require('./english-tech/lessons/5-3-3');
const lesson_B2_3_4 = require('./english-tech/lessons/5-3-4');
const lesson_B2_3_5 = require('./english-tech/lessons/5-3-5');
// B2 Theme 4 (Argumentation)
const lesson_B2_4_1 = require('./english-tech/lessons/5-4-1');
const lesson_B2_4_2 = require('./english-tech/lessons/5-4-2');
const lesson_B2_4_3 = require('./english-tech/lessons/5-4-3');
const lesson_B2_4_4 = require('./english-tech/lessons/5-4-4');
const lesson_B2_4_5 = require('./english-tech/lessons/5-4-5');
// --- C1 (section 6), Theme 1 ---
const lesson_C1_1_1 = require('./english-tech/lessons/6-1-1');
const lesson_C1_1_2 = require('./english-tech/lessons/6-1-2');
const lesson_C1_1_3 = require('./english-tech/lessons/6-1-3');
const lesson_C1_1_4 = require('./english-tech/lessons/6-1-4');
const lesson_C1_1_5 = require('./english-tech/lessons/6-1-5');
// C1 Theme 2 (Advanced systems vocabulary)
const lesson_C1_2_1 = require('./english-tech/lessons/6-2-1');
const lesson_C1_2_2 = require('./english-tech/lessons/6-2-2');
const lesson_C1_2_3 = require('./english-tech/lessons/6-2-3');
const lesson_C1_2_4 = require('./english-tech/lessons/6-2-4');
const lesson_C1_2_5 = require('./english-tech/lessons/6-2-5');
// C1 Theme 3 (Leadership communication)
const lesson_C1_3_1 = require('./english-tech/lessons/6-3-1');
const lesson_C1_3_2 = require('./english-tech/lessons/6-3-2');
const lesson_C1_3_3 = require('./english-tech/lessons/6-3-3');
const lesson_C1_3_4 = require('./english-tech/lessons/6-3-4');
const lesson_C1_3_5 = require('./english-tech/lessons/6-3-5');
// C1 Theme 4 (Complex argumentation)
const lesson_C1_4_1 = require('./english-tech/lessons/6-4-1');
const lesson_C1_4_2 = require('./english-tech/lessons/6-4-2');
const lesson_C1_4_3 = require('./english-tech/lessons/6-4-3');
const lesson_C1_4_4 = require('./english-tech/lessons/6-4-4');
const lesson_C1_4_5 = require('./english-tech/lessons/6-4-5');
// --- C2 (section 7), Theme 1 ---
const lesson_C2_1_1 = require('./english-tech/lessons/7-1-1');
const lesson_C2_1_2 = require('./english-tech/lessons/7-1-2');
const lesson_C2_1_3 = require('./english-tech/lessons/7-1-3');
const lesson_C2_1_4 = require('./english-tech/lessons/7-1-4');
const lesson_C2_1_5 = require('./english-tech/lessons/7-1-5');
// --- C2 (section 7), Theme 2 ---
const lesson_C2_2_1 = require('./english-tech/lessons/7-2-1');
const lesson_C2_2_2 = require('./english-tech/lessons/7-2-2');
const lesson_C2_2_3 = require('./english-tech/lessons/7-2-3');
const lesson_C2_2_4 = require('./english-tech/lessons/7-2-4');
const lesson_C2_2_5 = require('./english-tech/lessons/7-2-5');
// --- C2 (section 7), Theme 3 ---
const lesson_C2_3_1 = require('./english-tech/lessons/7-3-1');
const lesson_C2_3_2 = require('./english-tech/lessons/7-3-2');
const lesson_C2_3_3 = require('./english-tech/lessons/7-3-3');
const lesson_C2_3_4 = require('./english-tech/lessons/7-3-4');
const lesson_C2_3_5 = require('./english-tech/lessons/7-3-5');
// --- C2 (section 7), Theme 4 ---
const lesson_C2_4_1 = require('./english-tech/lessons/7-4-1');
const lesson_C2_4_2 = require('./english-tech/lessons/7-4-2');
const lesson_C2_4_3 = require('./english-tech/lessons/7-4-3');
const lesson_C2_4_4 = require('./english-tech/lessons/7-4-4');
const lesson_C2_4_5 = require('./english-tech/lessons/7-4-5');

module.exports = {
  id: 'qb-courses-english-tech-v2',
  title: 'Technical English A0-C2: английский для разработчиков',
  sections: [
    {
      id: 'sb-courses-english-tech-v2-1',
      title: 'A0 — Полный ноль: первые слова разработчика',
      themes: [
        {
          id: 'tb-courses-english-tech-v2-1-1',
          title: 'Буквы, символы и первые слова',
          lessons: [
            buildLesson(1, 1, 1, 'lb-courses-english-tech-v2-1-1-1', 'Symbols in paths and URLs', lesson_A0_1_1),
            buildLesson(1, 1, 2, 'lb-courses-english-tech-v2-1-1-2', 'The keyboard and its keys', lesson_A0_1_2),
            buildLesson(1, 1, 3, 'lb-courses-english-tech-v2-1-1-3', 'First nouns: file, folder, code', lesson_A0_1_3),
            buildLesson(1, 1, 4, 'lb-courses-english-tech-v2-1-1-4', 'Numbers, digits and versions', lesson_A0_1_4),
            buildLesson(1, 1, 5, 'lb-courses-english-tech-v2-1-1-5', 'Saying hello in team chat', lesson_A0_1_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-1-2',
          title: 'Объекты в приложении',
          lessons: [
            buildLesson(1, 2, 1, 'lb-courses-english-tech-v2-1-2-1', 'App, screen, page, window', lesson_A0_2_1),
            buildLesson(1, 2, 2, 'lb-courses-english-tech-v2-1-2-2', 'Button, field, label, icon', lesson_A0_2_2),
            buildLesson(1, 2, 3, 'lb-courses-english-tech-v2-1-2-3', 'File, folder, path', lesson_A0_2_3),
            buildLesson(1, 2, 4, 'lb-courses-english-tech-v2-1-2-4', 'Menu, list, tab', lesson_A0_2_4),
            buildLesson(1, 2, 5, 'lb-courses-english-tech-v2-1-2-5', 'Text, word, line of code', lesson_A0_2_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-1-3',
          title: 'Крошечные действия',
          lessons: [
            buildLesson(1, 3, 1, 'lb-courses-english-tech-v2-1-3-1', 'Click, tap, press', lesson_A0_3_1),
            buildLesson(1, 3, 2, 'lb-courses-english-tech-v2-1-3-2', 'Open, close, save', lesson_A0_3_2),
            buildLesson(1, 3, 3, 'lb-courses-english-tech-v2-1-3-3', 'Copy, paste, cut', lesson_A0_3_3),
            buildLesson(1, 3, 4, 'lb-courses-english-tech-v2-1-3-4', 'Read, write, delete', lesson_A0_3_4),
            buildLesson(1, 3, 5, 'lb-courses-english-tech-v2-1-3-5', 'Send, reply, ask', lesson_A0_3_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-1-4',
          title: 'Первые фразы на работе',
          lessons: [
            buildLesson(1, 4, 1, 'lb-courses-english-tech-v2-1-4-1', 'Yes, no, ok — short answers', lesson_A0_4_1),
            buildLesson(1, 4, 2, 'lb-courses-english-tech-v2-1-4-2', 'Please and thank you', lesson_A0_4_2),
            buildLesson(1, 4, 3, 'lb-courses-english-tech-v2-1-4-3', 'I do not understand, can you help', lesson_A0_4_3),
            buildLesson(1, 4, 4, 'lb-courses-english-tech-v2-1-4-4', 'Time words: at, on, in', lesson_A0_4_4),
            buildLesson(1, 4, 5, 'lb-courses-english-tech-v2-1-4-5', 'It works, it does not work', lesson_A0_4_5),
          ],
        },
      ],
    },
    {
      id: 'sb-courses-english-tech-v2-2', // A1 = section 2 (A0 = section 1)
      title: 'A1 — База: простые задачи и рабочие инструменты',
      themes: [
        {
          id: 'tb-courses-english-tech-v2-2-1',
          title: 'Грамматика для ежедневной работы',
          lessons: [
            buildLesson(2, 1, 1, 'lb-courses-english-tech-v2-2-1-1',
              'Местоимения и глагол "be"', lesson_A1_1_1),
            buildLesson(2, 1, 2, 'lb-courses-english-tech-v2-2-1-2',
              'Present Simple для рабочих рутин', lesson_A1_1_2),
            buildLesson(2, 1, 3, 'lb-courses-english-tech-v2-2-1-3',
              'There is / There are', lesson_A1_1_3),
            buildLesson(2, 1, 4, 'lb-courses-english-tech-v2-2-1-4',
              'Can / cannot (способность и разрешение)', lesson_A1_1_4),
            buildLesson(2, 1, 5, 'lb-courses-english-tech-v2-2-1-5',
              'Артикли a / an / the с техническими существительными', lesson_A1_1_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-2-2',
          title: 'Инструменты разработчика',
          lessons: [
            buildLesson(2, 2, 1, 'lb-courses-english-tech-v2-2-2-1', 'Browser and tabs', lesson_A1_2_1),
            buildLesson(2, 2, 2, 'lb-courses-english-tech-v2-2-2-2', 'Terminal basics', lesson_A1_2_2),
            buildLesson(2, 2, 3, 'lb-courses-english-tech-v2-2-2-3', 'Repository and commits', lesson_A1_2_3),
            buildLesson(2, 2, 4, 'lb-courses-english-tech-v2-2-2-4', 'Branches and pull requests', lesson_A1_2_4),
            buildLesson(2, 2, 5, 'lb-courses-english-tech-v2-2-2-5', 'Files, folders and paths', lesson_A1_2_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-2-3',
          title: 'Командная работа',
          lessons: [
            buildLesson(2, 3, 1, 'lb-courses-english-tech-v2-2-3-1', 'Tasks and tickets', lesson_A1_3_1),
            buildLesson(2, 3, 2, 'lb-courses-english-tech-v2-2-3-2', 'Standups and meetings', lesson_A1_3_2),
            buildLesson(2, 3, 3, 'lb-courses-english-tech-v2-2-3-3', 'Deadlines and priorities', lesson_A1_3_3),
            buildLesson(2, 3, 4, 'lb-courses-english-tech-v2-2-3-4', 'Reading a short README', lesson_A1_3_4),
            buildLesson(2, 3, 5, 'lb-courses-english-tech-v2-2-3-5', 'Asking for help in chat', lesson_A1_3_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-2-4',
          title: 'Числа, время и статусы',
          lessons: [
            buildLesson(2, 4, 1, 'lb-courses-english-tech-v2-2-4-1', 'Versions and dates', lesson_A1_4_1),
            buildLesson(2, 4, 2, 'lb-courses-english-tech-v2-2-4-2', 'Days, times and schedules', lesson_A1_4_2),
            buildLesson(2, 4, 3, 'lb-courses-english-tech-v2-2-4-3', 'Status words', lesson_A1_4_3),
            buildLesson(2, 4, 4, 'lb-courses-english-tech-v2-2-4-4', 'Short answers and agreement', lesson_A1_4_4),
            buildLesson(2, 4, 5, 'lb-courses-english-tech-v2-2-4-5', 'Polite requests', lesson_A1_4_5),
          ],
        },
      ],
    },
    {
      id: 'sb-courses-english-tech-v2-3',
      title: 'A2 — Уверенный старт: Git, API, тесты и обновления',
      themes: [
        {
          id: 'tb-courses-english-tech-v2-3-1',
          title: 'Грамматика: время, сравнение, количество',
          lessons: [
            buildLesson(3, 1, 1, 'lb-courses-english-tech-v2-3-1-1', 'Past Simple для завершённой работы', lesson_A2_1_1),
            buildLesson(3, 1, 2, 'lb-courses-english-tech-v2-3-1-2', 'Future: will и going to', lesson_A2_1_2),
            buildLesson(3, 1, 3, 'lb-courses-english-tech-v2-3-1-3', 'Comparatives in technical choices', lesson_A2_1_3),
            buildLesson(3, 1, 4, 'lb-courses-english-tech-v2-3-1-4', 'Quantifiers for logs and data', lesson_A2_1_4),
            buildLesson(3, 1, 5, 'lb-courses-english-tech-v2-3-1-5', 'Present Continuous vs Simple', lesson_A2_1_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-3-2',
          title: 'API, данные и тесты',
          lessons: [
            buildLesson(3, 2, 1, 'lb-courses-english-tech-v2-3-2-1', 'Endpoints and requests', lesson_A2_2_1),
            buildLesson(3, 2, 2, 'lb-courses-english-tech-v2-3-2-2', 'JSON and fields', lesson_A2_2_2),
            buildLesson(3, 2, 3, 'lb-courses-english-tech-v2-3-2-3', 'Database rows and records', lesson_A2_2_3),
            buildLesson(3, 2, 4, 'lb-courses-english-tech-v2-3-2-4', 'Manual and automated tests', lesson_A2_2_4),
            buildLesson(3, 2, 5, 'lb-courses-english-tech-v2-3-2-5', 'Status codes and responses', lesson_A2_2_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-3-3',
          title: 'Обновления и совместная работа',
          lessons: [
            buildLesson(3, 3, 1, 'lb-courses-english-tech-v2-3-3-1', 'Status updates', lesson_A2_3_1),
            buildLesson(3, 3, 2, 'lb-courses-english-tech-v2-3-3-2', 'Blockers and questions', lesson_A2_3_2),
            buildLesson(3, 3, 3, 'lb-courses-english-tech-v2-3-3-3', 'Estimates and scope', lesson_A2_3_3),
            buildLesson(3, 3, 4, 'lb-courses-english-tech-v2-3-3-4', 'Giving and receiving feedback', lesson_A2_3_4),
            buildLesson(3, 3, 5, 'lb-courses-english-tech-v2-3-3-5', 'Writing a clear commit message', lesson_A2_3_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-3-4',
          title: 'Прошлое и будущее задач',
          lessons: [
            buildLesson(3, 4, 1, 'lb-courses-english-tech-v2-3-4-1', 'Reporting what you did', lesson_A2_4_1),
            buildLesson(3, 4, 2, 'lb-courses-english-tech-v2-3-4-2', 'Planning what you will do', lesson_A2_4_2),
            buildLesson(3, 4, 3, 'lb-courses-english-tech-v2-3-4-3', 'Describing changes over time', lesson_A2_4_3),
            buildLesson(3, 4, 4, 'lb-courses-english-tech-v2-3-4-4', 'Talking about deadlines', lesson_A2_4_4),
            buildLesson(3, 4, 5, 'lb-courses-english-tech-v2-3-4-5', 'Following up', lesson_A2_4_5),
          ],
        },
      ],
    },
    {
      id: 'sb-courses-english-tech-v2-4',
      title: 'B1 — Средний уровень: архитектура, релизы и отладка',
      themes: [
        {
          id: 'tb-courses-english-tech-v2-4-1',
          title: 'Грамматика для объяснения причин',
          lessons: [
            buildLesson(4, 1, 1, 'lb-courses-english-tech-v2-4-1-1', 'Present Perfect for recent changes', lesson_B1_1_1),
            buildLesson(4, 1, 2, 'lb-courses-english-tech-v2-4-1-2', 'First conditional for risk', lesson_B1_1_2),
            buildLesson(4, 1, 3, 'lb-courses-english-tech-v2-4-1-3', 'Modals: advice and obligation', lesson_B1_1_3),
            buildLesson(4, 1, 4, 'lb-courses-english-tech-v2-4-1-4', 'Passive voice in bug reports', lesson_B1_1_4),
            buildLesson(4, 1, 5, 'lb-courses-english-tech-v2-4-1-5', 'Linking words for cause and result', lesson_B1_1_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-4-2',
          title: 'Архитектура и платформы',
          lessons: [
            buildLesson(4, 2, 1, 'lb-courses-english-tech-v2-4-2-1', 'Frontend basics', lesson_B1_2_1),
            buildLesson(4, 2, 2, 'lb-courses-english-tech-v2-4-2-2', 'Backend basics', lesson_B1_2_2),
            buildLesson(4, 2, 3, 'lb-courses-english-tech-v2-4-2-3', 'Mobile client basics', lesson_B1_2_3),
            buildLesson(4, 2, 4, 'lb-courses-english-tech-v2-4-2-4', 'Architecture boundaries', lesson_B1_2_4),
            buildLesson(4, 2, 5, 'lb-courses-english-tech-v2-4-2-5', 'Databases and storage', lesson_B1_2_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-4-3',
          title: 'Отладка и релизы',
          lessons: [
            buildLesson(4, 3, 1, 'lb-courses-english-tech-v2-4-3-1', 'Reading error messages', lesson_B1_3_1),
            buildLesson(4, 3, 2, 'lb-courses-english-tech-v2-4-3-2', 'Reproducing bugs', lesson_B1_3_2),
            buildLesson(4, 3, 3, 'lb-courses-english-tech-v2-4-3-3', 'Release notes', lesson_B1_3_3),
            buildLesson(4, 3, 4, 'lb-courses-english-tech-v2-4-3-4', 'Sprint planning', lesson_B1_3_4),
            buildLesson(4, 3, 5, 'lb-courses-english-tech-v2-4-3-5', 'Rollbacks and hotfixes', lesson_B1_3_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-4-4',
          title: 'Объяснение и обсуждение',
          lessons: [
            buildLesson(4, 4, 1, 'lb-courses-english-tech-v2-4-4-1', 'Explaining a cause', lesson_B1_4_1),
            buildLesson(4, 4, 2, 'lb-courses-english-tech-v2-4-4-2', 'Describing a process step by step', lesson_B1_4_2),
            buildLesson(4, 4, 3, 'lb-courses-english-tech-v2-4-4-3', 'Comparing two solutions', lesson_B1_4_3),
            buildLesson(4, 4, 4, 'lb-courses-english-tech-v2-4-4-4', 'Agreeing and disagreeing politely', lesson_B1_4_4),
            buildLesson(4, 4, 5, 'lb-courses-english-tech-v2-4-4-5', 'Summarizing a discussion', lesson_B1_4_5),
          ],
        },
      ],
    },
    {
      id: 'sb-courses-english-tech-v2-5',
      title: 'B2 — Выше среднего: ревью, безопасность, производительность',
      themes: [
        {
          id: 'tb-courses-english-tech-v2-5-1',
          title: 'Точность и осторожная формулировка',
          lessons: [
            buildLesson(5, 1, 1, 'lb-courses-english-tech-v2-5-1-1', 'Hedging in technical claims', lesson_B2_1_1),
            buildLesson(5, 1, 2, 'lb-courses-english-tech-v2-5-1-2', 'Relative clauses in specs', lesson_B2_1_2),
            buildLesson(5, 1, 3, 'lb-courses-english-tech-v2-5-1-3', 'Reported speech in reviews', lesson_B2_1_3),
            buildLesson(5, 1, 4, 'lb-courses-english-tech-v2-5-1-4', 'Second conditional for alternatives', lesson_B2_1_4),
            buildLesson(5, 1, 5, 'lb-courses-english-tech-v2-5-1-5', 'Cause-effect connectors', lesson_B2_1_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-5-2',
          title: 'Качественные атрибуты',
          lessons: [
            buildLesson(5, 2, 1, 'lb-courses-english-tech-v2-5-2-1', 'Performance bottlenecks', lesson_B2_2_1),
            buildLesson(5, 2, 2, 'lb-courses-english-tech-v2-5-2-2', 'Security reviews', lesson_B2_2_2),
            buildLesson(5, 2, 3, 'lb-courses-english-tech-v2-5-2-3', 'Concurrency and race conditions', lesson_B2_2_3),
            buildLesson(5, 2, 4, 'lb-courses-english-tech-v2-5-2-4', 'CI/CD pipelines', lesson_B2_2_4),
            buildLesson(5, 2, 5, 'lb-courses-english-tech-v2-5-2-5', 'Reliability and monitoring', lesson_B2_2_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-5-3',
          title: 'Профессиональная коммуникация',
          lessons: [
            buildLesson(5, 3, 1, 'lb-courses-english-tech-v2-5-3-1', 'Pull request reviews', lesson_B2_3_1),
            buildLesson(5, 3, 2, 'lb-courses-english-tech-v2-5-3-2', 'Design documents', lesson_B2_3_2),
            buildLesson(5, 3, 3, 'lb-courses-english-tech-v2-5-3-3', 'Incident updates', lesson_B2_3_3),
            buildLesson(5, 3, 4, 'lb-courses-english-tech-v2-5-3-4', 'Stakeholder summaries', lesson_B2_3_4),
            buildLesson(5, 3, 5, 'lb-courses-english-tech-v2-5-3-5', 'Writing constructive feedback', lesson_B2_3_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-5-4',
          title: 'Аргументация',
          lessons: [
            buildLesson(5, 4, 1, 'lb-courses-english-tech-v2-5-4-1', 'Making a recommendation', lesson_B2_4_1),
            buildLesson(5, 4, 2, 'lb-courses-english-tech-v2-5-4-2', 'Weighing trade-offs', lesson_B2_4_2),
            buildLesson(5, 4, 3, 'lb-courses-english-tech-v2-5-4-3', 'Justifying a decision', lesson_B2_4_3),
            buildLesson(5, 4, 4, 'lb-courses-english-tech-v2-5-4-4', 'Raising a concern diplomatically', lesson_B2_4_4),
            buildLesson(5, 4, 5, 'lb-courses-english-tech-v2-5-4-5', 'Responding to criticism', lesson_B2_4_5),
          ],
        },
      ],
    },
    {
      id: 'sb-courses-english-tech-v2-6',
      title: 'C1 — Продвинутый: RFC, распределённые системы и влияние',
      themes: [
        {
          id: 'tb-courses-english-tech-v2-6-1',
          title: 'Стиль, связность и позиция автора',
          lessons: [
            buildLesson(6, 1, 1, 'lb-courses-english-tech-v2-6-1-1', 'Nominalization in architecture prose', lesson_C1_1_1),
            buildLesson(6, 1, 2, 'lb-courses-english-tech-v2-6-1-2', 'Cohesion across paragraphs', lesson_C1_1_2),
            buildLesson(6, 1, 3, 'lb-courses-english-tech-v2-6-1-3', 'Register in technical disagreement', lesson_C1_1_3),
            buildLesson(6, 1, 4, 'lb-courses-english-tech-v2-6-1-4', 'Precision with assumptions', lesson_C1_1_4),
            buildLesson(6, 1, 5, 'lb-courses-english-tech-v2-6-1-5', 'Emphasis and focus structures', lesson_C1_1_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-6-2',
          title: 'Продвинутая системная лексика',
          lessons: [
            buildLesson(6, 2, 1, 'lb-courses-english-tech-v2-6-2-1', 'Distributed systems', lesson_C1_2_1),
            buildLesson(6, 2, 2, 'lb-courses-english-tech-v2-6-2-2', 'Observability', lesson_C1_2_2),
            buildLesson(6, 2, 3, 'lb-courses-english-tech-v2-6-2-3', 'Privacy and data protection', lesson_C1_2_3),
            buildLesson(6, 2, 4, 'lb-courses-english-tech-v2-6-2-4', 'API contracts and versioning', lesson_C1_2_4),
            buildLesson(6, 2, 5, 'lb-courses-english-tech-v2-6-2-5', 'Scalability and capacity', lesson_C1_2_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-6-3',
          title: 'Лидерская коммуникация',
          lessons: [
            buildLesson(6, 3, 1, 'lb-courses-english-tech-v2-6-3-1', 'Writing RFCs', lesson_C1_3_1),
            buildLesson(6, 3, 2, 'lb-courses-english-tech-v2-6-3-2', 'Mentoring through code review', lesson_C1_3_2),
            buildLesson(6, 3, 3, 'lb-courses-english-tech-v2-6-3-3', 'Negotiating scope', lesson_C1_3_3),
            buildLesson(6, 3, 4, 'lb-courses-english-tech-v2-6-3-4', 'Cross-team alignment', lesson_C1_3_4),
            buildLesson(6, 3, 5, 'lb-courses-english-tech-v2-6-3-5', 'Influencing without authority', lesson_C1_3_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-6-4',
          title: 'Сложная аргументация',
          lessons: [
            buildLesson(6, 4, 1, 'lb-courses-english-tech-v2-6-4-1', 'Structuring a long argument', lesson_C1_4_1),
            buildLesson(6, 4, 2, 'lb-courses-english-tech-v2-6-4-2', 'Anticipating objections', lesson_C1_4_2),
            buildLesson(6, 4, 3, 'lb-courses-english-tech-v2-6-4-3', 'Conceding and rebutting', lesson_C1_4_3),
            buildLesson(6, 4, 4, 'lb-courses-english-tech-v2-6-4-4', 'Framing for different audiences', lesson_C1_4_4),
            buildLesson(6, 4, 5, 'lb-courses-english-tech-v2-6-4-5', 'Driving consensus', lesson_C1_4_5),
          ],
        },
      ],
    },
    {
      id: 'sb-courses-english-tech-v2-7',
      title: 'C2 — Мастерство: стратегия, критика и высокие ставки',
      themes: [
        {
          id: 'tb-courses-english-tech-v2-7-1',
          title: 'Риторика и смысловая точность',
          lessons: [
            buildLesson(7, 1, 1, 'lb-courses-english-tech-v2-7-1-1', 'Nuanced, qualified claims', lesson_C2_1_1),
            buildLesson(7, 1, 2, 'lb-courses-english-tech-v2-7-1-2', 'Ambiguity and interpretation', lesson_C2_1_2),
            buildLesson(7, 1, 3, 'lb-courses-english-tech-v2-7-1-3', 'Formal keyword language in specs', lesson_C2_1_3),
            buildLesson(7, 1, 4, 'lb-courses-english-tech-v2-7-1-4', 'Strategic technical framing', lesson_C2_1_4),
            buildLesson(7, 1, 5, 'lb-courses-english-tech-v2-7-1-5', 'Tone and implication', lesson_C2_1_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-7-2',
          title: 'Экспертные технические домены',
          lessons: [
            buildLesson(7, 2, 1, 'lb-courses-english-tech-v2-7-2-1', 'Compiler and runtime trade-offs', lesson_C2_2_1),
            buildLesson(7, 2, 2, 'lb-courses-english-tech-v2-7-2-2', 'Reliability engineering', lesson_C2_2_2),
            buildLesson(7, 2, 3, 'lb-courses-english-tech-v2-7-2-3', 'AI-assisted development', lesson_C2_2_3),
            buildLesson(7, 2, 4, 'lb-courses-english-tech-v2-7-2-4', 'Platform governance', lesson_C2_2_4),
            buildLesson(7, 2, 5, 'lb-courses-english-tech-v2-7-2-5', 'Cost and efficiency at scale', lesson_C2_2_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-7-3',
          title: 'Коммуникация высоких ставок',
          lessons: [
            buildLesson(7, 3, 1, 'lb-courses-english-tech-v2-7-3-1', 'Executive technical briefs', lesson_C2_3_1),
            buildLesson(7, 3, 2, 'lb-courses-english-tech-v2-7-3-2', 'Critiquing technical papers and RFCs', lesson_C2_3_2),
            buildLesson(7, 3, 3, 'lb-courses-english-tech-v2-7-3-3', 'Cross-org conflict resolution', lesson_C2_3_3),
            buildLesson(7, 3, 4, 'lb-courses-english-tech-v2-7-3-4', 'Blameless postmortems', lesson_C2_3_4),
            buildLesson(7, 3, 5, 'lb-courses-english-tech-v2-7-3-5', 'Crisis communication', lesson_C2_3_5),
          ],
        },
        {
          id: 'tb-courses-english-tech-v2-7-4',
          title: 'Мастерство аргументации',
          lessons: [
            buildLesson(7, 4, 1, 'lb-courses-english-tech-v2-7-4-1', 'Persuasive executive writing', lesson_C2_4_1),
            buildLesson(7, 4, 2, 'lb-courses-english-tech-v2-7-4-2', 'Defending a decision under scrutiny', lesson_C2_4_2),
            buildLesson(7, 4, 3, 'lb-courses-english-tech-v2-7-4-3', 'Synthesizing opposing views', lesson_C2_4_3),
            buildLesson(7, 4, 4, 'lb-courses-english-tech-v2-7-4-4', 'Precision under ambiguity', lesson_C2_4_4),
            buildLesson(7, 4, 5, 'lb-courses-english-tech-v2-7-4-5', 'Writing for posterity', lesson_C2_4_5),
          ],
        },
      ],
    },
  ],
};

// ===========================================================================
// PLAN — full rewrite (7 levels x 4 themes x 5 lessons = 140 lessons, ~5600 Q)
//
//   1. A0 — Полный ноль: первые слова разработчика
//   2. A1 — База: простые задачи и рабочие инструменты   <-- pilot started here
//   3. A2 — Уверенный старт: Git, API, тесты и обновления
//   4. B1 — Средний уровень: архитектура, релизы и отладка
//   5. B2 — Выше среднего: ревью, безопасность, производительность
//   6. C1 — Продвинутый: RFC, распределённые системы и влияние
//   7. C2 — Мастерство: стратегия, критика и высокие ставки
// ===========================================================================
