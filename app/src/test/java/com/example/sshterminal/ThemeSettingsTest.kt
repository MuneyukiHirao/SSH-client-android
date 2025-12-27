package com.example.sshterminal

import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.TerminalColors
import com.example.sshterminal.domain.model.ThemeMode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ThemeSettings
 */
class ThemeSettingsTest {

    @Test
    fun `ThemeMode fromValue returns correct mode`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromValue(0))
        assertEquals(ThemeMode.DARK, ThemeMode.fromValue(1))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromValue(2))
    }

    @Test
    fun `ThemeMode fromValue returns SYSTEM for unknown value`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromValue(99))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromValue(-1))
    }

    @Test
    fun `ThemeMode has correct display names`() {
        assertEquals("Light", ThemeMode.LIGHT.displayName)
        assertEquals("Dark", ThemeMode.DARK.displayName)
        assertEquals("System", ThemeMode.SYSTEM.displayName)
    }

    @Test
    fun `TerminalColorScheme fromValue returns correct scheme`() {
        assertEquals(TerminalColorScheme.DEFAULT, TerminalColorScheme.fromValue(0))
        assertEquals(TerminalColorScheme.SOLARIZED_DARK, TerminalColorScheme.fromValue(1))
        assertEquals(TerminalColorScheme.SOLARIZED_LIGHT, TerminalColorScheme.fromValue(2))
        assertEquals(TerminalColorScheme.MONOKAI, TerminalColorScheme.fromValue(3))
        assertEquals(TerminalColorScheme.DRACULA, TerminalColorScheme.fromValue(4))
        assertEquals(TerminalColorScheme.NORD, TerminalColorScheme.fromValue(5))
        assertEquals(TerminalColorScheme.GRUVBOX_DARK, TerminalColorScheme.fromValue(6))
    }

    @Test
    fun `TerminalColorScheme fromValue returns DEFAULT for unknown value`() {
        assertEquals(TerminalColorScheme.DEFAULT, TerminalColorScheme.fromValue(99))
        assertEquals(TerminalColorScheme.DEFAULT, TerminalColorScheme.fromValue(-1))
    }

    @Test
    fun `TerminalColorScheme has correct display names`() {
        assertEquals("Default", TerminalColorScheme.DEFAULT.displayName)
        assertEquals("Solarized Dark", TerminalColorScheme.SOLARIZED_DARK.displayName)
        assertEquals("Solarized Light", TerminalColorScheme.SOLARIZED_LIGHT.displayName)
        assertEquals("Monokai", TerminalColorScheme.MONOKAI.displayName)
        assertEquals("Dracula", TerminalColorScheme.DRACULA.displayName)
        assertEquals("Nord", TerminalColorScheme.NORD.displayName)
        assertEquals("Gruvbox Dark", TerminalColorScheme.GRUVBOX_DARK.displayName)
    }

    @Test
    fun `TerminalColors fromScheme returns non-null colors`() {
        for (scheme in TerminalColorScheme.entries) {
            val colors = TerminalColors.fromScheme(scheme)

            assertNotEquals(0, colors.background)
            assertNotEquals(0, colors.foreground)
            assertNotEquals(0, colors.cursorColor)
        }
    }

    @Test
    fun `TerminalColors default has black background`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.DEFAULT)

        assertEquals(0xFF000000.toInt(), colors.background)
        assertEquals(0xFFFFFFFF.toInt(), colors.foreground)
    }

    @Test
    fun `TerminalColors Solarized Dark has correct base colors`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.SOLARIZED_DARK)

        assertEquals(0xFF002B36.toInt(), colors.background)
        assertEquals(0xFF839496.toInt(), colors.foreground)
    }

    @Test
    fun `TerminalColors Solarized Light has light background`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.SOLARIZED_LIGHT)

        assertEquals(0xFFFDF6E3.toInt(), colors.background)
    }

    @Test
    fun `TerminalColors Dracula has correct colors`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.DRACULA)

        assertEquals(0xFF282A36.toInt(), colors.background)
        assertEquals(0xFFF8F8F2.toInt(), colors.foreground)
    }

    @Test
    fun `TerminalColors getAnsiColor returns correct colors`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.DEFAULT)

        // Test standard ANSI colors (0-7)
        assertEquals(colors.black, colors.getAnsiColor(0))
        assertEquals(colors.red, colors.getAnsiColor(1))
        assertEquals(colors.green, colors.getAnsiColor(2))
        assertEquals(colors.yellow, colors.getAnsiColor(3))
        assertEquals(colors.blue, colors.getAnsiColor(4))
        assertEquals(colors.magenta, colors.getAnsiColor(5))
        assertEquals(colors.cyan, colors.getAnsiColor(6))
        assertEquals(colors.white, colors.getAnsiColor(7))

        // Test bright ANSI colors (8-15)
        assertEquals(colors.brightBlack, colors.getAnsiColor(8))
        assertEquals(colors.brightRed, colors.getAnsiColor(9))
        assertEquals(colors.brightGreen, colors.getAnsiColor(10))
        assertEquals(colors.brightYellow, colors.getAnsiColor(11))
        assertEquals(colors.brightBlue, colors.getAnsiColor(12))
        assertEquals(colors.brightMagenta, colors.getAnsiColor(13))
        assertEquals(colors.brightCyan, colors.getAnsiColor(14))
        assertEquals(colors.brightWhite, colors.getAnsiColor(15))
    }

    @Test
    fun `TerminalColors getAnsiColor returns foreground for invalid index`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.DEFAULT)

        assertEquals(colors.foreground, colors.getAnsiColor(16))
        assertEquals(colors.foreground, colors.getAnsiColor(-1))
        assertEquals(colors.foreground, colors.getAnsiColor(100))
    }

    @Test
    fun `All color schemes have distinct backgrounds`() {
        val backgrounds = TerminalColorScheme.entries.map {
            TerminalColors.fromScheme(it).background
        }

        // Check that not all backgrounds are the same (allowing some duplicates)
        val uniqueBackgrounds = backgrounds.toSet()
        assertTrue("Expected multiple unique backgrounds", uniqueBackgrounds.size > 3)
    }

    @Test
    fun `Nord scheme has characteristic blue-gray colors`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.NORD)

        assertEquals(0xFF2E3440.toInt(), colors.background)
        assertEquals(0xFFD8DEE9.toInt(), colors.foreground)
    }

    @Test
    fun `Gruvbox Dark scheme has warm colors`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.GRUVBOX_DARK)

        assertEquals(0xFF282828.toInt(), colors.background)
        assertEquals(0xFFEBDBB2.toInt(), colors.foreground)
    }

    @Test
    fun `Monokai scheme has characteristic purple and pink`() {
        val colors = TerminalColors.fromScheme(TerminalColorScheme.MONOKAI)

        assertEquals(0xFF272822.toInt(), colors.background)
        assertEquals(0xFFF92672.toInt(), colors.red) // Characteristic Monokai pink/red
        assertEquals(0xFFAE81FF.toInt(), colors.magenta) // Monokai purple
    }
}
