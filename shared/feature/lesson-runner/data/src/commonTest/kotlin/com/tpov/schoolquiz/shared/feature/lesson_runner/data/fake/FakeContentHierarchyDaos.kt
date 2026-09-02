package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake

import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.RoomLessonResultOutboxWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/*
 * Четыре предка урока, которых читает писатель очереди, чтобы назвать контекст прохождения.
 * Ему нужен только поиск по id; наблюдатели и счётчики здесь лишь ради интерфейса.
 */

class FakeLessonDao(vararg rows: LessonEntity) : LessonDao {
    private val store = rows.associateBy { it.id }.toMutableMap()

    override fun observeByTheme(themeId: String): Flow<List<LessonEntity>> =
        flowOf(store.values.filter { it.themeId == themeId })

    override suspend fun findById(id: String): LessonEntity? = store[id]

    override suspend fun themeCount(id: String): Int = 0

    override suspend fun insertOrReplace(entity: LessonEntity) {
        store[entity.id] = entity
    }

    override suspend fun deleteById(id: String) {
        store.remove(id)
    }
}

class FakeThemeDao(vararg rows: ThemeEntity) : ThemeDao {
    private val store = rows.associateBy { it.id }.toMutableMap()

    override fun observeBySection(sectionId: String): Flow<List<ThemeEntity>> =
        flowOf(store.values.filter { it.sectionId == sectionId })

    override suspend fun findById(id: String): ThemeEntity? = store[id]

    override suspend fun sectionCount(id: String): Int = 0

    override suspend fun insertOrReplace(entity: ThemeEntity) {
        store[entity.id] = entity
    }

    override suspend fun deleteById(id: String) {
        store.remove(id)
    }
}

class FakeSectionDao(vararg rows: SectionEntity) : SectionDao {
    private val store = rows.associateBy { it.id }.toMutableMap()

    override fun observeByQuest(questId: String): Flow<List<SectionEntity>> =
        flowOf(store.values.filter { it.questId == questId })

    override suspend fun findById(id: String): SectionEntity? = store[id]

    override suspend fun questCount(id: String): Int = 0

    override suspend fun insertOrReplace(entity: SectionEntity) {
        store[entity.id] = entity
    }

    override suspend fun deleteById(id: String) {
        store.remove(id)
    }
}

class FakeQuestDao(vararg rows: QuestEntity) : QuestDao {
    private val store = rows.associateBy { it.id }.toMutableMap()

    override fun observeMyQuests(authorUid: String): Flow<List<QuestEntity>> =
        flowOf(store.values.filter { it.authorUid == authorUid })

    override fun observeMyQuestsInCatalog(authorUid: String, catalogId: String): Flow<List<QuestEntity>> =
        flowOf(store.values.filter { it.authorUid == authorUid && it.catalogId == catalogId })

    override fun observeByShelf(shelf: String): Flow<List<QuestEntity>> =
        flowOf(store.values.filter { shelf in it.visibleOn })

    override fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>> =
        flowOf(store.values.filter { it.catalogId == catalogId && shelf in it.visibleOn })

    override fun observeDownloadedArchivedByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>> =
        flowOf(emptyList())

    override suspend fun findById(id: String): QuestEntity? = store[id]

    override suspend fun catalogCount(id: String): Int = 0

    override suspend fun insertOrReplace(entity: QuestEntity) {
        store[entity.id] = entity
    }

    override suspend fun deleteById(id: String) {
        store.remove(id)
    }
}

/** Один урок с тремя предками — наименьшая иерархия, которую писатель способен разрешить. */
object FakeContentHierarchy {
    const val LESSON_ID = "lesson-1"
    const val THEME_ID = "theme-1"
    const val SECTION_ID = "section-1"
    const val QUEST_ID = "quest-1"

    fun lesson() = LessonEntity(
        id = LESSON_ID,
        themeId = THEME_ID,
        title = "Lesson",
        order = 0,
        version = 1L,
        lastModifiedAt = 0L,
        archived = false,
    )

    fun theme() = ThemeEntity(
        id = THEME_ID,
        sectionId = SECTION_ID,
        title = "Theme",
        order = 0,
        version = 1L,
        lastModifiedAt = 0L,
        archived = false,
    )

    fun section() = SectionEntity(
        id = SECTION_ID,
        questId = QUEST_ID,
        title = "Section",
        order = 0,
        version = 1L,
        lastModifiedAt = 0L,
        archived = false,
    )

    fun quest(visibleOn: Set<String> = setOf("home")) = QuestEntity(
        id = QUEST_ID,
        catalogId = "catalog-1",
        authorUid = "author-1",
        title = "Quest",
        picturePath = null,
        pictureUrl = null,
        visibleOn = visibleOn,
        averageRating = null,
        averageRatingCount = 0,
        version = 1L,
        lastModifiedAt = 0L,
        archived = false,
    )

    /** Настоящий писатель поверх этой иерархии; часы стоят, чтобы тела можно было сравнивать. */
    fun roomWriter(nowMs: Long = 5_000L): RoomLessonResultOutboxWriter =
        RoomLessonResultOutboxWriter(
            lessonDao = FakeLessonDao(lesson()),
            themeDao = FakeThemeDao(theme()),
            sectionDao = FakeSectionDao(section()),
            questDao = FakeQuestDao(quest()),
            clock = FixedClock(nowMs),
        )

    private class FixedClock(private val nowMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }
}
