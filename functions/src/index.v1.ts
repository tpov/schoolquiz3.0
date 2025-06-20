// Используем firebase-functions v1 для обратной совместимости
import * as admin from 'firebase-admin';
// Инициализируем firebase-admin если не инициализирован
if (!admin.apps.length) {
  admin.initializeApp();
}

// @ts-ignore - используем старый import, так как это файл для v1
import * as functions from 'firebase-functions';

interface RewardData {
  type: string;
  amount: number;
  itemName?: string;
}

// Используем функцию editStructure для получения наград gift box
// Это позволит избежать создания новой функции при проблемах с биллингом
export const editStructure = functions.https.onCall(async (data, context) => {
  console.log('getGiftBoxReward function called (через editStructure)');

  // @ts-ignore - обходим проблему с типами в v6
  if (!context || !context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'The function must be called while authenticated.'
    );
  }

  // Generate random numbers
  const firstRoll = Math.random(); // 0-1
  const secondRoll = Math.random(); // 0-1

  console.log(`First roll: ${firstRoll}, Second roll: ${secondRoll}`);

  // Check first 5%
  if (firstRoll > 0.05) {
    // Common reward (95% of cases)
    const nolicsAmount = Math.floor(Math.random() * (2000 - 500 + 1)) + 500; // 500-2000
    console.log(`Common reward: ${nolicsAmount} nolics`);
    return {
      type: 'addNolics',
      amount: nolicsAmount
    } as RewardData;
  }

  // Rare reward (5% of cases) - check second random
  if (secondRoll <= 0.95) {
    // Rare reward (95% of 5% = 4.75% total cases)
    const rewardType = Math.floor(Math.random() * 3); // 0, 1 or 2

    switch (rewardType) {
      case 0: // addNolics
        const rareNolics = Math.floor(Math.random() * (13000 - 7000 + 1)) + 7000; // 7000-13000
        console.log(`Rare reward: ${rareNolics} nolics`);
        return {
          type: 'addNolics',
          amount: rareNolics
        } as RewardData;

      case 1: // addGold
        const goldAmount = Math.floor(Math.random() * 2); // 0 or 1
        if (goldAmount === 0) {
          // If got 0 gold, choose alternative reward
          const alternativeNolics = Math.floor(Math.random() * (13000 - 7000 + 1)) + 7000;
          console.log(`Rare reward (gold was 0): ${alternativeNolics} nolics`);
          return {
            type: 'addNolics',
            amount: alternativeNolics
          } as RewardData;
        }
        console.log(`Rare reward: ${goldAmount} gold`);
        return {
          type: 'addGold',
          amount: goldAmount
        } as RewardData;

      case 2: // datePremium
        console.log('Rare reward: 1 day premium');
        return {
          type: 'datePremium',
          amount: 86400 // 1 day in seconds
        } as RewardData;
    }
  }

  // Legendary reward (5% of 5% = 0.25% total cases)
  const legendaryType = Math.floor(Math.random() * 5); // 0-4

  switch (legendaryType) {
    case 0: // logo
      const logoNames = [
        'Golden Crown Logo',
        'Diamond Star Logo',
        'Phoenix Wings Logo',
        'Dragon Scale Logo',
        'Crystal Orb Logo',
        'Thunder Bolt Logo',
        'Mystic Eye Logo',
        'Royal Shield Logo'
      ];
      const randomLogo = logoNames[Math.floor(Math.random() * logoNames.length)];
      console.log(`Legendary reward: ${randomLogo}`);
      return {
        type: 'logo',
        amount: 1,
        itemName: randomLogo
      } as RewardData;

    case 1: // trophy
      const trophyAmount = Math.floor(Math.random() * 2); // 0 or 1
      if (trophyAmount === 0) {
        // If got 0 trophies, choose alternative reward
        const alternativeGold = Math.floor(Math.random() * (20 - 10 + 1)) + 10;
        console.log(`Legendary reward (trophy was 0): ${alternativeGold} gold`);
        return {
          type: 'addGold',
          amount: alternativeGold
        } as RewardData;
      }
      console.log(`Legendary reward: ${trophyAmount} trophy`);
      return {
        type: 'trophy',
        amount: trophyAmount
      } as RewardData;

    case 2: // addGold
      const legendaryGold = Math.floor(Math.random() * (20 - 10 + 1)) + 10; // 10-20
      console.log(`Legendary reward: ${legendaryGold} gold`);
      return {
        type: 'addGold',
        amount: legendaryGold
      } as RewardData;

    case 3: // datePremium
      console.log('Legendary reward: 30 days premium');
      return {
        type: 'datePremium',
        amount: 86400 * 30 // 30 days in seconds
      } as RewardData;

    case 4: // addNolics
      const legendaryNolics = Math.floor(Math.random() * (200000 - 50000 + 1)) + 50000; // 50000-200000
      console.log(`Legendary reward: ${legendaryNolics} nolics`);
      return {
        type: 'addNolics',
        amount: legendaryNolics
      } as RewardData;
  }

  // Fallback (shouldn't happen)
  console.log('Fallback reward: 1000 nolics');
  return {
    type: 'addNolics',
    amount: 1000
  } as RewardData;
}); 