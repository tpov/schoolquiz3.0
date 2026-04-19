import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

// Интерфейсы данных
interface StructureDataRemote {
    children?: StructureDataRemote[];
    nameItem: string;
    dataUpdate: string;
    dataCreate: string;
    version: number;
    ratingGlobal: number;
    starsAverageGlobal: number;
    starsMaxGlobal: number;
    tpovIdCreator: number;
    nameCreator: string;
    tpovIdMaxStarsGlobal: number;
    picture: string;
    languages: string;
    isShowArchive?: boolean;
    isShow?: boolean;
    searchVector?: number[];  // Наш новый вектор
    vectorVersion?: number;   // Версия алгоритма
}

// Текущая версия алгоритма векторов
const CURRENT_VECTOR_VERSION = 1;

// TODO: Добавить scheduled функцию для ежедневного запуска
// Пока используем только ручной запуск

/**
 * 🧪 Ручной запуск функции построения векторов (для тестирования)
 */
export const buildVectorsManual = functions.https.onCall(async (data: any, context: any) => {
    console.log('🧪 Manual vector building started');

    try {
        const db = admin.firestore();
        
        const events = ['QUIZ_HOME']; // Пока только QUIZ_HOME для тестирования
        let totalProcessed = 0;
        let totalErrors = 0;

        for (const eventName of events) {
            const result = await processEvent(db, eventName);
            totalProcessed += result.processed;
            totalErrors += result.errors;
        }

        return {
            success: true,
            processedItems: totalProcessed,
            errors: totalErrors,
            message: `Processed ${totalProcessed} items with ${totalErrors} errors`
        };

    } catch (error) {
        console.error('❌ Manual vector building failed:', error);
        throw new functions.https.HttpsError('internal', 'Vector building failed', String(error));
    }
});

/**
 * 📊 Функция для получения статистики векторов
 */
export const getVectorStats = functions.https.onCall(async (data: any, context: any) => {
    try {
        const db = admin.firestore();
        const stats = {
            totalStructures: 0,
            withVectors: 0,
            withoutVectors: 0,
            vectorVersion: CURRENT_VECTOR_VERSION
        };

        const events = ['QUIZ_HOME', 'QUIZ_ARENA', 'QUIZ_TOURNAMENT', 'QUIZ_BY_USER'];
        
        for (const eventName of events) {
            const categoriesSnapshot = await db
                .collection('structures')
                .doc('structureData')
                .collection(eventName)
                .get();

            for (const categoryDoc of categoriesSnapshot.docs) {
                const categoryData = categoryDoc.data() as StructureDataRemote;
                const eventStats = countVectorsRecursive(categoryData);
                
                stats.totalStructures += eventStats.total;
                stats.withVectors += eventStats.withVectors;
                stats.withoutVectors += eventStats.withoutVectors;
            }
        }

        return stats;

    } catch (error) {
        console.error('❌ Error getting vector stats:', error);
        throw new functions.https.HttpsError('internal', 'Failed to get stats', String(error));
    }
});

/**
 * Обрабатывает все структуры для одного события
 */
async function processEvent(
    db: admin.firestore.Firestore, 
    eventName: string
): Promise<{ processed: number; errors: number }> {
    let processed = 0;
    let errors = 0;

    try {
        // Читаем все категории для события
        const categoriesSnapshot = await db
            .collection('structures')
            .doc('structureData')
            .collection(eventName)
            .get();

        console.log(`📂 Found ${categoriesSnapshot.size} categories in ${eventName}`);

        for (const categoryDoc of categoriesSnapshot.docs) {
            try {
                const categoryData = categoryDoc.data() as StructureDataRemote;
                
                // Обрабатываем категорию и все её дочерние элементы рекурсивно
                const result = await processStructureRecursive(categoryData);
                
                processed += result.processed;
                errors += result.errors;

                // Сохраняем обновленную категорию
                await categoryDoc.ref.update({
                    searchVector: categoryData.searchVector,
                    vectorVersion: categoryData.vectorVersion
                });

                console.log(`✅ Updated category ${categoryDoc.id} with vectors`);

            } catch (error) {
                console.error(`❌ Error processing category ${categoryDoc.id}:`, error);
                errors++;
            }
        }

    } catch (error) {
        console.error(`❌ Error reading event ${eventName}:`, error);
        throw error;
    }

    return { processed, errors };
}

import { translateToEnglish } from './translateUtils';

/**
 * Генерация вектора из английского текста
 * TODO: Заменить на Universal Sentence Encoder
 */
function generateEnglishVector(englishText: string): number[] {
    const vector = new Array(512).fill(0);
    
    // Простая хэш-функция для английского текста
    for (let i = 0; i < englishText.length; i++) {
        const charCode = englishText.charCodeAt(i);
        vector[i % 512] += Math.sin(charCode * (i + 1)) * 0.1;
    }
    
    // Нормализуем вектор
    const norm = Math.sqrt(vector.reduce((sum, val) => sum + val * val, 0));
    if (norm > 0) {
        for (let i = 0; i < 512; i++) {
            vector[i] /= norm;
        }
    }
    
    return vector;
}

/**
 * Рекурсивно обрабатывает структуру и строит векторы
 */
async function processStructureRecursive(
    structure: StructureDataRemote
): Promise<{ processed: number; errors: number }> {
    let processed = 0;
    let errors = 0;

    try {
        // Сначала обрабатываем детей
        const childVectors: number[][] = [];
        
        if (structure.children && structure.children.length > 0) {
            for (const child of structure.children) {
                const childResult = await processStructureRecursive(child);
                processed += childResult.processed;
                errors += childResult.errors;
                
                // Собираем векторы детей для суммирования
                if (child.searchVector) {
                    childVectors.push(child.searchVector);
                }
            }
        }

        // Строим вектор для текущей структуры
        if (childVectors.length > 0) {
            // Суммируем векторы детей
            structure.searchVector = sumAndNormalizeVectors(childVectors);
        } else {
            // 🌐 Переводим в английский и генерируем вектор
            const language = structure.languages || 'ru';
            const englishText = await translateToEnglish(structure.nameItem, language);
            structure.searchVector = generateEnglishVector(englishText);
        }

        structure.vectorVersion = CURRENT_VECTOR_VERSION;
        processed++;

        console.log(`✅ Processed [${structure.languages || 'ru'}]: ${structure.nameItem} (vector: ${structure.searchVector ? 'generated' : 'failed'})`);

    } catch (error) {
        console.error(`❌ Error processing structure ${structure.nameItem}:`, error);
        errors++;
    }

    return { processed, errors };
}

/**
 * Суммирует и нормализует векторы
 */
function sumAndNormalizeVectors(vectors: number[][]): number[] | undefined {
    if (vectors.length === 0) return undefined;

    const vectorSize = vectors[0].length;
    const sumVector = new Array(vectorSize).fill(0);

    // Суммируем векторы
    for (const vector of vectors) {
        if (vector.length !== vectorSize) {
            console.warn('⚠️ Vector size mismatch, skipping');
            continue;
        }
        for (let i = 0; i < vectorSize; i++) {
            sumVector[i] += vector[i];
        }
    }

    // Нормализуем
    const norm = Math.sqrt(sumVector.reduce((sum, val) => sum + val * val, 0));
    if (norm > 0) {
        for (let i = 0; i < vectorSize; i++) {
            sumVector[i] /= norm;
        }
    }

    return sumVector;
}

/**
 * Рекурсивно считает статистику векторов
 */
function countVectorsRecursive(structure: StructureDataRemote): { total: number; withVectors: number; withoutVectors: number } {
    let total = 1;
    let withVectors = structure.searchVector ? 1 : 0;
    let withoutVectors = structure.searchVector ? 0 : 1;

    if (structure.children) {
        for (const child of structure.children) {
            const childStats = countVectorsRecursive(child);
            total += childStats.total;
            withVectors += childStats.withVectors;
            withoutVectors += childStats.withoutVectors;
        }
    }

    return { total, withVectors, withoutVectors };
} 