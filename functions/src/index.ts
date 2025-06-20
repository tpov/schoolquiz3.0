import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
admin.initializeApp();

// Define FieldConfig interface that was missing
interface FieldConfig {
    defaultValue: any;
    type: string;
}

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
    console.log('Function generateNewTpovId started - attempting deploy trigger.'); // Log at the very beginning

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
        throw new functions.https.HttpsError('internal', 'Failed to initialize lastId document.', error);
    }
    console.log('Finished check/create initial lastId document.'); // Log after check/create block

    console.log('Attempting to start Firestore transaction.');
    return db.runTransaction(async (transaction) => {
        console.log('Inside Firestore transaction block.'); // Log start of transaction within the block

        const lastIdRef = db.collection('variable').doc('lastId'); // Redefine inside transaction for transaction context
        const listTpovIdRef = db.collection('variable').doc('listTpovId');

        console.log('Attempting to get documents within transaction.'); // Log before gets
        const lastIdDoc = await transaction.get(lastIdRef);
        const listTpovIdDoc = await transaction.get(listTpovIdRef);
        console.log(`Finished getting documents within transaction. lastId exists: ${lastIdDoc.exists}, listTpovId exists: ${listTpovIdDoc.exists}.`); // Log after gets

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

        // Create or update token list in variable/listTpovId
        if (authUid) {
            console.log(`Attempting to update token list for hash: ${authUid} within transaction.`); // Log before set token

            // Use data from the get call at the beginning of the transaction
            const currentData = listTpovIdDoc.exists ? listTpovIdDoc.data() || {} : {};

            // Update the document with new key-value pair
            transaction.set(listTpovIdRef, {
                ...currentData,
                [authUid]: newTpovId
            });

            console.log(`Finished updating token list for hash: ${authUid} within transaction.`); // Log after set token
        } else {
            console.log('authUid is null, skipping token list update.'); // Log if skipping token creation
        }

        console.log('Transaction operations defined, returning from transaction block.');
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

// Helper function to get nested object value
function getNestedValue(obj: any, path: string): any {
    return path.split('.').reduce((current, key) => current && current[key], obj);
}

// Helper function to set nested object value
function setNestedValue(obj: any, path: string, value: any): void {
    const keys = path.split('.');
    const lastKey = keys.pop();
    const target = keys.reduce((current, key) => {
        if (!current[key]) current[key] = {};
        return current[key];
    }, obj);
    if (lastKey) target[lastKey] = value;
}

// Helper function to get tpovId from uid
async function getTpovIdFromUid(uid: string): Promise<number | null> {
    const db = admin.firestore();
    const listTpovIdDoc = await db.collection('variable').doc('listTpovId').get();

    if (listTpovIdDoc.exists) {
        const data = listTpovIdDoc.data();
        return data?.[uid] || null;
    }
    return null;
}

// Function to validate profile updates and report suspicious changes
export const validateProfileUpdate = functions.https.onCall(async (data, context) => {
    console.log('validateProfileUpdate started with data:', JSON.stringify(data));

    if (!context.auth) {
        console.log('Authentication failed - no auth context');
        throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
    }
    const auth = context.auth;
    console.log('User authenticated:', auth.uid);

    const { tpovId, profileData, isServerUpdate } = data;
    if (!tpovId || !profileData) {
        console.log('Missing required fields:', { tpovId, hasProfileData: !!profileData });
        throw new functions.https.HttpsError('invalid-argument', 'Missing required fields');
    }

    const db = admin.firestore();
    const profileRef = db.collection('profiles').doc(tpovId.toString());
    const currentProfile = await profileRef.get();
    console.log('Current profile exists:', currentProfile.exists);

    const suspiciousChanges = [];
    let dataToSave = { ...profileData }; // Start with incoming data

    // Fields that should not be modified by the client or have default values on creation
    const importantFields: Record<string, FieldConfig> = {
        'basic.nickname': { defaultValue: "", type: "string" },
        'qualification.admin': { defaultValue: 0, type: "number" },
        'qualification.developer': { defaultValue: 0, type: "number" },
        'qualification.sponsor': { defaultValue: 0, type: "number" },
        'qualification.tester': { defaultValue: 0, type: "number" },
        'qualification.moderator': { defaultValue: 0, type: "number" },
        'qualification.translater': { defaultValue: 0, type: "number" },
        'dates.dateBanned': { defaultValue: "", type: "string" },
        'dates.datePremium': { defaultValue: "", type: "string" },
        'points.gold': { defaultValue: 0, type: "number" },
        'points.skill': { defaultValue: 0, type: "number" },
        'points.nolics': { defaultValue: 0, type: "number" },
        'points.trophy': { defaultValue: "", type: "string" },
        'basic.tpovId': { defaultValue: tpovId, type: "number" }
    };

    // Fields that should be nullified after use and have default values on creation
    const nullifyFieldsDefaults: Record<string, FieldConfig> = {
        'addPoints.addMassage': { defaultValue: "", type: "string" },
        'addPoints.addGold': { defaultValue: 0, type: "number" },
        'addPoints.addNolics': { defaultValue: 0, type: "number" },
        'addPoints.addSkill': { defaultValue: 0, type: "number" },
        'addPoints.addTrophy': { defaultValue: "", type: "string" }
    };

    // Combine fields for checking defaults on new profile
    const fieldsToCheckDefaults = { ...importantFields, ...nullifyFieldsDefaults };

    if (!currentProfile.exists) {
        console.log('Creating new profile for tpovId:', tpovId);

        // Проверяем, что пользователь имеет право создавать профиль с этим tpovId
        const userTpovId = await getTpovIdFromUid(auth.uid);
        console.log('User tpovId from auth:', userTpovId);

        if (!isServerUpdate && userTpovId !== tpovId) {
            console.log('Unauthorized profile creation attempt:', {
                userTpovId,
                requestedTpovId: tpovId,
                isServerUpdate
            });
            throw new functions.https.HttpsError('permission-denied', 'User can only create their own profile');
        }

        // For new profiles, ensure all fields have their default values
        for (const [field, config] of Object.entries(fieldsToCheckDefaults)) {
            const currentValue = getNestedValue(profileData, field);
            if (currentValue !== undefined && typeof currentValue !== config.type) {
                console.log('Type mismatch in new profile:', {
                    field,
                    expectedType: config.type,
                    actualType: typeof currentValue,
                    value: currentValue
                });
                suspiciousChanges.push({
                    field,
                    oldValue: currentValue,
                    newValue: config.defaultValue,
                    reason: `Type mismatch: expected ${config.type}, got ${typeof currentValue}`
                });
                setNestedValue(dataToSave, field, config.defaultValue);
            }
        }
    } else {
        console.log('Updating existing profile');
        const currentData = currentProfile.data();

        // Check for unauthorized modifications of important fields
        for (const [field, config] of Object.entries(importantFields)) {
            const oldValue = getNestedValue(currentData, field);
            const newValue = getNestedValue(profileData, field);

            // Check for value type
            if (newValue !== undefined && typeof newValue !== config.type) {
                console.log('Type mismatch detected:', {
                    field,
                    expectedType: config.type,
                    actualType: typeof newValue,
                    value: newValue
                });
                suspiciousChanges.push({
                    field,
                    oldValue,
                    newValue,
                    reason: `Type mismatch: expected ${config.type}, got ${typeof newValue}`
                });
                setNestedValue(dataToSave, field, oldValue);
                continue;
            }

            // Check for unauthorized changes to protected fields
            if (newValue !== undefined && newValue !== oldValue && !isServerUpdate) {
                console.log('Unauthorized field modification detected:', {
                    field,
                    oldValue,
                    newValue,
                    isServerUpdate
                });
                suspiciousChanges.push({
                    field,
                    oldValue,
                    newValue,
                    reason: 'Unauthorized modification of protected field'
                });
                setNestedValue(dataToSave, field, oldValue);
            }
        }

        // Check and nullify addPoints fields
        for (const [field, config] of Object.entries(nullifyFieldsDefaults)) {
            const oldValue = getNestedValue(currentData, field);
            const newValue = getNestedValue(profileData, field);

            // Only log if the attempted value is not already the default
            if (newValue !== undefined) {
                if (newValue !== config.defaultValue) {
                    console.log('AddPoints field will be nullified:', {
                        field,
                        oldValue,
                        newValue,
                        defaultValue: config.defaultValue
                    });
                    suspiciousChanges.push({
                        field,
                        oldValue,
                        newValue,
                        reason: 'AddPoints field should be nullified'
                    });
                }
                setNestedValue(dataToSave, field, config.defaultValue);
            }
        }

        // Check for changes in points fields
        const pointsFields = ['points.skill', 'points.nolics', 'points.trophy'];
        for (const field of pointsFields) {
            const oldValue = getNestedValue(currentData, field);
            const newValue = getNestedValue(profileData, field);

            // Only log if the value is different from the old value and not a server update
            if (newValue !== undefined && newValue !== oldValue && !isServerUpdate) {
                console.log('Points field modification detected:', {
                    field,
                    oldValue,
                    newValue,
                    isServerUpdate
                });
                suspiciousChanges.push({
                    field,
                    oldValue,
                    newValue,
                    reason: 'Unauthorized modification of points field'
                });
                // Keep the old value in dataToSave
                setNestedValue(dataToSave, field, oldValue);
            }
        }

        // Additional check for suspicious values in points fields
        if (!isServerUpdate) {
            const skillValue = getNestedValue(profileData, 'points.skill');
            const nolicsValue = getNestedValue(profileData, 'points.nolics');
            const trophyValue = getNestedValue(profileData, 'points.trophy');

            // Only log if the value is suspicious and different from the current value
            if (skillValue !== undefined && skillValue > 1000000 && skillValue !== getNestedValue(currentData, 'points.skill')) {
                console.log('Suspicious skill value detected:', skillValue);
                suspiciousChanges.push({
                    field: 'points.skill',
                    oldValue: getNestedValue(currentData, 'points.skill'),
                    newValue: skillValue,
                    reason: 'Suspiciously high skill value'
                });
                setNestedValue(dataToSave, 'points.skill', getNestedValue(currentData, 'points.skill'));
            }

            // Only log if the value is suspicious and different from the current value
            if (nolicsValue !== undefined && nolicsValue > 1000000 && nolicsValue !== getNestedValue(currentData, 'points.nolics')) {
                console.log('Suspicious nolics value detected:', nolicsValue);
                suspiciousChanges.push({
                    field: 'points.nolics',
                    oldValue: getNestedValue(currentData, 'points.nolics'),
                    newValue: nolicsValue,
                    reason: 'Suspiciously high nolics value'
                });
                setNestedValue(dataToSave, 'points.nolics', getNestedValue(currentData, 'points.nolics'));
            }

            // Only log if the value is suspicious and different from the current value
            if (trophyValue !== undefined && trophyValue.length > 10 && trophyValue !== getNestedValue(currentData, 'points.trophy')) {
                console.log('Suspicious trophy value detected:', trophyValue);
                suspiciousChanges.push({
                    field: 'points.trophy',
                    oldValue: getNestedValue(currentData, 'points.trophy'),
                    newValue: trophyValue,
                    reason: 'Suspiciously long trophy value'
                });
                setNestedValue(dataToSave, 'points.trophy', getNestedValue(currentData, 'points.trophy'));
            }
        }
    }

    // If there were any suspicious changes, log them in the anticheat/editPrivateValue document
    if (suspiciousChanges.length > 0) {
        console.log('Logging suspicious changes to anticheat/editPrivateValue:', suspiciousChanges);
        const anticheatDocRef = db.collection('anticheat').doc('editPrivateValue');

        const changesArray: string[] = [];
        for (const change of suspiciousChanges) {
            let correctValue = change.oldValue;
            // If the reason is related to a type mismatch on a new profile, the correct value is the default
            if (change.reason.startsWith('Type mismatch')) {
                 // Find the correct default value from the field configurations
                 const fieldConfig = importantFields[change.field] || nullifyFieldsDefaults[change.field];
                 if (fieldConfig) {
                     correctValue = fieldConfig.defaultValue;
                 } else {
                     console.warn(`Could not find field config for ${change.field} to determine default value.`);
                     correctValue = 'Unknown Default'; // Fallback
                 }
            } else if (change.reason === 'AddPoints field should be nullified') {
                 const fieldConfig = nullifyFieldsDefaults[change.field];
                  if (fieldConfig) {
                     correctValue = fieldConfig.defaultValue;
                 } else {
                     console.warn(`Could not find field config for ${change.field} to determine default value.`);
                      correctValue = 'Unknown Default'; // Fallback
                 }
            } else {
                 // For other unauthorized changes, the correct value is the original value
                 correctValue = change.oldValue;
            }

            // Format the string as "field_path - attempted_value - correct_value"
            const attemptedValue = change.newValue !== undefined ? change.newValue : 'undefined';
            const correctedValue = correctValue !== undefined ? correctValue : 'undefined';
            const changeString = `${change.field} - ${attemptedValue} - ${correctedValue}`;
            changesArray.push(changeString);
        }

        // Use the user's tpovId as the field key and append the changes array
        const tpovIdKey = tpovId.toString();
        await anticheatDocRef.update({
          [tpovIdKey]: admin.firestore.FieldValue.arrayUnion(...changesArray)
        }).catch(async (error) => {
            // If the document or field doesn't exist, create it
            if (error.code === 'not-found' || error.message.includes('no document to update')) {
                 console.log(`Document anticheat/editPrivateValue not found, creating...`);
                 await anticheatDocRef.set({
                     [tpovIdKey]: changesArray
                 });
                 console.log(`Document anticheat/editPrivateValue created and changes added for tpovId ${tpovId}`);
            } else if (error.message.includes('No document to update')) {
                 console.log(`Field for tpovId ${tpovId} not found, creating as new array...`);
                  await anticheatDocRef.set({
                     [tpovIdKey]: changesArray
                 }, { merge: true });
                 console.log(`Field for tpovId ${tpovId} created and changes added`);
            }
             else {
                console.error('Error logging suspicious changes:', error);
                // Depending on desired behavior, re-throw or handle differently
            }
        });

        console.log(`Suspicious changes logged for tpovId ${tpovId} in anticheat/editPrivateValue`);
    }

    // Save the validated data
    console.log('Saving validated profile data');
    await profileRef.set(dataToSave, { merge: true });
    console.log('Profile update completed successfully');

    return {
        success: true,
        suspiciousChanges: suspiciousChanges.length > 0 ? suspiciousChanges : undefined
    };
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

// Export the getGiftBoxReward function from giftReward.ts
export { getGiftBoxReward } from './giftReward';
