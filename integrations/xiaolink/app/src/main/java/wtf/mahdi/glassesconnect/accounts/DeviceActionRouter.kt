package wtf.mahdi.glassesconnect.accounts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Executes direct hands-free Android actions spoken through the glasses
 * (phone calls, Google Maps navigation, launching apps, timers/alarms, media controls, time/battery).
 */
object DeviceActionRouter {
    sealed interface Action {
        data object TellTime : Action
        data object TellBattery : Action
        data object GlassesPhoto : Action
        data class GlassesVideo(val start: Boolean) : Action
        data object GlassesSync : Action
        data object GlassesStatus : Action
        data object NewChat : Action
        data class Media(val keyCode: Int, val reply: String) : Action
        data class Volume(val direction: Int, val reply: String) : Action
        data class Timer(val seconds: Int, val label: String) : Action
        data class Alarm(val hour: Int, val minute: Int) : Action
        data class Navigate(val destination: String) : Action
        data class Call(val target: String) : Action
        data class OpenApp(val appQuery: String) : Action
    }

    data class GlassesBridge(
        val battery: () -> Int?,
        val pendingCount: () -> Int,
        val isRecording: () -> Boolean,
        val takePhoto: () -> Unit,
        val toggleVideo: () -> Unit,
        val syncGallery: () -> Unit,
        val newChat: () -> Unit = {},
    )

    private val questionGuards = listOf("la gi", "tai sao", "vi sao", "nhu the nao", "the nao", "o dau", "ai la", "what is", "why ", "how ")
    private val batteryNonDeviceGuards = listOf("mat troi", "du phong", "lithium", "xe dien", "o to", "may tinh", "laptop")

    /** Pure parser (unit-testable without Android framework). Returns null if the utterance is a normal AI question. */
    fun parse(question: String): Action? {
        val raw = question.trim()
        if (raw.isEmpty()) return null
        val lower = raw.lowercase(Locale.ROOT)
        val norm = normalize(raw)
        val words = norm.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null

        // 1. Time / Date (short direct question only)
        if (words.size <= 8 && (norm.contains("may gio") || norm.contains("what time"))) return Action.TellTime

        // 2. Glasses battery (natural Vietnamese & English phrases, excluding unrelated battery topics)
        if (words.size <= 12 && batteryNonDeviceGuards.none { norm.contains(it) } &&
            ((norm.contains("pin") && (norm.contains("bao nhieu") || norm.contains("phan tram") || norm.contains("con") || norm.contains("kiem tra") || norm.contains("kinh"))) ||
                norm.contains("glasses battery") || norm.contains("battery level"))) {
            return Action.TellBattery
        }

        // 2b. Glasses hardware actions (photo, video, sync, status)
        if (words.size <= 8 && (norm.contains("chup mot tam") || norm.contains("chup anh luu") || norm == "chup anh" || norm == "chup hinh" || norm == "take a photo" || norm == "take photo")) {
            return Action.GlassesPhoto
        }
        if (words.size <= 8 && (norm.contains("bat dau quay video") || norm == "quay video" || norm == "quay video di" || norm.contains("start recording") || norm.contains("record video"))) {
            return Action.GlassesVideo(start = true)
        }
        if (words.size <= 8 && (norm.contains("dung quay") || norm.contains("tat quay") || norm.contains("ngung quay") || norm.contains("stop recording"))) {
            return Action.GlassesVideo(start = false)
        }
        if (words.size <= 8 && (norm.contains("dong bo anh") || norm.contains("dong bo kinh") || norm.contains("tai anh tu kinh") || norm.contains("sync gallery") || norm.contains("sync glasses"))) {
            return Action.GlassesSync
        }
        if (words.size <= 10 && (norm.contains("trang thai kinh") || norm.contains("kinh co bao nhieu anh") || norm.contains("glasses status"))) {
            return Action.GlassesStatus
        }
        if (words.size <= 6 && (norm.contains("doan chat moi") || norm.contains("chu de moi") || norm.contains("hoi thoai moi") ||
                norm.contains("quen chuyen vua roi") || norm == "new chat" || norm == "new topic")) {
            return Action.NewChat
        }

        // 3. Media controls & volume (strict short phrases only)
        if (norm in setOf("dung nhac", "tam dung nhac", "tat nhac", "pause", "pause music", "stop music")) {
            return Action.Media(KeyEvent.KEYCODE_MEDIA_PAUSE, "Đã tạm dừng nhạc.")
        }
        if (norm in setOf("phat nhac", "mo nhac", "tiep tuc nhac", "play music", "resume music")) {
            return Action.Media(KeyEvent.KEYCODE_MEDIA_PLAY, "Đã tiếp tục phát nhạc.")
        }
        if (words.size <= 5 && (norm.contains("qua bai") || norm.contains("bai tiep") || norm.contains("next song") || norm.contains("next track"))) {
            return Action.Media(KeyEvent.KEYCODE_MEDIA_NEXT, "Đã chuyển sang bài tiếp theo.")
        }
        if (words.size <= 5 && (norm.contains("bai truoc") || norm.contains("lui bai") || norm.contains("previous song"))) {
            return Action.Media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Đã quay lại bài trước.")
        }
        if (words.size <= 5 && (norm.contains("tang am luong") || norm.contains("am luong to") || norm.contains("volume up"))) {
            return Action.Volume(AudioManager.ADJUST_RAISE, "Đã tăng âm lượng.")
        }
        if (words.size <= 5 && (norm.contains("giam am luong") || norm.contains("am luong nho") || norm.contains("volume down"))) {
            return Action.Volume(AudioManager.ADJUST_LOWER, "Đã giảm âm lượng.")
        }

        // 4. Timer ("hẹn giờ 5 phút", "đếm ngược 30 giây", "set timer 10 minutes")
        Regex("""^(?:hãy\s+)?(?:đặt\s+)?(?:hẹn giờ|đếm ngược|set timer|timer)\s+(\d+)\s*(giây|phút|giờ|tiếng|seconds?|mins?|minutes?|hours?)""", RegexOption.IGNORE_CASE)
            .find(lower)?.let { m ->
                val value = m.groupValues[1].toIntOrNull() ?: return@let
                val unit = normalize(m.groupValues[2])
                val seconds = when {
                    unit.startsWith("giay") || unit.startsWith("sec") -> value
                    unit.startsWith("gio") || unit.startsWith("tieng") || unit.startsWith("hour") -> value * 3600
                    else -> value * 60
                }
                if (seconds in 1..86400) return Action.Timer(seconds, "$value ${m.groupValues[2]}")
            }

        // 5. Alarm ("đặt báo thức 7 giờ 30", "báo thức 6 giờ sáng")
        Regex("""^(?:hãy\s+)?(?:đặt báo thức|báo thức|set alarm|alarm)\s+(?:lúc\s+)?(\d{1,2})\s*(?:giờ|h|:)\s*(\d{1,2})?\s*(sáng|chiều|tối|am|pm)?""", RegexOption.IGNORE_CASE)
            .find(lower)?.let { m ->
                var hour = m.groupValues[1].toIntOrNull() ?: return@let
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val period = normalize(m.groupValues[3])
                if ((period == "chieu" || period == "toi" || period == "pm") && hour in 1..11) hour += 12
                if ((period == "sang" || period == "am") && hour == 12) hour = 0
                if (hour in 0..23 && minute in 0..59) return Action.Alarm(hour, minute)
            }

        // 6. Google Maps navigation (explicit navigation verb + not a general knowledge question)
        if (questionGuards.none { norm.contains(it) }) {
            Regex("""^(?:hãy\s+)?(?:chỉ đường|dẫn đường|mở bản đồ|navigate|directions)\s+(?:đến|tới|ra|về|đi|to)?\s*(.+)$""", RegexOption.IGNORE_CASE)
                .find(raw)?.let { m ->
                    val dest = m.groupValues[1].trim().trimEnd('.', '?', '!')
                    if (dest.isNotBlank() && dest.split(' ').size <= 10) return Action.Navigate(dest)
                }
        }

        // 7. Phone call (explicit call verb + short target, reject general questions like "gọi là gì")
        if (questionGuards.none { norm.contains(it) }) {
            Regex("""^(?:hãy\s+)?(?:gọi điện thoại|gọi điện|gọi cho|gọi số|call)\s+(?:cho|tới|đến|số|to)?\s*(.+)$""", RegexOption.IGNORE_CASE)
                .find(raw)?.let { m ->
                    val target = m.groupValues[1].trim().trimEnd('.', '?', '!')
                    val targetWords = normalize(target).split(' ').filter { it.isNotEmpty() }
                    if (targetWords.isNotEmpty() && targetWords.size <= 5 && !normalize(target).startsWith("tro ly")) {
                        return Action.Call(target)
                    }
                }
        }

        // 8. Open app (strict: explicit "mở app/ứng dụng" OR short "mở <1-3 words>", never long prose)
        if (questionGuards.none { norm.contains(it) }) {
            Regex("""^(?:hãy\s+)?(?:mở ứng dụng|mở app|bật ứng dụng|bật app|open app)\s+(.+)$""", RegexOption.IGNORE_CASE)
                .find(raw)?.let { m ->
                    val query = m.groupValues[1].trim().trimEnd('.', '?', '!')
                    if (query.isNotBlank() && query.length <= 32) return Action.OpenApp(query)
                }
            Regex("""^(?:hãy\s+)?(?:mở|open)\s+(.+)$""", RegexOption.IGNORE_CASE)
                .find(raw)?.let { m ->
                    val query = m.groupValues[1].trim().trimEnd('.', '?', '!')
                    val qWords = normalize(query).split(' ').filter { it.isNotEmpty() }
                    if (qWords.size in 1..3 && query.length <= 28) return Action.OpenApp(query)
                }
        }

        return null
    }

    /**
     * Parses and executes a direct device/glasses action. Returns null if the utterance is NOT a valid command
     * (including when an ambiguous "mở <x>" does not match any installed app), so it cleanly falls through to AI.
     */
    fun tryExecute(context: Context, question: String, glasses: GlassesBridge): String? {
        val action = parse(question) ?: return null
        if (action is Action.OpenApp) {
            return openInstalledAppOrNull(context, action.appQuery)
        }
        return execute(context, action, glasses)
    }

    /** Executes [action] and returns the short spoken confirmation text for the glasses speaker. */
    fun execute(context: Context, action: Action, glasses: GlassesBridge): String = when (action) {
        Action.TellTime -> {
            val now = Date()
            val hm = SimpleDateFormat("H 'giờ' m 'phút'", Locale.forLanguageTag("vi-VN")).format(now)
            val date = SimpleDateFormat("EEEE, 'ngày' d 'tháng' M", Locale.forLanguageTag("vi-VN")).format(now)
            "Bây giờ là $hm, $date."
        }
        Action.TellBattery -> {
            val pct = glasses.battery()
            if (pct != null) "Kính đang còn $pct phần trăm pin." else "Chưa đọc được phần trăm pin của kính."
        }
        Action.GlassesPhoto -> {
            glasses.takePhoto()
            "Đã gửi lệnh chụp ảnh lưu vào kính."
        }
        is Action.GlassesVideo -> {
            val rec = glasses.isRecording()
            if (action.start && rec) "Kính đang quay video sẵn rồi."
            else if (!action.start && !rec) "Kính hiện không quay video."
            else {
                glasses.toggleVideo()
                if (action.start) "Đã bắt đầu quay video trên kính." else "Đã dừng quay video trên kính."
            }
        }
        Action.GlassesSync -> {
            glasses.syncGallery()
            "Đang bắt đầu đồng bộ ảnh và video từ kính về điện thoại."
        }
        Action.GlassesStatus -> {
            val pct = glasses.battery()?.let { "Pin kính còn $it phần trăm" } ?: "Chưa rõ mức pin kính"
            val pend = glasses.pendingCount()
            val mediaStr = if (pend > 0) ", có $pend ảnh hoặc video mới chờ đồng bộ" else ", không có ảnh mới chờ đồng bộ"
            val recStr = if (glasses.isRecording()) ", đang quay video" else ""
            "$pct$mediaStr$recStr."
        }
        Action.NewChat -> {
            glasses.newChat()
            "Đã mở đoạn chat mới."
        }
        is Action.Media -> {
            val audio = context.getSystemService(AudioManager::class.java)
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, action.keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, action.keyCode))
            action.reply
        }
        is Action.Volume -> {
            val audio = context.getSystemService(AudioManager::class.java)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, action.direction, 0)
            action.reply
        }
        is Action.Timer -> {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchSafely(context, intent)) "Đã đặt hẹn giờ ${action.label}."
            else "Không tìm thấy ứng dụng đồng hồ để đặt hẹn giờ."
        }
        is Action.Alarm -> {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val minText = if (action.minute == 0) "" else " ${action.minute} phút"
            if (launchSafely(context, intent)) "Đã đặt báo thức lúc ${action.hour} giờ$minText."
            else "Không tìm thấy ứng dụng đồng hồ để đặt báo thức."
        }
        is Action.Navigate -> {
            val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(action.destination)}")).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(action.destination)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchSafely(context, nav) || launchSafely(context, geo)) "Đang mở chỉ đường đến ${action.destination}."
            else "Không mở được ứng dụng bản đồ."
        }
        is Action.Call -> placeCall(context, action.target)
        is Action.OpenApp -> openInstalledApp(context, action.appQuery)
    }

    private fun placeCall(context: Context, target: String): String {
        val digits = target.filter { it.isDigit() || it == '+' }
        val (displayName, number) = if (digits.count { it.isDigit() } >= 3) {
            target to digits
        } else {
            val match = findContactNumber(context, target)
                ?: return if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    "Chưa cấp quyền đọc danh bạ cho app."
                } else {
                    "Không tìm thấy số điện thoại của $target trong danh bạ."
                }
            match
        }
        val canCallDirectly = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (launchSafely(context, intent)) {
            if (canCallDirectly) "Đang gọi cho $displayName." else "Đã mở số của $displayName, bạn bấm gọi trên máy."
        } else "Không mở được ứng dụng gọi điện."
    }

    private fun findContactNumber(context: Context, query: String): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val targetNorm = normalize(query)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        var containsMatch: Pair<String, String>? = null
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)?.trim().orEmpty()
                val num = c.getString(1)?.trim().orEmpty()
                if (name.isEmpty() || num.isEmpty()) continue
                val norm = normalize(name)
                if (norm == targetNorm) return name to num
                if (containsMatch == null && (norm.contains(targetNorm) || targetNorm.contains(norm))) {
                    containsMatch = name to num
                }
            }
        }
        return containsMatch
    }

    private fun openInstalledApp(context: Context, appQuery: String): String =
        openInstalledAppOrNull(context, appQuery) ?: "Không tìm thấy ứng dụng $appQuery trên điện thoại."

    private fun openInstalledAppOrNull(context: Context, appQuery: String): String? {
        val pm = context.packageManager
        val targetNorm = normalize(appQuery)
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        var bestPkg: String? = null
        var bestLabel: String? = null
        for (info in apps) {
            val label = info.loadLabel(pm).toString().trim()
            val norm = normalize(label)
            if (norm == targetNorm) {
                bestPkg = info.activityInfo.packageName; bestLabel = label; break
            }
            if (bestPkg == null && targetNorm.length >= 3 && norm.contains(targetNorm)) {
                bestPkg = info.activityInfo.packageName; bestLabel = label
            }
        }
        val pkg = bestPkg ?: return null
        val launch = pm.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } ?: return null
        return if (launchSafely(context, launch)) "Đang mở $bestLabel." else "Không mở được $bestLabel."
    }

    private fun launchSafely(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)

    internal fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9\\s+]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
