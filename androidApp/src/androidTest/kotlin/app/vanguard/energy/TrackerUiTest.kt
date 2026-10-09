package app.vanguard.energy

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerUiTest {
    @get:Rule val ui = createEmptyComposeRule()
    private var activity: ActivityScenario<MainActivity>? = null
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun resetPreferences() {
        context.getSharedPreferences("vanguard-native-v1", 0).edit()
            .putString("preferences", PreferenceCodec.encode(Preferences())).commit()
    }
    @After fun closeActivity() { activity?.close() }
    private fun openSetup() { ui.onNodeWithTag("menu").performClick(); ui.onNodeWithTag("setup").performClick() }
    private fun launch() { activity = ActivityScenario.launch(MainActivity::class.java); ready() }
    private fun ready() { ui.waitUntil(10_000) { ui.onAllNodesWithTag("menu").fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        instrumentation.waitForIdleSync()
        // UI semantics update before the system compositor commits its next frame.
        Thread.sleep(100)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // Shared test images survive the test runner uninstalling the target APK.
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VanguardEnergyTests")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } else {
            val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }
    @Test fun controlsLimitsAndHoldReset() {
        launch()
        screenshot("two-players")
        ui.onNodeWithTag("charge-0").performClick()
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 3 energy")
        ui.onNodeWithTag("add-0").performTouchInput { click() }
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 4 energy")
        ui.onNodeWithTag("remove-0").performTouchInput { down(center) }
        ui.waitUntil(2_000) { ui.onNodeWithTag("energy-0").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] == listOf("Player 1: 0 energy") }
        ui.onNodeWithTag("remove-0").performTouchInput { up() }
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 0 energy")
        ui.onNodeWithTag("menu").performClick()
        ui.onNodeWithTag("menu-undo").performClick()
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 4 energy")
        openSetup()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val image = ui.onNodeWithText("Setup").captureToImage().toPixelMap()
            var readablePixels = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val color = image[x, y]
                if (color.red > .5f && color.green > .5f && color.blue > .5f) readablePixels++
            }
            assertTrue("Setup title must contrast with its dark background", readablePixels > 20)
        }
        ui.onNodeWithTag("count-4").performClick()
        ui.onNodeWithTag("limit-0").performClick()
        screenshot("setup")
        ui.onNodeWithTag("done").performClick()
        ui.onNodeWithTag("player-3").assertExists()
        repeat(5) { ui.onNodeWithTag("charge-0").performClick() }
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 19 energy")
        screenshot("four-players")
        openSetup()
        ui.onNodeWithTag("limit-0").assertIsNotEnabled()
    }
    @Test fun simultaneousPlayersAndCancelledHold() {
        launch()
        val p0 = ui.onNodeWithTag("add-0").fetchSemanticsNode().boundsInRoot.center
        val p1 = ui.onNodeWithTag("add-1").fetchSemanticsNode().boundsInRoot.center
        ui.onRoot().performTouchInput {
            down(0, p0); down(1, p1); up(0)
            down(2, p0); up(2); up(1)
        }
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 2 energy")
        ui.onNodeWithTag("energy-1").assertContentDescriptionEquals("Player 2: 1 energy")
        ui.onNodeWithTag("remove-0").performTouchInput { down(center); moveTo(center + Offset(100f, 0f)); up() }
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 2 energy")
        activity!!.recreate()
        ready()
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 2 energy")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        ui.waitUntil(5_000) { activity!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        launch()
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Player 1: 0 energy")
    }
    @OptIn(ExperimentalTestApi::class)
    @Test fun keyboardHoldResetsAndFocusLossCancels() {
        launch()
        ui.onNodeWithTag("charge-1").performClick()
        val remove = ui.onNodeWithTag("remove-1")
        remove.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        remove.performKeyInput { keyDown(Key.Spacebar) }
        ui.waitUntil(2_000) {
            ui.onNodeWithTag("energy-1").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] == listOf("Player 2: 0 energy")
        }
        remove.performKeyInput { keyUp(Key.Spacebar) }
        ui.onNodeWithTag("energy-1").assertContentDescriptionEquals("Player 2: 0 energy")

        ui.onNodeWithTag("charge-1").performClick()
        remove.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        remove.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        ui.onNodeWithTag("energy-1").assertContentDescriptionEquals("Player 2: 2 energy")
        // The hold ring animates while the key is down. Pause the test clock so moving focus
        // does not first wait out that animation, which takes as long as the hold itself.
        ui.mainClock.autoAdvance = false
        remove.performKeyInput { keyDown(Key.Spacebar) }
        ui.onNodeWithTag("add-1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        // The hold uses real elapsed time, independent of Compose's test clock.
        Thread.sleep(750)
        ui.onNodeWithTag("add-1").performKeyInput { keyUp(Key.Spacebar) }
        ui.mainClock.autoAdvance = true
        ui.onNodeWithTag("energy-1").assertContentDescriptionEquals("Player 2: 2 energy")
    }
    @Test fun largeTextFitsCountersAndChargeButtons() {
        launch()
        val store = object : PreferenceStore {
            override fun read(): String? = null
            override fun write(value: String) = true
        }
        val session = TrackerSession(store).also {
            it.setCount(4)
            it.adjust(0, 10)
            it.adjust(1, 10)
            it.setLimit(2, true); it.adjust(2, 9999)
            it.setLimit(3, true); it.adjust(3, 159)
        }
        activity!!.onActivity { host ->
            host.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.size(320.dp, 640.dp)) { EnergyApp(session) }
                }
            }
        }
        ui.waitForIdle()
        for (id in 0..3) {
            val counter = ui.onNodeWithTag("energy-$id").fetchSemanticsNode().boundsInRoot
            for (mark in listOf("add-mark", "remove-mark", "charge-$id")) {
                val control = ui.onNode(hasTestTag(mark) and hasAnyAncestor(hasTestTag("player-$id")), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertFalse("Player $id counter overlaps $mark with large text", counter.overlaps(control))
            }
            val layouts = mutableListOf<TextLayoutResult>()
            ui.onNode(hasText("+3") and hasAnyAncestor(hasTestTag("player-$id")), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertFalse("Player $id charge label is clipped", layouts.single().hasVisualOverflow)
        }
        screenshot("large-text")
    }
    @Test fun importsActualLegacyWebViewPreferences() {
        val latch = CountDownLatch(1)
        val players = (0..3).joinToString(",") { id ->
            """{"id":$id,"name":"Saved $id","color":"${PLAYER_COLORS[id]}","energy":15,"allowAbove10":true}"""
        }
        val raw = """{"board":{"count":4,"players":[$players]}}"""
        context.getSharedPreferences("vanguard-native-v1", 0).edit().clear().commit()
        instrumentation.runOnMainSync {
            @Suppress("SetJavaScriptEnabled")
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.blockNetworkLoads = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript("localStorage.setItem('vanguard-energy-v1', ${org.json.JSONObject.quote(raw)}); 'saved';") {
                        view.destroy(); latch.countDown()
                    }
                }
            }
            webView.loadDataWithBaseURL("https://localhost/", "<html></html>", "text/html", "UTF-8", null)
        }
        assertTrue("Legacy preferences seeded", latch.await(10, TimeUnit.SECONDS))
        launch()
        ui.onNodeWithTag("player-3").assertExists()
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Saved 0: 0 energy")
        repeat(5) { ui.onNodeWithTag("charge-0").performClick() }
        ui.onNodeWithTag("energy-0").assertContentDescriptionEquals("Saved 0: 15 energy")
    }
}
