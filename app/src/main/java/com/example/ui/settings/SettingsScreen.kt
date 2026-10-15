package com.example.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.*
import com.example.ui.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Appearance Section
            SettingsSection(
                title = stringResource(R.string.category_appearance),
                icon = AppIcons.Palette
            ) {
                SettingsInlineSegmentedTile(
                    title = stringResource(R.string.setting_theme_mode),
                    subtitle = "Select app color scheme"
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .testTag("setting_theme_mode_segmented")
                    ) {
                        val modes = ThemeMode.values()
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { repository.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                icon = { SegmentedButtonDefaults.Icon(active = settings.themeMode == mode) },
                                label = {
                                    Text(
                                        text = mode.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchTile(
                        title = stringResource(R.string.setting_dynamic_color),
                        subtitle = stringResource(R.string.setting_dynamic_color_desc),
                        checked = settings.dynamicColor,
                        onCheckedChange = { repository.setDynamicColor(it) },
                        modifier = Modifier.testTag("setting_dynamic_color_switch")
                    )
                }
            }

            // 2. Clipboard & Workflow Section
            SettingsSection(
                title = stringResource(R.string.category_clipboard),
                icon = AppIcons.ContentPaste
            ) {
                SettingsSwitchTile(
                    title = stringResource(R.string.setting_auto_paste),
                    subtitle = stringResource(R.string.setting_auto_paste_desc),
                    checked = settings.autoPasteOnLaunch,
                    onCheckedChange = { repository.setAutoPasteOnLaunch(it) },
                    modifier = Modifier.testTag("setting_auto_paste_switch")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                SettingsSwitchTile(
                    title = stringResource(R.string.setting_auto_copy),
                    subtitle = stringResource(R.string.setting_auto_copy_desc),
                    checked = settings.autoCopyOnConvert,
                    onCheckedChange = { repository.setAutoCopyOnConvert(it) },
                    modifier = Modifier.testTag("setting_auto_copy_switch")
                )
            }

            // 3. Markdown Formatting Section
            SettingsSection(
                title = stringResource(R.string.category_markdown),
                icon = AppIcons.Code
            ) {
                // Heading style
                SettingsInlineSegmentedTile(
                    title = stringResource(R.string.setting_heading_style),
                    subtitle = "Prefix with # (ATX) or underline with === (Setext)"
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .testTag("setting_heading_style_segmented")
                    ) {
                        val headingStyles = HeadingStyle.values()
                        headingStyles.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = settings.headingStyle == style,
                                onClick = { repository.setHeadingStyle(style) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = headingStyles.size),
                                icon = { SegmentedButtonDefaults.Icon(active = settings.headingStyle == style) },
                                label = {
                                    Text(
                                        text = if (style == HeadingStyle.ATX) "ATX (#)" else "Setext (===)",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                // Bullet marker
                SettingsInlineSegmentedTile(
                    title = stringResource(R.string.setting_bullet_marker),
                    subtitle = "Symbol used for unordered list items"
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .testTag("setting_bullet_marker_segmented")
                    ) {
                        val bullets = BulletMarker.values()
                        bullets.forEachIndexed { index, marker ->
                            SegmentedButton(
                                selected = settings.bulletMarker == marker,
                                onClick = { repository.setBulletMarker(marker) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = bullets.size),
                                icon = { SegmentedButtonDefaults.Icon(active = settings.bulletMarker == marker) },
                                label = {
                                    Text(
                                        text = marker.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                // Code block fence style
                SettingsInlineSegmentedTile(
                    title = stringResource(R.string.setting_code_fence),
                    subtitle = "Fence delimiter for multiline code blocks"
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .testTag("setting_code_fence_segmented")
                    ) {
                        val fences = CodeFenceStyle.values()
                        fences.forEachIndexed { index, fence ->
                            SegmentedButton(
                                selected = settings.codeFenceStyle == fence,
                                onClick = { repository.setCodeFenceStyle(fence) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = fences.size),
                                icon = { SegmentedButtonDefaults.Icon(active = settings.codeFenceStyle == fence) },
                                label = {
                                    Text(
                                        text = fence.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                // Emphasis marker
                SettingsInlineSegmentedTile(
                    title = stringResource(R.string.setting_emphasis_marker),
                    subtitle = "Delimiter for italic and bold formatting"
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .testTag("setting_emphasis_marker_segmented")
                    ) {
                        val markers = EmphasisMarker.values()
                        markers.forEachIndexed { index, marker ->
                            SegmentedButton(
                                selected = settings.emphasisMarker == marker,
                                onClick = { repository.setEmphasisMarker(marker) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = markers.size),
                                icon = { SegmentedButtonDefaults.Icon(active = settings.emphasisMarker == marker) },
                                label = {
                                    Text(
                                        text = if (marker == EmphasisMarker.ASTERISK) "* Asterisk" else "_ Underscore",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                SettingsSwitchTile(
                    title = stringResource(R.string.setting_soft_breaks),
                    subtitle = stringResource(R.string.setting_soft_breaks_desc),
                    checked = settings.preserveLineBreaks,
                    onCheckedChange = { repository.setPreserveLineBreaks(it) },
                    modifier = Modifier.testTag("setting_soft_breaks_switch")
                )
            }

            // 4. About & Reset Section
            SettingsSection(
                title = stringResource(R.string.category_about),
                icon = AppIcons.Info
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.app_about_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("v${BuildConfig.VERSION_NAME}") },
                            modifier = Modifier.testTag("app_version_chip")
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    )
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.setting_reset_defaults),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.setting_reset_defaults_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showResetConfirmDialog = true }
                        .testTag("setting_reset_defaults_tile")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = stringResource(R.string.dialog_reset_title))
            },
            text = {
                Text(text = stringResource(R.string.dialog_reset_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.resetToDefaults()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_reset_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsInlineSegmentedTile(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
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
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    )
}
