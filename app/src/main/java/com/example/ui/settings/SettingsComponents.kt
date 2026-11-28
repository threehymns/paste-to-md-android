package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.AppIcons

internal val SettingsMaxContentWidth = 640.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSectionHeader(
    title: String,
    isFirst: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = SettingsMaxContentWidth)
            .padding(
                start = 16.dp,
                top = if (isFirst) 0.dp else 22.dp,
                bottom = 8.dp
            )
    )
}

@Composable
internal fun SettingsIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsChoiceTile(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = SettingsMaxContentWidth)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsIconBadge(
                    icon = leadingIcon,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsInfoTile(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    index: Int,
    count: Int,
    badgeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = SettingsMaxContentWidth)
    ) {
        ListItem(
            leadingContent = {
                SettingsIconBadge(
                    icon = leadingIcon,
                    containerColor = badgeContainerColor,
                    contentColor = badgeContentColor
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: @Composable (T) -> String,
    iconFor: ((T) -> ImageVector)? = null,
    modifier: Modifier = Modifier
) {
    val toggleColors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { isChecked ->
                    if (isChecked) onSelect(option)
                },
                colors = toggleColors,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                if (iconFor != null) {
                    Icon(
                        imageVector = iconFor(option),
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
                } else {
                    AnimatedVisibility(
                        visible = checked,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AppIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
                        }
                    }
                }
                Text(
                    text = labelFor(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (checked) {
                        MaterialTheme.typography.labelLargeEmphasized
                    } else {
                        MaterialTheme.typography.labelLarge
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSwitchTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: ImageVector,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    val badgeContainerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "switch_badge_container"
    )
    val badgeContentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "switch_badge_content"
    )

    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        leadingContent = {
            SettingsIconBadge(
                icon = leadingIcon,
                containerColor = badgeContainerColor,
                contentColor = badgeContentColor
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                // Row handles toggle state for a full-row touch target and unified semantics.
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) AppIcons.Check else AppIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = SettingsMaxContentWidth)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
