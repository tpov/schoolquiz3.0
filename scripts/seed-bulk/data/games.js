'use strict';

module.exports = {
  catalogId: 'games',
  quests: [
    require('./games/quest-logic'),
    require('./games/quest-erudition'),
  ],
};
