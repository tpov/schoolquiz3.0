'use strict';

module.exports = {
  catalogId: 'courses',
  quests: [
    require('./courses/quest-programming'),
    require('./courses/quest-english'),
    require('./courses/quest-english-tech'),
  ],
};
