package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Empty attempt shelf; the home screen tests do not exercise the continue card. */
class FakeLessonAttemptRepository : LessonAttemptRepository {
    override suspend fun save(attempt: Attempt): Result<Unit> = Result.success(Unit)

    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> = flowOf(emptyList())

    override fun observeAllByUser(userId: String): Flow<List<Attempt>> = flowOf(emptyList())
}

/** Empty lesson shelf. */
class FakeLessonRepository : LessonRepository {
    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> = flowOf(emptyList())

    override suspend fun getById(id: LessonId): Lesson? = null

    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> =
        Result.success(emptySet())
}

/** Empty theme shelf. */
class FakeThemeRepository : ThemeRepository {
    override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> = flowOf(emptyList())

    override suspend fun getById(id: ThemeId): Theme? = null

    override suspend fun refreshByParents(sectionIds: Set<SectionId>, cursor: Long): Result<Set<ThemeId>> =
        Result.success(emptySet())
}

/** Empty section shelf. */
class FakeSectionRepository : SectionRepository {
    override fun observeByQuest(questId: com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId): Flow<List<Section>> =
        flowOf(emptyList())

    override suspend fun getById(id: SectionId): Section? = null

    override suspend fun refreshByParents(
        questIds: Set<com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId>,
        cursor: Long,
    ): Result<Set<SectionId>> = Result.success(emptySet())
}
