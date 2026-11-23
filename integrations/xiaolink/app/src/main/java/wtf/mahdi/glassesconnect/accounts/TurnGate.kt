package wtf.mahdi.glassesconnect.accounts

import java.util.concurrent.atomic.AtomicLong

/** The transport, camera, recognizer, HTTP and speech tasks share one cancellable generation. */
class TurnGate {
    private val generation = AtomicLong()
    @Volatile private var enabled = false
    @Synchronized fun begin(): Long { enabled = true; return generation.incrementAndGet() }
    @Synchronized fun cancel() { enabled = false; generation.incrementAndGet() }
    fun active(ticket: Long): Boolean = enabled && generation.get() == ticket
    companion object { fun acceptsWake(cmd: Int) = cmd == 1 || cmd == 2 || cmd == 5 }
}

/** Time is derived from 16 kHz mono PCM, so delayed packet delivery does not extend the utterance. */
class PcmUtteranceGate {
    private var samples = 0L
    private var silent = 0L
    private var voiced = 0L
    fun feed(pcm: ByteArray): Boolean {
        var energy = 0.0
        val count = pcm.size / 2
        if (count == 0) return false
        for (i in 0 until count) {
            val s = ((pcm[2 * i].toInt() and 255) or (pcm[2 * i + 1].toInt() shl 8)).toShort().toDouble()
            energy += s * s
        }
        samples += count
        if (kotlin.math.sqrt(energy / count) > 250) { voiced += count; silent = 0 } else silent += count
        return samples >= 30 * 16000 || (voiced >= 4800 && silent >= 25600) || (voiced == 0L && samples >= 8 * 16000)
    }
}

/** Detects whether a spoken question asks about visual surroundings and should trigger glasses camera capture. */
object SmartCameraIntent {
    private val keywords = listOf(
        "trước mặt", "nhìn", "xem", "đây là", "cái này", "cái gì đây", "thứ này", "vật này",
        "món này", "người này", "chụp", "ảnh", "hình", "camera", "cam", "đọc giúp", "đọc chữ",
        "đọc cái", "đọc cho", "dịch giúp", "dịch cái", "dịch chữ", "dịch đoạn", "màu gì",
        "chữ gì", "biển báo", "màn hình", "tờ giấy", "trang sách", "hóa đơn", "menu", "thực đơn",
        "look", "see", "what is this", "what's this", "read this", "translate this", "in front",
        "photo", "picture", "describe", "wearing", "holding"
    )
    private val hdKeywords = listOf(
        "đọc", "dịch", "chữ", "màn hình", "tờ giấy", "trang sách", "hóa đơn", "menu", "thực đơn",
        "biển báo", "số", "giá", "read", "translate", "text", "screen", "receipt", "label"
    )
    private val followUpKeywords = listOf(
        "cái đó", "thứ đó", "nó ", "ảnh vừa", "hình vừa", "trong ảnh", "trong hình", "bên trái",
        "bên phải", "ở trên", "ở dưới", "chi tiết", "rõ hơn", "giá bao nhiêu", "dịch thêm",
        "còn gì nữa", "what else", "more detail", "on the left", "on the right", "in that photo", "that one"
    )
    fun shouldCapture(question: String): Boolean {
        val q = question.lowercase()
        return keywords.any { q.contains(it) }
    }
    fun needsHd(question: String): Boolean {
        val q = question.lowercase()
        return hdKeywords.any { q.contains(it) }
    }
    fun isVisualFollowUp(question: String): Boolean {
        val q = question.lowercase()
        return followUpKeywords.any { q.contains(it) }
    }
}
