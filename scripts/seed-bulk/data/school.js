'use strict';

module.exports = {
  catalogId: 'school',
  quests: [
    require('./school/quest-math'),
    require('./school/quest-russian'),
    // Полные школьные курсы (7 разделов x 4 темы x 5 уроков x 40 вопросов).
    // Подключаются сюда ТОЛЬКО после сплошного аудита (см. .zcode/skills/quest-audit).
    require('./school/quest-math-full'),
    require('./school/quest-physics'),
    require('./school/quest-chemistry'),
    require('./school/quest-biology'),
    // require('./school/quest-history'),   // авторинг готов, аудит не проводился
    // require('./school/quest-geography'), // авторинг готов, аудит не проводился
  ],
};
