package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tpov.schoolquiz.android.core.designsystem.R
import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirGlassStroke
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeLg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT1
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
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(CATALOG_GAP),
        verticalArrangement = Arrangement.spacedBy(CATALOG_GAP),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                header()
            }
        }
        items(items, key = { it.id.value }) { item ->
            CatalogGridItem(
                item = item,
                onClick = { onCatalogClick(item.id, item.name) },
            )
        }
    }
}

/**
 * A catalog tile: the picture carries the mood, the name sits below it on a plate.
 *
 * Two rules from the canvas. The art is muted — saturate/brightness/contrast — so five loud
 * thumbnails do not fight each other, and the caption is a separate plate under the picture,
 * divided by a hairline, instead of text floating on a scrim: light user-supplied photos made the
 * overlay unreadable.
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
            .clip(NoirShapeLg)
            .background(NoirGlassFill)
            .border(1.dp, NoirGlassStroke, NoirShapeLg)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(imageData).crossfade(true).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(CatalogMuteMatrix),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(TILE_ASPECT_RATIO),
        )
        // The name sits on the picture, over a wash that fades upward into it.
        //
        // The wash is not decoration: half these catalogues are bright photographs, and white text
        // laid straight onto one is unreadable. Painting it always — rather than only over the
        // light ones — keeps every tile looking like the same component.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                )
                .padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.name,
                style = NoirType.rowTitle.copy(fontSize = 14.5.sp, color = NoirT1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.questCountLabel?.let { label ->
                Text(label, style = NoirType.rowSub.copy(fontSize = 12.5.sp, color = CatalogCaptionSub))
            }
        }
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

private val NoirGlassFill = Color.White.copy(alpha = 0.035f)

/** Wide tiles, so five catalogs fit the viewport without scrolling. */
private const val TILE_ASPECT_RATIO = 1.16f

private val CATALOG_GAP = 10.dp

/**
 * The canvas mutes catalog art with `saturate(.72) brightness(.55) contrast(1.06)`; collapsed into
 * one matrix (contrast ∘ brightness ∘ saturation).
 *
 * Darker than it was: the caption sits on the picture now, and art at .62 kept winning against it.
 */
@Suppress("MagicNumber")
private val CatalogMuteMatrix =
    ColorMatrix(
        floatArrayOf(
            0.4545f, 0.1167f, 0.0118f, 0f, -0.03f,
            0.0347f, 0.5365f, 0.0118f, 0f, -0.03f,
            0.0347f, 0.1167f, 0.4315f, 0f, -0.03f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

/** The caption's second line — a step below white, still well clear of the art behind it. */
private val CatalogCaptionSub = Color(0xFFD8D8D8)
