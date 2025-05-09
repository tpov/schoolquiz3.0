import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
admin.initializeApp();

interface StructureEditData {
  id?: number;
  idEventFrom: number;
  idCategoryFrom: number;
  idSubCategoryFrom: number;
  idSubsubCategoryFrom: number;
  idQuizFrom: number;

  idEventTo: number;
  idCategoryTo: number;
  idSubCategoryTo: number;
  idSubsubCategoryTo: number;
  idQuizTo: number;

  nameEventTo: string;
  nameCategoryTo: string;
  nameSubCategoryTo: string;
  nameSubsubCategoryTo: string;
  nameQuizTo: string;

  deleteOld: boolean;
  clearData: boolean;
}

export const editStructure = functions.https.onCall(async (data: StructureEditData, context) => {
  // 1. Формируем from и to пути
  const fromPath = buildPath(data.idEventFrom, data.idCategoryFrom, data.idSubCategoryFrom, data.idSubsubCategoryFrom, data.idQuizFrom);
  let toPath = buildPath(
    data.idEventTo,
    data.idCategoryTo,
    data.idSubCategoryTo,
    data.idSubsubCategoryTo,
    data.idQuizTo
  );

  // 2. Если в to есть 0 — ищем максимальный id и подставляем +1
  const toIds = [data.idEventTo, data.idCategoryTo, data.idSubCategoryTo, data.idSubsubCategoryTo, data.idQuizTo];
  for (let i = 0; i < toIds.length; i++) {
    if (toIds[i] === 0) {
      // Получаем parent path до этого уровня
      const parentIds: [number, number, number, number, number] = [
        ...toIds.slice(0, i),
        ...Array(5 - i).fill(-1)
      ] as [number, number, number, number, number];
      const parentPath = buildPath(...parentIds);
      // Получаем коллекцию на этом уровне
      const snapshot = await admin.firestore().collection(parentPath).get();
      let maxId = 0;
      snapshot.forEach(doc => {
        const id = parseInt(doc.id, 10);
        if (!isNaN(id) && id > maxId) maxId = id;
      });
      toIds[i] = maxId + 1;
    }
  }
  const toIdsTuple: [number, number, number, number, number] = toIds as [number, number, number, number, number];
  toPath = buildPath(...toIdsTuple);

  // 3. Копируем содержимое
  await copyCollection(fromPath, toPath);

  // 4. Если deleteOld — удаляем from
  if (data.deleteOld) {
    await deleteCollection(fromPath);
  }

  // 5. Если clearData — очищаем to
  if (data.clearData) {
    await clearDataInPath(toPath);
  }

  return { success: true, toPath };
});

// Вспомогательные функции
function buildPath(idEvent: number, idCategory: number, idSubCategory: number, idSubsubCategory: number, idQuiz: number) {
  let path = `structures/structureData/event/${idEvent}`;
  if (idCategory !== -1) path += `/category/${idCategory}`;
  if (idSubCategory !== -1) path += `/subCategory/${idSubCategory}`;
  if (idSubsubCategory !== -1) path += `/subsubCategory/${idSubsubCategory}`;
  if (idQuiz !== -1) path += `/quiz/${idQuiz}`;
  return path;
}

async function copyCollection(fromPath: string, toPath: string) {
  const snapshot = await admin.firestore().collection(fromPath).get();
  for (const doc of snapshot.docs) {
    await admin.firestore().doc(`${toPath}/${doc.id}`).set(doc.data());
  }
}

async function deleteCollection(path: string) {
  const snapshot = await admin.firestore().collection(path).get();
  for (const doc of snapshot.docs) {
    await doc.ref.delete();
  }
}

async function clearDataInPath(path: string) {

}
