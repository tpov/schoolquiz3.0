package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId

/**
 * 2-column grid of catalog items with AsyncImage thumbnails.
 *
 * Spec AC #15: LazyVerticalGrid(Fixed(2)). Coil 3 AsyncImage for HTTPS URLs.
 * ADR-HLA-07: no custom Fetcher needed — pictureUrl is pre-resolved HTTPS URL.
 */
@Preview(showBackground = true)
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
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id.value }) { item ->
            CatalogGridItem(
                item = item,
                onClick = { onCatalogClick(item.id, item.name) },
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun CatalogGridItem(
    item: CatalogDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clickable(onClick = onClick),
    ) {
        Column {
            // Defence-in-depth: only load HTTPS URLs (pre-resolved by data layer per ADR-HLA-07).
            // Non-HTTPS or null → AsyncImage shows error/placeholder; no cleartext request sent.
            val safeUrl = item.pictureUrl?.takeIf { it.startsWith("https://") }
            AsyncImage(
                model = safeUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
