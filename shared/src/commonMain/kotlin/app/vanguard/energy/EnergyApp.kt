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
import kotlin.random.Random

private val Background = Color(0xff0b0d10)
private val ZoneBase = Color(0xff101315)
private val Surface = Color(0xff14181b)
private val Field = Color(0xff101416)
private val Line = Color(0xff262d30)
private val Ink = Color(0xffe8eee6)
private val Muted = Color(0xff8f9b94)
private val Lime = Color(0xffc9f76f)
private val Warn = Color(0xffffb36b)
private val Scrim = Color(0xa6050709)
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
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Color(0xff10170a), background = Background,
        surface = Surface, onSurface = Ink), typography = typography) {
      CompositionLocalProvider(LocalContentColor provides Ink, LocalTextStyle provides typography.bodyMedium) {
        val haptics = LocalHapticFeedback.current
        val feel: Feel = { if (session.board.preferences.haptics) haptics.performHapticFeedback(it) }
        // "Who goes first" cycles a highlight around the table and settles on a random seat.
        var highlight by remember { mutableStateOf<Int?>(null) }
        var picks by remember { mutableIntStateOf(0) }
        LaunchedEffect(picks) {
            if (picks == 0) return@LaunchedEffect
            val count = session.board.preferences.count
            val steps = count * 3 + Random.nextInt(count) + 1
            var wait = 70.0
            for (step in 0 until steps) {
                highlight = step % count
                if (step < steps - 1) { delay(wait.toLong()); wait *= 1.12 }
            }
            feel(HapticFeedbackType.Confirm)
            delay(4_000)
            highlight = null
        }
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
                                    highlight == id, session.limitBumps[id], !overlay, session, digitFont, feel,
                                    Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
            if (session.menuOpen) Menu(session) { picks++ }
            Hub(session.menuOpen, Modifier.align(Alignment.Center).then(if (session.setupOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                session.showMenu(!session.menuOpen)
            }
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
private fun PlayerZone(player: Player, rotated: Boolean, nameOnRight: Boolean, highlighted: Boolean, bumps: Int,
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
    LaunchedEffect(player.energy) {
        val change = player.energy - previous
        previous = player.energy
        if (change != 0) {
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
        deltaText = "Max ${player.limit}"
        deltaShown++
        shake.snapTo(0f)
        shake.animateTo(0f, keyframes { durationMillis = 320; 6f at 90; -5f at 180; 2f at 260 })
    }
    LaunchedEffect(deltaShown) {
        if (deltaShown == 0) return@LaunchedEffect
        delay(1_600)
        deltaText = null
        deltaSum = 0
    }
    val deltaAlpha by animateFloatAsState(if (deltaText != null) .9f else 0f, tween(250), label = "delta")

    BoxWithConstraints(modifier.clip(shape)
        .border(if (highlighted) 3.dp else 1.dp, if (highlighted) color else lerp(ZoneBase, color, .2f), shape)
        .testTag("player-$id")) {
        val zoneWidth = maxWidth
        val zoneHeight = maxHeight
        // The dial sits slightly above centre, clear of the name and of the +3 button.
        val dial = min(min(zoneWidth * .8f, zoneHeight * .58f), (zoneHeight * .53f - 64.dp) * 2).coerceAtLeast(48.dp)
        val dialTop = zoneHeight * .47f - dial / 2
        val density = LocalDensity.current
        fun Dp.sp() = with(density) { toSp() }
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (rotated) 180f else 0f }.drawBehind {
            drawRect(lerp(ZoneBase, color, .07f))
            drawRect(Brush.radialGradient(listOf(color.copy(alpha = .16f), Color.Transparent),
                Offset(size.width / 2, size.height), size.height * .75f))
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
                Canvas(Modifier.fillMaxSize()) { drawDial(player.energy, color, holdProgress) }
                BasicText(deltaText ?: "", Modifier.align(Alignment.TopCenter).padding(top = dial * .13f)
                    .graphicsLayer { alpha = deltaAlpha }.clearAndSetSemantics {},
                    style = TextStyle(fontFamily = digits, fontWeight = FontWeight.SemiBold, fontSize = (dial * .13f).sp(),
                        color = if (deltaSum < 0) Muted else Ink), maxLines = 1)
                val digitsShown = player.energy.toString().length
                val numberSize = (dial * when (digitsShown) { 1 -> .56f; 2 -> .5f; 3 -> .36f; else -> .28f }).sp()
                BasicText(player.energy.toString(), Modifier.align(Alignment.Center)
                    .widthIn(max = dial * .8f).heightIn(max = dial * .58f).testTag("energy-$id")
                    .graphicsLayer { alpha = if (holding) .35f else 1f }
                    .clearAndSetSemantics { contentDescription = "$name: ${player.energy} energy"; liveRegion = LiveRegionMode.Polite },
                    style = TextStyle(fontFamily = digits, fontWeight = FontWeight.SemiBold, fontSize = numberSize,
                        color = color, textAlign = TextAlign.Center), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = numberSize, stepSize = .5.sp))
                val cap = when {
                    player.limit > 10 -> "No limit"
                    player.energy == 10 -> "Full"
                    else -> "of 10"
                }
                BasicText(cap.uppercase(), Modifier.align(Alignment.BottomCenter).padding(bottom = dial * .15f).clearAndSetSemantics {},
                    style = TextStyle(fontFamily = MaterialTheme.typography.bodyMedium.fontFamily, fontSize = (dial * .07f).sp(),
                        letterSpacing = (dial * .01f).sp(), color = lerp(Muted, color, .45f)), maxLines = 1)
            }
            Row(Modifier.align(if (nameOnRight) Alignment.TopEnd else Alignment.TopStart).padding(10.dp)
                .widthIn(max = min(160.dp, (zoneWidth - 24.dp).coerceAtLeast(1.dp))).height(40.dp)
                .consumeTouches().padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(9.dp)) { drawSeatMark(id, lerp(color, Ink, .2f)) }
                BasicText(name, style = MaterialTheme.typography.bodyMedium.copy(color = lerp(color, Ink, .2f),
                    fontWeight = FontWeight.SemiBold, lineHeight = TextUnit.Unspecified),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = if (zoneWidth <= 260.dp) 13.sp else 15.sp, stepSize = .5.sp))
            }
            if (highlighted) BasicText("GOES FIRST", Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp).height(24.dp)
                .clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "$name goes first" },
                style = MaterialTheme.typography.bodyMedium.copy(color = Background, fontWeight = FontWeight.SemiBold,
                    letterSpacing = .8.sp, lineHeight = TextUnit.Unspecified),
                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = 11.sp, stepSize = .5.sp))
            val canCharge = player.energy < player.limit
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).height(48.dp)
                .widthIn(min = min(116.dp, (zoneWidth - 32.dp).coerceAtLeast(1.dp)), max = (zoneWidth - 32.dp).coerceAtLeast(1.dp))
                .clip(CircleShape).background(lerp(ZoneBase, color, .14f))
                .border(1.dp, color.copy(alpha = if (canCharge) .35f else .12f), CircleShape)
                .testTag("charge-$id").clickable(enabled = canCharge && interactive, role = Role.Button) {
                    if (session.adjust(id, 3)) feel(HapticFeedbackType.Confirm)
                }
                .semantics { contentDescription = "Charge +3 for $name"; if (!canCharge) disabled() }
                .padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val tint = color.copy(alpha = if (canCharge) 1f else .3f)
                Canvas(Modifier.size(14.dp)) { drawBolt(tint) }
                Spacer(Modifier.width(6.dp))
                BasicText("+3", style = MaterialTheme.typography.bodyLarge.copy(color = tint, fontWeight = FontWeight.SemiBold,
                    lineHeight = TextUnit.Unspecified), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = 17.sp, stepSize = .5.sp))
            }
        }
    }
}

/** Ten arc segments, one per point of energy, echoing the broken ring of the app icon. */
private fun DrawScope.drawDial(energy: Int, color: Color, holdProgress: Float) {
    val stroke = size.minDimension * .055f
    val radius = size.minDimension / 2 - stroke / 2
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val gap = 12.5f
    val off = lerp(Color(0xff22292b), color, .14f)
    val over = lerp(color, Color.White, .3f)
    for (i in 0 until 10) {
        val tint = when {
            energy > 10 && i < energy - 10 -> over
            i < energy -> color
            else -> off
        }
        drawArc(tint, -90f + i * 36f + gap / 2, 36f - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    if (holdProgress > 0f) {
        val inner = size.minDimension * .38f
        drawArc(Warn, -90f, 360f * holdProgress, false, Offset(center.x - inner, center.y - inner), Size(inner * 2, inner * 2),
            style = Stroke(size.minDimension * .024f, cap = StrokeCap.Round))
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

private fun Modifier.consumeTouches() = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
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
        scope.launch { flash.snapTo(.1f); flash.animateTo(0f, tween(350)) }
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
    }) {
        val tint = color.copy(alpha = if (enabled) .45f else .18f)
        Canvas(Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(16.dp).testTag(if (up) "add-mark" else "remove-mark")) {
            val w = 1.8.dp.toPx()
            drawLine(tint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
            if (up) drawLine(tint, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
        }
    }
}

@Composable
private fun Hub(open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(68.dp).clip(CircleShape).background(Background).padding(6.dp).clip(CircleShape)
        .background(if (open) Lime else Color(0xff1f2628)).testTag("menu")
        .clickable(role = Role.Button, onClickLabel = if (open) "Close menu" else "Open menu", onClick = onClick)
        .semantics { contentDescription = if (open) "Close menu" else "Open menu" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(34.dp)) {
            val s = size.width / 512f
            val bright = if (open) Background else Lime
            val dim = if (open) Background.copy(alpha = .5f) else Color(0xff52623d)
            val r = 132f * s
            val topLeft = Offset(center.x - r, center.y - r)
            val stroke = Stroke(34f * s, cap = StrokeCap.Round)
            drawArc(bright, 135f, 80f, false, topLeft, Size(r * 2, r * 2), style = stroke)
            drawArc(bright, 230f, 80f, false, topLeft, Size(r * 2, r * 2), style = stroke)
            drawArc(dim, 325f, 80f, false, topLeft, Size(r * 2, r * 2), style = stroke)
            drawPath(Path().apply {
                moveTo(276f * s, 176f * s); lineTo(210f * s, 270f * s); lineTo(253f * s, 270f * s)
                lineTo(237f * s, 337f * s); lineTo(304f * s, 240f * s); lineTo(261f * s, 240f * s); close()
            }, bright)
        }
    }
}

private enum class MenuIcon { Undo, First, NewGame, Setup }

/** Shared actions sit around the hub: two above it, two below, so no seat has to reach further. */
@Composable
private fun BoxScope.Menu(session: TrackerSession, onPickFirst: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Box(Modifier.matchParentSize().background(Scrim)
        .clickable(interactionSource = null, indication = null, onClickLabel = "Close menu") { session.showMenu(false) })
    Column(Modifier.align(Alignment.Center).fillMaxHeight().widthIn(max = 320.dp).fillMaxWidth(.86f)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
            if (confirming) Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Line, RoundedCornerShape(18.dp))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Start a new game?", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Everyone goes back to 0. Undo can bring the counts back.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ confirming = false }, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Line)) { Text("Cancel", color = Ink) }
                    Button({ session.newGame() }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("confirm-new"),
                        shape = RoundedCornerShape(12.dp)) { Text("New game") }
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val last = session.history.lastOrNull()
                MenuTile("Undo", last?.label ?: "Nothing yet", MenuIcon.Undo, last != null, "menu-undo", Modifier.weight(1f)) {
                    session.undo(); session.showMenu(false)
                }
                MenuTile("Who goes first", null, MenuIcon.First, true, "menu-first", Modifier.weight(1f)) {
                    session.showMenu(false); onPickFirst()
                }
            }
        }
        Spacer(Modifier.height(68.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            if (!confirming) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuTile("New game", null, MenuIcon.NewGame, true, "menu-new", Modifier.weight(1f)) { confirming = true }
                MenuTile("Setup", null, MenuIcon.Setup, true, "setup", Modifier.weight(1f)) { session.showSetup(true) }
            }
        }
    }
}

@Composable
private fun MenuTile(title: String, detail: String?, icon: MenuIcon, enabled: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.heightIn(min = 92.dp).clip(shape).background(Surface).border(1.dp, Line, shape).testTag(tag)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp)
        .graphicsLayer { alpha = if (enabled) 1f else .4f },
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(22.dp)) { drawMenuIcon(icon, Lime) }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        MenuIcon.First -> {
            drawRoundRect(color, p(3f, 3f), Size(18f * s, 18f * s), androidx.compose.ui.geometry.CornerRadius(4f * s), style = stroke)
            listOf(p(8.5f, 8.5f), p(12f, 12f), p(15.5f, 15.5f)).forEach { drawCircle(color, 1.4f * s, it) }
        }
        MenuIcon.NewGame -> {
            drawArc(color, -150f, 300f, false, p(3f, 3f), Size(18f * s, 18f * s), style = stroke)
            drawPath(Path().apply { moveTo(3f * s, 3.5f * s); lineTo(3.6f * s, 8.3f * s); lineTo(8.4f * s, 7.6f * s) }, color, style = stroke)
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
    val shape = RoundedCornerShape(26.dp)
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
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Section("Players") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Background).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(2, 4).forEach { count ->
                        val selected = session.board.preferences.count == count
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color(0xff273032) else Color.Transparent).testTag("count-$count")
                            .selectable(selected, role = Role.RadioButton) { session.setCount(count) }, contentAlignment = Alignment.Center) {
                            Text("$count players", fontWeight = FontWeight.SemiBold, color = if (selected) Ink else Muted)
                        }
                    }
                }
            }
            Section("Each player") {
                repeat(session.board.preferences.count) { id -> PlayerSettings(session, id) }
            }
            Section("At the table") {
                SettingsCard {
                    SwitchRow("Vibrate on taps", "A light tick for each change and a firmer one at the limit.",
                        session.board.preferences.haptics, true, "haptics") {
                        session.setHaptics(it)
                        if (it) feel(HapticFeedbackType.ToggleOn)
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
        Button({ session.showSetup(false) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).heightIn(min = 48.dp).testTag("done"),
            shape = RoundedCornerShape(12.dp)) { Text("Done", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Muted,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Background).border(1.dp, Line, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun PlayerSettings(session: TrackerSession, id: Int) {
    val player = session.board.player(id)
    val color = playerColor(player.preference.color)
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(12.dp)) { drawSeatMark(id, color) }
            BasicTextField(player.preference.name, { session.setName(id, it) },
                Modifier.weight(1f).testTag("name-$id").semantics { contentDescription = "Name for player ${id + 1}" },
                singleLine = true, cursorBrush = SolidColor(Lime),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).background(Field)
                        .border(1.dp, Line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (player.preference.name.isEmpty()) Text("Player ${id + 1}", color = Muted)
                        field()
                    }
                })
        }
        Row(Modifier.fillMaxWidth().semantics { contentDescription = "Colour for ${player.preference.label}" }) {
            PLAYER_COLORS.forEachIndexed { index, value ->
                val selected = player.preference.color == value
                Box(Modifier.weight(1f).height(48.dp).testTag("color-$id-$index")
                    .selectable(selected, role = Role.RadioButton) { session.setColor(id, value) }
                    .semantics { contentDescription = COLOR_NAMES[index] }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).then(if (selected) Modifier.border(2.dp, Ink, CircleShape).padding(4.dp) else Modifier)
                        .clip(CircleShape).background(playerColor(value)))
                }
            }
        }
        SwitchRow("Allow energy above 10",
            if (player.energy > 10) "Lower energy to 10 before switching off." else "For decks that can hold extra energy.",
            player.preference.allowAbove10, player.energy <= 10, "limit-$id",
            "Allow energy above 10 for ${player.preference.label}") { session.setLimit(id, it) }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, enabled: Boolean, tag: String,
    description: String = title, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .semantics { contentDescription = description }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, color = Ink)
            Text(detail, fontSize = 12.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics {},
            colors = SwitchDefaults.colors(checkedTrackColor = Lime, checkedThumbColor = Color(0xff10170a),
                uncheckedTrackColor = Color(0xff2a3234), uncheckedThumbColor = Ink, uncheckedBorderColor = Color.Transparent))
    }
}
