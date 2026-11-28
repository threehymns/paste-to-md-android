package com.example

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutBreakpointTest {

    @Test
    fun landscapePhoneUsesHorizontalLayoutOnBothScreens() {
        assertTrue(shouldUseHorizontalPasteLayout(maxWidth = 840.dp, maxHeight = 360.dp))
        assertTrue(shouldUseHorizontalPreviewLayout(maxWidth = 840.dp, maxHeight = 360.dp))

        assertTrue(shouldUseHorizontalPasteLayout(maxWidth = 560.dp, maxHeight = 340.dp))
        assertTrue(shouldUseHorizontalPreviewLayout(maxWidth = 560.dp, maxHeight = 340.dp))
    }

    @Test
    fun portraitAndNarrowSplitScreenKeepVerticalLayoutOnBothScreens() {
        assertFalse(shouldUseHorizontalPasteLayout(maxWidth = 412.dp, maxHeight = 915.dp))
        assertFalse(shouldUseHorizontalPreviewLayout(maxWidth = 412.dp, maxHeight = 915.dp))

        assertFalse(shouldUseHorizontalPasteLayout(maxWidth = 480.dp, maxHeight = 400.dp))
        assertFalse(shouldUseHorizontalPreviewLayout(maxWidth = 480.dp, maxHeight = 400.dp))

        assertFalse(shouldUseHorizontalPasteLayout(maxWidth = 600.dp, maxHeight = 600.dp))
        assertFalse(shouldUseHorizontalPreviewLayout(maxWidth = 600.dp, maxHeight = 600.dp))
    }
}
