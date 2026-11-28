package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.AppIcons
import com.example.ui.ExpressiveSnackbarHost
import com.example.ui.navigation.PrimaryActionButton
import com.example.ui.navigation.linkToPrimaryActionPress
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MotionTokens

private val PasteMaxContentWidth = 560.dp
private val PasteHorizontalMaxContentWidth = 680.dp
private val PasteHorizontalMinWidth = 560.dp
private val PasteWideHorizontalThreshold = 720.dp
private val PasteHeroCompactSize = 120.dp
private val PasteHeroWideSize = 136.dp

internal fun shouldUseHorizontalPasteLayout(maxWidth: Dp, maxHeight: Dp): Boolean =
    maxWidth > maxHeight && maxWidth >= PasteHorizontalMinWidth

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasteToMarkdownScreen(
    isConverting: Boolean = false,
    onPaste: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.displayCutout),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { ExpressiveSnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val isHorizontalLayout = shouldUseHorizontalPasteLayout(maxWidth, maxHeight)

            if (isHorizontalLayout) {
                val isWideHorizontal = maxWidth >= PasteWideHorizontalThreshold
                val heroSize = if (isWideHorizontal) PasteHeroWideSize else PasteHeroCompactSize
                val horizontalGap = if (isWideHorizontal) 40.dp else 28.dp

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = PasteHorizontalMaxContentWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        space = horizontalGap,
                        alignment = Alignment.CenterHorizontally
                    )
                ) {
                    PasteHeroBadge(
                        isConverting = isConverting,
                        badgeSize = heroSize
                    )

                    PasteActionSection(
                        isConverting = isConverting,
                        onPaste = onPaste,
                        horizontalAlignment = Alignment.Start,
                        textAlign = TextAlign.Start,
                        verticalSpacing = 10.dp,
                        buttonExtraTopPadding = 6.dp,
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .weight(1f, fill = false)
                            .testTag("paste_content_column")
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = PasteMaxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PasteHeroBadge(
                        isConverting = isConverting,
                        badgeSize = PasteHeroCompactSize
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    PasteActionSection(
                        isConverting = isConverting,
                        onPaste = onPaste,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        textAlign = TextAlign.Center,
                        verticalSpacing = 16.dp,
                        buttonExtraTopPadding = 28.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paste_content_column")
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onOpenSettings,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = AppIcons.Settings,
                    contentDescription = stringResource(R.string.settings_title)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasteHeroBadge(
    isConverting: Boolean,
    badgeSize: Dp,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current

    Surface(
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .size(badgeSize)
            .linkToPrimaryActionPress(tiltDegrees = -3f)
            .testTag("paste_hero_icon")
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = isConverting,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(MotionTokens.reducedFadeSpec()) togetherWith
                            fadeOut(MotionTokens.reducedFadeSpec())
                    } else {
                        (fadeIn(MotionTokens.fastUiSpec()) +
                            scaleIn(
                                initialScale = MotionTokens.ContentSwapMinScale,
                                animationSpec = MotionTokens.fastUiSpec()
                            )) togetherWith
                            (fadeOut(MotionTokens.quickExitSpec()) +
                                scaleOut(
                                    targetScale = MotionTokens.ContentSwapMinScale,
                                    animationSpec = MotionTokens.quickExitSpec()
                                ))
                    }
                },
                label = "paste_hero_indicator"
            ) { converting ->
                if (converting) {
                    LoadingIndicator(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(64.dp)
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasteActionSection(
    isConverting: Boolean,
    onPaste: () -> Unit,
    horizontalAlignment: Alignment.Horizontal,
    textAlign: TextAlign,
    verticalSpacing: Dp,
    buttonExtraTopPadding: Dp,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        Text(
            text = stringResource(R.string.paste_headline),
            style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                lineBreak = LineBreak.Heading
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = textAlign
        )

        Text(
            text = stringResource(R.string.paste_supporting_text),
            style = MaterialTheme.typography.bodyLarge.copy(
                lineBreak = LineBreak.Paragraph.copy(
                    strategy = LineBreak.Strategy.Balanced
                )
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier.widthIn(max = 360.dp)
        )

        PrimaryActionButton(
            onClick = onPaste,
            enabled = !isConverting,
            modifier = Modifier
                .padding(top = buttonExtraTopPadding)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MediumContainerHeight)
                .testTag("paste_button")
        ) {
            Icon(
                imageVector = AppIcons.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.MediumIconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.MediumIconSpacing))
            Text(
                text = if (isConverting) {
                    stringResource(R.string.action_converting)
                } else {
                    stringResource(R.string.action_paste)
                },
                style = MaterialTheme.typography.titleMediumEmphasized
            )
        }

        AnimatedVisibility(
            visible = isConverting,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                expandVertically(motionScheme.defaultSpatialSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()) +
                shrinkVertically(motionScheme.fastSpatialSpec())
        ) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
            )
        }
    }
}
