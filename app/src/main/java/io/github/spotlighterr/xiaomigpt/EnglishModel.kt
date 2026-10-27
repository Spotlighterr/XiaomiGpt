package io.github.spotlighterr.xiaomigpt

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class EnglishModel(context: Context) {
    private val root = File(context.noBackupFilesDir, "speech").apply { mkdirs() }
    val directory = File(root, NAME)
    fun ready() = File(directory, "am/final.mdl").isFile && File(directory, "conf/model.conf").isFile && File(directory, ".verified").isFile
    fun install(active: () -> Boolean, progress: (Int) -> Unit) {
        if (ready()) return
        val zip = File(root, "english-download.zip")
        val staging = File(root, "english-staging")
        staging.deleteRecursively(); staging.mkdirs()
        try {
            val http = OkHttpClient.Builder().followRedirects(false).callTimeout(10, TimeUnit.MINUTES).build()
            http.newCall(Request.Builder().url("https://alphacephei.com/vosk/models/$NAME.zip").build()).execute().use { response ->
                check(response.isSuccessful) { "Chưa tải được model tiếng Anh (${response.code})." }
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
                        progress((total * 100 / 41_205_931).toInt().coerceAtMost(100))
                    }
                } }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                check(hash == SHA256) { "Model tải về không khớp checksum. Hãy thử lại." }
            }
            var expanded = 0L; var count = 0
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                while (true) {
                    check(active()) { "Đã dừng cài model." }
                    val entry = input.nextEntry ?: break
                    check(++count <= 1000 && entry.name.startsWith("$NAME/")) { "Cấu trúc model không hợp lệ." }
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
            val unpacked = File(staging, NAME)
            check(File(unpacked, "am/final.mdl").isFile && File(unpacked, "conf/model.conf").isFile) { "Model thiếu dữ liệu." }
            File(unpacked, ".verified").writeText(SHA256)
            directory.deleteRecursively()
            check(unpacked.renameTo(directory)) { "Không lưu được model tiếng Anh." }
        } finally { zip.delete(); staging.deleteRecursively() }
    }
    companion object {
        const val NAME = "vosk-model-small-en-us-0.15"
        // Pinned from the official HTTPS download on 2026-10-27.
        const val SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
    }
}
