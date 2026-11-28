package com.example

import androidx.compose.ui.geometry.Rect
import com.example.ui.computeMorphContentVisuals
import com.example.ui.interpolateMorphButtonRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryActionButtonTransitionMathTest {

    @Test
    fun interpolateMorphButtonRectSupportsEndpointsAndExpressiveOvershoot() {
        val pasteRect = Rect(left = 40f, top = 400f, right = 360f, bottom = 456f)
        val copyRect = Rect(left = 16f, top = 760f, right = 384f, bottom = 816f)

        val atStart = interpolateMorphButtonRect(pasteRect, copyRect, 0f)
        assertEquals(pasteRect, atStart)

        val atEnd = interpolateMorphButtonRect(pasteRect, copyRect, 1f)
        assertEquals(copyRect, atEnd)

        val atMid = interpolateMorphButtonRect(pasteRect, copyRect, 0.5f)
        assertEquals(28f, atMid.left, 0.01f)
        assertEquals(580f, atMid.top, 0.01f)
        assertEquals(344f, atMid.width, 0.01f)
        assertEquals(56f, atMid.height, 0.01f)

        val withOvershoot = interpolateMorphButtonRect(pasteRect, copyRect, 1.05f)
        assertTrue(withOvershoot.top > copyRect.top)
        assertTrue(withOvershoot.width > copyRect.width)

        val reverseOvershoot = interpolateMorphButtonRect(pasteRect, copyRect, -0.05f)
        assertTrue(reverseOvershoot.top < pasteRect.top)
        assertTrue(reverseOvershoot.width >= 1f)
        assertTrue(reverseOvershoot.height >= 1f)
    }

    @Test
    fun computeMorphContentVisualsClampsSafelyDuringOvershoot() {
        val start = computeMorphContentVisuals(0f)
        assertEquals(1f, start.pasteAlpha, 0.001f)
        assertEquals(0f, start.copyAlpha, 0.001f)
        assertEquals(1f, start.pasteScale, 0.001f)
        assertEquals(0f, start.midFlightArc, 0.001f)

        val mid = computeMorphContentVisuals(0.5f)
        assertEquals(1f, mid.midFlightArc, 0.001f)
        assertEquals(0f, mid.pasteAlpha, 0.001f)
        assertTrue(mid.copyAlpha in 0f..1f)

        val end = computeMorphContentVisuals(1f)
        assertEquals(0f, end.pasteAlpha, 0.001f)
        assertEquals(1f, end.copyAlpha, 0.001f)
        assertEquals(1f, end.copyScale, 0.001f)
        assertEquals(0f, end.midFlightArc, 0.001f)

        val overshotEnd = computeMorphContentVisuals(1.06f)
        assertEquals(0f, overshotEnd.pasteAlpha, 0.001f)
        assertEquals(1f, overshotEnd.copyAlpha, 0.001f)
        assertEquals(0f, overshotEnd.midFlightArc, 0.001f)

        val overshotStart = computeMorphContentVisuals(-0.06f)
        assertEquals(1f, overshotStart.pasteAlpha, 0.001f)
        assertEquals(0f, overshotStart.copyAlpha, 0.001f)
        assertEquals(0f, overshotStart.midFlightArc, 0.001f)
    }
}
