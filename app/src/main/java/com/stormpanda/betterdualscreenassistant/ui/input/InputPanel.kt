package com.stormpanda.betterdualscreenassistant.ui.input

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.input.InputInjector
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import kotlin.math.abs

private enum class InputMode(val label: String) {
    KEYBOARD("Keyboard"),
    TRACKPAD("Trackpad"),
    COMBINED("Combined")
}

/**
 * Virtual keyboard and trackpad for the top screen, shown on the bottom screen.
 * Key and mouse events are injected via the privileged helper.
 */
@Composable
fun InputPanel(modifier: Modifier = Modifier) {
    val colors = LocalTccColors.current
    var mode by remember { mutableStateOf(InputMode.COMBINED) }
    val trackpadState = remember { TrackpadState() }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panelBackground)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            InputMode.entries.forEach { m ->
                val selected = m == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.panelBackgroundSelected else colors.panelBackground)
                        .pointerInput(m) { detectTapGestures { mode = m } }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = m.label,
                        color = if (selected) colors.secondary else colors.primary,
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                InputMode.KEYBOARD -> VirtualKeyboard(Modifier.fillMaxSize())
                InputMode.TRACKPAD -> Trackpad(trackpadState, Modifier.fillMaxSize())
                InputMode.COMBINED -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Trackpad(trackpadState, Modifier.weight(0.38f).fillMaxWidth())
                    VirtualKeyboard(Modifier.weight(0.62f).fillMaxWidth(), compact = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Trackpad
// ---------------------------------------------------------------------------------------------

private class TrackpadState {
    var sensitivity by mutableFloatStateOf(1.6f)
}

@Composable
private fun Trackpad(state: TrackpadState, modifier: Modifier = Modifier) {
    val colors = LocalTccColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val startTime = first.uptimeMillis
                        var lastTime = startTime
                        var maxPointers = 1
                        var totalMove = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            lastTime = event.changes.maxOf { it.uptimeMillis }
                            if (pressed.size > maxPointers) maxPointers = pressed.size
                            if (pressed.isEmpty()) break
                            if (pressed.size == 1 && maxPointers == 1) {
                                val d = pressed[0].positionChange()
                                totalMove += abs(d.x) + abs(d.y)
                                val speed = abs(d.x) + abs(d.y)
                                val accel = 1f + (speed / 25f).coerceAtMost(1.5f)
                                InputInjector.move(d.x * state.sensitivity * accel, d.y * state.sensitivity * accel)
                            } else if (pressed.size >= 2) {
                                var dx = 0f
                                var dy = 0f
                                pressed.forEach {
                                    val d = it.positionChange()
                                    dx += d.x
                                    dy += d.y
                                }
                                dx /= pressed.size
                                dy /= pressed.size
                                totalMove += abs(dx) + abs(dy)
                                InputInjector.scroll(dx / 40f, dy / 40f)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (totalMove < 24f && lastTime - startTime < 280) {
                            when (maxPointers) {
                                1 -> InputInjector.click(InputInjector.BUTTON_LEFT)
                                2 -> InputInjector.click(InputInjector.BUTTON_RIGHT)
                                else -> InputInjector.click(InputInjector.BUTTON_MIDDLE)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "1 finger: move / tap = click\n2 fingers: scroll / tap = right click",
                color = colors.primary.copy(alpha = 0.35f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MouseButton("Left", InputInjector.BUTTON_LEFT, Modifier.weight(2f))
            MouseButton("Middle", InputInjector.BUTTON_MIDDLE, Modifier.weight(1f))
            MouseButton("Right", InputInjector.BUTTON_RIGHT, Modifier.weight(2f))
            Row(
                modifier = Modifier.weight(2f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PanelKey("Slow", Modifier.weight(1f)) {
                    state.sensitivity = (state.sensitivity - 0.2f).coerceAtLeast(0.6f)
                }
                PanelKey("Fast", Modifier.weight(1f)) {
                    state.sensitivity = (state.sensitivity + 0.2f).coerceAtMost(4f)
                }
            }
        }
    }
}

@Composable
private fun MouseButton(label: String, button: Int, modifier: Modifier) {
    val colors = LocalTccColors.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) colors.panelBackgroundSelected else colors.panelBackground)
            .pointerInput(button) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        InputInjector.button(button, true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            InputInjector.button(button, false)
                            pressed = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = colors.primary, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Keyboard
// ---------------------------------------------------------------------------------------------

private enum class KeyKind { NORMAL, SHIFT, CTRL, ALT, META }

private data class Key(
    val label: String,
    val code: Int,
    val shifted: String? = null,
    val weight: Float = 1f,
    val kind: KeyKind = KeyKind.NORMAL
)

private fun letters(chars: String): List<Key> = chars.map {
    Key(it.toString(), KeyEvent.KEYCODE_A + (it - 'a'), it.uppercase())
}

private val functionRow = listOf(Key("Esc", KeyEvent.KEYCODE_ESCAPE, weight = 1.3f)) +
    (1..12).map { Key("F$it", KeyEvent.KEYCODE_F1 + it - 1) } +
    listOf(Key("Del", KeyEvent.KEYCODE_FORWARD_DEL, weight = 1.3f))

private val numberRow = listOf(
    Key("`", KeyEvent.KEYCODE_GRAVE, "~"),
    Key("1", KeyEvent.KEYCODE_1, "!"), Key("2", KeyEvent.KEYCODE_2, "@"),
    Key("3", KeyEvent.KEYCODE_3, "#"), Key("4", KeyEvent.KEYCODE_4, "$"),
    Key("5", KeyEvent.KEYCODE_5, "%"), Key("6", KeyEvent.KEYCODE_6, "^"),
    Key("7", KeyEvent.KEYCODE_7, "&"), Key("8", KeyEvent.KEYCODE_8, "*"),
    Key("9", KeyEvent.KEYCODE_9, "("), Key("0", KeyEvent.KEYCODE_0, ")"),
    Key("-", KeyEvent.KEYCODE_MINUS, "_"), Key("=", KeyEvent.KEYCODE_EQUALS, "+"),
    Key("Bksp", KeyEvent.KEYCODE_DEL, weight = 1.8f)
)

private val topRow = listOf(Key("Tab", KeyEvent.KEYCODE_TAB, weight = 1.5f)) +
    letters("qwertyuiop") +
    listOf(
        Key("[", KeyEvent.KEYCODE_LEFT_BRACKET, "{"),
        Key("]", KeyEvent.KEYCODE_RIGHT_BRACKET, "}"),
        Key("\\", KeyEvent.KEYCODE_BACKSLASH, "|", weight = 1.3f)
    )

private val homeRow = listOf(Key("Caps", KeyEvent.KEYCODE_CAPS_LOCK, weight = 1.8f)) +
    letters("asdfghjkl") +
    listOf(
        Key(";", KeyEvent.KEYCODE_SEMICOLON, ":"),
        Key("'", KeyEvent.KEYCODE_APOSTROPHE, "\""),
        Key("Enter", KeyEvent.KEYCODE_ENTER, weight = 2.2f)
    )

private val bottomLetterRow = listOf(Key("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, weight = 2.3f, kind = KeyKind.SHIFT)) +
    letters("zxcvbnm") +
    listOf(
        Key(",", KeyEvent.KEYCODE_COMMA, "<"),
        Key(".", KeyEvent.KEYCODE_PERIOD, ">"),
        Key("/", KeyEvent.KEYCODE_SLASH, "?"),
        Key("↑", KeyEvent.KEYCODE_DPAD_UP, weight = 1.2f)
    )

private val spaceRow = listOf(
    Key("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, weight = 1.4f, kind = KeyKind.CTRL),
    Key("Win", KeyEvent.KEYCODE_META_LEFT, weight = 1.2f, kind = KeyKind.META),
    Key("Alt", KeyEvent.KEYCODE_ALT_LEFT, weight = 1.2f, kind = KeyKind.ALT),
    Key("Space", KeyEvent.KEYCODE_SPACE, weight = 6f),
    Key("←", KeyEvent.KEYCODE_DPAD_LEFT, weight = 1.2f),
    Key("↓", KeyEvent.KEYCODE_DPAD_DOWN, weight = 1.2f),
    Key("→", KeyEvent.KEYCODE_DPAD_RIGHT, weight = 1.2f)
)

@Composable
private fun VirtualKeyboard(modifier: Modifier = Modifier, compact: Boolean = false) {
    // One-shot latched modifiers: tap to latch, released after the next normal key.
    var shift by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var meta by remember { mutableStateOf(false) }
    var showFunction by remember { mutableStateOf(!compact) }

    fun metaState(): Int {
        var m = 0
        if (shift) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (ctrl) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (meta) m = m or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        return m
    }

    fun toggleModifier(key: Key) {
        val nowOn = when (key.kind) {
            KeyKind.SHIFT -> { shift = !shift; shift }
            KeyKind.CTRL -> { ctrl = !ctrl; ctrl }
            KeyKind.ALT -> { alt = !alt; alt }
            KeyKind.META -> { meta = !meta; meta }
            KeyKind.NORMAL -> return
        }
        InputInjector.key(key.code, metaState(), if (nowOn) 0 else 1)
    }

    fun releaseModifiers() {
        val state = metaState()
        if (shift) InputInjector.key(KeyEvent.KEYCODE_SHIFT_LEFT, state, 1)
        if (ctrl) InputInjector.key(KeyEvent.KEYCODE_CTRL_LEFT, state, 1)
        if (alt) InputInjector.key(KeyEvent.KEYCODE_ALT_LEFT, state, 1)
        if (meta) InputInjector.key(KeyEvent.KEYCODE_META_LEFT, state, 1)
        shift = false
        ctrl = false
        alt = false
        meta = false
    }

    fun press(key: Key) {
        if (key.kind != KeyKind.NORMAL) {
            toggleModifier(key)
            return
        }
        val state = metaState()
        InputInjector.key(key.code, state, 2)
        if (state != 0) releaseModifiers()
    }

    val rows = buildList {
        if (showFunction) add(functionRow)
        add(numberRow)
        add(topRow)
        add(homeRow)
        add(bottomLetterRow)
        add(spaceRow)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { key ->
                    val active = when (key.kind) {
                        KeyKind.SHIFT -> shift
                        KeyKind.CTRL -> ctrl
                        KeyKind.ALT -> alt
                        KeyKind.META -> meta
                        KeyKind.NORMAL -> false
                    }
                    val label = if (shift && key.shifted != null) key.shifted else key.label
                    PanelKey(
                        label = label,
                        modifier = Modifier.weight(key.weight),
                        highlighted = active,
                        small = label.length > 2,
                        onClick = { press(key) }
                    )
                }
            }
        }
        if (compact) {
            // Compact mode keeps the function row hidden; allow toggling it on demand.
            Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                PanelKey(
                    label = if (showFunction) "Hide F-keys" else "Show F-keys",
                    modifier = Modifier.weight(1f),
                    small = true,
                    onClick = { showFunction = !showFunction }
                )
            }
        }
    }
}

@Composable
private fun PanelKey(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalTccColors.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlighted || pressed) colors.panelBackgroundSelected else colors.panelBackground
            )
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) colors.secondary else colors.primary,
            fontSize = if (small) 12.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
