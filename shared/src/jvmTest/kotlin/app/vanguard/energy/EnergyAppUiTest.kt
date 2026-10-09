package app.vanguard.energy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private class UiStore : PreferenceStore {
    var value: String? = null
    override fun read() = value
    override fun write(value: String): Boolean { this.value = value; return true }
}

/** Screenshots land in shared/build/ui-screenshots for visual review. */
@OptIn(ExperimentalTestApi::class)
class EnergyAppUiTest {
    private val scale = 2f
    private fun ComposeUiTest.capture(name: String) {
        waitForIdle()
        val bitmap = onRoot().captureToImage().asSkiaBitmap()
        val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File("build/ui-screenshots").apply { mkdirs() }.resolve("$name.png").writeBytes(png)
    }
    private fun ComposeUiTest.energy(id: Int) =
        onNodeWithTag("energy-$id").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
    private fun ComposeUiTest.show(session: TrackerSession, width: Int = 390, height: Int = 844, fontScale: Float = 1f) = setContent {
        CompositionLocalProvider(LocalDensity provides Density(scale, fontScale)) {
            Box(Modifier.size(width.dp, height.dp)) { EnergyApp(session) }
        }
    }

    @Test fun playMenuUndoAndSetup() = runDesktopComposeUiTest(390 * 2, 844 * 2) {
        val session = TrackerSession(UiStore())
        show(session)
        capture("1-fresh")
        onNodeWithTag("charge-1").performClick()
        onNodeWithTag("add-1").performTouchInput { click() }
        onNodeWithTag("add-1").performTouchInput { click() }
        onNodeWithTag("charge-0").performClick()
        onNodeWithTag("add-0").performTouchInput { click() }
        onNodeWithTag("add-0").performTouchInput { click() }
        onNodeWithTag("add-0").performTouchInput { click() }
        assertEquals("Player 2: 5 energy", energy(1))
        assertEquals("Player 1: 6 energy", energy(0))
        capture("2-mid-game-delta")

        mainClock.autoAdvance = false
        onNodeWithTag("remove-1").performTouchInput { down(center) }
        mainClock.advanceTimeBy(320)
        capture("3-holding-reset")
        mainClock.advanceTimeBy(400)
        onNodeWithTag("remove-1").performTouchInput { up() }
        mainClock.autoAdvance = true
        assertEquals("Player 2: 0 energy", energy(1))

        onNodeWithTag("menu").performClick()
        capture("4-menu")
        onNodeWithTag("menu-undo").performClick()
        assertEquals("Player 2: 5 energy", energy(1))

        onNodeWithTag("menu").performClick()
        onNodeWithTag("menu-new").performClick()
        capture("5-new-game-confirm")
        onNodeWithTag("confirm-new").performClick()
        assertEquals("Player 1: 0 energy", energy(0))
        onNodeWithTag("menu").performClick()
        onNodeWithTag("menu-undo").performClick()
        assertEquals("Player 1: 6 energy", energy(0))

        onNodeWithTag("menu").performClick()
        onNodeWithTag("setup").performClick()
        onNodeWithTag("name-0").performTextReplacement("Kai")
        onNodeWithTag("color-1-5").performClick()
        capture("6-setup")
        onNodeWithTag("count-4").performClick()
        onNodeWithTag("done").performClick()
        assertEquals("Kai: 6 energy", energy(0))
        assertEquals(PLAYER_COLORS[5], session.board.preferences.players[1].color)

        onNodeWithTag("charge-2").performClick(); onNodeWithTag("charge-2").performClick()
        onNodeWithTag("charge-2").performClick(); onNodeWithTag("add-2").performTouchInput { click() }
        mainClock.autoAdvance = false
        onNodeWithTag("add-2").performTouchInput { click() }
        mainClock.advanceTimeBy(200)
        capture("7-four-players-at-limit")
        mainClock.autoAdvance = true
        assertEquals(1, session.limitBumps[2])
    }

    @Test fun whoGoesFirstSettlesOnASeat() = runDesktopComposeUiTest(390 * 2, 844 * 2) {
        val session = TrackerSession(UiStore()).apply { setCount(4); adjust(0, 7); adjust(1, 3); adjust(2, 10); adjust(3, 5) }
        show(session)
        mainClock.autoAdvance = false
        onNodeWithTag("menu").performClick()
        mainClock.advanceTimeBy(100)
        onNodeWithTag("menu-first").performClick()
        mainClock.advanceTimeBy(3_000) // The cycle settles within ~2.6 s; the badge then stays for 4 s.
        onAllNodesWithText("GOES FIRST", useUnmergedTree = true).assertCountEquals(1)
        capture("8-goes-first")
    }

    @Test fun largeTextAndHugeCountsFit() = runDesktopComposeUiTest(320 * 2, 640 * 2) {
        val session = TrackerSession(UiStore()).apply {
            setCount(4); adjust(0, 10); adjust(1, 10)
            setLimit(2, true); adjust(2, 9999); setLimit(3, true); adjust(3, 159)
            setName(1, "Aichi Sendou the Vanguard")
        }
        show(session, 320, 640, fontScale = 2f)
        for (id in 0..3) {
            val counter = onNodeWithTag("energy-$id").fetchSemanticsNode().boundsInRoot
            for (tag in listOf("add-mark", "remove-mark", "charge-$id")) {
                val other = onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("player-$id")), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assert(!counter.overlaps(other)) { "Player $id counter overlaps $tag" }
            }
        }
        capture("9-large-text")
    }

    @Test fun keyboardHoldIsCancelledWhenFocusMoves() = runDesktopComposeUiTest(390 * 2, 844 * 2) {
        val session = TrackerSession(UiStore()).apply { adjust(1, 3) }
        show(session)
        val remove = onNodeWithTag("remove-1")
        remove.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        remove.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        assertEquals("Player 2: 2 energy", energy(1))
        // Holding animates a ring; with the clock paused, moving focus must cancel the hold.
        mainClock.autoAdvance = false
        remove.performKeyInput { keyDown(Key.Spacebar) }
        onNodeWithTag("add-1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        mainClock.advanceTimeBy(750)
        onNodeWithTag("add-1").performKeyInput { keyUp(Key.Spacebar) }
        mainClock.autoAdvance = true
        assertEquals("Player 2: 2 energy", energy(1))
    }
}
