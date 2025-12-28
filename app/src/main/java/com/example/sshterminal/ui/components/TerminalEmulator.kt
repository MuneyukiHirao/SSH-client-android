package com.example.sshterminal.ui.components

import android.graphics.Color
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.min

/**
 * Simple terminal emulator with VT100/xterm support
 * Handles escape sequences and maintains a scrollback buffer
 * Thread-safe for concurrent access from UI and IO threads
 */
class TerminalEmulator(
    initialColumns: Int = 80,
    initialRows: Int = 24,
    private val scrollbackLines: Int = 10000
) {
    // Lock for thread-safe access to screen buffer and cursor
    private val lock = ReentrantReadWriteLock()

    @Volatile
    var columns: Int = initialColumns
        private set
    @Volatile
    var rows: Int = initialRows
        private set

    // Screen buffer: each cell contains a character and attributes
    private var screen: Array<Array<TerminalCell>> = createScreen(columns, rows)
    private var scrollback: MutableList<Array<TerminalCell>> = mutableListOf()

    // Cursor position (0-indexed) - volatile for thread-safe reads
    @Volatile
    var cursorX: Int = 0
        private set
    @Volatile
    var cursorY: Int = 0
        private set

    // Current text attributes
    private var currentFg: Int = Color.WHITE
    private var currentBg: Int = Color.BLACK
    private var currentBold: Boolean = false
    private var currentUnderline: Boolean = false
    private var currentReverse: Boolean = false

    // Escape sequence state
    private var escapeState: EscapeState = EscapeState.NORMAL
    private var escapeBuffer: StringBuilder = StringBuilder()

    // Saved cursor position
    private var savedCursorX: Int = 0
    private var savedCursorY: Int = 0

    // Scroll region
    private var scrollTop: Int = 0
    private var scrollBottom: Int = rows - 1

    // Dirty tracking for performance optimization
    private var isDirty: Boolean = false
    private var dirtyMinX: Int = Int.MAX_VALUE
    private var dirtyMaxX: Int = Int.MIN_VALUE
    private var dirtyMinY: Int = Int.MAX_VALUE
    private var dirtyMaxY: Int = Int.MIN_VALUE

    // Callbacks
    var onScreenUpdate: (() -> Unit)? = null
    var onBell: (() -> Unit)? = null
    var onTitleChange: ((String) -> Unit)? = null

    /**
     * Check if screen needs redraw
     */
    fun needsRedraw(): Boolean = isDirty

    /**
     * Get dirty region bounds
     * Returns null if no dirty region
     */
    fun getDirtyRegion(): DirtyRegion? {
        if (!isDirty) return null
        return DirtyRegion(
            minX = dirtyMinX.coerceIn(0, columns - 1),
            maxX = dirtyMaxX.coerceIn(0, columns - 1),
            minY = dirtyMinY.coerceIn(0, rows - 1),
            maxY = dirtyMaxY.coerceIn(0, rows - 1)
        )
    }

    /**
     * Clear dirty flag after redraw
     */
    fun clearDirty() {
        isDirty = false
        dirtyMinX = Int.MAX_VALUE
        dirtyMaxX = Int.MIN_VALUE
        dirtyMinY = Int.MAX_VALUE
        dirtyMaxY = Int.MIN_VALUE
    }

    private fun markDirty(x: Int, y: Int) {
        isDirty = true
        dirtyMinX = min(dirtyMinX, x)
        dirtyMaxX = max(dirtyMaxX, x)
        dirtyMinY = min(dirtyMinY, y)
        dirtyMaxY = max(dirtyMaxY, y)
    }

    private fun markLineDirty(y: Int) {
        isDirty = true
        dirtyMinX = 0
        dirtyMaxX = columns - 1
        dirtyMinY = min(dirtyMinY, y)
        dirtyMaxY = max(dirtyMaxY, y)
    }

    private fun markFullScreenDirty() {
        isDirty = true
        dirtyMinX = 0
        dirtyMaxX = columns - 1
        dirtyMinY = 0
        dirtyMaxY = rows - 1
    }

    data class DirtyRegion(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int
    )

    private enum class EscapeState {
        NORMAL,
        ESCAPE,
        CSI,
        OSC,
        OSC_STRING
    }

    data class TerminalCell(
        var char: Char = ' ',
        var fg: Int = Color.WHITE,
        var bg: Int = Color.BLACK,
        var bold: Boolean = false,
        var underline: Boolean = false,
        var reverse: Boolean = false
    )

    private fun createScreen(cols: Int, rows: Int): Array<Array<TerminalCell>> {
        return Array(rows) { Array(cols) { TerminalCell() } }
    }

    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns <= 0 || newRows <= 0) return
        if (newColumns == columns && newRows == rows) return

        lock.write {
            val newScreen = createScreen(newColumns, newRows)

            // Copy existing content
            for (y in 0 until min(rows, newRows)) {
                for (x in 0 until min(columns, newColumns)) {
                    newScreen[y][x] = screen[y][x].copy()
                }
            }

            columns = newColumns
            rows = newRows
            screen = newScreen
            scrollTop = 0
            scrollBottom = rows - 1

            // Adjust cursor
            cursorX = min(cursorX, columns - 1)
            cursorY = min(cursorY, rows - 1)
        }

        onScreenUpdate?.invoke()
    }

    fun processBytes(data: ByteArray) {
        val text = String(data, StandardCharsets.UTF_8)
        try {
            lock.write {
                for (char in text) {
                    processChar(char)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TerminalEmulator", "Error processing bytes: ${e.message}", e)
        }
        // Invoke callback outside of lock to prevent deadlock
        try {
            onScreenUpdate?.invoke()
        } catch (e: Exception) {
            android.util.Log.e("TerminalEmulator", "Error in onScreenUpdate callback: ${e.message}", e)
        }
    }

    private fun processChar(char: Char) {
        when (escapeState) {
            EscapeState.NORMAL -> processNormalChar(char)
            EscapeState.ESCAPE -> processEscapeChar(char)
            EscapeState.CSI -> processCSI(char)
            EscapeState.OSC -> processOSC(char)
            EscapeState.OSC_STRING -> processOSCString(char)
        }
    }

    private fun processNormalChar(char: Char) {
        when (char) {
            '\u0007' -> onBell?.invoke() // Bell
            '\u0008' -> cursorX = max(0, cursorX - 1) // Backspace
            '\t' -> {
                // Move to next tab stop (every 8 columns)
                val nextTabStop = ((cursorX / 8) + 1) * 8
                cursorX = min(columns - 1, nextTabStop)
            }
            '\n' -> lineFeed()
            '\r' -> cursorX = 0
            '\u001B' -> escapeState = EscapeState.ESCAPE
            else -> {
                if (char.code >= 32) {
                    putChar(char)
                }
            }
        }
    }

    private fun processEscapeChar(char: Char) {
        escapeBuffer.clear()
        when (char) {
            '[' -> escapeState = EscapeState.CSI
            ']' -> escapeState = EscapeState.OSC
            '7' -> { // Save cursor
                savedCursorX = cursorX
                savedCursorY = cursorY
                escapeState = EscapeState.NORMAL
            }
            '8' -> { // Restore cursor
                cursorX = savedCursorX
                cursorY = savedCursorY
                escapeState = EscapeState.NORMAL
            }
            'D' -> { // Index (line feed)
                lineFeed()
                escapeState = EscapeState.NORMAL
            }
            'M' -> { // Reverse index
                reverseLineFeed()
                escapeState = EscapeState.NORMAL
            }
            'c' -> { // Reset
                reset()
                escapeState = EscapeState.NORMAL
            }
            else -> escapeState = EscapeState.NORMAL
        }
    }

    private fun processCSI(char: Char) {
        when {
            char in '0'..'9' || char == ';' || char == '?' -> {
                escapeBuffer.append(char)
            }
            else -> {
                executeCSI(char)
                escapeState = EscapeState.NORMAL
            }
        }
    }

    private fun executeCSI(command: Char) {
        val params = escapeBuffer.toString()
            .split(';')
            .filter { it.isNotEmpty() && !it.startsWith('?') }
            .map { it.toIntOrNull() ?: 0 }

        val private = escapeBuffer.startsWith("?")

        fun param(index: Int, default: Int = 1): Int =
            params.getOrElse(index) { default }.let { if (it == 0) default else it }

        when (command) {
            'A' -> cursorY = max(0, cursorY - param(0)) // Cursor up
            'B' -> cursorY = min(rows - 1, cursorY + param(0)) // Cursor down
            'C' -> cursorX = min(columns - 1, cursorX + param(0)) // Cursor forward
            'D' -> cursorX = max(0, cursorX - param(0)) // Cursor back
            'E' -> { // Cursor next line
                cursorX = 0
                cursorY = min(rows - 1, cursorY + param(0))
            }
            'F' -> { // Cursor previous line
                cursorX = 0
                cursorY = max(0, cursorY - param(0))
            }
            'G' -> cursorX = min(columns - 1, max(0, param(0) - 1)) // Cursor horizontal absolute
            'H', 'f' -> { // Cursor position
                cursorY = min(rows - 1, max(0, param(0) - 1))
                cursorX = min(columns - 1, max(0, param(1, 1) - 1))
            }
            'J' -> { // Erase in display
                when (param(0, 0)) {
                    0 -> eraseFromCursor() // Erase from cursor to end
                    1 -> eraseToCursor() // Erase from start to cursor
                    2, 3 -> eraseScreen() // Erase entire screen
                }
            }
            'K' -> { // Erase in line
                when (param(0, 0)) {
                    0 -> eraseLineFromCursor()
                    1 -> eraseLineToCursor()
                    2 -> eraseLine()
                }
            }
            'L' -> insertLines(param(0)) // Insert lines
            'M' -> deleteLines(param(0)) // Delete lines
            'P' -> deleteChars(param(0)) // Delete characters
            'S' -> scrollUp(param(0)) // Scroll up
            'T' -> scrollDown(param(0)) // Scroll down
            '@' -> insertChars(param(0)) // Insert characters
            'd' -> cursorY = min(rows - 1, max(0, param(0) - 1)) // Line position absolute
            'm' -> processSGR(params) // Set graphics rendition
            'r' -> { // Set scroll region
                scrollTop = max(0, param(0) - 1)
                scrollBottom = min(rows - 1, param(1, rows) - 1)
                cursorX = 0
                cursorY = 0
            }
            's' -> { // Save cursor position
                savedCursorX = cursorX
                savedCursorY = cursorY
            }
            'u' -> { // Restore cursor position
                cursorX = savedCursorX
                cursorY = savedCursorY
            }
            'h' -> { // Set mode
                if (private) {
                    // Handle private modes like cursor visibility
                }
            }
            'l' -> { // Reset mode
                if (private) {
                    // Handle private modes
                }
            }
        }
    }

    private fun processSGR(params: List<Int>) {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }

        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> resetAttributes()
                1 -> currentBold = true
                4 -> currentUnderline = true
                7 -> currentReverse = true
                22 -> currentBold = false
                24 -> currentUnderline = false
                27 -> currentReverse = false
                in 30..37 -> currentFg = ansiColor(p - 30)
                38 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentFg = xterm256Color(params[i + 2])
                        i += 2
                    }
                }
                39 -> currentFg = Color.WHITE
                in 40..47 -> currentBg = ansiColor(p - 40)
                48 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentBg = xterm256Color(params[i + 2])
                        i += 2
                    }
                }
                49 -> currentBg = Color.BLACK
                in 90..97 -> currentFg = ansiBrightColor(p - 90)
                in 100..107 -> currentBg = ansiBrightColor(p - 100)
            }
            i++
        }
    }

    private fun processOSC(char: Char) {
        escapeBuffer.append(char)
        if (char == ';') {
            escapeState = EscapeState.OSC_STRING
            escapeBuffer.clear()
        } else if (!char.isDigit()) {
            escapeState = EscapeState.NORMAL
        }
    }

    private fun processOSCString(char: Char) {
        when (char) {
            '\u0007', '\u001B' -> {
                // End of OSC string
                onTitleChange?.invoke(escapeBuffer.toString())
                escapeState = EscapeState.NORMAL
                escapeBuffer.clear()
            }
            else -> escapeBuffer.append(char)
        }
    }

    private fun putChar(char: Char) {
        if (cursorX >= columns) {
            cursorX = 0
            lineFeed()
        }

        // Bounds check after lineFeed (Critical fix)
        if (cursorY < 0) cursorY = 0
        if (cursorY >= rows) cursorY = rows - 1
        if (cursorX < 0) cursorX = 0
        if (cursorX >= columns) cursorX = columns - 1

        screen[cursorY][cursorX] = TerminalCell(
            char = char,
            fg = if (currentReverse) currentBg else currentFg,
            bg = if (currentReverse) currentFg else currentBg,
            bold = currentBold,
            underline = currentUnderline,
            reverse = currentReverse
        )

        markDirty(cursorX, cursorY)
        cursorX++
    }

    private fun lineFeed() {
        if (cursorY >= scrollBottom) {
            scrollUp(1)
        } else {
            cursorY++
        }
    }

    private fun reverseLineFeed() {
        if (cursorY <= scrollTop) {
            scrollDown(1)
        } else {
            cursorY--
        }
    }

    private fun scrollUp(lines: Int) {
        for (i in 0 until lines) {
            // Add top line to scrollback
            if (scrollTop == 0 && scrollback.size < scrollbackLines) {
                scrollback.add(0, screen[scrollTop].map { it.copy() }.toTypedArray())
            }

            // Shift lines up
            for (y in scrollTop until scrollBottom) {
                screen[y] = screen[y + 1]
            }

            // Clear bottom line
            screen[scrollBottom] = Array(columns) { TerminalCell() }
        }
        // Mark scroll region as dirty
        for (y in scrollTop..scrollBottom) {
            markLineDirty(y)
        }
    }

    private fun scrollDown(lines: Int) {
        for (i in 0 until lines) {
            // Shift lines down
            for (y in scrollBottom downTo scrollTop + 1) {
                screen[y] = screen[y - 1]
            }

            // Clear top line
            screen[scrollTop] = Array(columns) { TerminalCell() }
        }
        // Mark scroll region as dirty
        for (y in scrollTop..scrollBottom) {
            markLineDirty(y)
        }
    }

    private fun eraseScreen() {
        screen = createScreen(columns, rows)
        markFullScreenDirty()
    }

    private fun eraseFromCursor() {
        eraseLineFromCursor()
        for (y in cursorY + 1 until rows) {
            for (x in 0 until columns) {
                screen[y][x] = TerminalCell()
            }
            markLineDirty(y)
        }
    }

    private fun eraseToCursor() {
        eraseLineToCursor()
        for (y in 0 until cursorY) {
            for (x in 0 until columns) {
                screen[y][x] = TerminalCell()
            }
            markLineDirty(y)
        }
    }

    private fun eraseLine() {
        for (x in 0 until columns) {
            screen[cursorY][x] = TerminalCell()
        }
        markLineDirty(cursorY)
    }

    private fun eraseLineFromCursor() {
        for (x in cursorX until columns) {
            screen[cursorY][x] = TerminalCell()
            markDirty(x, cursorY)
        }
    }

    private fun eraseLineToCursor() {
        for (x in 0..cursorX) {
            screen[cursorY][x] = TerminalCell()
            markDirty(x, cursorY)
        }
    }

    private fun insertLines(count: Int) {
        for (i in 0 until count) {
            for (y in scrollBottom downTo cursorY + 1) {
                screen[y] = screen[y - 1]
            }
            screen[cursorY] = Array(columns) { TerminalCell() }
        }
        // Mark affected lines as dirty
        for (y in cursorY..scrollBottom) {
            markLineDirty(y)
        }
    }

    private fun deleteLines(count: Int) {
        for (i in 0 until count) {
            for (y in cursorY until scrollBottom) {
                screen[y] = screen[y + 1]
            }
            screen[scrollBottom] = Array(columns) { TerminalCell() }
        }
        // Mark affected lines as dirty
        for (y in cursorY..scrollBottom) {
            markLineDirty(y)
        }
    }

    private fun insertChars(count: Int) {
        for (x in columns - 1 downTo cursorX + count) {
            screen[cursorY][x] = screen[cursorY][x - count]
        }
        for (x in cursorX until min(cursorX + count, columns)) {
            screen[cursorY][x] = TerminalCell()
        }
        markLineDirty(cursorY)
    }

    private fun deleteChars(count: Int) {
        for (x in cursorX until columns - count) {
            screen[cursorY][x] = screen[cursorY][x + count]
        }
        for (x in columns - count until columns) {
            screen[cursorY][x] = TerminalCell()
        }
        markLineDirty(cursorY)
    }

    private fun resetAttributes() {
        currentFg = Color.WHITE
        currentBg = Color.BLACK
        currentBold = false
        currentUnderline = false
        currentReverse = false
    }

    private fun reset() {
        screen = createScreen(columns, rows)
        cursorX = 0
        cursorY = 0
        scrollTop = 0
        scrollBottom = rows - 1
        resetAttributes()
        escapeState = EscapeState.NORMAL
        escapeBuffer.clear()
        markFullScreenDirty()
    }

    private fun ansiColor(index: Int): Int {
        return when (index) {
            0 -> Color.BLACK
            1 -> Color.RED
            2 -> Color.GREEN
            3 -> Color.YELLOW
            4 -> Color.BLUE
            5 -> Color.MAGENTA
            6 -> Color.CYAN
            7 -> Color.WHITE
            else -> Color.WHITE
        }
    }

    private fun ansiBrightColor(index: Int): Int {
        return when (index) {
            0 -> Color.DKGRAY
            1 -> Color.rgb(255, 85, 85)
            2 -> Color.rgb(85, 255, 85)
            3 -> Color.rgb(255, 255, 85)
            4 -> Color.rgb(85, 85, 255)
            5 -> Color.rgb(255, 85, 255)
            6 -> Color.rgb(85, 255, 255)
            7 -> Color.rgb(255, 255, 255)
            else -> Color.WHITE
        }
    }

    private fun xterm256Color(index: Int): Int {
        return when {
            index < 16 -> if (index < 8) ansiColor(index) else ansiBrightColor(index - 8)
            index < 232 -> {
                // 6x6x6 color cube
                val i = index - 16
                val r = (i / 36) * 51
                val g = ((i / 6) % 6) * 51
                val b = (i % 6) * 51
                Color.rgb(r, g, b)
            }
            else -> {
                // Grayscale
                val gray = (index - 232) * 10 + 8
                Color.rgb(gray, gray, gray)
            }
        }
    }

    fun getCell(x: Int, y: Int): TerminalCell? {
        return try {
            lock.read {
                screen.getOrNull(y)?.getOrNull(x)?.copy()
            }
        } catch (e: Exception) {
            android.util.Log.e("TerminalEmulator", "Error in getCell: ${e.message}")
            null
        }
    }

    fun getLine(y: Int): Array<TerminalCell>? {
        return try {
            // Use tryLock with timeout to prevent UI thread blocking
            if (lock.readLock().tryLock(10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                try {
                    screen.getOrNull(y)?.map { it.copy() }?.toTypedArray()
                } finally {
                    lock.readLock().unlock()
                }
            } else {
                // If we can't get the lock quickly, return null to prevent freeze
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("TerminalEmulator", "Error in getLine: ${e.message}")
            null
        }
    }

    fun getScrollbackLine(index: Int): Array<TerminalCell>? {
        return lock.read {
            scrollback.getOrNull(index)?.map { it.copy() }?.toTypedArray()
        }
    }

    fun getScrollbackSize(): Int = lock.read { scrollback.size }

    fun getScreenContent(): String {
        return lock.read {
            val sb = StringBuilder()
            for (y in 0 until rows) {
                for (x in 0 until columns) {
                    sb.append(screen[y][x].char)
                }
                sb.append('\n')
            }
            sb.toString()
        }
    }

    // Persistence methods
    fun getScrollbackLines(): Int = lock.read { scrollback.size }

    /**
     * Save terminal state to a string for persistence
     */
    fun saveToString(): String {
        return lock.read {
            val sb = StringBuilder()
            // Save screen content (simplified - just characters)
            for (y in 0 until rows) {
                for (x in 0 until columns) {
                    sb.append(screen[y][x].char)
                }
                if (y < rows - 1) sb.append('\n')
            }
            sb.toString()
        }
    }

    /**
     * Restore terminal state from a string
     */
    fun restoreFromString(content: String) {
        lock.write {
            val lines = content.split('\n')
            for ((y, line) in lines.withIndex()) {
                if (y >= rows) break
                for ((x, char) in line.withIndex()) {
                    if (x >= columns) break
                    screen[y][x] = TerminalCell(char = char)
                }
            }
        }
        onScreenUpdate?.invoke()
    }
}
