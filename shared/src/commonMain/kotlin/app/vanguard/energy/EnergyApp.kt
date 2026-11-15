package app.vanguard.energy

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.vanguard.energy.resources.Res
import app.vanguard.energy.resources.barlow_semibold
import app.vanguard.energy.resources.dm_sans
import org.jetbrains.compose.resources.Font
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Background = Color(0xff0b0d10)
private val ZoneBase = Color(0xff121417)
private val Surface = Color(0xff1a1f23)
private val Raised = Color(0xff22282c)
private val Line = Color(0xff30383c)
/** Outline for controls that must read as controls: 3.6:1 on Surface. */
private val Outline = Color(0xff6b777d)
private val Hairline = Color(0x14e8eee6)
private val Ink = Color(0xffe8eee6)
private val InkSecondary = Color(0xffa9b3ad)
private val Muted = Color(0xff8f9b94)
/** Chrome is neutral: Lime and Orange are seat colours, so the app's own controls never use them. */
private val OnInk = Color(0xff101315)
private val Warn = Color(0xffffb36b)
private val Scrim = Color(0xe6050709)
/** One curve for every decorative transition; the timed hold stays linear. */
private val Ease = CubicBezierEasing(.22f, 1f, .36f, 1f)
/** Table chrome stops growing here so it can never outweigh the count; Setup still scales fully. */
private const val TABLE_TEXT_SCALE = 1.3f
private const val HOLD_MS = 600
private val COLOR_NAMES = listOf("Lime", "Violet", "Coral", "Cyan", "Orange", "Sky blue")
private fun playerColor(value: String) = Color(0xff000000 or value.removePrefix("#").toLong(16))
private typealias Feel = (HapticFeedbackType) -> Unit

@Composable
fun EnergyApp(session: TrackerSession) {
    val uiFont = FontFamily(Font(Res.font.dm_sans))
    val digitFont = FontFamily(Font(Res.font.barlow_semibold, FontWeight.SemiBold))
    val typography = Typography().let { default ->
        default.copy(
            bodyLarge = default.bodyLarge.copy(fontFamily = uiFont),
            bodyMedium = default.bodyMedium.copy(fontFamily = uiFont),
            labelLarge = default.labelLarge.copy(fontFamily = uiFont),
            titleLarge = default.titleLarge.copy(fontFamily = uiFont),
        )
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Ink, onPrimary = OnInk, background = Background,
        surface = Surface, onSurface = Ink), typography = typography) {
      CompositionLocalProvider(LocalContentColor provides Ink, LocalTextStyle provides typography.bodyMedium) {
        val haptics = LocalHapticFeedback.current
        val feel: Feel = { if (session.board.preferences.haptics) haptics.performHapticFeedback(it) }
        BoxWithConstraints(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.safeDrawing).padding(6.dp)) {
            val board = session.board
            val overlay = session.menuOpen || session.setupOpen
            val hidden = if (overlay) Modifier.clearAndSetSemantics {} else Modifier
            Column(Modifier.fillMaxSize().then(hidden), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(board.preferences.count / 2) { column ->
                            val id = row * (board.preferences.count / 2) + column
                            key(id) {
                                PlayerZone(board.player(id), board.rotated(id), board.preferences.count == 4 && (id == 0 || id == 3),
                                    session.limitBumps[id], !overlay, session, digitFont, feel,
                                    Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
            if (session.menuOpen) Menu(session)
            Hub(session.menuOpen, Modifier.align(Alignment.Center).then(if (session.setupOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                session.showMenu(!session.menuOpen)
            }
            if (!overlay) QuickUndo(session, Modifier.align(Alignment.Center))
            if (session.setupOpen) SetupSheet(session, maxHeight, feel)
            session.saveWarning?.let { warning ->
                Text(warning, Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xffffd69b)).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    color = Color(0xff2d210b), fontSize = 12.sp)
            }
        }
      }
    }
}

@Composable
private fun PlayerZone(player: Player, rotated: Boolean, markOnRight: Boolean, bumps: Int,
    interactive: Boolean, session: TrackerSession, digits: FontFamily, feel: Feel, modifier: Modifier) {
    val id = player.preference.id
    val name = player.preference.label
    val color = playerColor(player.preference.color)
    val shape = RoundedCornerShape(22.dp)
    var holding by remember { mutableStateOf(false) }
    val holdProgress by animateFloatAsState(if (holding) 1f else 0f,
        if (holding) tween(HOLD_MS, easing = LinearEasing) else snap(), label = "hold")

    // A running total of recent changes ("+5"), or why a tap did nothing ("Max 10").
    var previous by remember { mutableIntStateOf(player.energy) }
    var deltaSum by remember { mutableIntStateOf(0) }
    var deltaText by remember { mutableStateOf<String?>(null) }
    var deltaShown by remember { mutableIntStateOf(0) }
    var limitNotice by remember { mutableStateOf(false) }
    LaunchedEffect(player.energy) {
        val change = player.energy - previous
        previous = player.energy
        if (change != 0) {
            limitNotice = false
            deltaSum += change
            deltaText = when {
                deltaSum > 0 -> "+$deltaSum"
                deltaSum < 0 -> "−${abs(deltaSum)}"
                else -> null // A change and its undo cancel out.
            }
            deltaShown++
        }
    }
    val shake = remember { Animatable(0f) }
    var seenBumps by remember { mutableIntStateOf(bumps) }
    LaunchedEffect(bumps) {
        if (bumps == seenBumps) return@LaunchedEffect
        seenBumps = bumps
        deltaSum = 0
        deltaText = null
        limitNotice = true
        deltaShown++
        shake.snapTo(0f)
        // One short nudge, not a wobble; the haptic reject carries the rest.
        shake.animateTo(0f, keyframes { durationMillis = 150; 5f at 50 using Ease })
    }
    LaunchedEffect(deltaShown) {
        if (deltaShown == 0) return@LaunchedEffect
        delay(1_600)
        deltaText = null
        deltaSum = 0
        limitNotice = false
    }

    BoxWithConstraints(modifier.clip(shape)
        .border(1.dp, Hairline, shape)
        .testTag("player-$id")) {
        val zoneWidth = maxWidth
        val zoneHeight = maxHeight
        // The dial sits slightly above centre, clear of the name and of the +3 button.
        // Short zones bring the +/− marks closer to the dial, so it narrows to keep them clear.
        val dialWidth = zoneWidth * if (zoneHeight < 360.dp) .88f else .92f
        val dial = min(min(dialWidth, zoneHeight * .68f), (zoneHeight * .5f - 64.dp) * 2).coerceAtLeast(48.dp)
        val dialTop = (zoneHeight - dial) / 2
        val density = LocalDensity.current
        fun Dp.sp() = with(density) { toSp() }
        fun tableSp(value: Float) = (value * minOf(density.fontScale, TABLE_TEXT_SCALE) / density.fontScale).sp
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (rotated) 180f else 0f }.drawBehind {
            // A flat neutral surface: the seat colour lives only in the ring, the count and the seat mark.
            drawRect(ZoneBase)
        }) {
            Column(Modifier.fillMaxSize()) {
                EnergyHalf("Add 1 energy to $name", true, player.energy < player.limit, interactive, false, color,
                    session.gestureEpoch, {
                        if (session.adjust(id, 1)) feel(HapticFeedbackType.SegmentTick) else feel(HapticFeedbackType.Reject)
                    }, {}, {}, Modifier.weight(1f).fillMaxWidth().testTag("add-$id"))
                EnergyHalf("Remove 1 energy from $name", false, player.energy > 0, interactive, true, color,
                    session.gestureEpoch, { if (session.adjust(id, -1)) feel(HapticFeedbackType.SegmentTick) },
                    { if (session.reset(id)) feel(HapticFeedbackType.LongPress) }, { holding = it },
                    Modifier.weight(1f).fillMaxWidth().testTag("remove-$id"))
            }
            Box(Modifier.align(Alignment.TopCenter).offset(y = dialTop).size(dial)
                .offset { IntOffset(shake.value.dp.roundToPx(), 0) }) {
                // A quick tap also starts the hold timer, so feedback waits until it is clearly a hold.
                val drain = ((holdProgress - .15f) / .85f).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize()) {
                    drawDial(player.energy, color, drain)
                    // Rotated seats face each other, so a lone 6 or 9 is underlined, as on dice and
                    // card stats, to say which way up it is.
                    if (player.energy == 6 || player.energy == 9) {
                        val bar = size.minDimension * .026f
                        drawRoundRect(color, Offset(size.width * .4f, size.height * .775f), Size(size.width * .2f, bar),
                            androidx.compose.ui.geometry.CornerRadius(bar / 2))
                    }
                }
                // The slot above the number is empty at rest. The reset word goes here because it is on
                // the far side of the zone from the holding thumb.
                // One word for the limit, at rest and when a tap is refused.
                val full = player.limit == 10 && player.energy == 10
                val notice = when {
                    drain > .1f -> "Reset"
                    limitNotice -> "Max ${player.limit}"
                    deltaText != null -> deltaText
                    full -> "Max 10"
                    else -> null
                }
                if (notice != null) BasicText(notice, Modifier.align(Alignment.TopCenter).padding(top = dial * .065f).clearAndSetSemantics {},
                    style = TextStyle(fontFamily = digits, fontWeight = FontWeight.SemiBold,
                        fontSize = (dial * .125f).coerceAtLeast(16.dp).sp(), color = when {
                            drain > .1f -> Ink
                            limitNotice || (deltaText == null && full) -> color
                            else -> InkSecondary
                        }), maxLines = 1)
                val digitsShown = player.energy.toString().length
                val numberDp = dial * when (digitsShown) { 1 -> .74f; 2 -> .66f; 3 -> .48f; else -> .38f }
                val numberSize = numberDp.sp()
                // Laid out at the digit's visible height, so it sits between the two slots; only its
                // empty line spacing overflows. Width alone decides how far long counts shrink.
                // Digits sit below the middle of their text line, by an amount set by the font size.
                BasicText(player.energy.toString(), Modifier.align(Alignment.Center).offset(y = -numberDp * .05f)
                    .widthIn(max = dial * .8f).testTag("energy-$id")
                    .clearAndSetSemantics { contentDescription = "$name: ${player.energy} energy"; liveRegion = LiveRegionMode.Polite }
                    .height(numberDp * .72f).wrapContentHeight(unbounded = true),
                    style = TextStyle(fontFamily = digits, fontWeight = FontWeight.SemiBold, fontSize = numberSize,
                        color = color, textAlign = TextAlign.Center), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = numberSize, stepSize = .5.sp))
            }
            // No name label: the seat's shape and colour identify it, so colour is never the only signal.
            Box(Modifier.align(if (markOnRight) Alignment.TopEnd else Alignment.TopStart).padding(16.dp).size(14.dp)
                .clearAndSetSemantics {}) {
                Canvas(Modifier.fillMaxSize()) { drawSeatMark(id, color) }
            }
            // Quiet + and − cues say which half adds and which removes, in this seat's own orientation.
            // They sit on the side away from the seat mark, level with the middle of each half.
            val cueSide = if (markOnRight) Alignment.TopStart else Alignment.TopEnd
            listOf(true, false).forEach { plus ->
                Canvas(Modifier.align(cueSide).offset(y = zoneHeight * (if (plus) .25f else .75f) - 8.dp)
                    .padding(horizontal = 14.dp).size(16.dp).clearAndSetSemantics {}) {
                    val w = 2.dp.toPx()
                    val cue = Ink.copy(alpha = .5f)
                    drawLine(cue, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
                    if (plus) drawLine(cue, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
                }
            }
            val canCharge = player.energy < player.limit
            val chargeShape = RoundedCornerShape(14.dp)
            // A dead margin around +3: a near miss does nothing rather than removing 1.
            Box(Modifier.align(Alignment.BottomCenter).consumeTouchesOutside(12.dp).padding(12.dp)) {
                Row(Modifier.height(48.dp)
                    .widthIn(min = min(116.dp, (zoneWidth - 32.dp).coerceAtLeast(1.dp)), max = (zoneWidth - 32.dp).coerceAtLeast(1.dp))
                    // Maxed out: no fill and dim text, so it reads as off rather than missing.
                    .clip(chargeShape).then(if (canCharge) Modifier.background(Raised) else Modifier)
                    .border(1.dp, if (canCharge) Outline else Line, chargeShape)
                    // Still tappable at the limit, so the tap can explain itself as the + half does.
                    .testTag("charge-$id").clickable(enabled = interactive, role = Role.Button) {
                        if (session.adjust(id, 3)) feel(HapticFeedbackType.Confirm) else feel(HapticFeedbackType.Reject)
                    }
                    .semantics { contentDescription = "Charge +3 for $name"; if (!canCharge) stateDescription = "At the limit" }
                    .padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(18.dp)) { drawBolt(if (canCharge) color else Muted) }
                    Spacer(Modifier.width(6.dp))
                    BasicText("+3", style = MaterialTheme.typography.bodyLarge.copy(color = if (canCharge) Ink else InkSecondary,
                        fontWeight = FontWeight.SemiBold, lineHeight = TextUnit.Unspecified), maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = tableSp(18f), stepSize = .5.sp))
                }
            }
        }
    }
}

/**
 * Ten square-cut energy cells, one per point, with a wider break after every five so a count reads
 * without counting. Holding to reset drains the lit cells, so the ring itself is the progress bar.
 */
private fun DrawScope.drawDial(energy: Int, color: Color, drain: Float) {
    val stroke = size.minDimension * .045f
    val radius = size.minDimension / 2 - stroke / 2
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val gap = 6f
    val halfGap = 9f
    val cell = (180f - halfGap) / 5
    val off = lerp(Color(0xff2a3134), color, .28f)
    val lit = kotlin.math.ceil(minOf(energy, 10) * (1f - drain)).toInt()
    for (i in 0 until 10) {
        // Above 10 the ring simply stays full; the number carries the rest.
        val tint = if (i >= lit) off else color
        val start = -90f + (i / 5) * 180f + halfGap / 2 + (i % 5) * cell + gap / 2
        drawArc(tint, start, cell - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}

/** A shape per seat, so players are never told apart by colour alone. */
private fun DrawScope.drawSeatMark(id: Int, color: Color) {
    val w = size.width; val h = size.height
    when (id) {
        0 -> drawCircle(color)
        1 -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close() }, color)
        2 -> drawRect(color, Offset(w * .06f, h * .06f), Size(w * .88f, h * .88f))
        else -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h / 2); lineTo(w / 2, h); lineTo(0f, h / 2); close() }, color)
    }
}

private fun DrawScope.drawBolt(color: Color) {
    val s = size.width / 24f
    drawPath(Path().apply {
        moveTo(13.5f * s, 2f * s); lineTo(6f * s, 13.2f * s); lineTo(11.2f * s, 13.2f * s)
        lineTo(10f * s, 22f * s); lineTo(18f * s, 10.4f * s); lineTo(12.7f * s, 10.4f * s); close()
    }, color)
}

/**
 * Swallows touches in a margin of [inset] around the content, so the energy half underneath never sees
 * a near miss. Touches on the content itself are left alone: consuming them would cancel its own tap.
 */
private fun Modifier.consumeTouchesOutside(inset: Dp) = pointerInput(inset) {
    val edge = inset.toPx()
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach {
            val p = it.position
            val inside = p.x in edge..(size.width - edge) && p.y in edge..(size.height - edge)
            if (!inside) it.consume()
        }
    }
}

@Composable
private fun EnergyHalf(description: String, up: Boolean, enabled: Boolean, interactive: Boolean, holdable: Boolean,
    color: Color, epoch: Int, onTap: () -> Unit, onHold: () -> Unit, onHolding: (Boolean) -> Unit, modifier: Modifier) {
    val latestTap by rememberUpdatedState(onTap)
    val latestHold by rememberUpdatedState(onHold)
    val latestHolding by rememberUpdatedState(onHolding)
    // Adding stays live at the limit so the tap can explain itself; removing at zero does nothing.
    val active = interactive && (enabled || up)
    val slop = with(LocalDensity.current) { 14.dp.toPx() }
    val keyGesture = remember { EnergyGesture(0f) }
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }
    var pressedKey by remember { mutableStateOf<Key?>(null) }
    var keyTimer by remember { mutableStateOf<Job?>(null) }
    fun tap() {
        latestTap()
        scope.launch { flash.snapTo(.1f); flash.animateTo(0f, tween(220, easing = Ease)) }
    }
    fun cancelKeyGesture() {
        keyTimer?.cancel()
        keyTimer = null
        keyGesture.cancel()
        pressedKey = null
        latestHolding(false)
    }
    DisposableEffect(active, epoch) { onDispose { cancelKeyGesture() } }
    Box(modifier.drawBehind { if (flash.value > 0f) drawRect(color.copy(alpha = flash.value)) }.semantics {
        role = Role.Button
        contentDescription = description
        if (!enabled) disabled()
        onClick { if (enabled) latestTap(); enabled }
        if (holdable) onLongClick("Reset energy to zero") { if (enabled) latestHold(); enabled }
    }.onKeyEvent { event ->
        if (!active || (event.key != Key.Enter && event.key != Key.Spacebar)) false
        else when (event.type) {
            KeyEventType.KeyDown -> {
                // Native key-repeat events must not start additional timers.
                if (pressedKey == null) {
                    pressedKey = event.key
                    keyGesture.start(0f, 0f)
                    if (holdable) {
                        latestHolding(true)
                        keyTimer = scope.launch { delay(HOLD_MS.toLong()); latestHolding(false); if (keyGesture.hold()) latestHold() }
                    }
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (pressedKey == event.key) {
                    keyTimer?.cancel()
                    latestHolding(false)
                    if (keyGesture.release()) tap()
                    pressedKey = null
                    keyTimer = null
                }
                true
            }
            else -> false
        }
    }.onFocusChanged { if (!it.isFocused) cancelKeyGesture() }.focusable(enabled && interactive).pointerInput(active, epoch, slop) {
        if (!active) return@pointerInput
        coroutineScope {
            val gesture = EnergyGesture(slop)
            var pointer: PointerId? = null
            var timer: Job? = null
            fun stopTimer() { timer?.cancel(); timer = null; latestHolding(false) }
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (pointer == null) {
                            val down = event.changes.firstOrNull { it.changedToDown() && it.position.x in 0f..size.width.toFloat() && it.position.y in 0f..size.height.toFloat() }
                                ?: continue
                            pointer = down.id
                            gesture.start(down.position.x, down.position.y)
                            down.consume()
                            if (holdable && enabled) {
                                latestHolding(true)
                                timer = launch { delay(HOLD_MS.toLong()); latestHolding(false); if (gesture.hold()) latestHold() }
                            }
                        } else {
                            val change = event.changes.firstOrNull { it.id == pointer } ?: continue
                            gesture.move(change.position.x, change.position.y,
                                !change.isConsumed && change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat())
                            if (!gesture.canHold) stopTimer()
                            change.consume()
                            if (!change.pressed) {
                                stopTimer()
                                if (gesture.release()) tap()
                                pointer = null
                            }
                        }
                    }
                }
            } finally { timer?.cancel(); latestHolding(false); gesture.cancel() }
        }
    })
}

@Composable
private fun Hub(open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(68.dp).clip(CircleShape).background(Background).padding(6.dp).clip(CircleShape)
        .background(Raised).border(1.dp, Outline, CircleShape).testTag("menu")
        .clickable(role = Role.Button, onClickLabel = if (open) "Close menu" else "Open menu", onClick = onClick)
        .semantics { contentDescription = if (open) "Close menu" else "Open menu" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val s = size.width / 24f
            if (open) {
                drawLine(Ink, Offset(7f * s, 7f * s), Offset(17f * s, 17f * s), 2f * s, StrokeCap.Round)
                drawLine(Ink, Offset(17f * s, 7f * s), Offset(7f * s, 17f * s), 2f * s, StrokeCap.Round)
                return@Canvas
            }
            // A neutral "more" glyph: the hub is chrome, so it never borrows a seat colour.
            for (x in listOf(5f, 12f, 19f)) drawCircle(Ink, 1.9f * s, Offset(x * s, 12f * s))
        }
    }
}

/**
 * For a few seconds after a reset or New game, an Undo chip sits beside the hub, so a mistaken hold
 * is one tap from recovery. It names the seat by its shape and colour; any later change dismisses it.
 */
@Composable
private fun QuickUndo(session: TrackerSession, modifier: Modifier) {
    val history = session.history
    var shown by remember { mutableStateOf<UndoStep?>(null) }
    var seenSize by remember { mutableIntStateOf(history.size) }
    LaunchedEffect(history) {
        val last = history.lastOrNull()
        val added = history.size > seenSize || (history.size == 50 && last != shown)
        seenSize = history.size
        if (last != null && last.reset && added) {
            shown = last
            delay(5_000)
        }
        shown = null
    }
    val step = shown ?: return
    val shape = RoundedCornerShape(14.dp)
    // Faces the seat that was reset, and sits to that player's right of the hub.
    val faceAway = step.seat != null && session.board.rotated(step.seat)
    Box(modifier.offset(x = if (faceAway) (-86).dp else 86.dp).graphicsLayer { rotationZ = if (faceAway) 180f else 0f }
        // A dead margin, as around +3, so a hurried miss doesn't change a neighbouring count.
        .consumeTouchesOutside(12.dp).padding(12.dp)) {
    Row(Modifier.height(48.dp).clip(shape).background(Raised).border(1.dp, Outline, shape).testTag("quick-undo")
        .clickable(role = Role.Button, onClickLabel = "Undo") { session.undo() }
        .semantics { contentDescription = "Undo ${step.description}" }
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(18.dp)) { drawMenuIcon(MenuIcon.Undo, Ink) }
        step.seat?.let { seat ->
            val color = playerColor(session.board.preferences.players[seat].color)
            Canvas(Modifier.size(11.dp)) { drawSeatMark(seat, color) }
        }
        Text("Undo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
    }
}

private enum class MenuIcon { Undo, NewGame, Setup }

/** Shared actions as one short list below the hub. Undo keeps the menu open so several steps can be undone. */
@Composable
private fun BoxScope.Menu(session: TrackerSession) {
    var confirming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.matchParentSize().background(Scrim)
        .clickable(interactionSource = null, indication = null, onClickLabel = "Close menu") { session.showMenu(false) })
    Column(Modifier.align(Alignment.Center).fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(.88f)) {
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(68.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxWidth().clip(shape).background(Surface).border(1.dp, Hairline, shape)
                .verticalScroll(rememberScrollState())) {
                if (confirming) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Start a new game?", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Everyone goes back to 0. Undo can bring the counts back.", color = InkSecondary, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ confirming = false }, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Outline)) { Text("Cancel", color = Ink) }
                            Button({ session.newGame() }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("confirm-new"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Warn, contentColor = Color(0xff2a1a08))) {
                                Text("New game", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    val last = session.history.lastOrNull()
                    MenuRow("Undo", last?.label ?: "Nothing to undo", MenuIcon.Undo, last != null, "menu-undo",
                        last?.seat?.let { it to playerColor(session.board.preferences.players[it].color) },
                        last?.let { "Undo ${it.description}" }) { session.undo() }
                    HorizontalDivider(color = Hairline)
                    MenuRow("New game", "Everyone to 0", MenuIcon.NewGame, true, "menu-new") { confirming = true }
                    HorizontalDivider(color = Hairline)
                    MenuRow("Setup", "Players, colours", MenuIcon.Setup, true, "setup") { session.showSetup(true) }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, detail: String, icon: MenuIcon, enabled: Boolean, tag: String,
    seat: Pair<Int, Color>? = null, description: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        .graphicsLayer { alpha = if (enabled) 1f else .45f }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(20.dp)) { drawMenuIcon(icon, Muted) }
        // Title over detail, so large text grows the row downwards instead of squeezing the title.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (seat != null) Canvas(Modifier.size(10.dp)) { drawSeatMark(seat.first, seat.second) }
                Text(detail, fontSize = 13.sp, color = InkSecondary)
            }
        }
    }
}

private fun DrawScope.drawMenuIcon(icon: MenuIcon, color: Color) {
    val s = size.width / 24f
    val stroke = Stroke(1.8f * s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    when (icon) {
        MenuIcon.Undo -> {
            drawPath(Path().apply { moveTo(9f * s, 14f * s); lineTo(4f * s, 9f * s); lineTo(9f * s, 4f * s) }, color, style = stroke)
            drawPath(Path().apply {
                moveTo(4f * s, 9f * s); lineTo(14.5f * s, 9f * s)
                arcTo(androidx.compose.ui.geometry.Rect(p(9f, 9f), Size(11f * s, 11f * s)), -90f, 180f, false)
                lineTo(11f * s, 20f * s)
            }, color, style = stroke)
        }
        MenuIcon.NewGame -> {
            // A flag: Undo already owns the curved arrow.
            drawLine(color, p(6f, 3f), p(6f, 21f), stroke.width, StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(6f * s, 4f * s); lineTo(19f * s, 4f * s); lineTo(15.5f * s, 8.5f * s)
                lineTo(19f * s, 13f * s); lineTo(6f * s, 13f * s)
            }, color, style = stroke)
        }
        MenuIcon.Setup -> {
            drawLine(color, p(4f, 7f), p(14f, 7f), stroke.width, StrokeCap.Round)
            drawLine(color, p(18f, 7f), p(20f, 7f), stroke.width, StrokeCap.Round)
            drawLine(color, p(4f, 17f), p(6f, 17f), stroke.width, StrokeCap.Round)
            drawLine(color, p(10f, 17f), p(20f, 17f), stroke.width, StrokeCap.Round)
            drawCircle(color, 2f * s, p(16f, 7f), style = stroke)
            drawCircle(color, 2f * s, p(8f, 17f), style = stroke)
        }
    }
}

@Composable
private fun BoxScope.SetupSheet(session: TrackerSession, availableHeight: Dp, feel: Feel) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(Modifier.matchParentSize().background(Scrim)
        .clickable(interactionSource = null, indication = null, onClickLabel = "Close setup") { session.showSetup(false) })
    Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 480.dp).fillMaxWidth()
        .heightIn(max = (availableHeight * .92f).coerceAtLeast(160.dp)).clip(shape).background(Surface).border(1.dp, Line, shape)
        .semantics { paneTitle = "Setup" }) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Setup", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { session.showSetup(false) }
                .semantics { contentDescription = "Close setup" }, contentAlignment = Alignment.Center) {
                Text("×", color = Muted, fontSize = 28.sp)
            }
        }
        Box(Modifier.weight(1f, fill = false)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Section("Players") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Background).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(2, 4).forEach { count ->
                        val selected = session.board.preferences.count == count
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Raised else Color.Transparent)
                            .then(if (selected) Modifier.border(1.dp, Outline, RoundedCornerShape(10.dp)) else Modifier)
                            .testTag("count-$count")
                            .selectable(selected, role = Role.RadioButton) { session.setCount(count) }, contentAlignment = Alignment.Center) {
                            Text("$count players", fontWeight = FontWeight.SemiBold, color = if (selected) Ink else Muted)
                        }
                    }
                }
            }
            Section("Each player") {
                repeat(session.board.preferences.count) { id ->
                    if (id > 0) HorizontalDivider(color = Hairline)
                    PlayerSettings(session, id)
                }
                Text("Energy above 10 is for decks that can hold extra energy. Counts stop at 9,999.",
                    fontSize = 13.sp, color = Muted)
            }
            Section("At the table") {
                SwitchRow("Vibrate on taps", "A light tick for each change and a firmer one at the limit.",
                    session.board.preferences.haptics, true, "haptics") {
                    session.setHaptics(it)
                    if (it) feel(HapticFeedbackType.ToggleOn)
                }
            }
        }
        // Fades the cut-off row so the list reads as scrollable.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Surface))))
        }
        HorizontalDivider(color = Line)
        Button({ session.showSetup(false) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).heightIn(min = 48.dp).testTag("done"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = OnInk)) { Text("Done", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = InkSecondary,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun PlayerSettings(session: TrackerSession, id: Int) {
    val player = session.board.player(id)
    val color = playerColor(player.preference.color)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
            Canvas(Modifier.size(12.dp)) { drawSeatMark(id, color) }
            Text(player.preference.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
        }
        // Six 48dp targets in one row when they fit, otherwise two rows of three.
        BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = "Colour for ${player.preference.label}" }) {
            val perRow = if (maxWidth / 6 >= 48.dp) 6 else 3
            Column {
                PLAYER_COLORS.chunked(perRow).forEachIndexed { rowIndex, rowColors ->
                    Row(Modifier.fillMaxWidth()) {
                        rowColors.forEachIndexed { column, value ->
                            val index = rowIndex * perRow + column
                            val selected = player.preference.color == value
                            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).testTag("color-$id-$index")
                                .selectable(selected, role = Role.RadioButton) { session.setColor(id, value) }
                                .semantics { contentDescription = COLOR_NAMES[index] }, contentAlignment = Alignment.Center) {
                                // Every selected swatch: the same ring, the same gap.
                                Box(Modifier.size(34.dp).clip(CircleShape)
                                    .border(2.dp, if (selected) Ink else Color.Transparent, CircleShape).padding(4.dp)
                                    .clip(CircleShape).background(playerColor(value)))
                            }
                        }
                    }
                }
            }
        }
        SwitchRow("Allow energy above 10",
            if (player.energy > 10) "Lower energy to 10 before switching off." else null,
            player.preference.allowAbove10, player.energy <= 10, "limit-$id",
            "Allow energy above 10 for ${player.preference.label}") { session.setLimit(id, it) }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, enabled: Boolean, tag: String,
    description: String = title, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .semantics { contentDescription = description }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, color = Ink)
            if (detail != null) Text(detail, fontSize = 13.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics {},
            colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = OnInk, checkedBorderColor = Ink,
                uncheckedTrackColor = Raised, uncheckedThumbColor = InkSecondary, uncheckedBorderColor = Outline))
    }
}
