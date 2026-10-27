package io.github.spotlighterr.xiaomigpt

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class SpeechModel(context: Context, val language: SpeechLanguage) {
    private val root = File(context.noBackupFilesDir, "speech").apply { mkdirs() }
    val directory = File(root, language.modelName)
    fun ready() = File(directory, "am/final.mdl").isFile && File(directory, "conf/model.conf").isFile && File(directory, ".verified").isFile
    fun install(active: () -> Boolean, progress: (Int) -> Unit) {
        if (ready()) return
        val zip = File(root, "${language.tag}-download.zip")
        val staging = File(root, "${language.tag}-staging")
        staging.deleteRecursively(); staging.mkdirs()
        try {
            val http = OkHttpClient.Builder().followRedirects(false).callTimeout(10, TimeUnit.MINUTES).build()
            http.newCall(Request.Builder().url("https://alphacephei.com/vosk/models/${language.modelName}.zip").build()).execute().use { response ->
                check(response.isSuccessful) { "Chưa tải được model ${language.label} (${response.code})." }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                response.body!!.byteStream().use { input -> zip.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        check(active()) { "Đã dừng tải model." }
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count; check(total <= 50_000_000) { "Tệp model vượt kích thước dự kiến." }
                        digest.update(buffer, 0, count); output.write(buffer, 0, count)
                        progress((total * 100 / language.modelBytes).toInt().coerceAtMost(100))
                    }
                } }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                check(hash == language.sha256) { "Model tải về không khớp checksum. Hãy thử lại." }
            }
            var expanded = 0L; var count = 0
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                while (true) {
                    check(active()) { "Đã dừng cài model." }
                    val entry = input.nextEntry ?: break
                    check(++count <= 1000 && entry.name.startsWith("${language.modelName}/")) { "Cấu trúc model không hợp lệ." }
                    val target = File(staging, entry.name)
                    check(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Đường dẫn model không hợp lệ." }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile!!.mkdirs()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                check(active()) { "Đã dừng cài model." }
                                val size = input.read(buffer)
                                if (size < 0) break
                                expanded += size; check(expanded <= 256_000_000) { "Model giải nén quá lớn." }
                                output.write(buffer, 0, size)
                            }
                        }
                    }
                    input.closeEntry()
                }
            }
            val unpacked = File(staging, language.modelName)
            check(File(unpacked, "am/final.mdl").isFile && File(unpacked, "conf/model.conf").isFile) { "Model thiếu dữ liệu." }
            File(unpacked, ".verified").writeText(language.sha256)
            directory.deleteRecursively()
            check(unpacked.renameTo(directory)) { "Không lưu được model ${language.label}." }
        } finally { zip.delete(); staging.deleteRecursively() }
    }
}
