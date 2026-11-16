package dev.maxxing.github.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom solid-filled vector icons so active toggle states (starred, bookmarked, checked)
 * are visually unmistakable at a glance without relying on text alone.
 */
object MaxxingFilledIcons {

    val Star: ImageVector by lazy {
        ImageVector.Builder(
            name = "StarFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2.25f)
                lineTo(14.95f, 8.23f)
                lineTo(21.55f, 9.19f)
                lineTo(16.77f, 13.85f)
                lineTo(17.90f, 20.42f)
                lineTo(12f, 17.32f)
                lineTo(6.10f, 20.42f)
                lineTo(7.23f, 13.85f)
                lineTo(2.45f, 9.19f)
                lineTo(9.05f, 8.23f)
                close()
            }
        }.build()
    }

    val Heart: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeartFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 21.35f)
                lineTo(10.55f, 20.03f)
                curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
                curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
                curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
                curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
                curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
                lineTo(12f, 21.35f)
                close()
            }
        }.build()
    }

    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "BookmarkFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6f, 3f)
                curveTo(4.9f, 3f, 4f, 3.9f, 4f, 5f)
                verticalLineTo(21f)
                lineTo(12f, 17.2f)
                lineTo(20f, 21f)
                verticalLineTo(5f)
                curveTo(20f, 3.9f, 19.1f, 3f, 18f, 3f)
                close()
            }
        }.build()
    }
}
