package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tpov.schoolquiz.android.core.designsystem.R
import com.tpov.schoolquiz.android.core.designsystem.currentSchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirS1
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId

/**
 * 2-column grid of catalog items with AsyncImage thumbnails.
 *
 * Spec AC #15: LazyVerticalGrid(Fixed(2)). Coil 3 AsyncImage for HTTPS URLs.
 * Offline catalogs fall back to bundled thumbnails by Storage picturePath.
 */
@Preview(showBackground = true)
@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Composable
private fun CatalogGridPreview() {
    CatalogGrid(items = emptyList(), onCatalogClick = { _, _ -> })
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun CatalogGrid(
    items: List<CatalogDisplayItem>,
    onCatalogClick: (CatalogId, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClean = currentSchoolQuizDesignStyle() == SchoolQuizDesignStyle.Clean
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(if (isClean) 18.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isClean) 14.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isClean) 14.dp else 12.dp),
    ) {
        items(items, key = { it.id.value }) { item ->
            CatalogGridItem(
                item = item,
                onClick = { onCatalogClick(item.id, item.name) },
            )
        }
    }
}

/**
 * A catalog tile: the picture is the card, the name sits on it.
 *
 * The scrim under the text is not decoration. Catalog art is user-supplied and often light, and
 * white on a bright photograph is unreadable — without it the name disappears on exactly the images
 * somebody chose because they looked good.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun CatalogGridItem(
    item: CatalogDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safeUrl = item.pictureUrl?.takeIf { it.startsWith("https://") }
    val imageData = safeUrl ?: item.picturePath.toCatalogPictureResId()
    Box(
        modifier
            .aspectRatio(1f)
            .clip(NoirShapeLg)
            .background(NoirS1)
            .border(1.dp, NoirGlassStroke, NoirShapeLg)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(imageData).crossfade(true).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        SCRIM_START to Color.Transparent,
                        1f to Color.Black.copy(alpha = SCRIM_STRENGTH),
                    ),
                ),
        )
        Text(
            text = item.name,
            style = NoirType.rowTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private fun String?.toCatalogPictureResId(): Int? =
    when (this) {
        "catalog-pictures/courses.jpg" -> R.drawable.catalog_courses
        "catalog-pictures/games.jpg" -> R.drawable.catalog_games
        "catalog-pictures/quests.jpg" -> R.drawable.catalog_quests
        "catalog-pictures/school.jpg" -> R.drawable.catalog_school
        "catalog-pictures/surveys.jpg" -> R.drawable.catalog_surveys
        else -> null
    }

/** Where the scrim starts, as a fraction of the tile. Above this the picture is untouched. */
private const val SCRIM_START = 0.45f

/** Dark enough to hold white text over a bright photograph. */
private const val SCRIM_STRENGTH = 0.78f
