package com.stormpanda.betterdualscreenassistant.ui.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private fun List<Key>.totalWeight(): Float = sumOf { it.weight.toDouble() }.toFloat()

    @Test
    fun `compact layout rows all span ten grid columns`() {
        compactRows.forEachIndexed { index, row ->
            assertEquals("compact row $index", 10f, row.totalWeight(), 0.001f)
        }
        assertEquals(7, compactRows.size)
    }

    @Test
    fun `full layout rows all span fifteen grid columns`() {
        fullRows.forEachIndexed { index, row ->
            assertEquals("full row $index", 15f, row.totalWeight(), 0.001f)
        }
        assertEquals(6, fullRows.size)
    }

    @Test
    fun `every key has a valid key code and label`() {
        (compactRows + fullRows).flatten().forEach { key ->
            assertTrue("blank label", key.label.isNotBlank())
            assertTrue("invalid key code for ${key.label}", key.code != KeyEvent.KEYCODE_UNKNOWN)
        }
    }

    @Test
    fun `compact layout exposes the essential function keys with themed style`() {
        val byLabel = compactRows.flatten().associateBy { it.label }
        listOf("Esc", "Caps", "Del", "Back", "Tab", "Enter", "Ctrl", "Shift", "Alt", "◄", "▲", "▼", "►").forEach {
            val key = byLabel[it]
            assertTrue("missing $it", key != null)
            assertTrue("$it should be styled as a function key", key!!.function)
        }
        assertTrue("Space is not a function key", byLabel.getValue("Space").function.not())
    }

    @Test
    fun `modifier keys use modifier kinds and character keys do not`() {
        val all = (compactRows + fullRows).flatten()
        all.filter { it.label in listOf("Ctrl", "Shift", "Alt", "Wn") }
            .forEach { assertTrue("${it.label} should be a modifier", it.kind != KeyKind.NORMAL) }
        all.filter { it.label.length == 1 && it.label[0].isLetter() }
            .forEach { assertEquals(KeyKind.NORMAL, it.kind) }
    }

    @Test
    fun `letters map to the matching android key codes`() {
        val letters = fullRows.flatten().filter { it.label.length == 1 && it.label[0].isLetter() }
        assertEquals(26, letters.map { it.label }.toSet().size)
        letters.forEach {
            assertEquals(KeyEvent.KEYCODE_A + (it.label[0] - 'A'), it.code)
        }
    }
}
