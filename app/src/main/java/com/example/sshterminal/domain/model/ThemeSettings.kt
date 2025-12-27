package com.example.sshterminal.domain.model

/**
 * App theme mode setting
 */
enum class ThemeMode(val value: Int, val displayName: String) {
    LIGHT(0, "Light"),
    DARK(1, "Dark"),
    SYSTEM(2, "System");

    companion object {
        fun fromValue(value: Int): ThemeMode {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}

/**
 * Terminal color scheme presets
 */
enum class TerminalColorScheme(val value: Int, val displayName: String) {
    DEFAULT(0, "Default"),
    SOLARIZED_DARK(1, "Solarized Dark"),
    SOLARIZED_LIGHT(2, "Solarized Light"),
    MONOKAI(3, "Monokai"),
    DRACULA(4, "Dracula"),
    NORD(5, "Nord"),
    GRUVBOX_DARK(6, "Gruvbox Dark");

    companion object {
        fun fromValue(value: Int): TerminalColorScheme {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}

/**
 * Color palette for terminal rendering
 */
data class TerminalColors(
    val background: Int,
    val foreground: Int,
    val cursorColor: Int,
    val selectionBackground: Int,
    // ANSI colors (0-7: normal, 8-15: bright)
    val black: Int,
    val red: Int,
    val green: Int,
    val yellow: Int,
    val blue: Int,
    val magenta: Int,
    val cyan: Int,
    val white: Int,
    val brightBlack: Int,
    val brightRed: Int,
    val brightGreen: Int,
    val brightYellow: Int,
    val brightBlue: Int,
    val brightMagenta: Int,
    val brightCyan: Int,
    val brightWhite: Int
) {
    companion object {
        fun fromScheme(scheme: TerminalColorScheme): TerminalColors {
            return when (scheme) {
                TerminalColorScheme.DEFAULT -> defaultColors()
                TerminalColorScheme.SOLARIZED_DARK -> solarizedDark()
                TerminalColorScheme.SOLARIZED_LIGHT -> solarizedLight()
                TerminalColorScheme.MONOKAI -> monokai()
                TerminalColorScheme.DRACULA -> dracula()
                TerminalColorScheme.NORD -> nord()
                TerminalColorScheme.GRUVBOX_DARK -> gruvboxDark()
            }
        }

        private fun defaultColors() = TerminalColors(
            background = 0xFF000000.toInt(),
            foreground = 0xFFFFFFFF.toInt(),
            cursorColor = 0xFF00FF00.toInt(),
            selectionBackground = 0xFF444444.toInt(),
            black = 0xFF000000.toInt(),
            red = 0xFFCD0000.toInt(),
            green = 0xFF00CD00.toInt(),
            yellow = 0xFFCDCD00.toInt(),
            blue = 0xFF0000EE.toInt(),
            magenta = 0xFFCD00CD.toInt(),
            cyan = 0xFF00CDCD.toInt(),
            white = 0xFFE5E5E5.toInt(),
            brightBlack = 0xFF7F7F7F.toInt(),
            brightRed = 0xFFFF0000.toInt(),
            brightGreen = 0xFF00FF00.toInt(),
            brightYellow = 0xFFFFFF00.toInt(),
            brightBlue = 0xFF5C5CFF.toInt(),
            brightMagenta = 0xFFFF00FF.toInt(),
            brightCyan = 0xFF00FFFF.toInt(),
            brightWhite = 0xFFFFFFFF.toInt()
        )

        private fun solarizedDark() = TerminalColors(
            background = 0xFF002B36.toInt(),
            foreground = 0xFF839496.toInt(),
            cursorColor = 0xFF93A1A1.toInt(),
            selectionBackground = 0xFF073642.toInt(),
            black = 0xFF073642.toInt(),
            red = 0xFFDC322F.toInt(),
            green = 0xFF859900.toInt(),
            yellow = 0xFFB58900.toInt(),
            blue = 0xFF268BD2.toInt(),
            magenta = 0xFFD33682.toInt(),
            cyan = 0xFF2AA198.toInt(),
            white = 0xFFEEE8D5.toInt(),
            brightBlack = 0xFF002B36.toInt(),
            brightRed = 0xFFCB4B16.toInt(),
            brightGreen = 0xFF586E75.toInt(),
            brightYellow = 0xFF657B83.toInt(),
            brightBlue = 0xFF839496.toInt(),
            brightMagenta = 0xFF6C71C4.toInt(),
            brightCyan = 0xFF93A1A1.toInt(),
            brightWhite = 0xFFFDF6E3.toInt()
        )

        private fun solarizedLight() = TerminalColors(
            background = 0xFFFDF6E3.toInt(),
            foreground = 0xFF657B83.toInt(),
            cursorColor = 0xFF586E75.toInt(),
            selectionBackground = 0xFFEEE8D5.toInt(),
            black = 0xFFEEE8D5.toInt(),
            red = 0xFFDC322F.toInt(),
            green = 0xFF859900.toInt(),
            yellow = 0xFFB58900.toInt(),
            blue = 0xFF268BD2.toInt(),
            magenta = 0xFFD33682.toInt(),
            cyan = 0xFF2AA198.toInt(),
            white = 0xFF073642.toInt(),
            brightBlack = 0xFFFDF6E3.toInt(),
            brightRed = 0xFFCB4B16.toInt(),
            brightGreen = 0xFF93A1A1.toInt(),
            brightYellow = 0xFF839496.toInt(),
            brightBlue = 0xFF657B83.toInt(),
            brightMagenta = 0xFF6C71C4.toInt(),
            brightCyan = 0xFF586E75.toInt(),
            brightWhite = 0xFF002B36.toInt()
        )

        private fun monokai() = TerminalColors(
            background = 0xFF272822.toInt(),
            foreground = 0xFFF8F8F2.toInt(),
            cursorColor = 0xFFF8F8F0.toInt(),
            selectionBackground = 0xFF49483E.toInt(),
            black = 0xFF272822.toInt(),
            red = 0xFFF92672.toInt(),
            green = 0xFFA6E22E.toInt(),
            yellow = 0xFFF4BF75.toInt(),
            blue = 0xFF66D9EF.toInt(),
            magenta = 0xFFAE81FF.toInt(),
            cyan = 0xFFA1EFE4.toInt(),
            white = 0xFFF8F8F2.toInt(),
            brightBlack = 0xFF75715E.toInt(),
            brightRed = 0xFFF92672.toInt(),
            brightGreen = 0xFFA6E22E.toInt(),
            brightYellow = 0xFFF4BF75.toInt(),
            brightBlue = 0xFF66D9EF.toInt(),
            brightMagenta = 0xFFAE81FF.toInt(),
            brightCyan = 0xFFA1EFE4.toInt(),
            brightWhite = 0xFFF9F8F5.toInt()
        )

        private fun dracula() = TerminalColors(
            background = 0xFF282A36.toInt(),
            foreground = 0xFFF8F8F2.toInt(),
            cursorColor = 0xFFF8F8F2.toInt(),
            selectionBackground = 0xFF44475A.toInt(),
            black = 0xFF21222C.toInt(),
            red = 0xFFFF5555.toInt(),
            green = 0xFF50FA7B.toInt(),
            yellow = 0xFFF1FA8C.toInt(),
            blue = 0xFFBD93F9.toInt(),
            magenta = 0xFFFF79C6.toInt(),
            cyan = 0xFF8BE9FD.toInt(),
            white = 0xFFF8F8F2.toInt(),
            brightBlack = 0xFF6272A4.toInt(),
            brightRed = 0xFFFF6E6E.toInt(),
            brightGreen = 0xFF69FF94.toInt(),
            brightYellow = 0xFFFFFFA5.toInt(),
            brightBlue = 0xFFD6ACFF.toInt(),
            brightMagenta = 0xFFFF92DF.toInt(),
            brightCyan = 0xFFA4FFFF.toInt(),
            brightWhite = 0xFFFFFFFF.toInt()
        )

        private fun nord() = TerminalColors(
            background = 0xFF2E3440.toInt(),
            foreground = 0xFFD8DEE9.toInt(),
            cursorColor = 0xFFD8DEE9.toInt(),
            selectionBackground = 0xFF434C5E.toInt(),
            black = 0xFF3B4252.toInt(),
            red = 0xFFBF616A.toInt(),
            green = 0xFFA3BE8C.toInt(),
            yellow = 0xFFEBCB8B.toInt(),
            blue = 0xFF81A1C1.toInt(),
            magenta = 0xFFB48EAD.toInt(),
            cyan = 0xFF88C0D0.toInt(),
            white = 0xFFE5E9F0.toInt(),
            brightBlack = 0xFF4C566A.toInt(),
            brightRed = 0xFFBF616A.toInt(),
            brightGreen = 0xFFA3BE8C.toInt(),
            brightYellow = 0xFFEBCB8B.toInt(),
            brightBlue = 0xFF81A1C1.toInt(),
            brightMagenta = 0xFFB48EAD.toInt(),
            brightCyan = 0xFF8FBCBB.toInt(),
            brightWhite = 0xFFECEFF4.toInt()
        )

        private fun gruvboxDark() = TerminalColors(
            background = 0xFF282828.toInt(),
            foreground = 0xFFEBDBB2.toInt(),
            cursorColor = 0xFFEBDBB2.toInt(),
            selectionBackground = 0xFF504945.toInt(),
            black = 0xFF282828.toInt(),
            red = 0xFFCC241D.toInt(),
            green = 0xFF98971A.toInt(),
            yellow = 0xFFD79921.toInt(),
            blue = 0xFF458588.toInt(),
            magenta = 0xFFB16286.toInt(),
            cyan = 0xFF689D6A.toInt(),
            white = 0xFFA89984.toInt(),
            brightBlack = 0xFF928374.toInt(),
            brightRed = 0xFFFB4934.toInt(),
            brightGreen = 0xFFB8BB26.toInt(),
            brightYellow = 0xFFFABD2F.toInt(),
            brightBlue = 0xFF83A598.toInt(),
            brightMagenta = 0xFFD3869B.toInt(),
            brightCyan = 0xFF8EC07C.toInt(),
            brightWhite = 0xFFEBDBB2.toInt()
        )
    }

    /**
     * Get ANSI color by index (0-15)
     */
    fun getAnsiColor(index: Int): Int {
        return when (index) {
            0 -> black
            1 -> red
            2 -> green
            3 -> yellow
            4 -> blue
            5 -> magenta
            6 -> cyan
            7 -> white
            8 -> brightBlack
            9 -> brightRed
            10 -> brightGreen
            11 -> brightYellow
            12 -> brightBlue
            13 -> brightMagenta
            14 -> brightCyan
            15 -> brightWhite
            else -> foreground
        }
    }
}
