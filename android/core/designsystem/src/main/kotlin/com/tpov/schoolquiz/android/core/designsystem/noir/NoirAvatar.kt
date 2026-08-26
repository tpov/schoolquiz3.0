package com.tpov.schoolquiz.android.core.designsystem.noir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A person's face, or the users glyph when there is no picture.
 *
 * The avatars are items the account earns and wears, so the picture is a remote image rather than
 * a bundled drawable. Offline, or before the account has picked one, the fallback keeps the spot
 * from reading as empty.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun NoirAvatar(
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color = NoirGlassStroke,
    fillColor: Color = LocalNoirAccent.current.copy(alpha = 0.10f),
) {
    val context = LocalContext.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fillColor)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = NoirIcons.Users,
                contentDescription = null,
                tint = LocalNoirAccent.current,
                modifier = Modifier.size(size / 2f),
            )
        }
    }
}
