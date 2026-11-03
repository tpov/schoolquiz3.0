import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentUpdated, onDocumentDeleted, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { onObjectFinalized, onObjectDeleted, onObjectMetadataUpdated } from "firebase-functions/v2/storage";
import { onValueCreated, onValueUpdated, onValueDeleted } from "firebase-functions/v2/database";
import * as admin from "firebase-admin";

// ===== 20 ПУСТЫХ ФУНКЦИЙ FIREBASE FUNCTIONS =====

// 1. HTTP Callable функция
export const emptyHttpCallable = onCall(async (request) => {
    console.log('emptyHttpCallable called with data:', request.data);
    return { success: true, message: 'Empty HTTP callable function executed' };
});

// 2. HTTP Request функция
export const emptyHttpRequest = onRequest(async (req, res) => {
    console.log('emptyHttpRequest called');
    res.json({ success: true, message: 'Empty HTTP request function executed' });
});

// 3. Firestore onCreate триггер
export const emptyFirestoreCreate = onDocumentCreated('test/{docId}', async (event) => {
    console.log('emptyFirestoreCreate triggered for document:', event.params.docId);
    const data = event.data?.data();
    console.log('Document data:', data);
});

// 4. Firestore onUpdate триггер
export const emptyFirestoreUpdate = onDocumentUpdated('test/{docId}', async (event) => {
    console.log('emptyFirestoreUpdate triggered for document:', event.params.docId);
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();
    console.log('Before data:', beforeData);
    console.log('After data:', afterData);
});

// 5. Firestore onDelete триггер
export const emptyFirestoreDelete = onDocumentDeleted('test/{docId}', async (event) => {
    console.log('emptyFirestoreDelete triggered for document:', event.params.docId);
    const deletedData = event.data?.data();
    console.log('Deleted document data:', deletedData);
});

// 6. Firestore onWrite триггер
export const emptyFirestoreWrite = onDocumentWritten('test/{docId}', async (event) => {
    console.log('emptyFirestoreWrite triggered for document:', event.params.docId);
    if (event.data?.before) {
        console.log('Document existed before');
    }
    if (event.data?.after) {
        console.log('Document exists after');
    }
});

// 7. Scheduled функция (каждый час)
export const emptyScheduledHourly = onSchedule('every 1 hours', async (event) => {
    console.log('emptyScheduledHourly triggered at:', new Date().toISOString());
});

// 8. Scheduled функция (каждый день в 00:00)
export const emptyScheduledDaily = onSchedule('0 0 * * *', async (event) => {
    console.log('emptyScheduledDaily triggered at:', new Date().toISOString());
});

// 9. Scheduled функция (каждую неделю)
export const emptyScheduledWeekly = onSchedule('0 0 * * 0', async (event) => {
    console.log('emptyScheduledWeekly triggered at:', new Date().toISOString());
});

// 10. Scheduled функция (каждый месяц)
export const emptyScheduledMonthly = onSchedule('0 0 1 * *', async (event) => {
    console.log('emptyScheduledMonthly triggered at:', new Date().toISOString());
});

// 11. Pub/Sub триггер
export const emptyPubSub = onMessagePublished('test-topic', async (message) => {
    console.log('emptyPubSub triggered');
    console.log('Message data:', message.data);
});

// 12. Auth onCreate триггер (пропускаем, так как v2 identity не поддерживается)
export const emptyAuthCreate = onCall(async (request) => {
    console.log('emptyAuthCreate placeholder function called');
    return { success: true, message: 'Auth create placeholder function' };
});

// 13. Auth onDelete триггер (пропускаем, так как v2 identity не поддерживается)
export const emptyAuthDelete = onCall(async (request) => {
    console.log('emptyAuthDelete placeholder function called');
    return { success: true, message: 'Auth delete placeholder function' };
});

// 14. Storage onFinalize триггер
export const emptyStorageFinalize = onObjectFinalized(async (event) => {
    console.log('emptyStorageFinalize triggered');
    console.log('File path:', event.data.name);
    console.log('Bucket:', event.data.bucket);
    console.log('Content type:', event.data.contentType);
});

// 15. Storage onDelete триггер
export const emptyStorageDelete = onObjectDeleted(async (event) => {
    console.log('emptyStorageDelete triggered');
    console.log('Deleted file path:', event.data.name);
    console.log('Bucket:', event.data.bucket);
});

// 16. Storage onMetadataUpdate триггер
export const emptyStorageMetadataUpdate = onObjectMetadataUpdated(async (event) => {
    console.log('emptyStorageMetadataUpdate triggered');
    console.log('File path:', event.data.name);
    console.log('Updated metadata:', event.data.metadata);
});

// 17. Realtime Database onCreate триггер
export const emptyRTDBCreate = onValueCreated('/test/{pushId}', async (event) => {
    console.log('emptyRTDBCreate triggered for path:', event.ref);
    console.log('Push ID:', event.params.pushId);
    console.log('Data:', event.data.val());
});

// 18. Realtime Database onUpdate триггер
export const emptyRTDBUpdate = onValueUpdated('/test/{pushId}', async (event) => {
    console.log('emptyRTDBUpdate triggered for path:', event.ref);
    console.log('Push ID:', event.params.pushId);
    console.log('Before data:', event.data.before.val());
    console.log('After data:', event.data.after.val());
});

// 19. Realtime Database onDelete триггер
export const emptyRTDBDelete = onValueDeleted('/test/{pushId}', async (event) => {
    console.log('emptyRTDBDelete triggered for path:', event.ref);
    console.log('Push ID:', event.params.pushId);
    console.log('Deleted data:', event.data.val());
});

// 20. Callable функция с валидацией
export const emptyCallableWithValidation = onCall(async (request) => {
    console.log('emptyCallableWithValidation called');
    
    // Проверка аутентификации
    if (!request.auth) {
        throw new HttpsError(
            'unauthenticated',
            'The function must be called while authenticated.'
        );
    }
    
    // Проверка данных
    if (!request.data || typeof request.data !== 'object') {
        throw new HttpsError(
            'invalid-argument',
            'Data must be an object.'
        );
    }
    
    console.log('User authenticated:', request.auth.uid);
    console.log('Received data:', request.data);
    
    return { 
        success: true, 
        message: 'Empty callable function with validation executed',
        userId: request.auth.uid,
        timestamp: new Date().toISOString()
    };
}); 