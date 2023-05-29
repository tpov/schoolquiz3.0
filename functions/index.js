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

exports.updateProfileStatsHTTP = functions.https.onRequest(async (req, res) => {
  try {
    const rootRef = admin.database().ref();

    rootRef.child("Profiles").once("value", async (snapshot) => {
      const profiles = snapshot.val();
      console.log(profiles);

      if (profiles === null) {
        console.log("No profiles found in the database.");
        res.status(400).send("No profiles found in the database.");
        return;
      }

      try {
        await Promise.all(Object.keys(profiles).map(async (id) => {
          const profile = profiles[id];
          const {qualification, timeInGames, points} = profile;

          const totalValues = {
            tester: 0,
            translater: 0,
            moderator: 0,
            admin: 0,
            developer: 0,
            allTime: 0,
            timeInQuiz: 0,
            smsPoints: 0,
            timeInChat: 0,
            skill: 0,
          };

          Object.values(profiles).forEach((p) => {
            totalValues.tester += p.qualification.tester;
            totalValues.translater += p.qualification.translater;
            totalValues.moderator += p.qualification.moderator;
            totalValues.admin += p.qualification.admin;
            totalValues.developer += p.qualification.developer;
            totalValues.allTime += p.timeInGames.allTime;
            totalValues.timeInQuiz += p.timeInGames.timeInQuiz;
            totalValues.smsPoints += p.timeInGames.smsPoints;
            totalValues.timeInChat += p.timeInGames.timeInChat;
            totalValues.skill += p.points.skill;
          });

          const profileRef = rootRef.child(`players/listPlayers/${id}`);
          await profileRef.update({
            qualification: {
              tester: (qualification.tester * 100) / totalValues.tester,
              translater: (qualification.translater * 100) / totalValues.translater,
              moderator: (qualification.moderator * 100) / totalValues.moderator,
              admin: (qualification.admin * 100) / totalValues.admin,
              developer: (qualification.developer * 100) / totalValues.developer,
            },
            timeInGames: {
              allTime: (timeInGames.allTime * 100) / totalValues.allTime,
              timeInQuiz: (timeInGames.timeInQuiz * 100) / totalValues.timeInQuiz,
              smsPoints: (timeInGames.smsPoints * 100) / totalValues.smsPoints,
              timeInChat: (timeInGames.timeInChat * 100) / totalValues.timeInChat,
            },
            points: {
              skill: (points.skill * 100) / totalValues.skill,
            },
          });
        }));
        res.status(200).send({result: "Profile stats updated successfully"});
      } catch (error) {
        res.status(500).send({error: "An error occurred while updating profile stats"});
      }
    });
  } catch (error) {
    console.error("Ошибка:", error);
    res.status(500).send("Произошла ошибка: " + error.message);
  }
});
exports.calculateAverages = functions.pubsub.schedule("0 8 * * *").timeZone("Europe/Kiev").onRun(async (context) => {
  const quizFolders = ["quiz5", "quiz6", "quiz7", "quiz8"];
  const database = admin.database();

  for (const quizFolder of quizFolders) {
    const snapshot = await database.ref(`players/${quizFolder}`).once("value");

    // Проверка существования папки
    if (!snapshot.exists()) {
      console.log(`Folder players/${quizFolder} does not exist`);
      continue;
    }

    let totalRating = 0.0;
    let totalStars = 0.0;
    let count = 0;

    snapshot.forEach((idQuizSnapshot) => {
      const rating = idQuizSnapshot.child("rating").val() || 0.0;
      const stars = idQuizSnapshot.child("stars").val() || 0.0;

      totalRating += rating;
      totalStars += stars;
      count++;
    });

    const averageRating = totalRating / count;
    const averageStars = totalStars / count;

    // Запись средних значений в соответствующую папку
    await database.ref(quizFolder).update({
      ratingPlayer: averageRating,
      starsAllPlayer: averageStars,
    });
  }
});
