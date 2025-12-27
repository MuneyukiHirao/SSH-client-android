package com.example.sshterminal.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.TerminalColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Custom terminal view with full Japanese IME support
 * Handles 12-key input, romaji input, and composition text
 */
class TerminalAndroidView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var emulator = TerminalEmulator()

    // Color scheme
    private var terminalColors: TerminalColors = TerminalColors.fromScheme(TerminalColorScheme.DEFAULT)

    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textSize = 36f
        color = terminalColors.foreground
    }

    private val bgPaint = Paint()
    private val cursorPaint = Paint().apply {
        color = terminalColors.cursorColor
        style = Paint.Style.FILL
    }

    private var charWidth: Float = 0f
    private var charHeight: Float = 0f
    private var charBaseline: Float = 0f

    // IME composition state
    private var composingText: String = ""
    private var composingStart: Int = 0

    // Callbacks
    var onSendData: ((ByteArray) -> Unit)? = null
    var onSizeChanged: ((Int, Int) -> Unit)? = null

    private val _inputEvents = MutableSharedFlow<String>()
    val inputEvents: SharedFlow<String> = _inputEvents

    init {
        isFocusable = true
        isFocusableInTouchMode = true

        // Enable hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)

        emulator.onScreenUpdate = {
            // Use dirty region for partial invalidation when available
            val dirtyRegion = emulator.getDirtyRegion()
            if (dirtyRegion != null && charWidth > 0 && charHeight > 0) {
                val left = (dirtyRegion.minX * charWidth).toInt()
                val top = (dirtyRegion.minY * charHeight).toInt()
                val right = ((dirtyRegion.maxX + 1) * charWidth).toInt()
                val bottom = ((dirtyRegion.maxY + 1) * charHeight).toInt()
                postInvalidate(left, top, right, bottom)
            } else {
                postInvalidate()
            }
        }

        emulator.onBell = {
            // Haptic feedback for bell
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        updateCharMetrics()
    }

    private fun updateCharMetrics() {
        val fm = textPaint.fontMetrics
        charWidth = textPaint.measureText("W")
        charHeight = fm.descent - fm.ascent + fm.leading
        charBaseline = -fm.ascent
    }

    fun setTextSize(size: Float) {
        textPaint.textSize = size
        updateCharMetrics()
        updateTerminalSize()
        invalidate()
    }

    fun processData(data: ByteArray) {
        emulator.processBytes(data)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSize()
    }

    private fun updateTerminalSize() {
        if (width > 0 && height > 0 && charWidth > 0 && charHeight > 0) {
            val cols = (width / charWidth).toInt()
            val rows = (height / charHeight).toInt()

            if (cols > 0 && rows > 0) {
                emulator.resize(cols, rows)
                onSizeChanged?.invoke(cols, rows)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawColor(terminalColors.background)

        // Draw terminal content
        for (y in 0 until emulator.rows) {
            val line = emulator.getLine(y) ?: continue
            val yPos = y * charHeight + charBaseline

            for (x in 0 until emulator.columns) {
                val cell = line[x]
                val xPos = x * charWidth

                // Draw cell background if different from terminal background
                val cellBg = mapCellColor(cell.bg, true)
                if (cellBg != terminalColors.background) {
                    bgPaint.color = cellBg
                    canvas.drawRect(
                        xPos,
                        y * charHeight,
                        xPos + charWidth,
                        (y + 1) * charHeight,
                        bgPaint
                    )
                }

                // Draw character
                if (cell.char != ' ') {
                    textPaint.color = mapCellColor(cell.fg, false)
                    textPaint.isFakeBoldText = cell.bold
                    textPaint.isUnderlineText = cell.underline
                    canvas.drawText(cell.char.toString(), xPos, yPos, textPaint)
                }
            }
        }

        // Draw cursor
        if (isFocused) {
            val cursorX = emulator.cursorX * charWidth
            val cursorY = emulator.cursorY * charHeight

            cursorPaint.color = terminalColors.cursorColor
            cursorPaint.alpha = 180
            canvas.drawRect(
                cursorX,
                cursorY,
                cursorX + charWidth,
                cursorY + charHeight,
                cursorPaint
            )
        }

        // Draw composition text (IME input in progress)
        if (composingText.isNotEmpty()) {
            val compX = emulator.cursorX * charWidth
            val compY = emulator.cursorY * charHeight + charBaseline

            // Background for composition
            bgPaint.color = terminalColors.selectionBackground
            val compWidth = textPaint.measureText(composingText)
            canvas.drawRect(
                compX,
                emulator.cursorY * charHeight,
                compX + compWidth,
                (emulator.cursorY + 1) * charHeight,
                bgPaint
            )

            // Draw composition text with underline
            textPaint.color = terminalColors.brightYellow
            textPaint.isUnderlineText = true
            canvas.drawText(composingText, compX, compY, textPaint)
            textPaint.isUnderlineText = false
        }

        // Clear dirty flag after drawing
        emulator.clearDirty()
    }

    /**
     * Map cell color to terminal color scheme.
     * If the cell uses default colors (black bg, white fg), use the scheme colors.
     * Otherwise, try to map ANSI colors to scheme colors.
     */
    private fun mapCellColor(color: Int, isBackground: Boolean): Int {
        return when (color) {
            android.graphics.Color.BLACK -> if (isBackground) terminalColors.background else terminalColors.black
            android.graphics.Color.WHITE -> if (isBackground) terminalColors.white else terminalColors.foreground
            android.graphics.Color.RED -> terminalColors.red
            android.graphics.Color.GREEN -> terminalColors.green
            android.graphics.Color.YELLOW -> terminalColors.yellow
            android.graphics.Color.BLUE -> terminalColors.blue
            android.graphics.Color.MAGENTA -> terminalColors.magenta
            android.graphics.Color.CYAN -> terminalColors.cyan
            // Bright colors
            0xFFFF0000.toInt() -> terminalColors.brightRed
            0xFF00FF00.toInt() -> terminalColors.brightGreen
            0xFFFFFF00.toInt() -> terminalColors.brightYellow
            0xFF0000FF.toInt() -> terminalColors.brightBlue
            0xFFFF00FF.toInt() -> terminalColors.brightMagenta
            0xFF00FFFF.toInt() -> terminalColors.brightCyan
            // Default: use the original color (for 256-color or true color)
            else -> color
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                // Request focus and show keyboard
                requestFocus()
                showKeyboard()
            }
        }
        return true
    }

    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.apply {
            // Configure for Japanese IME compatibility
            inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
                         EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                         EditorInfo.IME_ACTION_NONE

            // Important: Allow IME to process all input
            initialSelStart = 0
            initialSelEnd = 0
        }

        return TerminalInputConnection(this)
    }

    /**
     * Custom InputConnection for handling Japanese IME input
     * Supports 12-key (flick), romaji, and direct kana input
     */
    private inner class TerminalInputConnection(
        targetView: View
    ) : BaseInputConnection(targetView, true) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            // Clear composition state
            composingText = ""
            composingStart = 0

            // Send committed text to terminal
            text?.let { t ->
                sendText(t.toString())
            }

            invalidate()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            // Update composition display
            composingText = text?.toString() ?: ""

            invalidate()
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composingText.isNotEmpty()) {
                sendText(composingText)
                composingText = ""
            }
            invalidate()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            // Send backspace for each character to delete
            repeat(beforeLength) {
                sendData(byteArrayOf(0x7F)) // DEL
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event == null) return false

            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_ENTER -> {
                        sendData(byteArrayOf(0x0D)) // CR
                        return true
                    }
                    KeyEvent.KEYCODE_DEL -> {
                        sendData(byteArrayOf(0x7F)) // DEL
                        return true
                    }
                    KeyEvent.KEYCODE_TAB -> {
                        sendData(byteArrayOf(0x09)) // TAB
                        return true
                    }
                    KeyEvent.KEYCODE_ESCAPE -> {
                        sendData(byteArrayOf(0x1B)) // ESC
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        sendData("\u001B[A".toByteArray())
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        sendData("\u001B[B".toByteArray())
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        sendData("\u001B[C".toByteArray())
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        sendData("\u001B[D".toByteArray())
                        return true
                    }
                }

                // Handle Ctrl key combinations
                if (event.isCtrlPressed) {
                    val char = event.unicodeChar
                    if (char in 'a'.code..'z'.code) {
                        sendData(byteArrayOf((char - 'a'.code + 1).toByte()))
                        return true
                    }
                    if (char in 'A'.code..'Z'.code) {
                        sendData(byteArrayOf((char - 'A'.code + 1).toByte()))
                        return true
                    }
                }
            }

            return super.sendKeyEvent(event)
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            sendData(byteArrayOf(0x0D)) // Enter
            return true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                sendData(byteArrayOf(0x0D))
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                sendData(byteArrayOf(0x7F))
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                sendData(byteArrayOf(0x09))
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                sendData(byteArrayOf(0x1B))
                return true
            }
        }

        // Handle physical keyboard input
        val char = event.unicodeChar
        if (char != 0) {
            if (event.isCtrlPressed && char in 'a'.code..'z'.code) {
                sendData(byteArrayOf((char - 'a'.code + 1).toByte()))
            } else if (event.isCtrlPressed && char in 'A'.code..'Z'.code) {
                sendData(byteArrayOf((char - 'A'.code + 1).toByte()))
            } else {
                sendText(char.toChar().toString())
            }
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun sendText(text: String) {
        sendData(text.toByteArray(Charsets.UTF_8))
    }

    private fun sendData(data: ByteArray) {
        onSendData?.invoke(data)
    }

    // Special key methods for toolbar
    fun sendCtrl(char: Char) {
        val code = when {
            char in 'a'..'z' -> char.code - 'a'.code + 1
            char in 'A'..'Z' -> char.code - 'A'.code + 1
            else -> return
        }
        sendData(byteArrayOf(code.toByte()))
    }

    fun sendEscape() {
        sendData(byteArrayOf(0x1B))
    }

    fun sendTab() {
        sendData(byteArrayOf(0x09))
    }

    fun sendArrow(direction: String) {
        val code = when (direction) {
            "up" -> "\u001B[A"
            "down" -> "\u001B[B"
            "right" -> "\u001B[C"
            "left" -> "\u001B[D"
            else -> return
        }
        sendData(code.toByteArray())
    }

    fun showSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun sendFunctionKey(num: Int) {
        val code = when (num) {
            1 -> "\u001BOP"
            2 -> "\u001BOQ"
            3 -> "\u001BOR"
            4 -> "\u001BOS"
            5 -> "\u001B[15~"
            6 -> "\u001B[17~"
            7 -> "\u001B[18~"
            8 -> "\u001B[19~"
            9 -> "\u001B[20~"
            10 -> "\u001B[21~"
            11 -> "\u001B[23~"
            12 -> "\u001B[24~"
            else -> return
        }
        sendData(code.toByteArray())
    }

    fun getTerminalColumns(): Int = emulator.columns
    fun getTerminalRows(): Int = emulator.rows

    /**
     * Set the terminal emulator to use (for session restore)
     */
    fun setTerminalEmulator(newEmulator: TerminalEmulator) {
        emulator = newEmulator
        emulator.onScreenUpdate = {
            postInvalidate()
        }
        emulator.onBell = {
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        invalidate()
    }

    /**
     * Get the current terminal emulator
     */
    fun getTerminalEmulator(): TerminalEmulator = emulator

    /**
     * Set the color scheme for the terminal
     */
    fun setColorScheme(scheme: TerminalColorScheme) {
        terminalColors = TerminalColors.fromScheme(scheme)
        textPaint.color = terminalColors.foreground
        cursorPaint.color = terminalColors.cursorColor
        invalidate()
    }

    /**
     * Set the color scheme from TerminalColors directly
     */
    fun setTerminalColors(colors: TerminalColors) {
        terminalColors = colors
        textPaint.color = terminalColors.foreground
        cursorPaint.color = terminalColors.cursorColor
        invalidate()
    }

    /**
     * Get the current terminal colors
     */
    fun getTerminalColors(): TerminalColors = terminalColors
}

/**
 * Compose wrapper for TerminalAndroidView
 */
@Composable
fun TerminalView(
    modifier: Modifier = Modifier,
    colorScheme: TerminalColorScheme = TerminalColorScheme.DEFAULT,
    onSendData: (ByteArray) -> Unit,
    onSizeChanged: (cols: Int, rows: Int) -> Unit,
    onViewCreated: (TerminalAndroidView) -> Unit = {}
) {
    val context = LocalContext.current

    val terminalView = remember {
        TerminalAndroidView(context).apply {
            this.onSendData = onSendData
            this.onSizeChanged = onSizeChanged
            setColorScheme(colorScheme)
        }
    }

    // Update color scheme when it changes
    LaunchedEffect(colorScheme) {
        terminalView.setColorScheme(colorScheme)
    }

    DisposableEffect(terminalView) {
        onViewCreated(terminalView)
        onDispose { }
    }

    AndroidView(
        factory = { terminalView },
        modifier = modifier.fillMaxSize()
    )
}
