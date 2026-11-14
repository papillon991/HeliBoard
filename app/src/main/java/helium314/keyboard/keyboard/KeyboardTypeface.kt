// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

object KeyboardTypeface {
    private val lock = Any()

    private var cachedCustomTypeface: Typeface? = null
    private var cachedCustomFontFamily: FontFamily? = null
    @Volatile
    private var customTypefaceLoaded = false

    private var cachedEmojiTypeface: Typeface? = null
    @Volatile
    private var emojiTypefaceLoaded = false

    /** fork: the font the Pixel style brings (NeoDunggeunmo, res/font/neodgm.ttf), non-null only while it is in use */
    @Volatile
    private var pixelTypeface: Typeface? = null

    /** NeoDunggeunmo is drawn on a grid of 16 units, so it is sharp only at multiples of 16px */
    private const val PIXEL_GRID = 16f

    private fun loadCustomTypeface(context: Context): Typeface? {
        pixelTypeface = null
        val custom = runCatching {
            Typeface.createFromFile(Settings.getCustomFontFile(context))
        }.getOrNull()
        if (custom != null) return custom // a font the user chose wins over the style's
        val style = context.prefs().getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE)
        if (style != KeyboardTheme.STYLE_PIXEL) return null
        pixelTypeface = runCatching { ResourcesCompat.getFont(context, R.font.neodgm) }.getOrNull()
        return pixelTypeface
    }

    private fun loadCustomEmojiTypeface(context: Context): Typeface? {
        return runCatching {
            Typeface.createFromFile(Settings.getCustomEmojiFontFile(context))
        }.getOrNull()
    }

    @JvmStatic
    fun customTypeface(): Typeface? {
        if (customTypefaceLoaded) return cachedCustomTypeface
        val context = Settings.getCurrentContext() ?: return null
        synchronized(lock) {
            if (!customTypefaceLoaded) {
                cachedCustomTypeface = loadCustomTypeface(context)
                cachedCustomFontFamily = cachedCustomTypeface?.let(::FontFamily)
                customTypefaceLoaded = true
            }
            return cachedCustomTypeface
        }
    }

    @JvmStatic
    fun emojiTypeface(): Typeface? {
        if (emojiTypefaceLoaded) return cachedEmojiTypeface
        val context = Settings.getCurrentContext() ?: return null
        synchronized(lock) {
            if (!emojiTypefaceLoaded) {
                cachedEmojiTypeface = loadCustomEmojiTypeface(context)
                emojiTypefaceLoaded = true
            }
            return cachedEmojiTypeface
        }
    }

    @JvmStatic
    fun customFontFamily(): FontFamily? {
        if (!customTypefaceLoaded) customTypeface()
        return cachedCustomFontFamily
    }

    @JvmStatic
    fun resolve(
        text: CharSequence?,
        defaultTypeface: Typeface = Typeface.DEFAULT,
    ): Typeface {
        val emojiTypeface = emojiTypeface()
        return if (emojiTypeface != null && text != null && isEmoji(text)) {
            emojiTypeface
        } else {
            customTypeface() ?: defaultTypeface
        }
    }

    @JvmStatic
    fun applyToTextView(textView: TextView) {
        applyToTextView(textView, textView.text, Typeface.DEFAULT)
    }

    @JvmStatic
    fun applyToTextView(textView: TextView, text: CharSequence?, defaultTypeface: Typeface) {
        val typeface = resolve(text, defaultTypeface = defaultTypeface)
        textView.typeface = typeface
        if (typeface === pixelTypeface && typeface != null)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, onPixelGrid(textView.textSize))
    }

    /** the multiple of 16px nearest to [size], at least 16px */
    private fun onPixelGrid(size: Float) = PIXEL_GRID * max(1, (size / PIXEL_GRID).roundToInt())

    /**
     *  Call after typeface and text size are set on [paint]: if the typeface is the Pixel style's font,
     *  the text size moves to the nearest multiple of 16px, so that no pixel of a glyph is smeared.
     */
    @JvmStatic
    fun fitToPixelGrid(paint: Paint) {
        if (pixelTypeface == null || paint.typeface !== pixelTypeface) return
        paint.textSize = onPixelGrid(paint.textSize)
    }

    /**
     *  For a label that has to get narrower by [ratio] to fit its key. Returns false if the typeface is not
     *  the Pixel style's font. Otherwise the text size steps down along the grid (stretching would smear the
     *  pixels) and the result is true.
     */
    @JvmStatic
    fun shrinkOnPixelGrid(paint: Paint, ratio: Float): Boolean {
        if (pixelTypeface == null || paint.typeface !== pixelTypeface) return false
        val steps = max(1, floor(paint.textSize * ratio / PIXEL_GRID).toInt())
        paint.textSize = PIXEL_GRID * steps
        return true
    }

    @JvmStatic
    fun clearCache() {
        synchronized(lock) {
            cachedCustomTypeface = null
            cachedCustomFontFamily = null
            customTypefaceLoaded = false
            pixelTypeface = null
            cachedEmojiTypeface = null
            emojiTypefaceLoaded = false
        }
    }
}
