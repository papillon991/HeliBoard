// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.settings.Settings
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Optional local log of touches on the naratgeul alphabet layout, for adjusting key sizes and positions.
 * The log allows reconstructing what was typed, so it is off by default, skips password fields,
 * is excluded from backups, and stops growing at [MAX_SIZE]. Read it with
 * `adb exec-out run-as <package> cat no_backup/touch_log.tsv`.
 */
object TouchLog {
    const val FILE_NAME = "touch_log.tsv"
    const val MAX_SIZE = 5L * 1024 * 1024
    const val HEADER = "time_ms\tcode\tlabel\tdown_x\tdown_y\tup_x\tup_y\thold_ms\tkey_x\tkey_y\tkey_w\tkey_h\n"

    private val executor by lazy { Executors.newSingleThreadExecutor() }
    private var file: File? = null

    @JvmStatic
    fun init(context: Context) {
        file = File(context.noBackupFilesDir, FILE_NAME)
    }

    @JvmStatic
    fun log(keyboard: Keyboard, key: Key, downX: Int, downY: Int, upX: Int, upY: Int, holdMs: Long) {
        val id = keyboard.mId
        if (!id.element.isAlphabet || id.subtype.mainLayoutName != "naratgeul") return
        // incognito covers password fields, fields that ask for no learning, and forced incognito mode
        if (Settings.getValues().mIncognitoModeEnabled) return
        val target = file ?: return
        val line = formatLine(System.currentTimeMillis(), key.code, key.label, downX, downY, upX, upY, holdMs,
            key.x, key.y, key.width, key.height)
        executor.execute { append(target, line) }
    }

    internal fun formatLine(
        time: Long, code: Int, label: String?, downX: Int, downY: Int, upX: Int, upY: Int, holdMs: Long,
        keyX: Int, keyY: Int, keyWidth: Int, keyHeight: Int
    ) = "$time\t$code\t${label.orEmpty()}\t$downX\t$downY\t$upX\t$upY\t$holdMs\t$keyX\t$keyY\t$keyWidth\t$keyHeight\n"

    /** Deletes the log, called when logging is turned off. Runs on the writer thread, after any pending lines */
    @JvmStatic
    fun delete() {
        val target = file ?: return
        executor.execute { target.delete() }
    }

    internal fun append(target: File, line: String) {
        try {
            if (target.length() >= MAX_SIZE) return
            if (target.length() == 0L) target.writeText(HEADER)
            target.appendText(line)
        } catch (e: IOException) {
            Log.w("TouchLog", "could not write touch log", e)
        }
    }
}
