import * as admin from "firebase-admin";
import { getGiftBoxReward } from './giftReward';

// Initialize Firebase Admin SDK
admin.initializeApp();

// Export the functions
export { getGiftBoxReward }; 