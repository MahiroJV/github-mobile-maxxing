package dev.octoexpress.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OctoLoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier,
    contained: Boolean = true
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (contained) {
            ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
        } else {
            LoadingIndicator(modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OctoWavyProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    if (progress != null) {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth()
        )
    } else {
        LinearWavyProgressIndicator(
            modifier = modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OctoFloatingToolbar(
    expanded: Boolean = true,
    vibrant: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        colors = if (vibrant) {
            FloatingToolbarDefaults.vibrantFloatingToolbarColors()
        } else {
            FloatingToolbarDefaults.standardFloatingToolbarColors()
        },
        modifier = modifier,
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> OctoConnectedButtonGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    iconProvider: ((T) -> ImageVector?)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelect(option) },
                shapes = shapes,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton }
            ) {
                val icon = iconProvider?.invoke(option)
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(
                    text = labelProvider(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

data class SplitActionOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> OctoSplitActionButton(
    selectedOption: SplitActionOption<T>,
    options: List<SplitActionOption<T>>,
    onPrimaryClick: (T) -> Unit,
    onSelectOption: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
    )

    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { onPrimaryClick(selectedOption.value) },
                    enabled = enabled
                ) {
                    selectedOption.icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    }
                    Text(selectedOption.label)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menuExpanded,
                    onCheckedChange = { menuExpanded = it },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Lucide.ChevronDown,
                        contentDescription = "Select option",
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = option.icon?.let { icon ->
                        { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    },
                    onClick = {
                        menuExpanded = false
                        onSelectOption(option.value)
                    }
                )
            }
        }
    }
}

@Composable
fun UserAvatar(
    avatarUrl: String,
    login: String,
    sizeDp: Int = 28,
    modifier: Modifier = Modifier
) {
    if (avatarUrl.isNotBlank()) {
        val context = LocalPlatformContext.current
        val sizePx = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(32)
        val request = remember(avatarUrl, sizePx, context) {
            ImageRequest.Builder(context)
                .data(avatarUrl)
                .size(sizePx, sizePx)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = login,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = login.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun GitHubLabelChip(
    name: String,
    colorHex: String,
    modifier: Modifier = Modifier
) {
    val parsedColor = remember(colorHex) { parseHexColor(colorHex) }
    val bgColor = parsedColor.copy(alpha = 0.2f)
    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(parsedColor)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ExpressiveStatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.expressiveAnimateContentSize(): Modifier =
    this.animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())

fun parseHexColor(rawHex: String): Color {
    val clean = rawHex.removePrefix("#").trim()
    val rgb = clean.toLongOrNull(16) ?: 0x6E40C9L
    return Color(0xFF000000L or (rgb and 0xFFFFFFL))
}
