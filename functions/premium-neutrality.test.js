"use strict";

/**
 * История 2.4: премиум без преимущества.
 *
 * Единственное, что премиум меняет, — скорость восстановления обоих зарядов (CAP-13: личный
 * эффект). Счёт, позиция в рейтинге и содержимое коробки обязаны читать одни и те же входы, в
 * каких бы руках они ни оказались. Здесь это зажато с двух сторон: поведенчески — в каждый шов
 * дохода подсовываются премиальные поля, и ответ обязан не сдвинуться ни на бит, — и статически —
 * модули, считающие счёт, места и дроп, не упоминают премиум вовсе, а правила не выдают
 * привилегий по спонсорскому уровню: очки «спонсора» копятся и видны, но не открывают ничего.
 *
 * Эти строки — забор, а не описание: тест падает в момент, когда кто-то впервые даёт платности
 * доступ к сравнению игроков, и падает с именем шва.
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");
const {scoreAttempt} = require("./attempt-scoring");
const {calculateTournamentLeaderboard} = require("./tournament-ranking");
const {generateGiftBoxReward} = require("./gift-box-rewards");

const json = (value) => JSON.stringify(value);

const SINGLE = {
  type: "SingleChoice",
  text: "Which keyword declares a read-only binding?",
  imageUrl: null,
  options: [{id: "a", text: "var"}, {id: "b", text: "val"}],
  correctOptionId: "b",
};
const question = (id) => ({id, lessonId: "lesson-1", difficulty: "", payload: json(SINGLE)});
const answer = (questionId, selected) => ({
  questionId,
  codeAnswerIndex: 0,
  score: 9,
  answerPayload: json({type: "single-choice", selected}),
  answeredAtMs: 1700000000000,
  durationMs: 4200,
  wasTimeout: false,
});

/** Премиальные поля, какие только может придумать щедрый (или подлый) вызов. */
const premiumFields = {
  hasPremium: true,
  premiumUntilMs: Number.MAX_SAFE_INTEGER,
  user: {hasPremium: true, premiumUntilMs: Number.MAX_SAFE_INTEGER},
  balance: {hasPremium: true, premiumUntilMs: Number.MAX_SAFE_INTEGER},
};

const tests = [];
function test(name, body) { tests.push({name, body}); }

test("the same answers score the same with premium fields all over the input", () => {
  const plain = {
    questions: [question("q0"), question("q1")],
    served: [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 1, questionId: "q1"}],
    keyDocument: null,
    answers: [answer("q0", "b"), answer("q1", "c")],
  };
  const scored = scoreAttempt(plain);
  assert.equal(scored.scorable, true);
  assert.equal(scored.percentScore, 50);
  // Поля подсовываются не только поверху: каждая строка вопросов и ответов тоже «щедрая» —
  // ветка, читающая премиум со строки, должна упасть так же громко, как читающая с корня.
  const premiumEverywhere = {
    ...plain,
    ...premiumFields,
    questions: plain.questions.map((q) => ({...q, ...premiumFields})),
    served: plain.served.map((s) => ({...s, ...premiumFields})),
    answers: plain.answers.map((a) => ({...a, ...premiumFields})),
  };
  assert.deepStrictEqual(scoreAttempt(premiumEverywhere), scored, "счёт не видел премиума");
});

test("two players with identical play take identical places, premium or not", () => {
  const at = 1_800_000_000_000;
  const group = () => [{
    groupId: "group-1",
    results: [
      {userId: "premium-player", percent: 87, elapsedMs: 45_000, completedAtMs: at},
      {userId: "free-player", percent: 87, elapsedMs: 45_000, completedAtMs: at},
    ],
  }];
  // Продакшн зовёт с опциями (index.js передаёт minGroupsForStableRank) — пин обязан гонять тот
  // же вход: опция, выведенная из премиума под нейтральным именем, входит именно отсюда.
  const productionOptions = {minGroupsForStableRank: 7};
  const ranking = calculateTournamentLeaderboard(group(), productionOptions);
  // Равная игра — равные очки рейтинга; места разъехались только по имени (детерминированный тайбрейк).
  assert.equal(ranking.leaderboard[0].ratingPoints, ranking.leaderboard[1].ratingPoints);
  assert.deepEqual(ranking.leaderboard.map((row) => row.place), [1, 2]);
  const premiumRanking = calculateTournamentLeaderboard(
    group().map((entry) => ({
      ...entry, ...premiumFields,
      results: entry.results.map((row) => ({...row, ...premiumFields})),
    })),
    {...productionOptions, ...premiumFields},
  );
  // Ответ целиком: метаданные (компоненты, уверенность, связность) — та же поверхность
  // сравнения, что и места, и премиум им не вход.
  assert.deepStrictEqual(premiumRanking, ranking, "позиция не читала премиума");
});

test("a box opens the same for a sponsor and for a payer: entropy is the only channel", () => {
  // Два яруса: обычный (addNolics) и редкий — тот, что доходит до трофейной ветки (первый
  // бросок <= 0.05, второй > 0.95, третий выбирает редкий случай, четвёртый — сам трофей).
  // Самая владная ветка не должна читать премиум тоже.
  const common = [0.03, 0.9, 0.2, 0.4, 0.5, 0.6, 0.7, 0.8];
  const trophyTier = [0.01, 0.96, 0.3, 0.6, 0.5, 0.6, 0.7, 0.8];
  assert.equal(generateGiftBoxReward([], trophyTier).type, "trophy", "редкая ветка обязана дойти до трофея");
  assert.deepEqual(generateGiftBoxReward([], [...common]), generateGiftBoxReward([], common));
  assert.deepEqual(generateGiftBoxReward([], [...trophyTier]), generateGiftBoxReward([], trophyTier));
  assert.deepEqual(
    generateGiftBoxReward(["old-trophy"], trophyTier),
    generateGiftBoxReward(["old-trophy"], [...trophyTier]),
  );
  // Подпись принимает только трофеи и восемь честных бросков: премиуму некуда войти — пока входа
  // нет, забор ниже держит.
});

test("the scoring, ranking, qualification and drop modules never mention premium", () => {
  // gift-box-rewards.js упомянут нарочно не здесь: коробка может ВЫДАТЬ премиум (дроп 2.2) —
  // это право выдаваемого, а не вход рулки; его нейтральность держат сигнатура и тест выше.
  // Квалификация решает, кто вообще дойдёт до рейтинга, — тот же шов сравнения, что и места.
  for (const file of [
    "attempt-scoring.js",
    "assessment-scoring.js",
    "result-verification.js",
    "tournament-ranking.js",
    "tournament-qualification.js",
    "tournament-qualification-scan.js",
    "tournament-qualification-freeze.js",
    "tournament-qualification-version.js",
  ]) {
    const source = fs.readFileSync(path.join(__dirname, file), "utf8");
    assert.doesNotMatch(source, /premium/i, `${file} узнал о премиуме`);
  }
});

test("firestore rules grant nothing by sponsor level or premium", () => {
  const rules = fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8");
  assert.doesNotMatch(rules, /sponsor/i, "правила выдали привилегию за спонсорство");
  // Премиум живёт в правилах только именем своего хранилища: ветка прав, читающая премиум-признак
  // игрока, — это привилегия за деньги и обязана упасть громко.
  for (const line of rules.split("\n").filter((line) => /premium/i.test(line))) {
    assert.match(line, /match\s+\/premium_accounts\b/,
      `подозрительное употребление премиума в правилах: ${line.trim()}`);
  }
});

test("sponsor points are carried, shown and never asked for anything else", () => {
  // Каждое упоминание спонсорского уровня на всём сервере — перенос между документами (профиль ↔
  // пользователь ↔ доверенный профиль) или его чтение для отображения. Ни одной проверки «достаточно
  // ли он велик, чтобы…». Сканируется каждый серверный модуль: спонсорская ветка в любом другом
  // файле падает так же громко, как упала бы в index.js.
  const serverFiles = fs.readdirSync(__dirname).filter((file) => file.endsWith(".js") && !file.endsWith(".test.js"));
  let mentions = 0;
  for (const file of serverFiles) {
    const source = fs.readFileSync(path.join(__dirname, file), "utf8");
    const lines = source.split("\n").filter((line) => /sponsor/i.test(line));
    mentions += lines.length;
    for (const line of lines) {
      // Только форма переноса: ключ, которым значение пишется в другой документ, или строковая
      // метка. Передача уровня аргументом или сравнение — не перенос и не показ.
      assert.match(line, /^\s*(sponsor:|sponsorLevel:|"sponsor"|\.\.\.)/,
        `подозрительное употребление спонсорского уровня в ${file}: ${line.trim()}`);
    }
  }
  assert.ok(mentions > 0, "спонсорские очки должны хотя бы переноситься — иначе этот забор не о чем");
});

async function main() {
  for (const {name, body} of tests) { await body(); process.stdout.write(`ok - ${name}\n`); }
  process.stdout.write(`premium-neutrality: ${tests.length} behavioral tests passed\n`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
