# 🔍 Firebase Functions: Построение векторов для семантического поиска

## 📋 Описание

Этот модуль автоматически строит векторы для всех структур в Firebase Firestore, обеспечивая семантический поиск по содержимому викторин.

## 🔧 Функции

### 1. `buildVectorsDaily` - Ежедневная автоматическая обработка
- **Запуск**: Автоматически каждый день в 02:00 UTC
- **Цель**: Обрабатывает все события (`QUIZ_HOME`, `QUIZ_ARENA`, `QUIZ_TOURNAMENT`, `QUIZ_BY_USER`)
- **Алгоритм**: Universal Sentence Encoder (512-мерные векторы)

### 2. `buildVectorsManual` - Ручной запуск для тестирования
- **Запуск**: Через HTTP вызов из приложения
- **Параметры**: `{ events: ['QUIZ_HOME'] }` - список событий для обработки
- **Авторизация**: Требуется аутентифицированный пользователь

## 🏗️ Архитектура обработки

```
Event (QUIZ_HOME) 
├── Category 1
│   ├── Subcategory 1.1
│   │   ├── Lesson 1.1.1 (вектор из вопросов)
│   │   └── Lesson 1.1.2 (вектор из вопросов)
│   │   └── Subcategory vector = sum(lessons) + normalize
│   └── Category vector = sum(subcategories) + normalize
```

## 📊 Процесс построения векторов

1. **Листовые элементы** (уроки без детей):
   - Текст = название + все вопросы + все ответы
   - Генерируется вектор через Universal Sentence Encoder

2. **Родительские элементы** (категории, подкатегории):
   - Суммируются векторы всех дочерних элементов
   - Нормализуются для получения единичного вектора

3. **Сохранение**:
   - `searchVector: number[]` - сам вектор (512 чисел)
   - `vectorVersion: number` - версия алгоритма для совместимости

## 🚀 Деплой

```bash
# 1. Собираем проект
cd functions
npm run build

# 2. Деплоим функции
npm run deploy

# 3. Проверяем в Firebase Console
# https://console.firebase.google.com/project/YOUR_PROJECT/functions
```

## 🧪 Тестирование

### Ручной запуск из Android приложения:
```kotlin
// В RepositoryStructureImpl.kt
val functions = Firebase.functions
val data = hashMapOf("events" to listOf("QUIZ_HOME"))

functions
    .getHttpsCallable("buildVectorsManual")
    .call(data)
    .addOnSuccessListener { result ->
        Log.d("VectorBuild", "Success: ${result.data}")
    }
    .addOnFailureListener { error ->
        Log.e("VectorBuild", "Error: ${error.message}")
    }
```

### Проверка логов:
```bash
# Смотрим логи в реальном времени
firebase functions:log --follow

# Или в Firebase Console
# https://console.firebase.google.com/project/YOUR_PROJECT/functions/logs
```

## 📈 Мониторинг

Функция выводит подробные логи:
- `🚀 buildVectorsDaily started` - начало работы
- `📂 Found X categories in QUIZ_HOME` - количество найденных категорий  
- `✅ Processed: CategoryName (vector: generated)` - успешная обработка
- `🎉 buildVectorsDaily completed in 1234ms` - завершение с метриками

## ⚙️ Настройки

### Расписание (в buildVectors.ts):
```typescript
.schedule('0 2 * * *') // Каждый день в 02:00 UTC
.timeZone('UTC')
```

### Размер вектора:
- **512 измерений** = ~2KB на элемент
- **10,000 элементов** = ~20MB общей памяти

### Версионирование:
```typescript
const CURRENT_VECTOR_VERSION = 1; // Увеличивать при изменении алгоритма
```

## 🔄 Интеграция с поиском

После построения векторов, данные готовы для семантического поиска:

```firestore
structures/structureData/QUIZ_HOME/{categoryId}
{
  "nameItem": "Математика",
  "children": [...],
  "searchVector": [0.123, -0.456, 0.789, ...], // 512 чисел
  "vectorVersion": 1
}
```

## 🔍 Следующие шаги

1. **Интеграция вопросов**: Доработать `getQuestionsForStructure()` для получения реальных вопросов
2. **Клиентский поиск**: Реализовать TensorFlow Lite на Android для поиска
3. **Оптимизация**: Добавить кэширование и инкрементальное обновление

## 📞 Поддержка

Если функция не работает:
1. Проверьте логи в Firebase Console
2. Убедитесь что права доступа настроены
3. Проверьте структуру данных в Firestore 