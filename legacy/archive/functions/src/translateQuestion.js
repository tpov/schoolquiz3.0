const functions = require('@google-cloud/functions-framework');
const { Firestore } = require('@google-cloud/firestore');
const fetch = require('node-fetch');

const db = new Firestore();

const TRANSLATOR_LEVELS = {
    HUMAN: 200,
    GOOGLE: 180,
    MYMEMORY: 160,
    LINGVA: 140,
    ERROR: 100
};

// Функция для чтения количества доступных символов
async function getLeftSymbols() {
    const docRef = db.collection('variable').doc('translateConfig');
    const doc = await docRef.get();

    if (doc.exists) {
        console.log('Document data:', doc.data());
        return doc.data().leftSymbolTranslate;
    }
    throw new Error('translateConfig not found');
}

// Функция для обновления оставшихся символов
async function updateLeftSymbols(newValue) {
    const docRef = db.collection('variable').doc('translateConfig');
    await docRef.update({ leftSymbolTranslate: newValue });
}

// Функции для маскировки/восстановления слов
function maskItalicWords(text) {
    const italicRegex = /\*(.*?)\*/g;
    const maskedWords = [];
    const cleanedText = text.replace(italicRegex, (_, word) => {
        maskedWords.push(word);
        return `{{${maskedWords.length - 1}}}`;
    });
    return { cleanedText, maskedWords };
}

function unmaskItalicWords(translatedText, maskedWords) {
    return translatedText.replace(/{{(\d+)}}/g, (_, index) => `*${maskedWords[index]}*`);
}

// Создание структурированного ID для переведенного вопроса
function createStructuredQuestionId(difficulty, language, sequenceNumber) {
    const paddedNumber = sequenceNumber.toString().padStart(3, '0');
    return `${difficulty}_${language}_question_${paddedNumber}`;
}

// Получение следующего номера для конкретной группы
async function getNextSequenceNumber(collectionPath, difficulty, language) {
    const prefix = `${difficulty}_${language}_question_`;
    
    const snapshot = await db.collection(collectionPath)
        .where(db.FieldPath.documentId(), '>=', prefix)
        .where(db.FieldPath.documentId(), '<', prefix + '\uf8ff')
        .get();
    
    let maxNumber = 0;
    snapshot.forEach(doc => {
        const id = doc.id;
        const match = id.match(new RegExp(`${prefix}(\\d+)$`));
        if (match) {
            const number = parseInt(match[1], 10);
            if (number > maxNumber) {
                maxNumber = number;
            }
        }
    });
    
    return maxNumber + 1;
}

// Создание пути коллекции для новой архитектуры
function createCollectionPath(event, pathStructure) {
    const { nameCategory, nameSubCategory, nameSubsubCategory } = pathStructure;
    const categoryPath = `${nameCategory}_${nameSubCategory}_${nameSubsubCategory}`;
    return `questions/question${event}/${categoryPath}`;
}

// Google Translate API
async function googleTranslate(text, fromLang, toLang) {
    const url = `https://translation.googleapis.com/language/translate/v2?key=AIzaSyBAQxFD8zxcdaTRns1B97DzWcItFiWW_9I`;
    const body = JSON.stringify({
        q: text,
        source: fromLang,
        target: toLang,
        format: "text"
    });

    try {
        const response = await fetch(url, {
            method: 'POST',
            body,
            headers: { 'Content-Type': 'application/json' }
        });

        const data = await response.json();

        if (response.ok && data.data.translations.length > 0) {
            return {
                text: data.data.translations[0].translatedText,
                level: TRANSLATOR_LEVELS.GOOGLE
            };
        } else {
            throw new Error('Google Translate API error');
        }
    } catch (error) {
        console.error('Google Translate error:', error);
        throw error;
    }
}

// Бесплатные переводчики
async function myMemoryTranslate(text, fromLang, toLang) {
    const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=${fromLang}|${toLang}`;
    try {
        const response = await fetch(url);
        const data = await response.json();
        return {
            text: data.responseData.translatedText,
            level: TRANSLATOR_LEVELS.MYMEMORY
        };
    } catch (error) {
        console.error('MyMemory error:', error);
        throw error;
    }
}

async function lingvaTranslate(text, fromLang, toLang) {
    const url = 'https://lingva.ml/api/v1/translate';
    try {
        const response = await fetch(url, {
            method: 'POST',
            body: JSON.stringify({ text, fromLang, toLang }),
            headers: { 'Content-Type': 'application/json' }
        });
        const data = await response.json();
        return {
            text: data.translation,
            level: TRANSLATOR_LEVELS.LINGVA
        };
    } catch (error) {
        console.error('Lingva error:', error);
        throw error;
    }
}

// Fallback для перевода
async function translateWithFallback(text, fromLang, toLang) {
    try {
        console.log('Attempting MyMemory translation');
        return await myMemoryTranslate(text, fromLang, toLang);
    } catch (error) {
        console.log('MyMemory failed, trying Lingva');
    }

    try {
        console.log('Attempting Lingva translation');
        return await lingvaTranslate(text, fromLang, toLang);
    } catch (error) {
        console.log('Lingva failed');
        return {
            text: text,
            level: TRANSLATOR_LEVELS.ERROR
        };
    }
}

// Перевод ответов
async function translateAnswers(nameAnswers, fromLang, toLang) {
    const answers = nameAnswers.split('|');
    const translatedAnswers = await Promise.all(
        answers.map(async (answer) => {
            const { cleanedText, maskedWords } = maskItalicWords(answer);
            const translated = await translateWithFallback(cleanedText, fromLang, toLang);
            return unmaskItalicWords(translated.text, maskedWords);
        })
    );
    return translatedAnswers.join('|');
}

// Сохранение перевода в Firestore с новой архитектурой
async function saveTranslatedQuestion(translatedQuestion, event, pathStructure, difficulty, toLang) {
    const collectionPath = createCollectionPath(event, pathStructure);
    const sequenceNumber = await getNextSequenceNumber(collectionPath, difficulty, toLang);
    const structuredId = createStructuredQuestionId(difficulty, toLang, sequenceNumber);

    console.log(`Saving translated question with ID: ${structuredId}`);
    console.log(`Collection path: ${collectionPath}`);

    await db.collection(collectionPath).doc(structuredId).set(translatedQuestion);
    
    return {
        documentId: structuredId,
        collectionPath: collectionPath,
        sequenceNumber: sequenceNumber
    };
}

// Основная функция
functions.http('translate-question', async (req, res) => {
    try {
        const { 
            question, 
            pathStructure, // { nameEvent, nameCategory, nameSubCategory, nameSubsubCategory, nameQuiz }
            difficulty,     // "easy", "medium", "hard"
            toLang, 
            event, 
            usePaidTranslation 
        } = req.body;

        // Валидация входных параметров
        if (!pathStructure || !pathStructure.nameCategory || !pathStructure.nameSubCategory || !pathStructure.nameSubsubCategory) {
            return res.status(400).json({ 
                success: false, 
                error: 'pathStructure with nameCategory, nameSubCategory, nameSubsubCategory is required' 
            });
        }

        if (!difficulty || !['easy', 'medium', 'hard'].includes(difficulty)) {
            return res.status(400).json({ 
                success: false, 
                error: 'difficulty must be one of: easy, medium, hard' 
            });
        }

        let translatedQuestion = { 
            ...question,
            language: toLang, // Обновляем язык
            translatedFrom: question.language || 'ru' // Сохраняем исходный язык
        };
        
        const textLength = question.nameQuestion.length + question.nameAnswers.length;

        if (usePaidTranslation) {
            const leftSymbols = await getLeftSymbols();

            if (textLength <= leftSymbols) {
                console.log('Using Google Translate (paid)');
                const { cleanedText, maskedWords } = maskItalicWords(question.nameQuestion);
                const questionTranslation = await googleTranslate(cleanedText, question.language, toLang);
                translatedQuestion.nameQuestion = unmaskItalicWords(questionTranslation.text, maskedWords);

                translatedQuestion.nameAnswers = await translateAnswers(question.nameAnswers, question.language, toLang);
                translatedQuestion.lvlTranslate = questionTranslation.level;

                await updateLeftSymbols(leftSymbols - textLength);
                
                const saveResult = await saveTranslatedQuestion(translatedQuestion, event, pathStructure, difficulty, toLang);

                res.json({ 
                    success: true, 
                    translatedQuestion,
                    saveResult,
                    translationMethod: 'google',
                    symbolsUsed: textLength
                });
                return;
            } else {
                console.log('Not enough symbols for Google Translate, using free translators');
            }
        }

        console.log('Using free translators (fallback)');
        const { cleanedText, maskedWords } = maskItalicWords(question.nameQuestion);
        const questionTranslation = await translateWithFallback(cleanedText, question.language, toLang);
        translatedQuestion.nameQuestion = unmaskItalicWords(questionTranslation.text, maskedWords);

        translatedQuestion.nameAnswers = await translateAnswers(question.nameAnswers, question.language, toLang);
        translatedQuestion.lvlTranslate = questionTranslation.level;

        const saveResult = await saveTranslatedQuestion(translatedQuestion, event, pathStructure, difficulty, toLang);

        res.json({ 
            success: true, 
            translatedQuestion,
            saveResult,
            translationMethod: 'free',
            symbolsUsed: 0
        });
    } catch (error) {
        console.error('Translation error:', error);
        res.status(500).json({ success: false, error: error.message });
    }
}); 