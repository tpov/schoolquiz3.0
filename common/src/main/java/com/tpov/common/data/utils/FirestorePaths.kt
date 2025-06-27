package com.tpov.common.data.utils

object FirestorePaths {
    // Base paths
    const val STRUCTURES = "structures"
    const val STRUCTURE_DATA = "structureData"
    
    // Quiz types
    const val QUIZ_HOME = "QUIZ_HOME"
    const val QUIZ_ARENA = "QUIZ_ARENA" 
    const val QUIZ_USER = "QUIZ_USER"
    const val QUIZ_TOURNAMENT = "QUIZ_TOURNAMENT"
    
    // Questions
    const val QUESTIONS = "questions"
    const val QUESTIONS_DETAIL = "questionsDetail"
    
    // Helper functions
    fun getQuizPath(quizType: String) = "$STRUCTURES/$STRUCTURE_DATA/$quizType"
    
    fun getQuestionsPath(quizType: String) = "${FirestorePaths.QUESTIONS}/$quizType"
    
    fun getQuestionsDetailPath(quizType: String) = "${FirestorePaths.QUESTIONS_DETAIL}/$quizType"
    
    // Specific paths
    object SchoolQuiz {
        val ROOT = "${FirestorePaths.STRUCTURES}/${FirestorePaths.STRUCTURE_DATA}/${FirestorePaths.QUIZ_HOME}/SchoolQuiz"
        val QUESTIONS = "${FirestorePaths.QUESTIONS}/${FirestorePaths.QUIZ_HOME}"
        val QUESTIONS_DETAIL = "${FirestorePaths.QUESTIONS_DETAIL}/${FirestorePaths.QUIZ_HOME}"
    }
} 