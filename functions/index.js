/* eslint-disable no-console */

/**
 * Update profile stats.
 */

const admin = require("firebase-admin");
const functions = require("firebase-functions");

const serviceAccount = require("./school-quiz-89336951-b9972bf69baa.json");
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://school-quiz-89336951-default-rtdb.firebaseio.com",
});

exports.calculatePlayers = functions.pubsub
    .schedule("0 8 * * *")
    .timeZone("Europe/Kiev")
    .onRun(async (context) => {
      const database = admin.database();

      try {
        const profilesRef = database.ref(`Profiles`);
        const profilesSnapshot = await profilesRef.once("value");

        const tpovIdList = profilesSnapshot.val();

        let maxSkill = 0;
        let maxAdmin = 0;
        let maxDeveloper = 0;
        let maxModerator = 0;
        let maxSponsor = 0;
        let maxTester = 0;
        let maxTranslater = 0;

        let maxCountQuestions = 0;
        let maxSmsPoints = 0;
        let maxTimeInChat = 0;
        let maxTimeInQuiz = 0;
        let maxTimeInTrueQuestion = 0;

        // Вычисление максимальных значений
        for (const key in tpovIdList) {
          if (Object.prototype.hasOwnProperty.call(tpovIdList, key)) {
            const tpovIdProfileSnapshot = tpovIdList[key];

            // Получение значений переменных и обновление максимальных значений при необходимости
            const skill = tpovIdProfileSnapshot.points.skill || 0;
            const admin = tpovIdProfileSnapshot.qualification.admin || 0;
            const developer = tpovIdProfileSnapshot.qualification.developer || 0;
            const moderator = tpovIdProfileSnapshot.qualification.moderator || 0;
            const sponsor = tpovIdProfileSnapshot.qualification.sponsor || 0;
            const tester = tpovIdProfileSnapshot.qualification.tester || 0;
            const translater = tpovIdProfileSnapshot.qualification.translater || 0;
            const countQuestions = tpovIdProfileSnapshot.timeInGames.countQuestions || 0;
            const countTrueQuestion = tpovIdProfileSnapshot.timeInGames.countTrueQuestion || 0;
            const smsPoints = tpovIdProfileSnapshot.timeInGames.smsPoints || 0;
            const timeInChat = tpovIdProfileSnapshot.timeInGames.timeInChat || 0;
            const timeInQuiz = tpovIdProfileSnapshot.timeInGames.timeInQuiz || 0;

            maxSkill = Math.max(maxSkill, skill);
            maxAdmin = Math.max(maxAdmin, admin);
            maxDeveloper = Math.max(maxDeveloper, developer);
            maxModerator = Math.max(maxModerator, moderator);
            maxSponsor = Math.max(maxSponsor, sponsor);
            maxTester = Math.max(maxTester, tester);
            maxTranslater = Math.max(maxTranslater, translater);
            maxCountQuestions = Math.max(maxCountQuestions, countQuestions);
            maxSmsPoints = Math.max(maxSmsPoints, smsPoints);
            maxTimeInChat = Math.max(maxTimeInChat, timeInChat);
            maxTimeInQuiz = Math.max(maxTimeInQuiz, timeInQuiz);
            maxTimeInTrueQuestion = Math.max(maxTimeInTrueQuestion, countTrueQuestion);
          }
        }

        // Вычисление процентов и запись в папку players/listPlayers/$tpovId
        for (const key in tpovIdList) {
          // Добавьте следующий код в ваш цикл for (const key in tpovIdList) ...
const quizTables = ['quiz5', 'quiz6', 'quiz7', 'quiz8'];  // список ваших таблиц
const playerQuizRef = database.ref(`players/quiz`);

// Подготовим переменные для хранения общего рейтинга и количества рейтингов
let totalRating = 0;
let ratingCount = 0;

for (const quizTable of quizTables) {

  console.log(`Processing quizTable: ${quizTable}`);
  const quizRef = database.ref(quizTable);

  // Получим список всех quizId для данного tpovId
  const quizSnapshot = await quizRef.once("value");
  const quizIdList = quizSnapshot.val();
  for (const quizId in quizIdList) {
    if (Object.prototype.hasOwnProperty.call(quizIdList, quizId)) {
      const tpovIdSnapshot = await quizRef.child(quizId).child(key).once("value");
      const tpovIdRating = tpovIdSnapshot.val();

  console.log(`Processing quizTabtpovIdRatingle: ${tpovIdRating}`);
      if (tpovIdRating !== null) {
        totalRating += tpovIdRating;
        ratingCount++;
      }
    }
  }
}

// Получим рейтинги из players/quiz/$idQuiz/$tpovId/rating
const playerQuizSnapshot = await playerQuizRef.once("value");
const playerQuizIdList = playerQuizSnapshot.val();
for (const idQuiz in playerQuizIdList) {
  if (Object.prototype.hasOwnProperty.call(playerQuizIdList, idQuiz)) {
    const tpovIdSnapshot = await playerQuizRef.child(idQuiz).child(key).child('rating').once("value");
    const tpovIdRating = tpovIdSnapshot.val();
    if (tpovIdRating !== null) {
      totalRating += tpovIdRating;
      ratingCount++;
    }
  }
}

// Вычисляем среднее значение рейтинга
const averageRating = ratingCount > 0 ? totalRating / ratingCount : 0;

// Записываем в базу данных
const playerNodeRef = database.ref(`players/listPlayers/${key}`);
playerNodeRef.update({ ratingQuiz: parseInt(averageRating) });

          if (Object.prototype.hasOwnProperty.call(tpovIdList, key)) {
            const tpovIdProfileSnapshot = tpovIdList[key];

            // Получение значений переменных и обновление максимальных значений при необходимости
            const skill = tpovIdProfileSnapshot.points.skill || 0;
            const admin = tpovIdProfileSnapshot.qualification.admin || 0;
            const developer = tpovIdProfileSnapshot.qualification.developer || 0;
            const moderator = tpovIdProfileSnapshot.qualification.moderator || 0;
            const sponsor = tpovIdProfileSnapshot.qualification.sponsor || 0;
            const tester = tpovIdProfileSnapshot.qualification.tester || 0;
            const translater = tpovIdProfileSnapshot.qualification.translater || 0;
            const countQuestions = tpovIdProfileSnapshot.timeInGames.countQuestions || 0;
            const countTrueQuestion = tpovIdProfileSnapshot.timeInGames.countTrueQuestion || 0;
            const countPercentTrueQuestion = (countTrueQuestion / countQuestions) * 100 || 0;
            const smsPoints = tpovIdProfileSnapshot.timeInGames.smsPoints || 0;
            const timeInChat = tpovIdProfileSnapshot.timeInGames.timeInChat || 0;
            const timeInQuiz = tpovIdProfileSnapshot.timeInGames.timeInQuiz || 0;

            const userName = tpovIdProfileSnapshot.nickname || ""

            const percentSkill = (skill / maxSkill) * 100;
            const percentAdmin = (admin / maxAdmin) * 100;
            const percentDeveloper = (developer / maxDeveloper) * 100;
            const percentModerator = (moderator / maxModerator) * 100;
            const percentSponsor = (sponsor / maxSponsor) * 100;
            const percentTester = (tester / maxTester) * 100;
            const percentTranslater = (translater / maxTranslater) * 100;
            const percentCountQuestions = (countQuestions / maxCountQuestions) * 100;
            const percentCountTrueQuestion = countPercentTrueQuestion;
            const percentSmsPoints = (smsPoints / maxSmsPoints) * 100;
            const percentTimeInChat = (timeInChat / maxTimeInChat) * 100;
            const percentTimeInQuiz = (timeInQuiz / maxTimeInQuiz) * 100;
            const playerNodeRef = database.ref(`players/listPlayers/${key}`);

            // Проверяем существование папки
            playerNodeRef.once("value").then((snapshot) => {
              if (snapshot.exists()) {
                // Папка существует, выполняем обновление
                return playerNodeRef.update({
                  skill: parseInt(percentSkill) || 0,
                  admin: parseInt(percentAdmin) || 0,
                  developer: parseInt(percentDeveloper) || 0,
                  moderator: parseInt(percentModerator) || 0,
                  sponsor: parseInt(percentSponsor) || 0,
                  tester: parseInt(percentTester) || 0,
                  translater: parseInt(percentTranslater) || 0,
                  ratingCountQuestions: parseInt(percentCountQuestions) || 0,
                  ratingCountTrueQuestion: parseInt(percentCountTrueQuestion) || 0,
                  ratingSmsPoints: parseInt(percentSmsPoints) || 0,
                  ratingTimeInChat: parseInt(percentTimeInChat) || 0,
                  ratingTimeInQuiz: parseInt(percentTimeInQuiz) || 0,
                  userName: parseString(userName) || ""
                });
              } else {
                // Папка не существует, выполняем запись
                return playerNodeRef.set({
                  skill: parseInt(percentSkill) || 0,
                  admin: parseInt(percentAdmin) || 0,
                  developer: parseInt(percentDeveloper) || 0,
                  moderator: parseInt(percentModerator) || 0,
                  sponsor: parseInt(percentSponsor) || 0,
                  tester: parseInt(percentTester) || 0,
                  translater: parseInt(percentTranslater) || 0,
                  ratingCountQuestions: parseInt(percentCountQuestions) || 0,
                  ratingCountTrueQuestion: parseInt(percentCountTrueQuestion) || 0,
                  ratingSmsPoints: parseInt(percentSmsPoints) || 0,
                  ratingTimeInChat: parseInt(percentTimeInChat) || 0,
                  ratingTimeInQuiz: parseInt(percentTimeInQuiz) || 0,
                  userName: parseString(userName) || ""
                });
              }
            })
                .then(() => {
                  // Операция успешно выполнена
                  console.log("Запись успешно обновлена или создана.");
                })
                .catch((error) => {
                  // Обработка ошибок
                  console.error("Произошла ошибка:", error);
                });
          }
        }
      } catch (error) {
        console.error("An error occurred while calculating averages:");
        console.error(error);
      }
    });

exports.calculateAverages = functions.pubsub
    .schedule("0 8 * * *")
    .timeZone("Europe/Kiev")
    .onRun(async (context) => {
      const database = admin.database();
      const quizFolders = ["quiz5, quiz6, quiz7, quiz8"];
      const updates = {};

      try {
        for (const quizFolder of quizFolders) {
          const quizFolderRef = database.ref(`${quizFolder}`);
          const quizFolderSnapshot = await quizFolderRef.once("value");

          const quizFolderSnapshotVal = quizFolderSnapshot.val();

          for (const key in quizFolderSnapshotVal) {
            if (Object.prototype.hasOwnProperty.call(quizFolderSnapshotVal, key)) {
              const idQuizSnapshot = quizFolderSnapshotVal[key];

              try {
                const versionQuiz = idQuizSnapshot.versionQuiz || 0;
                const idQuiz = idQuizSnapshot.id;
                const playerFolder = database.ref(`players/quiz/${idQuiz}`);

                const playerFolderSnapshot = await playerFolder.once("value");

                let totalRating = 0.0;
                let totalStars = 0.0;
                let count = 0;

                const playerFolderSnapshotVal = playerFolderSnapshot.val();

                for (const ratingItemKey in playerFolderSnapshotVal) {
                  if (Object.prototype.hasOwnProperty.call(playerFolderSnapshotVal, ratingItemKey)) {
                    let ratingItem;
                    try {
                      ratingItem = playerFolderSnapshotVal[ratingItemKey];
                      const rating = ratingItem.rating || 0.0;
                      const stars = ratingItem.stars || 0.0;

                      totalRating += rating;
                      totalStars += stars;
                      count++;
                    } catch (error) {
                      console.error("Error processing ratingItem:", ratingItem);
                      console.error(error);
                    }
                  }
                }

                if (count === 0) {
                  console.log(`No ratings found for quiz: ${idQuiz}`);
                  continue;
                }

                const averageRating = totalRating * 100 / count;
                const averageStars = totalStars / count;

                updates[`${quizFolder}/${idQuiz}/ratingPlayer`] = averageRating;
                updates[`${quizFolder}/${idQuiz}/starsAllPlayer`] = averageStars;
                updates[`${quizFolder}/${idQuiz}/versionQuiz`] = versionQuiz + 1;
              } catch (error) {
                console.error("Error processing quiz:", idQuizSnapshot.id);
                console.error(error);
              }
            }
          }
        }

        await database.ref().update(updates);
      } catch (error) {
        console.error("An error occurred while calculating averages:");
        console.error(error);
      }
    });
