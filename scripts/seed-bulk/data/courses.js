'use strict';

module.exports = {
  catalogId: 'courses',
  quests: [
    require('./courses/quest-programming'),
    require('./courses/quest-english'),
    require('./courses/quest-english-tech.v2'),
    require('./courses/quest-crypto-smartmoney'),
    require('./courses/quest-german'),
    require('./courses/quest-business'),
  ],
};
