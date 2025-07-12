import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

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
    const fromPath = buildPath(data.idEventFrom, data.idCategoryFrom, data.idSubCategoryFrom, data.idSubsubCategoryFrom, data.idQuizFrom);
    let toPath = buildPath(
        data.idEventTo,
        data.idCategoryTo,
        data.idSubCategoryTo,
        data.idSubsubCategoryTo,
        data.idQuizTo
    );

    const toIds = [data.idEventTo, data.idCategoryTo, data.idSubCategoryTo, data.idSubsubCategoryTo, data.idQuizTo];
    for (let i = 0; i < toIds.length; i++) {
        if (toIds[i] === 0) {
            const parentIds: [number, number, number, number, number] = [
                ...toIds.slice(0, i),
                ...Array(5 - i).fill(-1)
            ] as [number, number, number, number, number];
            const parentPath = buildPath(...parentIds);
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

    await copyCollection(fromPath, toPath);

    if (data.deleteOld) {
        await deleteCollection(fromPath);
    }

    if (data.clearData) {
        await clearDataInPath(toPath);
    }

    return { success: true, toPath };
});

function buildPath(idEvent: number, idCategory: number, idSubCategory: number, idSubsubCategory: number, idQuiz: number): string {
    const path = ['structures', 'structureData'];
    if (idEvent > 0) path.push(idEvent.toString());
    if (idCategory > 0) path.push(idCategory.toString());
    if (idSubCategory > 0) path.push(idSubCategory.toString());
    if (idSubsubCategory > 0) path.push(idSubsubCategory.toString());
    if (idQuiz > 0) path.push(idQuiz.toString());
    return path.join('/');
}

async function copyCollection(fromPath: string, toPath: string) {
    const db = admin.firestore();
    const fromSnapshot = await db.collection(fromPath).get();
    const batch = db.batch();
    
    fromSnapshot.forEach(doc => {
        const newDocRef = db.collection(toPath).doc(doc.id);
        batch.set(newDocRef, doc.data());
    });
    
    await batch.commit();
}

async function deleteCollection(path: string) {
    const db = admin.firestore();
    const snapshot = await db.collection(path).get();
    const batch = db.batch();
    
    snapshot.forEach(doc => {
        batch.delete(doc.ref);
    });
    
    await batch.commit();
}

async function clearDataInPath(path: string) {
    const db = admin.firestore();
    const snapshot = await db.collection(path).get();
    const batch = db.batch();
    
    snapshot.forEach(doc => {
        batch.update(doc.ref, { data: {} });
    });
    
    await batch.commit();
}