package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.ui.AppIcons
import com.example.ui.ExpressiveSnackbarHost
import com.example.ui.theme.MonoFontFamily

private val PreviewMaxContentWidth = 720.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkdownPreviewScreen(
    markdownText: String,
    isConverting: Boolean = false,
    onMarkdownChange: (String) -> Unit,
    onClear: () -> Unit,
    onPasteAgain: () -> Unit,
    onSaveFile: () -> Unit,
    onCopy: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isCopyButtonHidden: () -> Boolean = { false },
    onCopyButtonPlaced: ((LayoutCoordinates) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val bodyTextStyle = MaterialTheme.typography.bodyLarge
    val syntaxHighlighter = remember(colorScheme, bodyTextStyle.fontSize) {
        MarkdownSyntaxHighlighter(
            colorScheme = colorScheme,
            baseFontSize = bodyTextStyle.fontSize,
            codeFontFamily = MonoFontFamily
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { ExpressiveSnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = PreviewMaxContentWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = isConverting,
                    enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                        expandVertically(motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(motionScheme.fastEffectsSpec()) +
                        shrinkVertically(motionScheme.fastSpatialSpec())
                ) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    TextField(
                        value = markdownText,
                        onValueChange = onMarkdownChange,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("markdown_text_field"),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.preview_placeholder),
                                style = bodyTextStyle
                            )
                        },
                        textStyle = bodyTextStyle.copy(lineHeight = TextUnit.Unspecified),
                        visualTransformation = syntaxHighlighter,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("preview_floating_toolbar")
                ) {
                    FilledTonalIconButton(
                        onClick = onClear,
                        enabled = markdownText.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.testTag("clear_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.Delete,
                            contentDescription = stringResource(R.string.action_clear)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledTonalButton(
                        onClick = onPasteAgain,
                        enabled = !isConverting,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("paste_again_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.action_paste_again),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledTonalIconButton(
                        onClick = onSaveFile,
                        enabled = markdownText.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.testTag("save_file_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.Download,
                            contentDescription = stringResource(R.string.action_save_file)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onCopy,
                    enabled = markdownText.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonDefaults.MediumContainerHeight)
                        .then(
                            if (onCopyButtonPlaced != null) {
                                Modifier.onGloballyPositioned(onCopyButtonPlaced)
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer { alpha = if (isCopyButtonHidden()) 0f else 1f }
                        .testTag("copy_button")
                ) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.MediumIconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.MediumIconSpacing))
                    Text(
                        text = stringResource(R.string.action_copy_markdown),
                        style = MaterialTheme.typography.titleMediumEmphasized
                    )
                }
            }
        }
    }
}
