# 20 Пустых Функций Firebase Functions

Этот файл содержит 20 пустых функций Firebase Functions, которые можно использовать как заготовки для различных типов триггеров.

## 📁 Структура

Функции находятся в файле `src/emptyFunctions.ts` и экспортируются через `src/index.ts`.

## 🚀 Типы функций

### 1. HTTP Callable функции
- **`emptyHttpCallable`** - Базовая HTTP Callable функция
- **`emptyCallableWithValidation`** - HTTP Callable функция с валидацией аутентификации и данных

### 2. HTTP Request функции
- **`emptyHttpRequest`** - HTTP Request функция для REST API

### 3. Firestore триггеры
- **`emptyFirestoreCreate`** - Срабатывает при создании документа в коллекции `test/{docId}`
- **`emptyFirestoreUpdate`** - Срабатывает при обновлении документа в коллекции `test/{docId}`
- **`emptyFirestoreDelete`** - Срабатывает при удалении документа из коллекции `test/{docId}`
- **`emptyFirestoreWrite`** - Срабатывает при любом изменении документа в коллекции `test/{docId}`

### 4. Scheduled функции (по расписанию)
- **`emptyScheduledHourly`** - Запускается каждый час
- **`emptyScheduledDaily`** - Запускается каждый день в 00:00
- **`emptyScheduledWeekly`** - Запускается каждую неделю в воскресенье в 00:00
- **`emptyScheduledMonthly`** - Запускается каждый месяц 1-го числа в 00:00

### 5. Pub/Sub триггеры
- **`emptyPubSub`** - Срабатывает при публикации сообщения в топик `test-topic`

### 6. Auth триггеры (заглушки)
- **`emptyAuthCreate`** - Заглушка для триггера создания пользователя
- **`emptyAuthDelete`** - Заглушка для триггера удаления пользователя

### 7. Storage триггеры
- **`emptyStorageFinalize`** - Срабатывает при завершении загрузки файла
- **`emptyStorageDelete`** - Срабатывает при удалении файла
- **`emptyStorageMetadataUpdate`** - Срабатывает при обновлении метаданных файла

### 8. Realtime Database триггеры
- **`emptyRTDBCreate`** - Срабатывает при создании записи в `/test/{pushId}`
- **`emptyRTDBUpdate`** - Срабатывает при обновлении записи в `/test/{pushId}`
- **`emptyRTDBDelete`** - Срабатывает при удалении записи из `/test/{pushId}`

## 🔧 Использование

### Развертывание всех функций
```bash
firebase deploy --only functions
```

### Развертывание конкретной функции
```bash
firebase deploy --only functions:emptyHttpCallable
```

### Локальное тестирование
```bash
firebase emulators:start --only functions
```

## 📝 Примеры использования

### HTTP Callable функция
```javascript
// В клиентском коде
const functions = firebase.functions();
const emptyFunction = functions.httpsCallable('emptyHttpCallable');

emptyFunction({ test: 'data' })
  .then(result => console.log(result.data))
  .catch(error => console.error(error));
```

### HTTP Request функция
```bash
curl https://your-project.cloudfunctions.net/emptyHttpRequest
```

### Firestore триггер
```javascript
// Создание документа для тестирования
firebase.firestore().collection('test').add({
  message: 'Hello World',
  timestamp: new Date()
});
```

### Scheduled функция
Функции запускаются автоматически по расписанию. Можно изменить расписание в коде:
- `every 1 hours` - каждый час
- `0 0 * * *` - каждый день в 00:00 (cron формат)
- `0 0 * * 0` - каждое воскресенье в 00:00
- `0 0 1 * *` - 1-го числа каждого месяца в 00:00

## 🛠️ Модификация функций

Все функции содержат базовую логику логирования. Для добавления собственной логики:

1. Найдите нужную функцию в `src/emptyFunctions.ts`
2. Добавьте свой код в тело функции
3. Разверните обновленную функцию

### Пример модификации
```typescript
export const emptyHttpCallable = onCall(async (request) => {
    console.log('emptyHttpCallable called with data:', request.data);
    
    // Добавьте свою логику здесь
    const result = await processData(request.data);
    
    return { 
        success: true, 
        message: 'Function executed',
        result: result
    };
});
```

## ⚠️ Важные замечания

1. **Firebase Functions v2**: Все функции используют Firebase Functions v2 API
2. **Типизация**: Функции используют TypeScript для лучшей типизации
3. **Логирование**: Все функции содержат базовое логирование для отладки
4. **Обработка ошибок**: Добавьте try-catch блоки для обработки ошибок
5. **Аутентификация**: Функция `emptyCallableWithValidation` демонстрирует проверку аутентификации

## 🔗 Полезные ссылки

- [Firebase Functions v2 Documentation](https://firebase.google.com/docs/functions/v2)
- [Firebase Functions Triggers](https://firebase.google.com/docs/functions/triggers)
- [Firebase Functions Scheduling](https://firebase.google.com/docs/functions/schedule-functions)
- [Firebase Functions Callable](https://firebase.google.com/docs/functions/callable) 