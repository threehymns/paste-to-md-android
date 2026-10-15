package com.example.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.*
import com.example.ui.AppIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // The reset dialog must dismiss before Settings pops.
    BackHandler(enabled = showResetConfirmDialog) { showResetConfirmDialog = false }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                subtitle = { Text(text = stringResource(R.string.settings_subtitle)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_navigate_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Appearance
            item {
                SettingsSection(title = stringResource(R.string.category_appearance)) {
                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_theme_mode),
                        subtitle = stringResource(R.string.setting_theme_mode_desc)
                    ) {
                        ConnectedChoiceRow(
                            options = ThemeMode.values().toList(),
                            selected = settings.themeMode,
                            onSelect = { repository.setThemeMode(it) },
                            labelFor = {
                                when (it) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                }
                            },
                            iconFor = {
                                when (it) {
                                    ThemeMode.SYSTEM -> AppIcons.BrightnessAuto
                                    ThemeMode.LIGHT -> AppIcons.LightMode
                                    ThemeMode.DARK -> AppIcons.DarkMode
                                }
                            },
                            modifier = Modifier.testTag("setting_theme_mode_segmented")
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ExpressiveDivider()
                        SettingsSwitchTile(
                            title = stringResource(R.string.setting_dynamic_color),
                            subtitle = stringResource(R.string.setting_dynamic_color_desc),
                            checked = settings.dynamicColor,
                            onCheckedChange = { repository.setDynamicColor(it) },
                            leadingIcon = AppIcons.Palette,
                            modifier = Modifier.testTag("setting_dynamic_color_switch")
                        )
                    }
                }
            }

            // 2. Clipboard & Automation
            item {
                SettingsSection(title = stringResource(R.string.category_clipboard)) {
                    SettingsSwitchTile(
                        title = stringResource(R.string.setting_auto_paste),
                        subtitle = stringResource(R.string.setting_auto_paste_desc),
                        checked = settings.autoPasteOnLaunch,
                        onCheckedChange = { repository.setAutoPasteOnLaunch(it) },
                        leadingIcon = AppIcons.ContentPaste,
                        modifier = Modifier.testTag("setting_auto_paste_switch")
                    )

                    ExpressiveDivider()

                    SettingsSwitchTile(
                        title = stringResource(R.string.setting_auto_copy),
                        subtitle = stringResource(R.string.setting_auto_copy_desc),
                        checked = settings.autoCopyOnConvert,
                        onCheckedChange = { repository.setAutoCopyOnConvert(it) },
                        leadingIcon = AppIcons.ContentCopy,
                        modifier = Modifier.testTag("setting_auto_copy_switch")
                    )
                }
            }

            // 3. Markdown Formatting
            item {
                SettingsSection(title = stringResource(R.string.category_markdown)) {
                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_heading_style),
                        subtitle = stringResource(R.string.setting_heading_style_desc)
                    ) {
                        ConnectedChoiceRow(
                            options = HeadingStyle.values().toList(),
                            selected = settings.headingStyle,
                            onSelect = { repository.setHeadingStyle(it) },
                            labelFor = { if (it == HeadingStyle.ATX) "ATX (#)" else "Setext (===)" },
                            iconFor = { AppIcons.Title },
                            modifier = Modifier.testTag("setting_heading_style_segmented")
                        )
                    }

                    ExpressiveDivider()

                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_bullet_marker),
                        subtitle = stringResource(R.string.setting_bullet_marker_desc)
                    ) {
                        ConnectedChoiceRow(
                            options = BulletMarker.values().toList(),
                            selected = settings.bulletMarker,
                            onSelect = { repository.setBulletMarker(it) },
                            labelFor = { it.title },
                            iconFor = { AppIcons.FormatListBulleted },
                            modifier = Modifier.testTag("setting_bullet_marker_segmented")
                        )
                    }

                    ExpressiveDivider()

                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_code_fence),
                        subtitle = stringResource(R.string.setting_code_fence_desc)
                    ) {
                        ConnectedChoiceRow(
                            options = CodeFenceStyle.values().toList(),
                            selected = settings.codeFenceStyle,
                            onSelect = { repository.setCodeFenceStyle(it) },
                            labelFor = { it.title },
                            iconFor = { AppIcons.DataObject },
                            modifier = Modifier.testTag("setting_code_fence_segmented")
                        )
                    }

                    ExpressiveDivider()

                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_emphasis_marker),
                        subtitle = stringResource(R.string.setting_emphasis_marker_desc)
                    ) {
                        ConnectedChoiceRow(
                            options = EmphasisMarker.values().toList(),
                            selected = settings.emphasisMarker,
                            onSelect = { repository.setEmphasisMarker(it) },
                            labelFor = {
                                if (it == EmphasisMarker.ASTERISK) "* Asterisk" else "_ Underscore"
                            },
                            iconFor = {
                                if (it == EmphasisMarker.ASTERISK) AppIcons.FormatBold
                                else AppIcons.FormatItalic
                            },
                            modifier = Modifier.testTag("setting_emphasis_marker_segmented")
                        )
                    }

                    ExpressiveDivider()

                    SettingsSwitchTile(
                        title = stringResource(R.string.setting_soft_breaks),
                        subtitle = stringResource(R.string.setting_soft_breaks_desc),
                        checked = settings.preserveLineBreaks,
                        onCheckedChange = { repository.setPreserveLineBreaks(it) },
                        leadingIcon = AppIcons.WrapText,
                        modifier = Modifier.testTag("setting_soft_breaks_switch")
                    )
                }
            }

            // 4. About (separated from destructive action)
            item {
                SettingsSection(title = stringResource(R.string.category_about)) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = AppIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium
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
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("app_version_chip")
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    )
                }
            }

            // 5. Reset (own section, separated from About)
            item {
                SettingsSection(title = stringResource(R.string.category_reset)) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = AppIcons.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.setting_reset_defaults),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_reset_defaults_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClick = { showResetConfirmDialog = true }
                            )
                            .testTag("setting_reset_defaults_tile")
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
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
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
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
private fun ExpressiveDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsChoiceTile(
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: @Composable (T) -> String,
    iconFor: (T) -> androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Icon(
                    imageVector = iconFor(option),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = labelFor(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
                // Row handles the toggle for a larger touch target and single semantics.
                onCheckedChange = null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else {
                    null
                }
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}
