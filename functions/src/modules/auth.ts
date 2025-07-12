import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const generateNewTpovId = functions.https.onCall(async (data, context) => {
    console.log('Function generateNewTpovId started - attempting deploy trigger.');

    if (!context.auth) {
        throw new functions.https.HttpsError(
            'unauthenticated',
            'The function must be called while authenticated.'
        );
    }

    const authUid = context.auth?.uid || null;
    console.log(`authUid determined: ${authUid}`);

    const db = admin.firestore();
    const lastIdRef = db.collection('variable').doc('lastId');

    try {
        const lastIdDocInitial = await lastIdRef.get();
        if (!lastIdDocInitial.exists) {
            await lastIdRef.set({ tpovId: 0 });
            console.log('Created initial variable/lastId document.');
        }
    } catch (error) {
        console.error('Error checking or creating initial lastId document:', error);
        throw new functions.https.HttpsError('internal', 'Failed to initialize lastId document.', error);
    }

    return db.runTransaction(async (transaction) => {
        const lastIdRef = db.collection('variable').doc('lastId');
        const listTpovIdRef = db.collection('variable').doc('listTpovId');

        const lastIdDoc = await transaction.get(lastIdRef);
        const listTpovIdDoc = await transaction.get(listTpovIdRef);

        let currentTpovId = 0;
        if (lastIdDoc.exists) {
            currentTpovId = lastIdDoc.data()?.tpovId || 0;
        }

        const newTpovId = currentTpovId + 1;
        console.log(`Current tpovId: ${currentTpovId}, New tpovId: ${newTpovId}`);

        transaction.set(lastIdRef, { tpovId: newTpovId });

        if (authUid) {
            const currentData = listTpovIdDoc.exists ? listTpovIdDoc.data() || {} : {};
            transaction.set(listTpovIdRef, {
                ...currentData,
                [authUid]: newTpovId
            });
        }

        return { tpovId: newTpovId, authUid: authUid };
    }).catch(error => {
        console.error('Transaction failed:', error);
        throw new functions.https.HttpsError('internal', 'Failed to generate new tpovId.', error);
    });
});

export async function getTpovIdFromUid(uid: string): Promise<number | null> {
    const db = admin.firestore();
    const listTpovIdDoc = await db.collection('variable').doc('listTpovId').get();
    
    if (listTpovIdDoc.exists) {
        const data = listTpovIdDoc.data();
        return data?.[uid] || null;
    }
    
    return null;
}