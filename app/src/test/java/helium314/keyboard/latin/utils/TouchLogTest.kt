// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TouchLogTest {
    private fun tempFile() = File.createTempFile("touch_log", ".tsv").also { it.deleteOnExit(); it.delete() }

    @Test fun lineHasOneColumnPerHeaderColumn() {
        val line = TouchLog.formatLine(1L, -10100, "ㄱ", 2, 3, 4, 5, 6L, 7, 8, 9, 10)
        assertEquals("1\t-10100\tㄱ\t2\t3\t4\t5\t6\t7\t8\t9\t10\n", line)
        assertEquals(TouchLog.HEADER.count { it == '\t' }, line.count { it == '\t' })
        // the action key has no label
        assertEquals(TouchLog.HEADER.count { it == '\t' }, TouchLog.formatLine(1L, 1, null, 2, 3, 4, 5, 6L, 7, 8, 9, 10).count { it == '\t' })
    }

    @Test fun headerIsWrittenOnce() {
        val file = tempFile()
        TouchLog.append(file, "a\n")
        TouchLog.append(file, "b\n")
        assertEquals(TouchLog.HEADER + "a\nb\n", file.readText())
    }

    @Test fun stopsAtMaxSize() {
        val file = tempFile()
        file.writeText("x".repeat(TouchLog.MAX_SIZE.toInt()))
        TouchLog.append(file, "a\n")
        assertEquals(TouchLog.MAX_SIZE, file.length())
    }
}
