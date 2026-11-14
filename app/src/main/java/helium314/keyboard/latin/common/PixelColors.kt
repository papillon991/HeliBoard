// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.common

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType.*
import helium314.keyboard.latin.suggestions.SuggestionStripView
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 *  Colors and key shapes of the "Pixel" style (fork). Everything is drawn on a grid of 2dp cells with
 *  one of the fixed palettes of [PixelPalette]: no rounded corners, no blur, no transparency.
 *  A key is a plate with an ink outline, cut corners and a bevel lit from the top left. A pressed key
 *  sinks into a dark well, because the label keeps its color and would not read on a lighter face.
 *  Plain colors go through [AllColors]; the shapes are made here instead of tinting the style's drawables.
 */
class PixelColors(context: Context, val palette: PixelPalette, backgroundImage: Drawable?) : Colors {
    override val themeStyle = KeyboardTheme.STYLE_PIXEL
    override val hasKeyBorders = true

    /** one cell in screen pixels */
    val unit = max(1, (2 * context.resources.displayMetrics.density).roundToInt())

    private val desk = palette.desk
    private val plate = palette.plate
    private val plateLight = palette.plateLight
    private val raised = palette.raised
    private val raisedLight = palette.raisedLight
    private val ink = palette.ink
    private val inkSub = palette.inkSub
    private val paper = palette.paper
    private val paperLight = palette.paperLight
    private val paperDark = palette.paperDark
    private val dim = palette.dim
    private val brass = palette.accent

    private val base = AllColors(colorMap(), themeStyle, hasKeyBorders, backgroundImage)

    private fun colorMap(): EnumMap<ColorType, Int> {
        val map = EnumMap<ColorType, Int>(ColorType::class.java)
        ColorType.entries.forEach { map[it] = paper } // text and icons, unless listed below
        // grounds
        map[MAIN_BACKGROUND] = desk
        map[NAVIGATION_BAR] = desk
        map[KEY_BACKGROUND] = raised
        map[MORE_SUGGESTIONS_WORD_BACKGROUND] = raised
        map[POPUP_KEYS_BACKGROUND] = raised
        map[GESTURE_PREVIEW] = raised
        map[AUTOFILL_BACKGROUND_CHIP] = raised
        map[CLIPBOARD_SUGGESTION_BACKGROUND] = raised
        map[TOOL_BAR_EXPAND_KEY_BACKGROUND] = raised
        map[FUNCTIONAL_KEY_BACKGROUND] = plate
        map[SPACE_BAR_BACKGROUND] = plate
        map[STRIP_BACKGROUND] = plate
        map[MORE_SUGGESTIONS_BACKGROUND] = plate
        map[EMOJI_SEARCH_BACKGROUND] = plate
        map[ACTION_KEY_BACKGROUND] = paper
        map[ACTION_KEY_POPUP_KEYS_BACKGROUND] = paper
        map[KEY_PREVIEW_BACKGROUND] = paper
        map[TOOL_BAR_KEY_ENABLED_BACKGROUND] = inkSub
        // what stands on paper is ink
        map[ACTION_KEY_ICON] = ink
        map[KEY_PREVIEW_TEXT] = ink
        // secondary text
        map[KEY_HINT_TEXT] = dim
        map[SPACE_BAR_TEXT] = dim
        map[MORE_SUGGESTIONS_HINT] = dim
        // what is on, or about to be chosen, is the accent (brass in the first palette)
        map[SUGGESTION_AUTO_CORRECT] = brass
        map[SHIFT_KEY_ICON] = brass
        map[EMOJI_CATEGORY_SELECTED] = brass
        map[CLIPBOARD_PIN] = brass
        map[GESTURE_TRAIL] = brass
        return map
    }

    override fun get(color: ColorType): Int = base.get(color)

    /** the divider between two suggestions: a dotted line, light enough to show on the strip */
    fun stripDivider(): Drawable = PixelDots(unit, plateLight, 13)

    override fun setColor(drawable: Drawable, color: ColorType) = base.setColor(drawable, color)

    override fun setColor(view: ImageView, color: ColorType) = base.setColor(view, color)

    override fun setBackground(view: View, color: ColorType) {
        when (color) {
            // the panel of long-press keys and of more suggestions: a raised plate
            POPUP_KEYS_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND ->
                replaceBackground(view) { PixelShape(unit, raised, ink, raisedLight, desk) }
            // the tab that shows the pressed key: paper
            KEY_PREVIEW_BACKGROUND ->
                replaceBackground(view) { PixelShape(unit, paper, ink, paperLight, paperDark) }
            STRIP_BACKGROUND ->
                if (view is SuggestionStripView) {
                    // The emoji tab strip and the clipboard strip share this view's first drawable and are never
                    // colored themselves: the other styles rely on the tint spreading to them. Keep that working.
                    if (view.background !is PixelDrawable) base.setBackground(view, color)
                    band(view)
                } else {
                    // The strip keeps the first drawable of its toolbar key and copies it for other keys later,
                    // so that drawable gets the strip color like in the other styles before it is replaced.
                    if (view.background !is PixelDrawable) base.setBackground(view, color)
                    // words and toolbar keys lie on the strip: nothing until pressed
                    replaceBackground(view) { PixelKey(unit, 0, 0, 0, 0, raised, 0, true) }
                }
            else -> base.setBackground(view, color)
        }
    }

    /** makes [view] a strip like the suggestion strip: a bakelite band with an ink line and a light line on top */
    fun band(view: View) = replaceBackground(view) { PixelStrip(unit, plate, ink, plateLight, desk) }

    /** sets a pixel drawable once per view, and keeps the padding the style's drawable gave the view */
    private fun replaceBackground(view: View, create: () -> Drawable) {
        if (view.background is PixelDrawable) return
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        view.background = create()
        view.setPadding(left, top, right, bottom)
    }

    override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable = when (color) {
        FUNCTIONAL_KEY_BACKGROUND, SPACE_BAR_BACKGROUND ->
            PixelKey(unit, plate, ink, plateLight, desk, desk, ink, false)
        ACTION_KEY_BACKGROUND ->
            PixelKey(unit, paper, ink, paperLight, paperDark, paperDark, ink, false)
        // keys inside a popup lie on its plate: only the chosen one is drawn, as a well
        POPUP_KEYS_BACKGROUND, ACTION_KEY_POPUP_KEYS_BACKGROUND ->
            PixelKey(unit, 0, 0, 0, 0, desk, ink, true)
        else -> // KEY_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND
            PixelKey(unit, raised, ink, raisedLight, desk, desk, ink, false)
    }
}

/** marks the drawables made here, so a view's background is replaced only once */
abstract class PixelDrawable : Drawable() {
    protected val paint = Paint()

    /** a rectangle with one cell cut off each corner */
    protected fun cut(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int, unit: Int, color: Int) {
        if (color == 0 || right - left <= 2 * unit || bottom - top <= 2 * unit) return
        paint.color = color
        canvas.drawRect((left + unit).toFloat(), top.toFloat(), (right - unit).toFloat(), bottom.toFloat(), paint)
        canvas.drawRect(left.toFloat(), (top + unit).toFloat(), right.toFloat(), (bottom - unit).toFloat(), paint)
    }

    /** outline, then the bevel (dark under light), then the face. A color of 0 is not drawn. */
    protected fun plate(canvas: Canvas, unit: Int, fill: Int, outline: Int, light: Int, dark: Int) {
        var left = bounds.left
        var top = bounds.top
        var right = bounds.right
        var bottom = bounds.bottom
        if (outline != 0) {
            cut(canvas, left, top, right, bottom, unit, outline)
            left += unit
            top += unit
            right -= unit
            bottom -= unit
        }
        if (light != 0 && dark != 0) {
            cut(canvas, left, top, right, bottom, unit, dark)
            cut(canvas, left, top, right - unit, bottom - unit, unit, light)
            cut(canvas, left + unit, top + unit, right - unit, bottom - unit, unit, fill)
        } else {
            cut(canvas, left, top, right, bottom, unit, fill)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** a plate that looks the same in every state */
class PixelShape(private val unit: Int, private val fill: Int, private val outline: Int, private val light: Int,
                 private val dark: Int) : PixelDrawable() {
    override fun draw(canvas: Canvas) = plate(canvas, unit, fill, outline, light, dark)
}

/** the suggestion strip: a band with an ink line and a light line on top and a dark line below */
class PixelStrip(private val unit: Int, private val fill: Int, private val outline: Int, private val light: Int,
                 private val dark: Int) : PixelDrawable() {
    override fun draw(canvas: Canvas) {
        val left = bounds.left.toFloat()
        val right = bounds.right.toFloat()
        val top = bounds.top
        val bottom = bounds.bottom
        paint.color = fill
        canvas.drawRect(left, top.toFloat(), right, bottom.toFloat(), paint)
        paint.color = outline
        canvas.drawRect(left, top.toFloat(), right, (top + unit).toFloat(), paint)
        paint.color = light
        canvas.drawRect(left, (top + unit).toFloat(), right, (top + 2 * unit).toFloat(), paint)
        paint.color = dark
        canvas.drawRect(left, (bottom - unit).toFloat(), right, bottom.toFloat(), paint)
    }
}

/** a column of [cells] cells, one cell wide, every second one filled */
class PixelDots(private val unit: Int, private val color: Int, private val cells: Int) : PixelDrawable() {
    override fun getIntrinsicWidth() = unit

    override fun getIntrinsicHeight() = unit * cells

    override fun draw(canvas: Canvas) {
        paint.color = color
        val left = bounds.left.toFloat()
        var top = bounds.top
        while (top + unit <= bounds.bottom) {
            canvas.drawRect(left, top.toFloat(), left + unit, (top + unit).toFloat(), paint)
            top += 2 * unit
        }
    }
}

/**
 *  A key. Not pressed it is a beveled plate, or nothing when [hiddenUntilPressed] or when the key has the
 *  empty background type. Pressed it is a flat well of [pressedFill].
 */
class PixelKey(private val unit: Int, private val fill: Int, private val outline: Int, private val light: Int,
               private val dark: Int, private val pressedFill: Int, private val pressedOutline: Int,
               private val hiddenUntilPressed: Boolean) : PixelDrawable() {
    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val state = state
        if (state.contains(android.R.attr.state_pressed))
            plate(canvas, unit, pressedFill, pressedOutline, 0, 0)
        else if (!hiddenUntilPressed && !state.contains(android.R.attr.state_empty))
            plate(canvas, unit, fill, outline, light, dark)
    }
}
