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

export const generateNewTpovId = functions.https.onCall(async (data, context) => {
  console.log('Function generateNewTpovId started.'); // Log at the very beginning

  // Возвращаем проверку аутентификации
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'The function must be called while authenticated.'
    );
  }

  const authUid = context.auth?.uid || null; // Handle potentially undefined context.auth
  console.log(`authUid determined: ${authUid}`); // Log authUid

  console.log('Attempting to get Firestore instance.'); // Log before getting db instance
  const db = admin.firestore();
  console.log('Firestore instance obtained.'); // Log after getting db instance

  console.log('Attempting to define lastIdRef.'); // Log before defining lastIdRef
  const lastIdRef = db.collection('variable').doc('lastId'); // Define lastIdRef here
  console.log('lastIdRef defined.'); // Log after defining lastIdRef


  // Check if the lastId document exists and create it with tpovId = 0 if not
  console.log('Attempting to check/create initial lastId document.'); // Log before check/create block
  try {
    const lastIdDocInitial = await lastIdRef.get();
    if (!lastIdDocInitial.exists) {
      await lastIdRef.set({ tpovId: 0 });
      console.log('Created initial variable/lastId document.');
    } else {
      console.log('Initial variable/lastId document already exists.');
    }
  } catch (error) {
    console.error('Error checking or creating initial lastId document:', error);
    // Optionally, re-throw the error or handle it appropriately
    throw new functions.https.HttpsError('internal', 'Failed to initialize lastId document.', error);
  }
  console.log('Finished check/create initial lastId document.'); // Log after check/create block


  console.log('Attempting to start Firestore transaction.'); // Log before starting transaction
  return db.runTransaction(async (transaction) => {
    console.log('Inside Firestore transaction block.'); // Log start of transaction within the block

    const lastIdRef = db.collection('variable').doc('lastId'); // Redefine inside transaction for transaction context
    console.log('Attempting to get lastId document within transaction.'); // Log before get
    const lastIdDoc = await transaction.get(lastIdRef);
    console.log(`Finished getting lastId document within transaction. Exists: ${lastIdDoc.exists}.`); // Log after get

    let currentTpovId = 0;
    if (lastIdDoc.exists) {
      currentTpovId = lastIdDoc.data()?.tpovId || 0;
    }

    const newTpovId = currentTpovId + 1;
    console.log(`Current tpovId: ${currentTpovId}, New tpovId: ${newTpovId}`); // Log ID values

    // Update lastId
    console.log('Attempting to set new tpovId in lastId document within transaction.'); // Log before set
    transaction.set(lastIdRef, { tpovId: newTpovId });
    console.log('Finished setting new tpovId in lastId document within transaction.'); // Log after set

    // Create profile entry using authUid as document ID only if authUid is not null
    if (authUid) {
      const listTpovIdRef = db.collection('variable').doc('listTpovId').collection('tokens').doc(authUid);
      console.log(`Attempting to set token document for authUid: ${authUid} within transaction.`); // Log before set token
      transaction.set(listTpovIdRef, {
        status: 1, // Assuming 1 is for anonymous/newly created
        tpovId: newTpovId,
      });
      console.log(`Finished setting token document for authUid: ${authUid} within transaction.`); // Log after set token
    } else {
        console.log('authUid is null, skipping token document creation.'); // Log if skipping token creation
    }

    console.log('Transaction operations defined, returning from transaction block.'); // Log before returning from the async block
    return { tpovId: newTpovId, authUid: authUid };
  }).then(result => {
    console.log('Transaction promise resolved. Transaction success:', result); // More specific success log
    return result;
  }).catch(error => {
    console.error('Transaction promise rejected. Transaction failed:', error); // More specific failure log
    console.error('Transaction failed with error details:', error.code, error.details); // Added more specific error logging
    throw new functions.https.HttpsError('internal', 'Failed to generate new tpovId.', error);
  });
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
