package app.vanguard.energy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.hypot

const val MAX_ENERGY = 9999
const val NAME_LENGTH = 24
/** The first four are the seat defaults; the last two are colour-blind-safe Okabe-Ito hues. */
val PLAYER_COLORS = listOf("#c9f76f", "#b6a0ff", "#ffac87", "#81dce5", "#e69f00", "#56b4e9")

data class PlayerPreference(
    val id: Int,
    val name: String = "Player ${id + 1}",
    val color: String = PLAYER_COLORS[id],
    val allowAbove10: Boolean = false,
) {
    /** A cleared name field still needs a readable label at the table. */
    val label: String get() = name.trim().ifEmpty { "Player ${id + 1}" }
}
data class Preferences(
    val count: Int = 2,
    val players: List<PlayerPreference> = List(4) { PlayerPreference(it) },
    val flips: List<Boolean> = List(4) { false },
    val haptics: Boolean = false,
)
data class Player(val preference: PlayerPreference, val energy: Int = 0) {
    val limit: Int get() = if (preference.allowAbove10) MAX_ENERGY else 10
}
data class Board(val preferences: Preferences = Preferences(), val energies: List<Int> = List(4) { 0 }) {
    fun player(id: Int) = Player(preferences.players[id], energies[id])
    fun rotated(id: Int) = (id < preferences.count / 2) != preferences.flips[id]
    fun adjust(id: Int, delta: Int): Board {
        if (id !in 0..3) return this
        // Long arithmetic prevents overflow even for untrusted callers.
        val next = energies[id].toLong() + delta.toLong()
        if (next < 0) return this
        val value = next.coerceAtMost(player(id).limit.toLong()).toInt()
        return copy(energies = energies.mapIndexed { index, energy -> if (index == id) value else energy })
    }
    fun reset(id: Int) = if (id !in 0..3) this else copy(
        energies = energies.mapIndexed { index, energy -> if (index == id) 0 else energy })
    fun resetAll() = copy(energies = List(4) { 0 })
    /** Restores earlier counts without exceeding limits changed since they were recorded. */
    fun restore(earlier: List<Int>) = if (earlier.size != 4) this else
        copy(energies = earlier.mapIndexed { id, energy -> energy.coerceIn(0, player(id).limit) })
    fun setCount(count: Int) = if (count == 2 || count == 4) copy(preferences = preferences.copy(count = count)) else this
    fun setLimit(id: Int, extended: Boolean): Board {
        if (id !in 0..3 || (!extended && energies[id] > 10)) return this
        return copy(preferences = preferences.copy(players = preferences.players.mapIndexed { index, player ->
            if (index == id) player.copy(allowAbove10 = extended) else player
        }))
    }
    fun setName(id: Int, name: String) = updatePlayer(id) { it.copy(name = name.take(NAME_LENGTH)) }
    fun setColor(id: Int, color: String) = if (color !in PLAYER_COLORS) this else updatePlayer(id) { it.copy(color = color) }
    fun setHaptics(enabled: Boolean) = copy(preferences = preferences.copy(haptics = enabled))
    private fun updatePlayer(id: Int, change: (PlayerPreference) -> PlayerPreference) = if (id !in 0..3) this else
        copy(preferences = preferences.copy(players = preferences.players.mapIndexed { index, player ->
            if (index == id) change(player) else player
        }))
}

/** One undoable change to the counts, labelled for the Undo button. */
data class UndoStep(val label: String, val energies: List<Int>)

/** Only preferences are stored; a fresh process/session always starts all counts at zero. */
interface PreferenceStore {
    fun read(): String?
    fun write(value: String): Boolean
}

object PreferenceCodec {
    private fun objectFrom(raw: String?): JsonObject? = try {
        if (raw == null || raw.length > 65_536) null else Json.parseToJsonElement(raw) as? JsonObject
    } catch (_: Exception) { null }
    private fun JsonElement?.string() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.integer() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    private fun JsonElement?.bool() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true

    private fun parsePlayers(board: JsonObject, legacy: Boolean): List<PlayerPreference>? {
        val players = board["players"] as? JsonArray ?: return null
        if (players.size != 4) return null
        return players.mapIndexed { id, element ->
            val player = element as? JsonObject ?: return null
            val name = player["name"].string() ?: return null
            val color = player["color"].string() ?: return null
            if (player["id"].integer() != id || name.length > NAME_LENGTH || color !in PLAYER_COLORS) return null
            val extended = player["allowAbove10"].bool()
            if (legacy) {
                val energy = player["energy"].integer() ?: return null
                if (energy !in 0..(if (extended) MAX_ENERGY else 10)) return null
            }
            PlayerPreference(id, name, color, extended)
        }
    }
    fun decode(raw: String?): Preferences {
        val obj = objectFrom(raw) ?: return Preferences()
        if (obj["version"].integer() != 1) return Preferences()
        val count = obj["count"].integer() ?: return Preferences()
        if (count != 2 && count != 4) return Preferences()
        val players = parsePlayers(obj, legacy = false) ?: return Preferences()
        val flips = obj["flips"] as? JsonArray
        return Preferences(count, players, List(4) { flips?.getOrNull(it).bool() }, obj["haptics"].bool())
    }
    fun migrateLegacy(rawBoard: String?, rawTable: String?): Preferences {
        val obj = objectFrom(rawBoard)?.get("board") as? JsonObject
        val count = obj?.get("count").integer()
        val players = obj?.let { parsePlayers(it, legacy = true) }
        val flips = objectFrom(rawTable)?.get("flips") as? JsonArray
        val defaults = Preferences(flips = List(4) { flips?.getOrNull(it).bool() })
        return if (count !in 1..4 || players == null) defaults
        else defaults.copy(count = if (count!! <= 2) 2 else 4, players = players)
    }
    fun encode(preferences: Preferences): String = buildJsonObject {
        put("version", 1)
        put("count", preferences.count)
        putJsonArray("players") {
            preferences.players.forEach { player -> add(buildJsonObject {
                put("id", player.id); put("name", player.name); put("color", player.color)
                put("allowAbove10", player.allowAbove10)
            }) }
        }
        putJsonArray("flips") { preferences.flips.forEach { add(JsonPrimitive(it)) } }
        put("haptics", preferences.haptics)
    }.toString()
}

class TrackerSession(private val store: PreferenceStore, preferences: Preferences = PreferenceCodec.decode(store.read())) {
    var board by mutableStateOf(Board(preferences))
        private set
    var setupOpen by mutableStateOf(false)
        private set
    var menuOpen by mutableStateOf(false)
        private set
    var history by mutableStateOf(emptyList<UndoStep>())
        private set
    /** Per-player count of taps refused at the limit, so the UI can shake and say why. */
    var limitBumps by mutableStateOf(List(4) { 0 })
        private set
    var saveWarning by mutableStateOf<String?>(null)
        private set
    var gestureEpoch by mutableStateOf(0)
        private set
    private var migrationPending = false

    private fun update(next: Board, undoLabel: String? = null): Boolean {
        if (next == board) return false
        if (undoLabel != null && next.energies != board.energies)
            history = (history + UndoStep(undoLabel, board.energies)).takeLast(50)
        val save = next.preferences != board.preferences
        board = next
        if (save) savePreferences()
        return true
    }
    private fun label(id: Int) = board.player(id.coerceIn(0, 3)).preference.label
    /** Returns whether the count changed; a refused increase at the limit is recorded as a bump. */
    fun adjust(id: Int, delta: Int): Boolean {
        val next = board.adjust(id, delta)
        val change = if (id in 0..3) next.energies[id] - board.energies[id] else 0
        val changed = update(next, "${label(id)} ${if (change > 0) "+" else "−"}${abs(change)}")
        if (!changed && delta > 0 && id in 0..3) limitBumps = limitBumps.mapIndexed { i, n -> if (i == id) n + 1 else n }
        return changed
    }
    fun reset(id: Int) = update(board.reset(id), "${label(id)} reset")
    fun newGame() { cancelGestures(); update(board.resetAll(), "New game"); menuOpen = false }
    fun undo(): Boolean {
        val last = history.lastOrNull() ?: return false
        cancelGestures()
        history = history.dropLast(1)
        board = board.restore(last.energies)
        return true
    }
    fun setCount(count: Int) { cancelGestures(); update(board.setCount(count)) }
    fun setLimit(id: Int, extended: Boolean) = update(board.setLimit(id, extended))
    fun setName(id: Int, name: String) = update(board.setName(id, name))
    fun setColor(id: Int, color: String) = update(board.setColor(id, color))
    fun setHaptics(enabled: Boolean) = update(board.setHaptics(enabled))
    fun showSetup(open: Boolean) { cancelGestures(); setupOpen = open; if (open) menuOpen = false }
    fun showMenu(open: Boolean) { cancelGestures(); menuOpen = open }
    fun cancelGestures() { gestureEpoch++ }
    fun savePreferences() {
        // A native save is also the upgrade-completion marker. Do not replace
        // unread legacy preferences with this game's temporary defaults.
        if (migrationPending) return
        val saved = try { store.write(PreferenceCodec.encode(board.preferences)) } catch (_: Exception) { false }
        saveWarning = if (saved) null else "Player settings could not be saved for the next game."
    }
    fun migrationUnavailable() {
        migrationPending = true
        saveWarning = "Previous settings could not be read. Changes last for this game; reopen to retry."
    }
}

/** One pointer per control, with independent state for every player's input. */
class EnergyGesture(private val slop: Float) {
    private var x = 0f
    private var y = 0f
    private var active = false
    private var cancelled = false
    private var held = false
    fun start(x: Float, y: Float) { this.x = x; this.y = y; active = true; cancelled = false; held = false }
    fun move(x: Float, y: Float, inside: Boolean) {
        if (!inside || hypot(x - this.x, y - this.y) > slop) cancelled = true
    }
    fun hold(): Boolean {
        if (!active || cancelled || held) return false
        held = true
        return true
    }
    fun release(): Boolean {
        val tap = active && !cancelled && !held
        active = false
        return tap
    }
    fun cancel() { active = false; cancelled = true }
    val canHold get() = active && !cancelled && !held
}
