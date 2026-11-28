package com.example

import com.example.data.AppSettings
import com.example.data.HeadingStyle
import com.example.session.ClipboardAdapter
import com.example.session.ClipboardPayload
import com.example.session.ConversionSession
import com.example.session.DocumentExporter
import com.example.session.SessionEffect
import com.example.session.SessionFeedback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversionSessionTest {

    private class InMemoryClipboardAdapter(
        var payload: ClipboardPayload = ClipboardPayload.Empty
    ) : ClipboardAdapter {
        val writtenPlainTexts = mutableListOf<String>()

        override fun read(): ClipboardPayload = payload

        override fun writePlainText(text: String) {
            writtenPlainTexts += text
            payload = ClipboardPayload.PlainText(text)
        }
    }

    private class InMemoryDocumentExporter(
        var shouldSucceed: Boolean = true
    ) : DocumentExporter {
        val writes = mutableListOf<Pair<String, String>>()

        override suspend fun write(targetId: String, markdown: String): Boolean {
            writes += targetId to markdown
            return shouldSucceed
        }
    }

    private fun runSessionTest(
        block: suspend TestScope.(sessionScope: CoroutineScope) -> Unit
    ) = runTest {
        val sessionJob = SupervisorJob()
        val sessionScope = CoroutineScope(coroutineContext + sessionJob)
        try {
            block(sessionScope)
        } finally {
            sessionJob.cancel()
        }
    }

    private fun TestScope.collectEffects(session: ConversionSession): MutableList<SessionEffect> {
        val effects = mutableListOf<SessionEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.effects.collect { effects += it }
        }
        return effects
    }

    @Test
    fun htmlPasteConvertsWithSettingsSnapshotAndOpensPreviewOnlyWhenRequested() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings(headingStyle = HeadingStyle.SETEXT))
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.Html("<h1>Title</h1>"))
        val exporter = InMemoryDocumentExporter()
        val observedSettings = mutableListOf<AppSettings>()
        val gate = CompletableDeferred<String>()

        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = exporter,
            htmlConverter = { _, settings ->
                observedSettings += settings
                gate.await()
            }
        )
        val effects = collectEffects(session)

        session.paste(openPreview = true)
        assertTrue(session.state.value.isConverting)
        assertEquals("", session.state.value.markdownText)

        // Mutating settings mid-flight must not alter the snapshot captured when paste started.
        settingsFlow.value = AppSettings(headingStyle = HeadingStyle.ATX)
        gate.complete("Title\n=====")
        advanceUntilIdle()

        assertFalse(session.state.value.isConverting)
        assertEquals("Title\n=====", session.state.value.markdownText)
        assertEquals(listOf(HeadingStyle.SETEXT), observedSettings.map { it.headingStyle })
        assertEquals(listOf(SessionEffect.OpenPreview), effects)

        // Paste in place (openPreview = false) updates Markdown without emitting OpenPreview.
        val secondGate = CompletableDeferred<String>()
        val inPlaceSession = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = exporter,
            htmlConverter = { _, _ -> secondGate.await() }
        )
        val inPlaceEffects = collectEffects(inPlaceSession)
        inPlaceSession.paste(openPreview = false)
        secondGate.complete("# Updated")
        advanceUntilIdle()

        assertEquals("# Updated", inPlaceSession.state.value.markdownText)
        assertTrue(inPlaceEffects.isEmpty())
    }

    @Test
    fun htmlAutoCopyWritesMarkdownAndEmitsConvertedAndCopiedFeedback() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings(autoCopyOnConvert = true))
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.Html("<p>Hello</p>"))
        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { _, _ -> "Hello" }
        )
        val effects = collectEffects(session)

        session.paste(openPreview = true)
        advanceUntilIdle()

        assertEquals("Hello", session.state.value.markdownText)
        assertEquals(listOf("Hello"), clipboard.writtenPlainTexts)
        assertEquals(
            listOf(
                SessionEffect.ShowFeedback(SessionFeedback.ConvertedAndCopied),
                SessionEffect.OpenPreview
            ),
            effects
        )
    }

    @Test
    fun plainTextPasteBypassesConverterAndRespectsAutoCopy() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings(autoCopyOnConvert = true))
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.PlainText("Plain note"))
        var converterCalled = false
        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { _, _ ->
                converterCalled = true
                "unexpected"
            }
        )
        val effects = collectEffects(session)

        session.paste(openPreview = true)
        advanceUntilIdle()

        assertFalse(converterCalled)
        assertEquals("Plain note", session.state.value.markdownText)
        assertEquals(listOf("Plain note"), clipboard.writtenPlainTexts)
        assertEquals(
            listOf(
                SessionEffect.ShowFeedback(SessionFeedback.Copied),
                SessionEffect.OpenPreview
            ),
            effects
        )
    }

    @Test
    fun emptyAndNonTextClipboardsEmitTypedFeedbackWithoutMutatingDocument() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings())
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.Empty)
        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { html, _ -> html }
        )
        session.updateMarkdown("Keep me")
        val effects = collectEffects(session)

        session.paste(openPreview = true)
        clipboard.payload = ClipboardPayload.NoText
        session.paste(openPreview = true)
        advanceUntilIdle()

        assertEquals("Keep me", session.state.value.markdownText)
        assertEquals(
            listOf(
                SessionEffect.ShowFeedback(SessionFeedback.ClipboardEmpty),
                SessionEffect.ShowFeedback(SessionFeedback.ClipboardNoText)
            ),
            effects
        )
    }

    @Test
    fun htmlConversionErrorPreservesExistingMarkdownAndSkipsAutoCopy() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings(autoCopyOnConvert = true))
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.Html("<p>bad</p>"))
        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { _, _ -> throw IllegalStateException("Malformed HTML") }
        )
        session.updateMarkdown("Existing Markdown")
        val effects = collectEffects(session)

        session.paste(openPreview = false)
        advanceUntilIdle()

        assertFalse(session.state.value.isConverting)
        assertEquals("Existing Markdown", session.state.value.markdownText)
        assertTrue(clipboard.writtenPlainTexts.isEmpty())
        assertEquals(
            listOf(SessionEffect.ShowFeedback(SessionFeedback.ConversionError("Malformed HTML"))),
            effects
        )
    }

    @Test
    fun cancellingOrEndingInFlightConversionDropsStaleResultAndClearsSessionOnEnd() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings())
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.Html("<p>slow</p>"))
        val gate = CompletableDeferred<String>()
        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { _, _ -> gate.await() }
        )
        session.updateMarkdown("First Doc")
        val effects = collectEffects(session)

        session.paste(openPreview = true)
        assertTrue(session.state.value.isConverting)

        session.endSession()
        assertFalse(session.state.value.isConverting)
        assertEquals("", session.state.value.markdownText)

        gate.complete("Stale Result")
        advanceUntilIdle()

        assertEquals("", session.state.value.markdownText)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun newerPasteCancelsOlderInFlightConversion() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings())
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.Html("<p>first</p>"))
        val firstGate = CompletableDeferred<String>()
        val secondGate = CompletableDeferred<String>()

        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { html, _ ->
                if (html == "<p>first</p>") firstGate.await() else secondGate.await()
            }
        )
        val effects = collectEffects(session)

        session.paste(openPreview = true)
        runCurrent()
        clipboard.payload = ClipboardPayload.Html("<p>second</p>")
        session.paste(openPreview = true)
        runCurrent()

        firstGate.complete("First")
        secondGate.complete("Second")
        advanceUntilIdle()

        assertEquals("Second", session.state.value.markdownText)
        assertEquals(listOf(SessionEffect.OpenPreview), effects)
    }

    @Test
    fun autoPasteWaitsForWindowFocusAndPasteScreenAndRunsOnlyOnce() = runSessionTest { sessionScope ->
        val settingsFlow = MutableStateFlow(AppSettings(autoPasteOnLaunch = false))
        val clipboard = InMemoryClipboardAdapter(ClipboardPayload.PlainText("Focused Paste"))
        val session = ConversionSession(
            scope = sessionScope,
            settings = settingsFlow,
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { html, _ -> html }
        )
        val effects = collectEffects(session)

        // Setting enabled while unfocused -> must wait.
        session.onLaunchReadinessChanged(hasWindowFocus = false, isOnPasteScreen = true)
        settingsFlow.value = AppSettings(autoPasteOnLaunch = true)
        runCurrent()
        assertEquals("", session.state.value.markdownText)
        assertTrue(effects.isEmpty())

        // Focus gained while off Paste screen (e.g. Settings) -> must wait.
        session.onLaunchReadinessChanged(hasWindowFocus = true, isOnPasteScreen = false)
        assertEquals("", session.state.value.markdownText)

        // Focus + Paste screen ready -> triggers once.
        session.onLaunchReadinessChanged(hasWindowFocus = true, isOnPasteScreen = true)
        advanceUntilIdle()
        assertEquals("Focused Paste", session.state.value.markdownText)
        assertEquals(listOf(SessionEffect.OpenPreview), effects)

        // Ending session and re-focusing Paste must not auto-paste a second time.
        effects.clear()
        session.endSession()
        clipboard.payload = ClipboardPayload.PlainText("Second Clip")
        session.onLaunchReadinessChanged(hasWindowFocus = false, isOnPasteScreen = true)
        session.onLaunchReadinessChanged(hasWindowFocus = true, isOnPasteScreen = true)
        advanceUntilIdle()

        assertEquals("", session.state.value.markdownText)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun manualCopyWritesClipboardWithNoFeedbackEffect() = runSessionTest { sessionScope ->
        val clipboard = InMemoryClipboardAdapter()
        val session = ConversionSession(
            scope = sessionScope,
            settings = MutableStateFlow(AppSettings()),
            clipboard = clipboard,
            exporter = InMemoryDocumentExporter(),
            htmlConverter = { html, _ -> html }
        )
        val effects = collectEffects(session)

        session.updateMarkdown("# Copied")
        session.copy()
        advanceUntilIdle()

        assertEquals(listOf("# Copied"), clipboard.writtenPlainTexts)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun exportWritesCurrentMarkdownAndEmitsSavedOrFailedFeedback() = runSessionTest { sessionScope ->
        val exporter = InMemoryDocumentExporter(shouldSucceed = true)
        val session = ConversionSession(
            scope = sessionScope,
            settings = MutableStateFlow(AppSettings()),
            clipboard = InMemoryClipboardAdapter(),
            exporter = exporter,
            htmlConverter = { html, _ -> html }
        )
        val effects = collectEffects(session)

        session.updateMarkdown("# Saved Doc")
        session.export("content://docs/converted.md")
        advanceUntilIdle()

        exporter.shouldSucceed = false
        session.export("content://docs/failing.md")
        advanceUntilIdle()

        assertEquals(
            listOf(
                "content://docs/converted.md" to "# Saved Doc",
                "content://docs/failing.md" to "# Saved Doc"
            ),
            exporter.writes
        )
        assertEquals(
            listOf(
                SessionEffect.ShowFeedback(SessionFeedback.FileSaved),
                SessionEffect.ShowFeedback(SessionFeedback.FileSaveFailed)
            ),
            effects
        )
    }
}
