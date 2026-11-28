package com.example

import com.example.ui.ImeDismissProgressTracker
import com.example.ui.PreviewImeTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeDismissProgressTrackerTest {

    @Test
    fun openingKeyboardNeverProducesDismissProgress() {
        val tracker = ImeDismissProgressTracker()

        assertEquals(
            PreviewImeTransitionState(isKeyboardOpen = false, dismissProgress = null),
            tracker.update(isImeVisible = false, imeBottomPx = 0, imeTargetBottomPx = 0)
        )
        assertEquals(
            PreviewImeTransitionState(isKeyboardOpen = true, dismissProgress = null),
            tracker.update(isImeVisible = true, imeBottomPx = 0, imeTargetBottomPx = 900)
        )
        assertEquals(
            PreviewImeTransitionState(isKeyboardOpen = true, dismissProgress = null),
            tracker.update(isImeVisible = true, imeBottomPx = 300, imeTargetBottomPx = 900)
        )
        assertEquals(
            PreviewImeTransitionState(isKeyboardOpen = true, dismissProgress = null),
            tracker.update(isImeVisible = true, imeBottomPx = 900, imeTargetBottomPx = 900)
        )
    }

    @Test
    fun predictiveBackScrubTracksProgressAndCleanlyCancels() {
        val tracker = ImeDismissProgressTracker()
        tracker.update(isImeVisible = true, imeBottomPx = 900, imeTargetBottomPx = 900)

        // Finger drags back: IME lowers while still nominally visible.
        val peek1 = tracker.update(isImeVisible = true, imeBottomPx = 810, imeTargetBottomPx = 900)
        assertTrue(peek1.isKeyboardOpen)
        assertEquals(0.10f, peek1.dismissProgress!!, 0.001f)

        val peek2 = tracker.update(isImeVisible = true, imeBottomPx = 720, imeTargetBottomPx = 900)
        assertTrue(peek2.isKeyboardOpen)
        assertEquals(0.20f, peek2.dismissProgress!!, 0.001f)

        // User cancels back swipe: keyboard returns upward to full open height.
        val reversing = tracker.update(isImeVisible = true, imeBottomPx = 855, imeTargetBottomPx = 900)
        assertTrue(reversing.isKeyboardOpen)
        assertEquals(0.05f, reversing.dismissProgress!!, 0.001f)

        val restoredOpen = tracker.update(isImeVisible = true, imeBottomPx = 900, imeTargetBottomPx = 900)
        assertTrue(restoredOpen.isKeyboardOpen)
        assertNull(restoredOpen.dismissProgress)
    }

    @Test
    fun predictiveBackScrubHandsOffSeamlesslyOnCommit() {
        val tracker = ImeDismissProgressTracker()
        tracker.update(isImeVisible = true, imeBottomPx = 900, imeTargetBottomPx = 900)

        // Scrub to 20% while finger is held.
        val scrub = tracker.update(isImeVisible = true, imeBottomPx = 720, imeTargetBottomPx = 900)
        assertEquals(0.20f, scrub.dismissProgress!!, 0.001f)

        // Commit: visibility flips false and IME glides down from 720 -> 0.
        val committed = tracker.update(isImeVisible = false, imeBottomPx = 720, imeTargetBottomPx = 0)
        assertFalse(committed.isKeyboardOpen)
        assertEquals(0.20f, committed.dismissProgress!!, 0.001f)

        val midExit = tracker.update(isImeVisible = false, imeBottomPx = 450, imeTargetBottomPx = 0)
        assertFalse(midExit.isKeyboardOpen)
        assertEquals(0.50f, midExit.dismissProgress!!, 0.001f)

        val settledClosed = tracker.update(isImeVisible = false, imeBottomPx = 0, imeTargetBottomPx = 0)
        assertFalse(settledClosed.isKeyboardOpen)
        assertNull(settledClosed.dismissProgress)
    }

    @Test
    fun keyboardHeightResizesDoNotTriggerFalseRevealPeek() {
        val tracker = ImeDismissProgressTracker()
        tracker.update(isImeVisible = true, imeBottomPx = 900, imeTargetBottomPx = 900)

        // Shrink (e.g. switching to a shorter symbol/emoji panel): target updates to 840.
        val shrinking = tracker.update(isImeVisible = true, imeBottomPx = 870, imeTargetBottomPx = 840)
        assertTrue(shrinking.isKeyboardOpen)
        assertNull(shrinking.dismissProgress)

        val settledShorter = tracker.update(isImeVisible = true, imeBottomPx = 840, imeTargetBottomPx = 840)
        assertTrue(settledShorter.isKeyboardOpen)
        assertNull(settledShorter.dismissProgress)

        // Grow taller to 960.
        val growing = tracker.update(isImeVisible = true, imeBottomPx = 900, imeTargetBottomPx = 960)
        assertTrue(growing.isKeyboardOpen)
        assertNull(growing.dismissProgress)

        val settledTaller = tracker.update(isImeVisible = true, imeBottomPx = 960, imeTargetBottomPx = 960)
        assertTrue(settledTaller.isKeyboardOpen)
        assertNull(settledTaller.dismissProgress)
    }

    @Test
    fun standardDismissStartsTrackingImmediatelyBeforeImeReachesZero() {
        val tracker = ImeDismissProgressTracker()
        tracker.update(isImeVisible = true, imeBottomPx = 800, imeTargetBottomPx = 800)

        // Frame 0 of dismiss: target is 0, IME still at 800px.
        val start = tracker.update(isImeVisible = false, imeBottomPx = 800, imeTargetBottomPx = 0)
        assertFalse(start.isKeyboardOpen)
        assertEquals(0f, start.dismissProgress!!, 0.001f)

        // Mid-dismiss: IME at 200px -> 75% revealed while keyboard is still going down.
        val mid = tracker.update(isImeVisible = false, imeBottomPx = 200, imeTargetBottomPx = 0)
        assertFalse(mid.isKeyboardOpen)
        assertEquals(0.75f, mid.dismissProgress!!, 0.001f)

        // Done.
        val done = tracker.update(isImeVisible = false, imeBottomPx = 0, imeTargetBottomPx = 0)
        assertFalse(done.isKeyboardOpen)
        assertNull(done.dismissProgress)
    }

    @Test
    fun instantaneousDismissFallsBackToNonScrubbedEntrance() {
        val tracker = ImeDismissProgressTracker()
        tracker.update(isImeVisible = true, imeBottomPx = 800, imeTargetBottomPx = 800)

        val closed = tracker.update(isImeVisible = false, imeBottomPx = 0, imeTargetBottomPx = 0)
        assertFalse(closed.isKeyboardOpen)
        assertNull(closed.dismissProgress)
    }

    @Test
    fun devicesWithoutTargetInsetsStillTrackPredictiveBackDescent() {
        val tracker = ImeDismissProgressTracker()
        tracker.update(isImeVisible = true, imeBottomPx = 0, imeTargetBottomPx = 0)
        val open = tracker.update(isImeVisible = true, imeBottomPx = 900, imeTargetBottomPx = 0)
        assertTrue(open.isKeyboardOpen)
        assertNull(open.dismissProgress)

        val scrub = tracker.update(isImeVisible = true, imeBottomPx = 675, imeTargetBottomPx = 0)
        assertTrue(scrub.isKeyboardOpen)
        assertNotNull(scrub.dismissProgress)
        assertEquals(0.25f, scrub.dismissProgress!!, 0.001f)
    }
}
