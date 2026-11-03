const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Инициализация Firebase Admin SDK
// Используем Application Default Credentials (работает с Firebase CLI авторизацией)
admin.initializeApp({
  projectId: 'school-quiz-89336951'
});

const db = admin.firestore();

async function uploadSchoolQuizData() {
  try {
    console.log('🚀 Начинаю загрузку данных SchoolQuiz в Firestore...');
    
    // Читаем JSON файл с данными
    const dataPath = path.join(__dirname, 'school_quiz_data.json');
    const jsonData = JSON.parse(fs.readFileSync(dataPath, 'utf8'));
    
    // Получаем данные SchoolQuiz
    const schoolQuizData = jsonData.structures.structureData.QUIZ_HOME.SchoolQuiz;
    
    // Путь в Firestore: structures/structureData/QUIZ_HOME/SchoolQuiz
    const docRef = db.collection('structures')
                     .doc('structureData')
                     .collection('QUIZ_HOME')
                     .doc('SchoolQuiz');

    // Загружаем основные данные SchoolQuiz
    await docRef.set(schoolQuizData);
    console.log('✅ Основные данные SchoolQuiz загружены!');

    // Загружаем детей (категории) как отдельные документы
    if (schoolQuizData.children && schoolQuizData.children.length > 0) {
      console.log('📚 Загружаю категории...');
      
      for (let i = 0; i < schoolQuizData.children.length; i++) {
        const category = schoolQuizData.children[i];
        const categoryRef = db.collection('structures')
                             .doc('structureData')
                             .collection('event')
                             .doc('8')
                             .collection('SchoolQuiz')
                             .doc('categories')
                             .collection('items')
                             .doc(category.nameItem);
        
        await categoryRef.set(category);
        console.log(`✅ Категория "${category.nameItem}" загружена!`);
        
        // Загружаем подкатегории если есть
        if (category.children && category.children.length > 0) {
          console.log(`📖 Загружаю подкатегории для "${category.nameItem}"...`);
          
          for (let j = 0; j < category.children.length; j++) {
            const subcategory = category.children[j];
            const subcategoryRef = categoryRef.collection('subcategories')
                                              .doc(subcategory.nameItem);
            
            await subcategoryRef.set(subcategory);
            console.log(`  ✅ Подкатегория "${subcategory.nameItem}" загружена!`);
          }
        }
      }
    }

    console.log('🎉 Все данные SchoolQuiz успешно загружены в Firestore!');
    console.log('📍 Путь в Firestore: structures/structureData/event/8/SchoolQuiz');
    
  } catch (error) {
    console.error('❌ Ошибка при загрузке данных:', error);
  } finally {
    // Закрываем соединение
    admin.app().delete();
  }
}

// Альтернативный способ - загрузка как одного документа со всей структурой
async function uploadAsDocument() {
  try {
    console.log('🚀 Загружаю как один документ...');
    
    const dataPath = path.join(__dirname, 'school_quiz_data.json');
    const jsonData = JSON.parse(fs.readFileSync(dataPath, 'utf8'));
    
    const schoolQuizData = jsonData.structures.structureData.QUIZ_HOME.SchoolQuiz;
    
    // Простая загрузка как один документ - упрощенный путь
    const docRef = db.collection('structures')
                     .doc('structureData')
                     .collection('QUIZ_HOME')
                     .doc('SchoolQuiz');
    await docRef.set(schoolQuizData);
    
    console.log('✅ Данные загружены как один документ!');
    console.log('📍 Путь: structures/structureData/event/8/SchoolQuiz');
    
  } catch (error) {
    console.error('❌ Ошибка:', error);
  } finally {
    admin.app().delete();
  }
}

// Запускаем загрузку
const args = process.argv.slice(2);
if (args.includes('--single-doc')) {
  uploadAsDocument();
} else {
  uploadSchoolQuizData();
} 