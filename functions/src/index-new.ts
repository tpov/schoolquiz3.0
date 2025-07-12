import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

// Инициализация Firebase Admin
admin.initializeApp();

// Импорт модулей
export { generateNewTpovId } from './modules/auth';
export { editStructure } from './modules/structure';

// Экспорт функций построения векторов
export { buildVectorsDaily, buildVectorsManual, getVectorStats } from './buildVectors';

// Экспорт функций перевода
export { translateQuestion } from './translateQuestion';
export { translateUtils } from './translateUtils';

// Экспорт функций наград
export { giftReward } from './giftReward';

// Экспорт функций редактирования структуры
export { editStructure as editStructureV2 } from './editStructure';

// Вспомогательные функции для работы с вложенными объектами
export function getNestedValue(obj: any, path: string): any {
    return path.split('.').reduce((current, key) => current && current[key], obj);
}

export function setNestedValue(obj: any, path: string, value: any): void {
    const keys = path.split('.');
    const lastKey = keys.pop();
    const target = keys.reduce((current, key) => {
        if (!current[key]) current[key] = {};
        return current[key];
    }, obj);
    if (lastKey) target[lastKey] = value;
}

// Функция для получения tpovId из uid
export async function getTpovIdFromUid(uid: string): Promise<number | null> {
    const db = admin.firestore();
    const listTpovIdDoc = await db.collection('variable').doc('listTpovId').get();
    
    if (listTpovIdDoc.exists) {
        const data = listTpovIdDoc.data();
        return data?.[uid] || null;
    }
    
    return null;
}

// Функция для обновления tpovId для пользователя
export async function updateTpovIdForUser(uid: string, tpovId: number): Promise<void> {
    const db = admin.firestore();
    const listTpovIdRef = db.collection('variable').doc('listTpovId');
    
    await listTpovIdRef.set({
        [uid]: tpovId
    }, { merge: true });
}

// Функция для получения всех tpovId
export async function getAllTpovIds(): Promise<Record<string, number>> {
    const db = admin.firestore();
    const listTpovIdDoc = await db.collection('variable').doc('listTpovId').get();
    
    if (listTpovIdDoc.exists) {
        return listTpovIdDoc.data() as Record<string, number>;
    }
    
    return {};
}

// Функция для удаления tpovId пользователя
export async function deleteTpovIdForUser(uid: string): Promise<void> {
    const db = admin.firestore();
    const listTpovIdRef = db.collection('variable').doc('listTpovId');
    
    await listTpovIdRef.update({
        [uid]: admin.firestore.FieldValue.delete()
    });
}

// Функция для получения статистики пользователей
export async function getUserStats(): Promise<{
    totalUsers: number;
    activeUsers: number;
    anonymousUsers: number;
}> {
    const db = admin.firestore();
    const listTpovIdDoc = await db.collection('variable').doc('listTpovId').get();
    
    if (!listTpovIdDoc.exists) {
        return {
            totalUsers: 0,
            activeUsers: 0,
            anonymousUsers: 0
        };
    }
    
    const data = listTpovIdDoc.data() as Record<string, number>;
    const totalUsers = Object.keys(data).length;
    
    // Подсчитываем активных пользователей (имеющих tpovId > 0)
    const activeUsers = Object.values(data).filter(id => id > 0).length;
    const anonymousUsers = totalUsers - activeUsers;
    
    return {
        totalUsers,
        activeUsers,
        anonymousUsers
    };
}