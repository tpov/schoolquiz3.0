"use strict";

/**
 * История 8.4: приватный квиз бежит под AD-29/AD-31 — полным циклом через настоящие
 * обработчики `index.js` (гейт старта → замороженный прогон → подача → результат).
 *
 * Здесь зажато то, что обещан участнику и автору:
 *  - дверь тратит лимит атомарно и идемпотентно (retry того же startId — тот же старт);
 *  - прогон выдаёт только публичные половины вопросов: ключа участнику нет ни при каком флаге;
 *  - подача считается сервером; повтор подачи отвечает сохранённым терминалом;
 *  - showResult=false — только подтверждение завершения, без счёта/процента/цифр, и на ПОВТОРНОМ
 *    чтении тоже; true — собственный результат; авторская статистика — при любом флаге;
 *  - закрытая после старта ссылка не рвёт начатую попытку, но новый старт отказывает;
 *  - тайм-аут закрывает попытку НАВСЕГДА: терминал по всему замороженному квизу, невыданные —
 *    без ответа, продолжения не появляется;
 *  - ни одной строки в книге движений от всего цикла (поведенческая половина 8.5).
 */

const assert = require("node:assert/strict");
const test = require("node:test");
const Module = require("module");
const {createFirestoreStub} = require("./_firestore-stub");
const {admin, store} = createFirestoreStub({});
const adminPath = require.resolve("firebase-admin");
const stub = new Module(adminPath, null);
stub.filename = adminPath;
stub.loaded = true;
stub.exports = admin;
require.cache[adminPath] = stub;
const functions = require("./index");
const {ATTEMPT_REASON, ATTEMPT_STATUS, attemptDeadlineMs} = require("./private-quiz-run");

const AUTHOR = "run-author-1";
const PARTICIPANT = "run-participant-1";
const CATALOG = "private-quizzes-v1";
const QUEST = "quest-run-1";
const QUEST_PATH = `private/${AUTHOR}/catalogs/${CATALOG}/quests/${QUEST}`;
const NOW = Date.parse("2026-10-22T00:00:00Z");
const START_ID = "start-run-00001";

function call(method, uid, data) {
  return functions[method].run({auth: {uid}, data});
}

/** Замороженный набор: два вопроса с ключами — как их пишет приватное дерево автора. */
function questions() {
  return [
    {
      id: "q-1",
      lessonId: "lesson-1",
      order: 0,
      payload: JSON.stringify({
        type: "SingleChoice",
        text: "2+2?",
        imageUrl: null,
        options: [{id: "a", text: "3"}, {id: "b", text: "4"}],
        correctOptionId: "b",
      }),
    },
    {
      id: "q-2",
      lessonId: "lesson-1",
      order: 1,
      payload: JSON.stringify({
        type: "SingleChoice",
        text: "2+3?",
        imageUrl: null,
        options: [{id: "a", text: "5"}, {id: "b", text: "6"}],
        correctOptionId: "a",
      }),
    },
  ];
}

function world(patch = {}) {
  return {
    [`profiles/${AUTHOR}`]: {},
    [`profiles/${PARTICIPANT}`]: {},
    "profiles/late-participant": {},
    [QUEST_PATH]: {
      id: QUEST,
      submissionId: "submission-1",
      version: 3,
      privateQuizSettings: {attemptLimit: 1, showResult: false, linkExpiresAtMs: null, enabled: true, startedCount: 0},
      ...patch.quest,
    },
    [`${QUEST_PATH}/sections/section-1`]: {id: "section-1", title: "Вопросы", order: 0},
    [`${QUEST_PATH}/sections/section-1/themes/theme-1`]: {id: "theme-1", title: "Вопросы", order: 0},
    [`${QUEST_PATH}/sections/section-1/themes/theme-1/lessons/lesson-1`]: {id: "lesson-1", title: "Вопросы", order: 0},
    [`${QUEST_PATH}/sections/section-1/themes/theme-1/lessons/lesson-1/questions/q-1`]: questions()[0],
    [`${QUEST_PATH}/sections/section-1/themes/theme-1/lessons/lesson-1/questions/q-2`]: questions()[1],
    ...patch.extra,
  };
}

async function start(data = {}, uid = PARTICIPANT, startId = START_ID) {
  return call("startPrivateQuizAttempt", uid, {
    ownerUid: AUTHOR,
    catalogId: CATALOG,
    questId: QUEST,
    startId,
    ...data,
  });
}

function submit(data = {}, uid = PARTICIPANT) {
  return call("submitMutation", uid, {
    mutationId: data.mutationId || `submit-${data.startId || START_ID}`,
    operation: "private_quiz.SUBMIT_ATTEMPT",
    payload: {
      ownerUid: AUTHOR,
      catalogId: CATALOG,
      questId: QUEST,
      startId: START_ID,
      served: [
        {questionId: "q-1", codeAnswerIndex: 0},
        {questionId: "q-2", codeAnswerIndex: 1},
      ],
      answers: [
        {
          questionId: "q-1",
          codeAnswerIndex: 0,
          answerPayload: JSON.stringify({type: "single-choice", selected: "b"}),
          answeredAtMs: NOW + 1000,
        },
        {
          questionId: "q-2",
          codeAnswerIndex: 1,
          answerPayload: JSON.stringify({type: "single-choice", selected: "b"}),
          answeredAtMs: NOW + 2000,
        },
      ],
      ...(data.payload || {}),
    },
  });
}

function ledgerPaths() {
  return [...store.documents.keys()].filter((path) => path.startsWith("audit/currency_ledger/"));
}

test("a passing start consumes the limit once, a retry of the same startId does not", async () => {
  store.reset(world());
  const record = await start();
  assert.equal(record.attemptLimit, 1);
  assert.equal(record.participantAttemptIndex, 1, "первая попытка из лимита");
  assert.equal(record.quizVersion, 3, "старт замораживает идентичность квеста");
  assert.equal(record.quizSubmissionId, "submission-1");
  assert.equal(store.documents.get(QUEST_PATH).privateQuizSettings.startedCount, 1);

  const retry = await start();
  assert.equal(retry.startedAtMs, record.startedAtMs, "повтор возвращает сохранённый старт");
  assert.equal(store.documents.get(QUEST_PATH).privateQuizSettings.startedCount, 1, "лимит не сгорел дважды");
});

test("the run serves only redacted halves and a deadline from the original start", async () => {
  store.reset(world());
  const record = await start();
  const run = await call("fetchPrivateQuizRun", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
  });
  assert.equal(run.startedAtMs, record.startedAtMs);
  const expectedDeadline = attemptDeadlineMs({startedAtMs: record.startedAtMs, questions: questions()});
  assert.equal(run.deadlineMs, expectedDeadline, "срок попытки выведен от исходного старта");
  for (const question of run.questions) {
    const payload = JSON.parse(question.payload);
    assert.equal(payload.type, "SingleChoiceRedacted", `публичная половина, а не исходник: ${payload.type}`);
    assert.equal(payload.correctOptionId, undefined, "ключа в прогоне нет");
  }

  // Чужому участнику чужой старт не виден, несуществующий — тоже.
  await assert.rejects(
    call("fetchPrivateQuizRun", "late-participant", {
      ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
    }),
    (error) => {
      // Граница вызываемого: одно фиксированное сообщение, машиночитаемый reason в деталях.
      assert.equal(error.message, "Private quiz run could not be loaded");
      assert.deepEqual(error.details, {reason: "NOT_FOUND"});
      return true;
    },
  );
});

test("a finished attempt scores server-side and replays the stored terminal on resubmission", async () => {
  store.reset(world({quest: {privateQuizSettings: {attemptLimit: 1, showResult: true, startedCount: 0}}}));
  await start();
  const result = await submit();
  assert.equal(result.replayed, false);
  assert.equal(result.result.reason, ATTEMPT_REASON.FINISHED);
  assert.equal(result.result.percentScore, 50, "первый верно, второй неверно");
  assert.deepEqual(result.result.questionScores, [9, 1]);

  // Повтор того же старта под новым ключом — сохранённый терминал той же формы.
  const replay = await submit({mutationId: "submit-again-same-start"});
  assert.equal(replay.replayed, false, "ключ новый — это новая доставка того же намерения");
  assert.deepEqual(replay.result, result.result, "результат не пересчитан");

  const stored = store.documents.get(`${QUEST_PATH}/starts/${START_ID}`).attempt;
  assert.equal(stored.status, ATTEMPT_STATUS.TERMINAL);
  assert.equal(stored.reason, ATTEMPT_REASON.FINISHED);
});

test("showResult=false gives completion only, on every read; true unlocks the own result; the author always sees statistics", async () => {
  store.reset(world({quest: {privateQuizSettings: {attemptLimit: 1, showResult: false, startedCount: 0}}}));
  await start();
  await submit();
  const hidden = await call("readPrivateQuizResult", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
  });
  assert.equal(hidden.status, ATTEMPT_STATUS.TERMINAL, "подтверждение завершения на месте");
  assert.equal(hidden.reason, ATTEMPT_REASON.FINISHED);
  assert.equal(hidden.percentScore, undefined, "процента нет");
  assert.equal(hidden.correctCount, undefined, "счёта нет");
  assert.equal(hidden.questionScores, undefined, "по-вопросных цифр нет");

  // Автор включил показ — прежняя попытка участника становится читаемой (текущая настройка).
  store.documents.get(QUEST_PATH).privateQuizSettings.showResult = true;
  const revealed = await call("readPrivateQuizResult", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
  });
  assert.equal(revealed.percentScore, 50, "текущая настройка действует и на прошлые попытки");
  assert.deepEqual(revealed.questionScores, [9, 1]);

  // И обратно: выключение скрывает при повторном чтении, хранимое не переписывается.
  store.documents.get(QUEST_PATH).privateQuizSettings.showResult = false;
  const hiddenAgain = await call("readPrivateQuizResult", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
  });
  assert.equal(hiddenAgain.percentScore, undefined);
  assert.equal(hiddenAgain.status, ATTEMPT_STATUS.TERMINAL);

  // Статистика автора — при любом флаге.
  const statistics = await call("readPrivateQuizStatistics", AUTHOR, {catalogId: CATALOG, questId: QUEST});
  assert.equal(statistics.attemptSummary ?? statistics.attemptLimitSummary.totalAttempts, 1);
  assert.equal(statistics.attempts[0].percentScore, 50, "автор видит процент при скрытом результате");
  assert.equal(statistics.settings.showResult, false);

  // Чужой результат не читается ни при каком флаге.
  store.documents.get(QUEST_PATH).privateQuizSettings.showResult = true;
  await assert.rejects(
    call("readPrivateQuizResult", "late-participant", {
      ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
    }),
    (error) => {
      // Граница вызываемого: одно фиксированное сообщение, машиночитаемый reason в деталях.
      assert.equal(error.message, "Private quiz run could not be loaded");
      assert.deepEqual(error.details, {reason: "NOT_FOUND"});
      return true;
    },
  );
});

test("closing the link mid-attempt lets the started attempt finish and refuses a new start", async () => {
  store.reset(world());
  await start();
  await call("submitMutation", AUTHOR, {
    mutationId: "close-mid-run",
    operation: "quest_authoring.CLOSE_PRIVATE_QUIZ_LINK",
    payload: {catalogId: CATALOG, questId: QUEST},
  });

  const result = await submit();
  assert.equal(result.result.reason, ATTEMPT_REASON.FINISHED, "начатая попытка закончилась после закрытия ссылки");

  await assert.rejects(
    start({startId: "start-after-close1"}, "late-participant"),
    (error) => {
      assert.match(error.message, /link is disabled/, "новый старт закрытой ссылкой не начинается");
      return true;
    },
  );
});

test("a limit raise from 1 to 3 admits two more starts and preserves the counter", async () => {
  store.reset(world({quest: {privateQuizSettings: {attemptLimit: 3, showResult: false, startedCount: 0}}}));
  await start({startId: "start-raise-0001"}, "run-participant-1");
  await start({startId: "start-raise-0002"}, "late-participant");
  const third = await start({startId: "start-raise-0003"}, "late-participant");
  assert.equal(third.participantAttemptIndex, 3);
  await assert.rejects(
    start({startId: "start-raise-0004"}, "late-participant"),
    (error) => {
      // Отказ двери идёт по проводу кодом в деталях, а не текстом (типизированный вход, AD-21).
      assert.deepEqual(error.details, {reason: "ATTEMPT_LIMIT_REACHED"});
      return true;
    },
  );
  // Понижение до 1: счётчик сохраняется, начатые попытки не при чём.
  store.documents.get(QUEST_PATH).privateQuizSettings.attemptLimit = 1;
  await assert.rejects(
    start({startId: "start-raise-0005"}, "late-participant"),
    (error) => {
      // Отказ двери идёт по проводу кодом в деталях, а не текстом (типизированный вход, AD-21).
      assert.deepEqual(error.details, {reason: "ATTEMPT_LIMIT_REACHED"});
      return true;
    },
  );
  assert.equal(store.documents.get(QUEST_PATH).privateQuizSettings.startedCount, 3);
});

test("the recovery window ending terminates the attempt with a timeout scored over the whole frozen quiz", async () => {
  store.reset(world({quest: {privateQuizSettings: {attemptLimit: 1, showResult: true, startedCount: 0}}}));
  const record = await start();
  const deadline = attemptDeadlineMs({startedAtMs: record.startedAtMs, questions: questions()});
  const realNow = Date.now;
  Date.now = () => deadline + 1;
  try {
    // Подача за сроком закрывает попытку терминалом с причиной timeout: продолжения не
    // появляется, а попытка остаётся начатой — новый startId теперь отказывал бы лимитом.
    const late = await submit({
      mutationId: "submit-late",
      payload: {
        served: [{questionId: "q-1", codeAnswerIndex: 0}],
        answers: [{questionId: "q-1", codeAnswerIndex: 0, answerPayload: "{", answeredAtMs: deadline - 1}],
      },
    });
    assert.equal(late.result.reason, ATTEMPT_REASON.TIMEOUT, "поздняя подача — терминал timeout");
    assert.equal(late.result.questionCount, 2, "знаменатель — весь замороженный квиз, включая невыданное");
    assert.equal(late.result.percentScore, 0, "принятых вовремя ответов не было");

    const stored = store.documents.get(`${QUEST_PATH}/starts/${START_ID}`).attempt;
    assert.deepEqual(stored.questionScores, [1, 1], "все позиции «показан, без ответа»");
    assert.equal(stored.finishedAtMs, deadline, "попытка закрыта исходным сроком, а не моментом подачи");

    // Чтение после окна возвращает тот же терминал: продолжения не появляется.
    const terminal = await call("readPrivateQuizResult", PARTICIPANT, {
      ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
    });
    assert.equal(terminal.reason, ATTEMPT_REASON.TIMEOUT);

    // Прогон после терминала отказывает: попытку продолжить нельзя.
    await assert.rejects(
      call("fetchPrivateQuizRun", PARTICIPANT, {
        ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
      }),
      (error) => {
        assert.equal(error.message, "Private quiz run could not be loaded");
        assert.deepEqual(error.details, {reason: "PRIVATE_ATTEMPT_TERMINAL"});
        return true;
      },
    );
  } finally {
    Date.now = realNow;
  }
});

test("an ordering answer in scrambled ids is translated back by the frozen keys", async () => {
  // Ordering красакция перемешивает id айтемов (ri-*): ответ участника живёт в выданных id, и
  // приём подачи переводит его ключами заморозки — иначе честный порядок считался бы как мусор.
  const ordering = {
    id: "q-order",
    lessonId: "lesson-1",
    order: 0,
    payload: JSON.stringify({
      type: "Ordering",
      text: "Поставь по порядку",
      imageUrl: null,
      items: [{id: "item-b", text: "второй"}, {id: "item-a", text: "первый"}],
    }),
  };
  const base = world({quest: {privateQuizSettings: {attemptLimit: 1, showResult: true, startedCount: 0}}});
  // Порядковый вопрос один: обычные q-1/q-2 из мира не сеются.
  for (const path of Object.keys(base)) {
    if (path.endsWith("/questions/q-1") || path.endsWith("/questions/q-2")) delete base[path];
  }
  base[`${QUEST_PATH}/sections/section-1/themes/theme-1/lessons/lesson-1/questions/q-order`] = ordering;
  store.reset(base);
  await start({startId: "start-order-0001"});
  const run = await call("fetchPrivateQuizRun", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: "start-order-0001",
  });
  assert.equal(run.questions.length, 1);
  const publicPayload = JSON.parse(run.questions[0].payload);
  assert.equal(publicPayload.type, "OrderingRedacted");
  // Ответ участника — в выданных (перемешанных) id, ровно как их показал экран. Правильный
  // порядок — порядок айтемов ИСХОДНИКА: [второй, первый].
  const presented = publicPayload.items.map((item) => item.id);
  const correctFirst = presented.find((id) => publicPayload.items.find((i) => i.id === id).text === "второй");
  const correctSecond = presented.find((id) => publicPayload.items.find((i) => i.id === id).text === "первый");
  const submitted = await call("submitMutation", PARTICIPANT, {
    mutationId: "submit-order",
    operation: "private_quiz.SUBMIT_ATTEMPT",
    payload: {
      ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: "start-order-0001",
      served: [{questionId: "q-order", codeAnswerIndex: 0}],
      answers: [{
        questionId: "q-order", codeAnswerIndex: 0,
        answerPayload: JSON.stringify({type: "ordering", order: [correctFirst, correctSecond]}),
        answeredAtMs: NOW + 500,
      }],
    },
  });
  assert.equal(submitted.result.reason, ATTEMPT_REASON.FINISHED);
  assert.equal(submitted.result.percentScore, 100, "правильный порядок в выданных id засчитан");
});

test("no ledger row and no consent exists anywhere in the private cycle", async () => {
  store.reset(world({quest: {privateQuizSettings: {attemptLimit: 1, showResult: true, startedCount: 0}}}));
  await start();
  await submit();
  await call("readPrivateQuizResult", PARTICIPANT, {
    ownerUid: AUTHOR, catalogId: CATALOG, questId: QUEST, startId: START_ID,
  });
  await call("readPrivateQuizStatistics", AUTHOR, {catalogId: CATALOG, questId: QUEST});
  assert.deepEqual(ledgerPaths(), [], "приватный цикл не пишет в книгу движений");

  // Тело подачи с формой платных заявок — несовпадение контракта, а не данные (8.5).
  await assert.rejects(
    submit({mutationId: "submit-charged-claims", payload: {chargeClaims: "SP"}}),
    (error) => {
      assert.match(error.message, /Unsupported submission fields: chargeClaims/);
      return true;
    },
  );

  // Операция подачи не входит в реестр согласий с платными условиями (проверка реестра на сервере).
  const source = require("fs").readFileSync(require.resolve("./index.js"), "utf8");
  const registry = /const MUTATION_TERMS_OPERATIONS = new Set\(\[([^\]]*)\]\)/.exec(source)[1];
  assert.ok(!registry.includes("private_quiz"), "приватные операции без согласий: их нет в реестре");
  assert.ok(!registry.includes("CLOSE_PRIVATE_QUIZ_LINK"), "закрытие ссылки тоже бесплатно");
});
