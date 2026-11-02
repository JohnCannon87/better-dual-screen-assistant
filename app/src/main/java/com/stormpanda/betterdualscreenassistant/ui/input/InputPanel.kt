package com.stormpanda.betterdualscreenassistant.ui.input

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
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
    COMPACT("Compact"),
    FULL("Full"),
    TRACKPAD("Trackpad")
}

private const val PREFS_NAME = "input_panel"
private const val PREF_MODE = "last_mode"
private const val PREF_SENSITIVITY = "trackpad_sensitivity"

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

/**
 * Virtual keyboard and trackpad for the top screen, shown on the bottom screen.
 * Key and mouse events are injected via the privileged helper.
 */
@Composable
fun InputPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTccColors.current
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(
            InputMode.entries.firstOrNull { it.name == prefs(context).getString(PREF_MODE, null) }
                ?: InputMode.COMPACT
        )
    }
    val trackpadState = remember { TrackpadState(context) }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panelBackground)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent)
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = colors.onAccent, fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
            InputMode.entries.forEach { m ->
                val selected = m == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.panelBackgroundSelected else colors.panelBackground)
                        .pointerInput(m) {
                            detectTapGestures {
                                mode = m
                                prefs(context).edit().putString(PREF_MODE, m.name).apply()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = m.label,
                        color = if (selected) colors.accent else colors.primary,
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                InputMode.COMPACT -> VirtualKeyboard(compactRows, Modifier.fillMaxSize())
                InputMode.FULL -> VirtualKeyboard(fullRows, Modifier.fillMaxSize())
                InputMode.TRACKPAD -> Trackpad(trackpadState, onClose, Modifier.fillMaxSize())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Trackpad
// ---------------------------------------------------------------------------------------------

private class TrackpadState(private val context: Context) {
    private var value by mutableFloatStateOf(prefs(context).getFloat(PREF_SENSITIVITY, 1.6f))
    var sensitivity: Float
        get() = value
        set(v) {
            value = v
            prefs(context).edit().putFloat(PREF_SENSITIVITY, v).apply()
        }
}

@Composable
private fun Trackpad(state: TrackpadState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTccColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .border(1.dp, colors.accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
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
                PanelKey("Speed −", Modifier.weight(1f), small = true) {
                    state.sensitivity = (state.sensitivity - 0.2f).coerceAtLeast(0.6f)
                }
                PanelKey("Speed +", Modifier.weight(1f), small = true) {
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
    val kind: KeyKind = KeyKind.NORMAL,
    val function: Boolean = false
)

private fun letter(c: Char) = Key(c.uppercase(), KeyEvent.KEYCODE_A + (c - 'a'), c.uppercase())

private fun letters(chars: String): List<Key> = chars.map { letter(it) }

private val digitCodes = listOf(
    Key("1", KeyEvent.KEYCODE_1, "!"), Key("2", KeyEvent.KEYCODE_2, "@"),
    Key("3", KeyEvent.KEYCODE_3, "#"), Key("4", KeyEvent.KEYCODE_4, "$"),
    Key("5", KeyEvent.KEYCODE_5, "%"), Key("6", KeyEvent.KEYCODE_6, "^"),
    Key("7", KeyEvent.KEYCODE_7, "&"), Key("8", KeyEvent.KEYCODE_8, "*"),
    Key("9", KeyEvent.KEYCODE_9, "("), Key("0", KeyEvent.KEYCODE_0, ")")
)

private val keyMinus = Key("-", KeyEvent.KEYCODE_MINUS, "_")
private val keyEquals = Key("=", KeyEvent.KEYCODE_EQUALS, "+")
private val keyLBracket = Key("[", KeyEvent.KEYCODE_LEFT_BRACKET, "{")
private val keyRBracket = Key("]", KeyEvent.KEYCODE_RIGHT_BRACKET, "}")
private val keyBackslash = Key("\\", KeyEvent.KEYCODE_BACKSLASH, "|")
private val keySemicolon = Key(";", KeyEvent.KEYCODE_SEMICOLON, ":")
private val keyApostrophe = Key("'", KeyEvent.KEYCODE_APOSTROPHE, "\"")
private val keyComma = Key(",", KeyEvent.KEYCODE_COMMA, "<")
private val keyPeriod = Key(".", KeyEvent.KEYCODE_PERIOD, ">")
private val keySlash = Key("/", KeyEvent.KEYCODE_SLASH, "?")

private fun mod(label: String, code: Int, kind: KeyKind, weight: Float) = Key(label, code, null, weight, kind, true)
private fun plain(label: String, code: Int, weight: Float = 1f) = Key(label, code, null, weight)
/** A non-character "function" key such as Esc, Tab or Enter, drawn with a themed background. */
private fun fn(label: String, code: Int, weight: Float = 1f) = Key(label, code, null, weight, KeyKind.NORMAL, true)

/** Compact layout: strict 10-column grid with large keys (DS Keyboard "US-0" style). */
private val compactRows: List<List<Key>> = listOf(
    listOf(
        fn("Esc", KeyEvent.KEYCODE_ESCAPE, 2f), mod("Alt", KeyEvent.KEYCODE_ALT_LEFT, KeyKind.ALT, 2f),
        fn("Caps", KeyEvent.KEYCODE_CAPS_LOCK, 2f), fn("Del", KeyEvent.KEYCODE_FORWARD_DEL, 2f),
        fn("Back", KeyEvent.KEYCODE_DEL, 2f)
    ),
    listOf(fn("Tab", KeyEvent.KEYCODE_TAB, 2f), keyApostrophe, keyBackslash, keyLBracket, keyRBracket,
        keyMinus, keyEquals, fn("Enter", KeyEvent.KEYCODE_ENTER, 2f)),
    digitCodes,
    letters("qwertyuiop"),
    letters("asdfghjkl") + keySemicolon,
    letters("zxcvbnm") + listOf(keyComma, keyPeriod, keySlash),
    listOf(
        mod("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, KeyKind.CTRL, 2f),
        mod("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, KeyKind.SHIFT, 2f),
        plain("Space", KeyEvent.KEYCODE_SPACE, 2f),
        fn("◄", KeyEvent.KEYCODE_DPAD_LEFT), fn("▲", KeyEvent.KEYCODE_DPAD_UP),
        fn("▼", KeyEvent.KEYCODE_DPAD_DOWN), fn("►", KeyEvent.KEYCODE_DPAD_RIGHT)
    )
)

/** Full PC layout on a 15-unit grid (DS Keyboard "US-1" style). */
private val fullRows: List<List<Key>> = listOf(
    listOf(fn("Esc", KeyEvent.KEYCODE_ESCAPE, 2f)) +
        (1..12).map { plain("F$it", KeyEvent.KEYCODE_F1 + it - 1) } +
        fn("SL", KeyEvent.KEYCODE_SCROLL_LOCK),
    listOf(Key("`", KeyEvent.KEYCODE_GRAVE, "~")) + digitCodes + listOf(keyMinus, keyEquals) +
        fn("Back", KeyEvent.KEYCODE_DEL, 2f),
    listOf(fn("Tab", KeyEvent.KEYCODE_TAB, 2f)) + letters("qwertyuiop") +
        listOf(keyLBracket, keyRBracket, keyBackslash),
    listOf(fn("Caps", KeyEvent.KEYCODE_CAPS_LOCK, 2f)) + letters("asdfghjkl") +
        listOf(keySemicolon, keyApostrophe, fn("Enter", KeyEvent.KEYCODE_ENTER, 2f)),
    listOf(mod("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, KeyKind.SHIFT, 2f), keyBackslash) + letters("zxcvbnm") +
        listOf(keyComma, keyPeriod, keySlash, mod("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, KeyKind.SHIFT, 2f)),
    listOf(
        mod("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, KeyKind.CTRL, 2f),
        mod("Wn", KeyEvent.KEYCODE_META_LEFT, KeyKind.META, 1f),
        mod("Alt", KeyEvent.KEYCODE_ALT_LEFT, KeyKind.ALT, 2f),
        plain("Space", KeyEvent.KEYCODE_SPACE, 6f),
        mod("Alt", KeyEvent.KEYCODE_ALT_LEFT, KeyKind.ALT, 2f),
        mod("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, KeyKind.CTRL, 2f)
    )
)

private val KeyGap = 3.dp

@Composable
private fun VirtualKeyboard(rows: List<List<Key>>, modifier: Modifier = Modifier) {
    // One-shot latched modifiers: tap to latch, released after the next normal key.
    var shift by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var meta by remember { mutableStateOf(false) }

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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KeyGap)
            ) {
                row.forEach { key ->
                    val active = when (key.kind) {
                        KeyKind.SHIFT -> shift
                        KeyKind.CTRL -> ctrl
                        KeyKind.ALT -> alt
                        KeyKind.META -> meta
                        KeyKind.NORMAL -> false
                    }
                    // Symbol keys show the shifted symbol small above the base symbol.
                    val stacked = key.shifted != null && !key.label[0].isLetter()
                    PanelKey(
                        label = key.label,
                        topLabel = if (stacked) key.shifted else null,
                        modifier = Modifier.weight(key.weight),
                        highlighted = active,
                        function = key.function,
                        small = key.label.length > 2,
                        onClick = { press(key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelKey(
    label: String,
    modifier: Modifier = Modifier,
    topLabel: String? = null,
    highlighted: Boolean = false,
    function: Boolean = false,
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
                when {
                    highlighted -> colors.accent.copy(alpha = 0.40f).compositeOver(colors.panelBackground)
                    function && pressed -> colors.accent.copy(alpha = 0.32f).compositeOver(colors.panelBackground)
                    function -> colors.accent.copy(alpha = 0.16f).compositeOver(colors.panelBackground)
                    pressed -> colors.panelBackgroundSelected
                    else -> colors.panelBackground
                }
            )
            .border(
                1.dp,
                when {
                    highlighted -> colors.accent.copy(alpha = 0.8f)
                    function -> colors.accent.copy(alpha = 0.35f)
                    else -> colors.primary.copy(alpha = 0.08f)
                },
                RoundedCornerShape(8.dp)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (topLabel != null) {
                Text(
                    text = topLabel,
                    color = colors.primary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1
                )
            }
            Text(
                text = label,
                color = if (highlighted) colors.accent else colors.primary,
                fontSize = if (small) 14.sp else 18.sp,
                lineHeight = if (small) 14.sp else 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
