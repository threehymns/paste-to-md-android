package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.ui.AppIcons
import com.example.ui.ExpressivePrimaryActionButton
import com.example.ui.ExpressiveSnackbarHost
import com.example.ui.PreviewKeyboardAnimatedContainer
import com.example.ui.PreviewKeyboardRevealOrientation
import com.example.ui.rememberPreviewImeTransitionState
import com.example.ui.theme.MonoFontFamily

private val PreviewMaxContentWidth = 720.dp
private val PreviewHorizontalMaxContentWidth = 1040.dp
private val PreviewHorizontalMinWidth = 560.dp
private val PreviewWideHorizontalThreshold = 720.dp
private val PreviewSidePanelCompactWidth = 224.dp
private val PreviewSidePanelExpandedWidth = 244.dp
private val PreviewHorizontalPadding = 16.dp
private val PreviewVerticalPadding = 12.dp
private val FooterOffscreenBuffer = 24.dp
private val KeyboardScrubPeekOffset = 16.dp

internal fun shouldUseHorizontalPreviewLayout(maxWidth: Dp, maxHeight: Dp): Boolean =
    maxWidth > maxHeight && maxWidth >= PreviewHorizontalMinWidth

@Composable
internal fun rememberIsImeVisible(): Boolean =
    rememberPreviewImeTransitionState().isKeyboardOpen

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
    copyInteractionSource: MutableInteractionSource? = null,
    pressShapeFraction: (() -> Float)? = null,
    isKeyboardOpen: Boolean? = null,
    imeDismissProgress: Float? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bodyTextStyle = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val liveImeState = rememberPreviewImeTransitionState()
    val effectiveKeyboardOpen = isKeyboardOpen ?: liveImeState.isKeyboardOpen
    val effectiveDismissProgress = if (isKeyboardOpen != null) {
        imeDismissProgress
    } else {
        imeDismissProgress ?: liveImeState.dismissProgress
    }
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
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.displayCutout),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { ExpressiveSnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val bottomInsetPadding = innerPadding.calculateBottomPadding()
        val endInsetPadding = innerPadding.calculateEndPadding(layoutDirection)
        val footerOffscreenClearancePx = remember(density, bottomInsetPadding) {
            with(density) {
                (bottomInsetPadding + PreviewVerticalPadding + FooterOffscreenBuffer).roundToPx()
            }
        }
        val sidePanelOffscreenClearancePx = remember(density, endInsetPadding) {
            with(density) {
                (endInsetPadding + PreviewHorizontalPadding + FooterOffscreenBuffer).roundToPx()
            }
        }
        val scrubPeekOffsetPx = remember(density) {
            with(density) { KeyboardScrubPeekOffset.roundToPx() }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = PreviewHorizontalPadding, vertical = PreviewVerticalPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isHorizontalLayout = shouldUseHorizontalPreviewLayout(maxWidth, maxHeight)

            if (isHorizontalLayout) {
                val sidePanelWidth = if (maxWidth >= PreviewWideHorizontalThreshold) {
                    PreviewSidePanelExpandedWidth
                } else {
                    PreviewSidePanelCompactWidth
                }

                Row(
                    modifier = Modifier
                        .widthIn(max = PreviewHorizontalMaxContentWidth)
                        .fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewEditorPane(
                        markdownText = markdownText,
                        isConverting = isConverting,
                        bodyTextStyle = bodyTextStyle,
                        syntaxHighlighter = syntaxHighlighter,
                        onMarkdownChange = onMarkdownChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    PreviewKeyboardAnimatedContainer(
                        isKeyboardOpen = effectiveKeyboardOpen,
                        imeDismissProgress = effectiveDismissProgress,
                        orientation = PreviewKeyboardRevealOrientation.Horizontal,
                        offscreenClearancePx = sidePanelOffscreenClearancePx,
                        scrubPeekOffsetPx = scrubPeekOffsetPx
                    ) { isActionsSettledVisible ->
                        var latestCopyButtonCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        LaunchedEffect(isActionsSettledVisible, latestCopyButtonCoords, onCopyButtonPlaced) {
                            val coords = latestCopyButtonCoords
                            if (isActionsSettledVisible && coords != null && coords.isAttached) {
                                onCopyButtonPlaced?.invoke(coords)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("preview_footer"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier
                                    .width(sidePanelWidth)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                            ) {
                                HorizontalFloatingToolbar(
                                    expanded = true,
                                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("preview_floating_toolbar")
                                ) {
                                    PreviewClearButton(
                                        enabled = markdownText.isNotEmpty(),
                                        onClick = onClear
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    PreviewSaveFileButton(
                                        enabled = markdownText.isNotEmpty(),
                                        onClick = onSaveFile
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    PreviewSettingsButton(onClick = onOpenSettings)
                                }

                                PreviewPasteAgainButton(
                                    enabled = !isConverting,
                                    onClick = onPasteAgain,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                )

                                PreviewCopyActionButton(
                                    enabled = markdownText.isNotEmpty(),
                                    onClick = onCopy,
                                    interactionSource = copyInteractionSource,
                                    pressShapeFraction = pressShapeFraction,
                                    isCopyButtonHidden = isCopyButtonHidden,
                                    onPositioned = if (onCopyButtonPlaced != null) {
                                        { coords ->
                                            latestCopyButtonCoords = coords
                                            if (isActionsSettledVisible) {
                                                onCopyButtonPlaced(coords)
                                            }
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = PreviewMaxContentWidth)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PreviewEditorPane(
                        markdownText = markdownText,
                        isConverting = isConverting,
                        bodyTextStyle = bodyTextStyle,
                        syntaxHighlighter = syntaxHighlighter,
                        onMarkdownChange = onMarkdownChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    PreviewKeyboardAnimatedContainer(
                        isKeyboardOpen = effectiveKeyboardOpen,
                        imeDismissProgress = effectiveDismissProgress,
                        orientation = PreviewKeyboardRevealOrientation.Vertical,
                        offscreenClearancePx = footerOffscreenClearancePx,
                        scrubPeekOffsetPx = scrubPeekOffsetPx
                    ) { isFooterSettledVisible ->
                        var latestCopyButtonCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        LaunchedEffect(isFooterSettledVisible, latestCopyButtonCoords, onCopyButtonPlaced) {
                            val coords = latestCopyButtonCoords
                            if (isFooterSettledVisible && coords != null && coords.isAttached) {
                                onCopyButtonPlaced?.invoke(coords)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_footer"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalFloatingToolbar(
                                expanded = true,
                                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("preview_floating_toolbar")
                            ) {
                                PreviewClearButton(
                                    enabled = markdownText.isNotEmpty(),
                                    onClick = onClear
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                PreviewPasteAgainButton(
                                    enabled = !isConverting,
                                    onClick = onPasteAgain
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                PreviewSaveFileButton(
                                    enabled = markdownText.isNotEmpty(),
                                    onClick = onSaveFile
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                PreviewSettingsButton(onClick = onOpenSettings)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            PreviewCopyActionButton(
                                enabled = markdownText.isNotEmpty(),
                                onClick = onCopy,
                                interactionSource = copyInteractionSource,
                                pressShapeFraction = pressShapeFraction,
                                isCopyButtonHidden = isCopyButtonHidden,
                                onPositioned = if (onCopyButtonPlaced != null) {
                                    { coords ->
                                        latestCopyButtonCoords = coords
                                        if (isFooterSettledVisible) {
                                            onCopyButtonPlaced(coords)
                                        }
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewEditorPane(
    markdownText: String,
    isConverting: Boolean,
    bodyTextStyle: TextStyle,
    syntaxHighlighter: MarkdownSyntaxHighlighter,
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme

    Column(modifier = modifier) {
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
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewClearButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewPasteAgainButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier.testTag("paste_again_button")
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewSaveFileButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.testTag("save_file_button")
    ) {
        Icon(
            imageVector = AppIcons.Download,
            contentDescription = stringResource(R.string.action_save_file)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewSettingsButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.testTag("settings_button")
    ) {
        Icon(
            imageVector = AppIcons.Settings,
            contentDescription = stringResource(R.string.settings_title)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewCopyActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource?,
    pressShapeFraction: (() -> Float)?,
    isCopyButtonHidden: () -> Boolean,
    onPositioned: ((LayoutCoordinates) -> Unit)?
) {
    ExpressivePrimaryActionButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        pressShapeFraction = pressShapeFraction,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonDefaults.MediumContainerHeight)
            .then(
                if (onPositioned != null) {
                    Modifier.onGloballyPositioned(onPositioned)
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
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
