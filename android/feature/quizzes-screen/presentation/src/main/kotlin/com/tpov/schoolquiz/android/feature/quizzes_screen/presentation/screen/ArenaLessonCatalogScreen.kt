package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirButtonStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGroup
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaCatalogNotice
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryEvent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryUiState
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaLessonCatalogUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPlacement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogScore

/*
 * NOIR (эпик 9.3): каталог арены на noir-типографике и NoirButton; карточки уроков — NoirGroup.
 * Один primary на экране — «загрузить/обновить» (arena_refresh); выбор урока и «ещё» — ghost.
 * ВСЕ testTag сохранены байт-в-байт, строки не менялись (UX-9).
 */

/** Stateless catalogue and explicit entry intents. No direct network calls or lesson-start authority. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun ArenaLessonCatalogScreen(
    state: ArenaLessonCatalogUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entryState: ArenaEntryUiState? = null,
    entryCallbacks: ArenaEntryCallbacks = ArenaEntryCallbacks({}),
) {
    val onEntryEvent = entryCallbacks.onEvent
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("arena_catalog"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoirButton(
                    text = stringResource(R.string.arena_catalog_back),
                    onClick = onBack,
                    modifier = Modifier.testTag("arena_back"),
                    style = NoirButtonStyle.Ghost,
                    fillWidth = false,
                )
                Text(stringResource(R.string.arena_catalog_title), style = NoirType.groupTitle)
                if (entryState == null) {
                    Text(
                        stringResource(R.string.arena_catalog_preview),
                        style = NoirType.rowSub,
                    )
                    Text(
                        stringResource(R.string.arena_catalog_price_notice),
                        style = NoirType.rowSub,
                    )
                } else {
                    Text(stringResource(R.string.arena_entry_intro), style = NoirType.rowSub)
                }
                val loadLabel =
                    if (state.totalLessons == null) R.string.arena_catalog_load else R.string.arena_catalog_refresh
                NoirButton(
                    text = stringResource(loadLabel),
                    onClick = onRefresh,
                    enabled = state.sessionAvailable && !state.isLoading,
                    modifier = Modifier.testTag("arena_refresh"),
                )
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("arena_loading"),
                        color = LocalNoirAccent.current,
                    )
                }
                state.notice?.let { notice ->
                    Text(
                        stringResource(notice.resourceId()),
                        modifier = Modifier.testTag("arena_notice"),
                        style = NoirType.rowSub,
                    )
                }
                state.totalLessons?.let { total ->
                    Text(
                        stringResource(R.string.arena_catalog_count, state.lessons.size, total),
                        modifier = Modifier.testTag("arena_count"),
                        style = NoirType.rowSub,
                    )
                    if (total == 0) {
                        Text(
                            stringResource(R.string.arena_catalog_empty),
                            modifier = Modifier.testTag("arena_empty"),
                            style = NoirType.rowSub,
                        )
                    }
                }
            }
        }
        // The parser has already established exact rational order and uniqueness. Do not re-sort.
        if (entryState != null) arenaEntryItems(entryState, entryCallbacks)
        items(state.lessons, key = { "catalog_lesson_" + it.lessonId }) { lesson ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                ArenaLessonReadOnlyCard(lesson)
                val stamp = state.session
                if (entryState != null && stamp != null) {
                    NoirButton(
                        text = stringResource(R.string.arena_entry_select),
                        onClick = { onEntryEvent(ArenaEntryEvent.ShowPrepare(stamp, lesson)) },
                        enabled = entryState.session == stamp && !entryState.busy,
                        modifier = Modifier.fillMaxWidth().testTag("arena_select_" + lesson.lessonId),
                        style = NoirButtonStyle.Ghost,
                    )
                }
            }
        }
        if (state.canLoadMore) {
            item {
                NoirButton(
                    text = stringResource(R.string.arena_catalog_more),
                    onClick = onLoadMore,
                    enabled = !state.isLoading && state.sessionAvailable,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("arena_more"),
                    style = NoirButtonStyle.Ghost,
                )
            }
        }
    }
    if (entryState != null) ArenaEntryDialogs(entryState, onEntryEvent)
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ArenaLessonReadOnlyCard(
    lesson: ArenaLessonCatalogCard,
    modifier: Modifier = Modifier,
) {
    NoirGroup(modifier = modifier.fillMaxWidth().testTag("arena_lesson_" + lesson.lessonId)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                lesson.title ?: stringResource(R.string.arena_catalog_untitled),
                style = NoirType.rowTitle,
            )
            Text(
                stringResource(R.string.arena_catalog_author_id, lesson.authorUid),
                style = NoirType.rowSub,
            )
            if (lesson.rating.count == 0L) {
                Text(stringResource(R.string.arena_catalog_unrated), style = NoirType.rowSub)
            } else {
                val hundredths = arenaRatingHundredths(lesson.rating.weightedScore)
                Text(
                    stringResource(R.string.arena_catalog_rating, hundredths / RATING_SCALE, hundredths % RATING_SCALE),
                    style = NoirType.rowSub,
                    color = NoirT2,
                )
                Text(
                    stringResource(R.string.arena_catalog_votes, lesson.rating.count),
                    style = NoirType.rowSub,
                )
            }
            Text(
                stringResource(R.string.arena_catalog_price, lesson.priceNolics),
                style = NoirType.rowSub,
            )
        }
    }
}

/** Display only. JS-safe numerator * 100 plus half the denominator fits Long; never used to sort. */
internal fun arenaRatingHundredths(score: ArenaLessonCatalogScore): Long =
    (score.numerator * RATING_SCALE + score.denominator / HALF) / score.denominator

private const val RATING_SCALE = 100L
private const val HALF = 2L

private fun ArenaCatalogNotice.resourceId(): Int =
    when (this) {
        ArenaCatalogNotice.NOT_LOADED -> R.string.arena_catalog_not_loaded
        ArenaCatalogNotice.ACCOUNT_UNAVAILABLE -> R.string.arena_catalog_auth
        ArenaCatalogNotice.ACCOUNT_CHANGED -> R.string.arena_catalog_account_changed
        ArenaCatalogNotice.CATALOG_CHANGED -> R.string.arena_catalog_changed
        ArenaCatalogNotice.UNAVAILABLE -> R.string.arena_catalog_unavailable
        ArenaCatalogNotice.TOO_LARGE -> R.string.arena_catalog_too_large
        ArenaCatalogNotice.INVALID_CATALOG -> R.string.arena_catalog_invalid
    }

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ArenaLessonCatalogScreenPreview() {
    SchoolQuizTheme {
        ArenaLessonCatalogScreen(
            state = ArenaLessonCatalogUiState(sessionAvailable = true),
            onRefresh = {},
            onLoadMore = {},
            onBack = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming", "MagicNumber")
@Preview(showBackground = true)
@Composable
private fun ArenaLessonReadOnlyCardPreview() {
    SchoolQuizTheme {
        ArenaLessonReadOnlyCard(
            ArenaLessonCatalogCard(
                lessonId = "preview-lesson",
                contentVersion = 1,
                title = "Example lesson",
                authorUid = "public-author-id",
                rating = ArenaLessonCatalogRating(3, 1, ArenaLessonCatalogScore(43, 21), 1),
                priceNolics = 1,
                placement = ArenaLessonCatalogPlacement(null, null, null),
            ),
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ArenaLessonCatalogEmptyPreview() {
    SchoolQuizTheme {
        ArenaLessonCatalogScreen(
            ArenaLessonCatalogUiState(totalLessons = 0, sessionAvailable = true, notice = null),
            onRefresh = {},
            onLoadMore = {},
            onBack = {},
        )
    }
}

@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Preview(showBackground = true)
@Composable
private fun ArenaLessonCatalogErrorPreview() {
    SchoolQuizTheme {
        ArenaLessonCatalogScreen(
            ArenaLessonCatalogUiState(sessionAvailable = true, notice = ArenaCatalogNotice.UNAVAILABLE),
            onRefresh = {},
            onLoadMore = {},
            onBack = {},
        )
    }
}
