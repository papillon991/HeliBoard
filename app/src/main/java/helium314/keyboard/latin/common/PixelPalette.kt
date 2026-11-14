// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.common

import helium314.keyboard.keyboard.KeyboardTheme

/**
 *  The palettes of the Pixel style (fork), one per color theme name. They are the values of the PALETTES in
 *  Px.java of the Pixel Cold War icon pack (design/android), made there by tools/build_colours.py from
 *  palette.tsv and themes.tsv. When the pack changes them, copy them here by hand.
 *  Every palette is dark: the plates are bakelite, what stands on them is paper, and [accent] marks what is on.
 */
enum class PixelPalette(
    val themeName: String,
    val desk: Int,
    val plate: Int, val plateLight: Int,
    val raised: Int, val raisedLight: Int,
    val ink: Int, val inkSub: Int,
    val paper: Int, val paperLight: Int, val paperDark: Int,
    val dim: Int,
    val accent: Int,
) {
    COLD_WAR(KeyboardTheme.THEME_PIXEL_COLD_WAR,
        0xFF1A1716.toInt(),
        0xFF2B2724.toInt(), 0xFF46413E.toInt(),
        0xFF3A3330.toInt(), 0xFF4A433D.toInt(),
        0xFF1E1A18.toInt(), 0xFF5A4331.toInt(),
        0xFFEDE5CF.toInt(), 0xFFF7F2E2.toInt(), 0xFFD2C6AA.toInt(),
        0xFFA99D80.toInt(),
        0xFFCFA64B.toInt()),
    BERLIN(KeyboardTheme.THEME_PIXEL_COLD_WAR_BERLIN,
        0xFF151717.toInt(),
        0xFF262929.toInt(), 0xFF454A49.toInt(),
        0xFF353939.toInt(), 0xFF474C4B.toInt(),
        0xFF161818.toInt(), 0xFF4B4E4A.toInt(),
        0xFFE3E2D9.toInt(), 0xFFF1F0EA.toInt(), 0xFFC5C4B9.toInt(),
        0xFF9EA099.toInt(),
        0xFF7CC3AD.toInt()),
    MOSCOW(KeyboardTheme.THEME_PIXEL_COLD_WAR_MOSCOW,
        0xFF10161F.toInt(),
        0xFF1A2534.toInt(), 0xFF34465E.toInt(),
        0xFF26344A.toInt(), 0xFF364860.toInt(),
        0xFF131922.toInt(), 0xFF5A4331.toInt(),
        0xFFEDE5CF.toInt(), 0xFFF7F2E2.toInt(), 0xFFD2C6AA.toInt(),
        0xFFA99D80.toInt(),
        0xFFEC6A8C.toInt());

    companion object {
        val themeNames = entries.map { it.themeName }

        /** the palette of a color theme name, or null when the name is not one of these */
        fun of(themeName: String?) = entries.firstOrNull { it.themeName == themeName }
    }
}
