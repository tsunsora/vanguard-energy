package app.vanguard.energy

import kotlin.test.*

private class MemoryStore(var value: String? = null, var fail: Boolean = false) : PreferenceStore {
    var writes = 0
    override fun read() = value
    override fun write(value: String): Boolean {
        writes++
        if (fail) return false
        this.value = value
        return true
    }
}

class ModelTest {
    @Test fun normalLimitAndIndependentPlayers() {
        val board = Board().adjust(0, 3).adjust(0, 9).adjust(1, 1)
        assertEquals(listOf(10, 1, 0, 0), board.energies)
        assertEquals(board, board.adjust(0, 1))
        assertEquals(board, board.adjust(1, -2))
    }
    @Test fun extendedLimitNeverOverflows() {
        val board = Board().setLimit(0, true).adjust(0, Int.MAX_VALUE)
        assertEquals(9999, board.energies[0])
        assertEquals(9999, board.adjust(0, Int.MAX_VALUE).energies[0])
        assertEquals(10, board.adjust(1, 99).energies[1])
        assertEquals(board, board.adjust(0, Int.MIN_VALUE))
    }
    @Test fun restoringLimitDoesNotDiscardEnergy() {
        val extended = Board().setLimit(0, true).adjust(0, 15)
        assertEquals(extended, extended.setLimit(0, false))
        val normal = extended.adjust(0, -5).setLimit(0, false)
        assertEquals(10, normal.energies[0])
        assertFalse(normal.preferences.players[0].allowAbove10)
    }
    @Test fun resetOnlyClearsOnePlayer() {
        val board = Board().adjust(0, 3).adjust(1, 6).adjust(3, 9).reset(1)
        assertEquals(listOf(3, 0, 0, 9), board.energies)
        assertEquals(board, board.reset(-1))
    }
    @Test fun seatingRetainsHiddenEnergyAndRespectsFlips() {
        val board = Board().setCount(4).adjust(3, 3).setCount(2)
        assertEquals(3, board.setCount(4).energies[3])
        assertTrue(board.rotated(0)); assertFalse(board.rotated(1))
        val four = board.setCount(4)
        assertTrue(four.rotated(1)); assertFalse(four.rotated(2))
        assertFalse(Board(Preferences(flips = listOf(true, false, false, false))).rotated(0))
        assertEquals(board, board.setCount(3))
    }
    @Test fun preferencesRoundTripWithoutGameCounts() {
        val store = MemoryStore()
        val session = TrackerSession(store)
        session.setCount(4); session.setLimit(3, true); session.adjust(3, 15); session.adjust(0, 3)
        assertEquals(2, store.writes)
        assertFalse(store.value!!.contains("energy"))
        val next = TrackerSession(store)
        assertEquals(4, next.board.preferences.count)
        assertTrue(next.board.preferences.players[3].allowAbove10)
        assertEquals(listOf(0, 0, 0, 0), next.board.energies)
    }
    @Test fun legacyMigrationProjectsOnlyPreferences() {
        val players = (0..3).joinToString(",") { id ->
            """{"id":$id,"name":"Saved $id","color":"${PLAYER_COLORS[id]}","energy":${if (id == 0) 15 else 3},"allowAbove10":${id == 0},"damage":6,"wins":9}"""
        }
        val prefs = PreferenceCodec.migrateLegacy("""{"board":{"count":3,"players":[$players]},"past":[1,2,3]}""", """{"facing":false,"flips":[true,false,true]}""")
        assertEquals(4, prefs.count)
        assertEquals("Saved 0", prefs.players[0].name)
        assertTrue(prefs.players[0].allowAbove10)
        assertEquals(listOf(true, false, true, false), prefs.flips)
        assertEquals(listOf(0, 0, 0, 0), Board(prefs).energies)
        val encoded = PreferenceCodec.encode(prefs)
        assertFalse(encoded.contains("damage")); assertFalse(encoded.contains("past")); assertFalse(encoded.contains("energy"))
        assertEquals(prefs, PreferenceCodec.decode(encoded))
    }
    @Test fun malformedAndOversizedSavesAreRejected() {
        listOf(null, "not json", "[]", "{}", "x".repeat(65_537), """{"version":99}""").forEach {
            assertEquals(Preferences(), PreferenceCodec.decode(it))
        }
        val valid = PreferenceCodec.encode(Preferences())
        assertEquals(Preferences(), PreferenceCodec.decode(valid.replace("#c9f76f", "javascript:alert(1)")))
        assertEquals(Preferences(), PreferenceCodec.decode(valid.replace("\"count\":2", "\"count\":\"4\"")))
        assertEquals(Preferences(), PreferenceCodec.decode(valid.replace("\"id\":0", "\"id\":3")))
    }
    @Test fun saveFailureWarnsWithoutChangingGame() {
        val store = MemoryStore(fail = true)
        val session = TrackerSession(store)
        session.adjust(0, 3); session.setLimit(0, true)
        assertEquals(3, session.board.energies[0])
        assertTrue(session.board.preferences.players[0].allowAbove10)
        assertNotNull(session.saveWarning)
        store.fail = false
        session.setCount(4)
        assertNull(session.saveWarning)
    }
    @Test fun holdDoesNotAlsoDecrementOnRelease() {
        val gesture = EnergyGesture(14f)
        gesture.start(10f, 20f)
        assertTrue(gesture.hold()); assertFalse(gesture.hold()); assertFalse(gesture.release())
    }
    @Test fun failedImportRemainsPendingAfterSettingsChanges() {
        val store = MemoryStore()
        val session = TrackerSession(store)
        session.migrationUnavailable()
        val warning = session.saveWarning
        session.setCount(4)
        session.setLimit(0, true)
        session.adjust(0, 15)
        session.savePreferences()
        assertEquals(15, session.board.energies[0])
        assertEquals(4, session.board.preferences.count)
        assertEquals(warning, session.saveWarning)
        assertEquals(0, store.writes)
        assertNull(store.read()) // Android still takes the legacy-import path on reopening.
    }
    @Test fun slidesAndLeavingCancelTapAndHold() {
        val gesture = EnergyGesture(14f)
        gesture.start(0f, 0f); gesture.move(15f, 0f, true)
        assertFalse(gesture.hold()); assertFalse(gesture.release())
        gesture.start(0f, 0f); gesture.move(1f, 0f, false)
        assertFalse(gesture.hold()); assertFalse(gesture.release())
        gesture.start(0f, 0f); gesture.move(5f, 0f, true)
        assertTrue(gesture.release())
    }
    @Test fun simultaneousGesturesRemainIndependent() {
        val first = EnergyGesture(14f); val second = EnergyGesture(14f)
        first.start(0f, 0f); second.start(0f, 0f)
        assertTrue(first.hold()); assertTrue(second.release()); assertFalse(first.release())
        second.start(0f, 0f); assertTrue(second.release())
    }
    @Test fun backgroundAndModalChangesInvalidateGestures() {
        val session = TrackerSession(MemoryStore())
        val before = session.gestureEpoch
        session.cancelGestures(); session.showSetup(true); session.showSetup(false); session.setCount(4)
        assertEquals(before + 4, session.gestureEpoch)
    }
    @Test fun undoRestoresCountsInOrder() {
        val session = TrackerSession(MemoryStore())
        session.adjust(0, 3); session.adjust(0, 1); session.reset(0)
        assertEquals("Player 1 reset", session.history.last().label)
        assertTrue(session.undo()); assertEquals(4, session.board.energies[0])
        assertTrue(session.undo()); assertEquals(3, session.board.energies[0])
        assertTrue(session.undo()); assertEquals(0, session.board.energies[0])
        assertFalse(session.undo())
    }
    @Test fun refusedChangesAreNotUndoStepsButBumpAtTheLimit() {
        val session = TrackerSession(MemoryStore())
        session.adjust(1, 10)
        assertFalse(session.adjust(1, 1)); assertFalse(session.adjust(0, -1)); assertFalse(session.reset(0))
        assertEquals(1, session.history.size)
        assertEquals(listOf(0, 1, 0, 0), session.limitBumps)
    }
    @Test fun newGameClearsEveryoneAndCanBeUndone() {
        val session = TrackerSession(MemoryStore())
        session.setCount(4); session.adjust(0, 3); session.adjust(3, 7)
        session.showMenu(true); session.newGame()
        assertEquals(listOf(0, 0, 0, 0), session.board.energies)
        assertFalse(session.menuOpen)
        session.undo()
        assertEquals(listOf(3, 0, 0, 7), session.board.energies)
    }
    @Test fun undoNeverExceedsALimitLoweredSince() {
        val board = Board().setLimit(0, true).adjust(0, 15)
        val lowered = board.adjust(0, -10).setLimit(0, false)
        assertEquals(10, lowered.restore(board.energies).energies[0])
    }
    @Test fun namesColorsAndHapticsPersist() {
        val store = MemoryStore()
        val session = TrackerSession(store)
        session.setName(1, "Aichi Sendou the Vanguard of Destiny")
        session.setColor(1, PLAYER_COLORS[5]); session.setColor(2, "#ff0000"); session.setHaptics(true)
        val next = TrackerSession(store).board.preferences
        assertEquals("Aichi Sendou the Vanguar", next.players[1].name)
        assertEquals(PLAYER_COLORS[5], next.players[1].color)
        assertEquals(PLAYER_COLORS[2], next.players[2].color)
        assertTrue(next.haptics)
        session.setName(0, "  ")
        assertEquals("Player 1", session.board.player(0).preference.label)
    }
    @Test fun savesWithoutHapticsStillLoad() {
        val older = PreferenceCodec.encode(Preferences(count = 4)).replace(",\"haptics\":false", "")
        assertFalse(older.contains("haptics"))
        assertEquals(Preferences(count = 4), PreferenceCodec.decode(older))
    }
    @Test fun menuAndSetupInvalidateGestures() {
        val session = TrackerSession(MemoryStore())
        val before = session.gestureEpoch
        session.showMenu(true); session.showSetup(true)
        assertFalse(session.menuOpen); assertTrue(session.setupOpen)
        assertEquals(before + 2, session.gestureEpoch)
    }
}
