package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.ui.AppIcons
import com.example.ui.ExpressiveSnackbarHost

private val PasteMaxContentWidth = 560.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasteToMarkdownScreen(
    isConverting: Boolean = false,
    onPaste: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { ExpressiveSnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
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

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .widthIn(max = PasteMaxContentWidth)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = isConverting,
                            transitionSpec = {
                                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                                    fadeOut(motionScheme.fastEffectsSpec())
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

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.paste_headline),
                    style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                        lineBreak = LineBreak.Heading
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.paste_supporting_text),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineBreak = LineBreak.Paragraph.copy(
                            strategy = LineBreak.Strategy.Balanced
                        )
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onPaste,
                    enabled = !isConverting,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                    modifier = Modifier
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
    }
}
