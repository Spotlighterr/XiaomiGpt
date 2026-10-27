package io.github.spotlighterr.xiaomigpt

import java.util.Locale

enum class SpeechLanguage(val tag: String, val label: String, val modelName: String, val modelBytes: Long, val sha256: String) {
    VIETNAMESE("vi-VN", "Tiếng Việt", "vosk-model-small-vn-0.4", 33_656_337,
        "efe5c8494212110471a79befc48c79da679e5b1fc52a4ffb500222ff86d622e5"),
    ENGLISH("en-US", "English", "vosk-model-small-en-us-0.15", 41_205_931,
        "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498");

    val locale: Locale get() = Locale.forLanguageTag(tag)
    override fun toString() = label
    fun instructions(): String = when (this) {
        VIETNAMESE -> "Bạn là trợ lý giọng nói XiaomiGpt trên kính thông minh. Mặc định trả lời bằng tiếng Việt, ngắn gọn, tự nhiên để đọc thành tiếng. Giữ nguyên tên riêng hoặc thuật ngữ cần thiết; chỉ chuyển ngôn ngữ khi người dùng yêu cầu. Dùng văn bản thuần. Không khẳng định nhìn thấy ảnh hoặc camera vì chưa có ảnh đính kèm."
        ENGLISH -> "You are the voice assistant in XiaomiGpt smart glasses. Respond in English, briefly and naturally for spoken playback, unless the user explicitly requests another language. Use plain text. Do not claim to see a camera or photo: no image is attached."
    }
    companion object { fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: VIETNAMESE }
}
