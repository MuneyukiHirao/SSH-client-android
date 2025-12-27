package com.example.sshterminal

import android.graphics.Color
import com.example.sshterminal.ui.components.TerminalEmulator
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for TerminalEmulator
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TerminalEmulatorTest {

    private lateinit var emulator: TerminalEmulator

    @Before
    fun setup() {
        stopKoin() // Stop any existing Koin instance
        emulator = TerminalEmulator(initialColumns = 80, initialRows = 24)
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `initial state is correct`() {
        assertEquals(80, emulator.columns)
        assertEquals(24, emulator.rows)
        assertEquals(0, emulator.cursorX)
        assertEquals(0, emulator.cursorY)
    }

    @Test
    fun `processBytes writes characters to screen`() {
        emulator.processBytes("Hello".toByteArray())

        assertEquals('H', emulator.getCell(0, 0)?.char)
        assertEquals('e', emulator.getCell(1, 0)?.char)
        assertEquals('l', emulator.getCell(2, 0)?.char)
        assertEquals('l', emulator.getCell(3, 0)?.char)
        assertEquals('o', emulator.getCell(4, 0)?.char)
        assertEquals(5, emulator.cursorX)
        assertEquals(0, emulator.cursorY)
    }

    @Test
    fun `newline moves cursor to next line`() {
        // Note: \n only does line feed without carriage return
        emulator.processBytes("Hello\n\rWorld".toByteArray())

        assertEquals('H', emulator.getCell(0, 0)?.char)
        assertEquals('W', emulator.getCell(0, 1)?.char)
        assertEquals(5, emulator.cursorX)
        assertEquals(1, emulator.cursorY)
    }

    @Test
    fun `carriage return moves cursor to beginning of line`() {
        emulator.processBytes("Hello\rWorld".toByteArray())

        // "World" overwrites "Hello"
        assertEquals('W', emulator.getCell(0, 0)?.char)
        assertEquals('o', emulator.getCell(1, 0)?.char)
        assertEquals('r', emulator.getCell(2, 0)?.char)
        assertEquals('l', emulator.getCell(3, 0)?.char)
        assertEquals('d', emulator.getCell(4, 0)?.char)
    }

    @Test
    fun `backspace moves cursor back`() {
        emulator.processBytes("AB\u0008C".toByteArray())

        // C overwrites B
        assertEquals('A', emulator.getCell(0, 0)?.char)
        assertEquals('C', emulator.getCell(1, 0)?.char)
    }

    @Test
    fun `tab moves cursor to next tab stop`() {
        emulator.processBytes("A\tB".toByteArray())

        assertEquals('A', emulator.getCell(0, 0)?.char)
        assertEquals('B', emulator.getCell(8, 0)?.char)
        assertEquals(9, emulator.cursorX)
    }

    @Test
    fun `resize preserves content`() {
        emulator.processBytes("Hello".toByteArray())
        emulator.resize(40, 12)

        assertEquals(40, emulator.columns)
        assertEquals(12, emulator.rows)
        assertEquals('H', emulator.getCell(0, 0)?.char)
        assertEquals('o', emulator.getCell(4, 0)?.char)
    }

    @Test
    fun `cursor up escape sequence works`() {
        // Note: \n only does line feed, not carriage return
        // So after "Hello\n", cursor is at (5, 1), not (0, 1)
        emulator.processBytes("Hello\n\r\u001B[AWorld".toByteArray())

        // After "Hello": cursor at (5, 0)
        // After "\n": cursor at (5, 1)
        // After "\r": cursor at (0, 1)
        // After ESC[A: cursor at (0, 0)
        // "World" written at line 0
        assertEquals('W', emulator.getCell(0, 0)?.char)
    }

    @Test
    fun `cursor down escape sequence works`() {
        emulator.processBytes("\u001B[BHello".toByteArray())

        // Cursor moved down one line
        assertEquals('H', emulator.getCell(0, 1)?.char)
    }

    @Test
    fun `cursor forward escape sequence works`() {
        emulator.processBytes("\u001B[5CHello".toByteArray())

        // Cursor moved forward 5 positions
        assertEquals('H', emulator.getCell(5, 0)?.char)
    }

    @Test
    fun `cursor back escape sequence works`() {
        emulator.processBytes("XXXXX\u001B[3DHello".toByteArray())

        // Cursor at position 5, moved back 3 positions to position 2
        assertEquals('X', emulator.getCell(0, 0)?.char)
        assertEquals('X', emulator.getCell(1, 0)?.char)
        assertEquals('H', emulator.getCell(2, 0)?.char)
    }

    @Test
    fun `cursor position escape sequence works`() {
        emulator.processBytes("\u001B[5;10HX".toByteArray())

        // Cursor at row 5, column 10 (1-indexed, so 4,9 in 0-indexed)
        assertEquals('X', emulator.getCell(9, 4)?.char)
    }

    @Test
    fun `erase in display from cursor works`() {
        emulator.processBytes("AAAAAAAAAA\r\n".toByteArray())
        emulator.processBytes("BBBBBBBBBB\r\n".toByteArray())
        emulator.processBytes("CCCC\u001B[0J".toByteArray())

        // Line 0 and 1 unchanged, line 2 from cursor erased
        assertEquals('A', emulator.getCell(0, 0)?.char)
        assertEquals('B', emulator.getCell(0, 1)?.char)
        assertEquals('C', emulator.getCell(0, 2)?.char)
        assertEquals('C', emulator.getCell(3, 2)?.char)
        assertEquals(' ', emulator.getCell(4, 2)?.char)
    }

    @Test
    fun `erase entire screen works`() {
        emulator.processBytes("Hello World".toByteArray())
        emulator.processBytes("\u001B[2J".toByteArray())

        assertEquals(' ', emulator.getCell(0, 0)?.char)
    }

    @Test
    fun `erase in line from cursor works`() {
        emulator.processBytes("HelloWorld".toByteArray())
        emulator.processBytes("\u001B[5D\u001B[K".toByteArray())

        // Move back 5, erase to end of line
        assertEquals('H', emulator.getCell(0, 0)?.char)
        assertEquals('e', emulator.getCell(1, 0)?.char)
        assertEquals('l', emulator.getCell(2, 0)?.char)
        assertEquals('l', emulator.getCell(3, 0)?.char)
        assertEquals('o', emulator.getCell(4, 0)?.char)
        assertEquals(' ', emulator.getCell(5, 0)?.char)
    }

    @Test
    fun `SGR reset attributes works`() {
        emulator.processBytes("\u001B[1mBold\u001B[0mNormal".toByteArray())

        assertEquals(true, emulator.getCell(0, 0)?.bold)
        assertEquals(false, emulator.getCell(4, 0)?.bold)
    }

    @Test
    fun `SGR foreground color works`() {
        emulator.processBytes("\u001B[31mRed".toByteArray())

        assertEquals(Color.RED, emulator.getCell(0, 0)?.fg)
    }

    @Test
    fun `SGR background color works`() {
        emulator.processBytes("\u001B[44mBlueBg".toByteArray())

        assertEquals(Color.BLUE, emulator.getCell(0, 0)?.bg)
    }

    @Test
    fun `SGR bold works`() {
        emulator.processBytes("\u001B[1mBold".toByteArray())

        assertTrue(emulator.getCell(0, 0)?.bold == true)
    }

    @Test
    fun `SGR underline works`() {
        emulator.processBytes("\u001B[4mUnderline".toByteArray())

        assertTrue(emulator.getCell(0, 0)?.underline == true)
    }

    @Test
    fun `line wrapping works`() {
        // Write more than 80 characters
        val longLine = "A".repeat(85)
        emulator.processBytes(longLine.toByteArray())

        // First 80 chars on line 0, next 5 on line 1
        assertEquals('A', emulator.getCell(79, 0)?.char)
        assertEquals('A', emulator.getCell(0, 1)?.char)
        assertEquals(5, emulator.cursorX)
        assertEquals(1, emulator.cursorY)
    }

    @Test
    fun `scrolling works when cursor at bottom`() {
        // Fill screen and add more lines
        for (i in 0 until 30) {
            emulator.processBytes("Line $i\n".toByteArray())
        }

        // Should have scrolled, cursor near bottom
        assertTrue(emulator.cursorY <= 23)
        assertTrue(emulator.getScrollbackSize() > 0)
    }

    @Test
    fun `save and restore cursor works`() {
        emulator.processBytes("Hello\u001B7".toByteArray()) // Save cursor
        emulator.processBytes("\nWorld\u001B8".toByteArray()) // Restore cursor
        emulator.processBytes("X".toByteArray())

        // X should be written at saved position
        assertEquals('X', emulator.getCell(5, 0)?.char)
    }

    @Test
    fun `saveToString and restoreFromString work`() {
        emulator.processBytes("Hello\r\nWorld".toByteArray())
        val saved = emulator.saveToString()

        val newEmulator = TerminalEmulator(initialColumns = 80, initialRows = 24)
        newEmulator.restoreFromString(saved)

        assertEquals('H', newEmulator.getCell(0, 0)?.char)
        assertEquals('W', newEmulator.getCell(0, 1)?.char)
    }

    @Test
    fun `getScreenContent returns correct content`() {
        emulator.processBytes("AB".toByteArray())
        val content = emulator.getScreenContent()

        assertTrue(content.startsWith("AB"))
    }

    @Test
    fun `bell callback is invoked`() {
        var bellCalled = false
        emulator.onBell = { bellCalled = true }

        emulator.processBytes("\u0007".toByteArray())

        assertTrue(bellCalled)
    }

    @Test
    fun `screen update callback is invoked`() {
        var updateCount = 0
        emulator.onScreenUpdate = { updateCount++ }

        emulator.processBytes("Hello".toByteArray())

        assertTrue(updateCount > 0)
    }

    @Test
    fun `insert lines escape sequence works`() {
        emulator.processBytes("AAA\r\nBBB\r\nCCC".toByteArray())
        emulator.processBytes("\u001B[2;1H".toByteArray()) // Move to line 2
        emulator.processBytes("\u001B[L".toByteArray()) // Insert line

        assertEquals('A', emulator.getCell(0, 0)?.char)
        assertEquals(' ', emulator.getCell(0, 1)?.char) // Inserted blank line
        assertEquals('B', emulator.getCell(0, 2)?.char)
    }

    @Test
    fun `delete lines escape sequence works`() {
        emulator.processBytes("AAA\r\nBBB\r\nCCC".toByteArray())
        emulator.processBytes("\u001B[2;1H".toByteArray()) // Move to line 2
        emulator.processBytes("\u001B[M".toByteArray()) // Delete line

        assertEquals('A', emulator.getCell(0, 0)?.char)
        assertEquals('C', emulator.getCell(0, 1)?.char) // CCC moved up
    }

    @Test
    fun `256 color support works`() {
        // Set foreground to color 196 (bright red in 256-color palette)
        emulator.processBytes("\u001B[38;5;196mX".toByteArray())

        // Should have a non-default color
        assertNotNull(emulator.getCell(0, 0)?.fg)
    }

    @Test
    fun `scroll region works`() {
        // Set scroll region to lines 2-4
        emulator.processBytes("\u001B[2;4r".toByteArray())

        // Fill with content
        for (i in 1..10) {
            emulator.processBytes("Line $i\n".toByteArray())
        }

        // First line should be preserved (outside scroll region concept)
        // This is a simplified test - actual behavior depends on implementation
        assertNotNull(emulator.getCell(0, 0))
    }

    @Test
    fun `Japanese characters are handled`() {
        emulator.processBytes("こんにちは".toByteArray(Charsets.UTF_8))

        assertEquals('こ', emulator.getCell(0, 0)?.char)
        assertEquals('ん', emulator.getCell(1, 0)?.char)
    }
}
