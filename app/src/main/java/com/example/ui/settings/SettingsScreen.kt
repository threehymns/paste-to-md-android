package com.example.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.AppSettings
import com.example.data.AppSettingsRepository
import com.example.data.BulletMarker
import com.example.data.CodeFenceStyle
import com.example.data.EmphasisMarker
import com.example.data.HeadingStyle
import com.example.data.ThemeMode
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
    val layoutDirection = LocalLayoutDirection.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // The reset dialog must dismiss before Settings pops.
    BackHandler(enabled = showResetConfirmDialog) { showResetConfirmDialog = false }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                subtitle = { Text(text = stringResource(R.string.settings_subtitle)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onNavigateBack,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Appearance
            item(key = "section_appearance") {
                val itemCount = if (supportsDynamicColor) 2 else 1
                SettingsSection(title = stringResource(R.string.category_appearance)) {
                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_theme_mode),
                        subtitle = stringResource(R.string.setting_theme_mode_desc),
                        leadingIcon = AppIcons.BrightnessAuto,
                        index = 0,
                        count = itemCount
                    ) {
                        ConnectedChoiceRow(
                            options = ThemeMode.entries,
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

                    if (supportsDynamicColor) {
                        SettingsSwitchTile(
                            title = stringResource(R.string.setting_dynamic_color),
                            subtitle = stringResource(R.string.setting_dynamic_color_desc),
                            checked = settings.dynamicColor,
                            onCheckedChange = { repository.setDynamicColor(it) },
                            leadingIcon = AppIcons.Palette,
                            index = 1,
                            count = itemCount,
                            modifier = Modifier.testTag("setting_dynamic_color_switch")
                        )
                    }
                }
            }

            // 2. Clipboard & Automation
            item(key = "section_clipboard") {
                SettingsSection(title = stringResource(R.string.category_clipboard)) {
                    SettingsSwitchTile(
                        title = stringResource(R.string.setting_auto_paste),
                        subtitle = stringResource(R.string.setting_auto_paste_desc),
                        checked = settings.autoPasteOnLaunch,
                        onCheckedChange = { repository.setAutoPasteOnLaunch(it) },
                        leadingIcon = AppIcons.ContentPaste,
                        index = 0,
                        count = 2,
                        modifier = Modifier.testTag("setting_auto_paste_switch")
                    )

                    SettingsSwitchTile(
                        title = stringResource(R.string.setting_auto_copy),
                        subtitle = stringResource(R.string.setting_auto_copy_desc),
                        checked = settings.autoCopyOnConvert,
                        onCheckedChange = { repository.setAutoCopyOnConvert(it) },
                        leadingIcon = AppIcons.ContentCopy,
                        index = 1,
                        count = 2,
                        modifier = Modifier.testTag("setting_auto_copy_switch")
                    )
                }
            }

            // 3. Markdown Formatting
            item(key = "section_markdown") {
                SettingsSection(title = stringResource(R.string.category_markdown)) {
                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_heading_style),
                        subtitle = stringResource(R.string.setting_heading_style_desc),
                        leadingIcon = AppIcons.Title,
                        index = 0,
                        count = 5
                    ) {
                        ConnectedChoiceRow(
                            options = HeadingStyle.entries,
                            selected = settings.headingStyle,
                            onSelect = { repository.setHeadingStyle(it) },
                            labelFor = {
                                when (it) {
                                    HeadingStyle.ATX -> stringResource(R.string.option_heading_atx)
                                    HeadingStyle.SETEXT -> stringResource(R.string.option_heading_setext)
                                }
                            },
                            modifier = Modifier.testTag("setting_heading_style_segmented")
                        )
                    }

                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_bullet_marker),
                        subtitle = stringResource(R.string.setting_bullet_marker_desc),
                        leadingIcon = AppIcons.FormatListBulleted,
                        index = 1,
                        count = 5
                    ) {
                        ConnectedChoiceRow(
                            options = BulletMarker.entries,
                            selected = settings.bulletMarker,
                            onSelect = { repository.setBulletMarker(it) },
                            labelFor = {
                                when (it) {
                                    BulletMarker.HYPHEN -> stringResource(R.string.option_bullet_hyphen)
                                    BulletMarker.ASTERISK -> stringResource(R.string.option_bullet_asterisk)
                                    BulletMarker.PLUS -> stringResource(R.string.option_bullet_plus)
                                }
                            },
                            modifier = Modifier.testTag("setting_bullet_marker_segmented")
                        )
                    }

                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_code_fence),
                        subtitle = stringResource(R.string.setting_code_fence_desc),
                        leadingIcon = AppIcons.DataObject,
                        index = 2,
                        count = 5
                    ) {
                        ConnectedChoiceRow(
                            options = CodeFenceStyle.entries,
                            selected = settings.codeFenceStyle,
                            onSelect = { repository.setCodeFenceStyle(it) },
                            labelFor = {
                                when (it) {
                                    CodeFenceStyle.BACKTICKS -> stringResource(R.string.option_fence_backticks)
                                    CodeFenceStyle.TILDE -> stringResource(R.string.option_fence_tilde)
                                }
                            },
                            modifier = Modifier.testTag("setting_code_fence_segmented")
                        )
                    }

                    SettingsChoiceTile(
                        title = stringResource(R.string.setting_emphasis_marker),
                        subtitle = stringResource(R.string.setting_emphasis_marker_desc),
                        leadingIcon = AppIcons.FormatItalic,
                        index = 3,
                        count = 5
                    ) {
                        ConnectedChoiceRow(
                            options = EmphasisMarker.entries,
                            selected = settings.emphasisMarker,
                            onSelect = { repository.setEmphasisMarker(it) },
                            labelFor = {
                                when (it) {
                                    EmphasisMarker.ASTERISK -> stringResource(R.string.option_emphasis_asterisk)
                                    EmphasisMarker.UNDERSCORE -> stringResource(R.string.option_emphasis_underscore)
                                }
                            },
                            modifier = Modifier.testTag("setting_emphasis_marker_segmented")
                        )
                    }

                    SettingsSwitchTile(
                        title = stringResource(R.string.setting_soft_breaks),
                        subtitle = stringResource(R.string.setting_soft_breaks_desc),
                        checked = settings.preserveLineBreaks,
                        onCheckedChange = { repository.setPreserveLineBreaks(it) },
                        leadingIcon = AppIcons.WrapText,
                        index = 4,
                        count = 5,
                        modifier = Modifier.testTag("setting_soft_breaks_switch")
                    )
                }
            }

            // 4. About (separated from destructive action)
            item(key = "section_about") {
                SettingsSection(title = stringResource(R.string.category_about)) {
                    SettingsInfoTile(
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.app_about_desc),
                        leadingIcon = AppIcons.Info,
                        index = 0,
                        count = 1,
                        trailingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("app_version_chip")
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelMediumEmphasized,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    )
                }
            }

            // 5. Reset (own section, isolated destructive action)
            item(key = "section_reset") {
                SettingsSection(title = stringResource(R.string.category_reset)) {
                    SegmentedListItem(
                        onClick = { showResetConfirmDialog = true },
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        leadingContent = {
                            SettingsIconBadge(
                                icon = AppIcons.Refresh,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
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
                            .testTag("setting_reset_defaults_tile")
                    ) {
                        Text(
                            text = stringResource(R.string.setting_reset_defaults),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                SettingsIconBadge(
                    icon = AppIcons.Refresh,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_reset_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_reset_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.resetToDefaults()
                        showResetConfirmDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_reset),
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("cancel_reset_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            }
        )
    }
}
