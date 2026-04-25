package com.tpov.schoolquiz.shared.core.sync

enum class SyncLevel {
    Catalog, Quest, Section, Theme, Lesson, Question;

    val next: SyncLevel? get() = when (this) {
        Catalog  -> Quest
        Quest    -> Section
        Section  -> Theme
        Theme    -> Lesson
        Lesson   -> Question
        Question -> null
    }

    val collectionId: String get() = when (this) {
        Catalog  -> "catalogs"
        Quest    -> "quests"
        Section  -> "sections"
        Theme    -> "themes"
        Lesson   -> "lessons"
        Question -> "questions"
    }
}
