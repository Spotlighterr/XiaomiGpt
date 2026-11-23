package wtf.mahdi.glassesconnect.accounts

import io.github.spotlighterr.xiaomigpt.VisualInput
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Bounded, in-memory JPEG assembly for standard (type 3/4, 576x432) and HD (type 9/11, 960x720) AI images. */
class CameraFrames {
    private var image: ByteArrayOutputStream? = null
    private var expected = 0
    private var activeFirstType = 0
    @Volatile var acceptHd = false

    @Synchronized fun clear() { image = null; expected = 0; activeFirstType = 0 }
    @Synchronized fun feed(data: ByteArray): ByteArray? {
        if (data.size <= 12 || data[1].toInt() != 2) return null
        val type = ((data[4].toInt() and 255) shl 8) or (data[5].toInt() and 255)
        val isStd = type == 3 || type == 4
        val isHd = acceptHd && (type == 9 || type == 11)
        if (!isStd && !isHd) return null
        val total = ByteBuffer.wrap(data, 6, 4).int
        val length = ((data[10].toInt() and 255) shl 8) or (data[11].toInt() and 255)
        val payload = data.copyOfRange(12, data.size)
        if (length != payload.size) { clear(); return null }
        val isFirst = type == 3 || type == 9
        if (isFirst) {
            if (total !in 4..VisualInput.MAX_JPEG) { clear(); return null }
            clear(); expected = total; activeFirstType = type; image = ByteArrayOutputStream(minOf(total, 65536))
        } else {
            val expectedFirst = if (type == 4) 3 else 9
            if (activeFirstType != expectedFirst || (total != 0 && total != expected)) { clear(); return null }
        }
        val buffer = image ?: return null
        if (payload.size > expected - buffer.size()) { clear(); return null }
        buffer.write(payload)
        if (buffer.size() != expected) return null
        val jpeg = buffer.toByteArray(); clear()
        return runCatching { VisualInput.content("camera validation", jpeg); jpeg }.getOrNull()
    }
}
