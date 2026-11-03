/**
 * 🌐 Переводит текст в английский
 * TODO: Интегрировать с Google Translate API
 */
export async function translateToEnglish(text: string, sourceLanguage: string): Promise<string> {
    // Если уже английский - возвращаем как есть
    if (sourceLanguage === 'en') {
        return text;
    }

    // Простой словарь для демонстрации концепции
    const translations: { [key: string]: { [word: string]: string } } = {
        'ru': {
            'Математика': 'Mathematics',
            'Физика': 'Physics', 
            'История': 'History',
            'Спорт': 'Sports',
            'Футбол': 'Football',
            'Баскетбол': 'Basketball',
            'Видеоигры': 'Video Games',
            'Minecraft': 'Minecraft',
            'Программирование': 'Programming',
            'Животные': 'Animals',
            'Собака': 'Dog',
            'Кот': 'Cat'
        },
        'uk': {
            'Математика': 'Mathematics',
            'Фізика': 'Physics',
            'Історія': 'History',
            'Спорт': 'Sports',
            'Футбол': 'Football'
        },
        'de': {
            'Mathematik': 'Mathematics',
            'Physik': 'Physics',
            'Geschichte': 'History',
            'Sport': 'Sports',
            'Fußball': 'Football',
            'Tiere': 'Animals',
            'Hund': 'Dog'
        }
    };
    
    // Простой перевод по словарю (для демо)
    const translated = translations[sourceLanguage]?.[text] || text;
    
    console.log(`🌐 Translated [${sourceLanguage}] "${text}" → [en] "${translated}"`);
    return translated;
}

/**
 * 🔍 Переводит поисковый запрос пользователя в английский
 */
export async function translateSearchQuery(query: string, userLanguage: string): Promise<string> {
    // Определяем язык запроса (можно через Google Language Detection API)
    const detectedLanguage = userLanguage || 'ru';
    
    return await translateToEnglish(query, detectedLanguage);
}

/**
 * 📊 Поддерживаемые языки
 */
export const SUPPORTED_LANGUAGES = {
    'ru': 'Russian',
    'en': 'English', 
    'uk': 'Ukrainian',
    'de': 'German',
    'fr': 'French',
    'es': 'Spanish',
    'it': 'Italian',
    'pl': 'Polish',
    'cs': 'Czech',
    'pt': 'Portuguese'
} as const;

export type SupportedLanguage = keyof typeof SUPPORTED_LANGUAGES; 