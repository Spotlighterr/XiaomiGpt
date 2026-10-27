package io.github.spotlighterr.xiaomigpt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

class AssistantActivity : Activity() {
    private lateinit var accounts: AccountStore
    private lateinit var client: ChatGptClient
    private lateinit var signIn: BrowserSignIn
    private lateinit var speechModels: Map<SpeechLanguage, SpeechModel>
    private var language = SpeechLanguage.VIETNAMESE
    private val preferences by lazy { getSharedPreferences("assistant-preferences", MODE_PRIVATE) }
    private val activations by lazy { ActivationJournal(this) }
    private val activationGate = ActivationGate()
    private lateinit var voice: VoiceAssistant
    private val worker = Executors.newSingleThreadExecutor()
    private var devices = emptyList<AudioDeviceInfo>()
    private var choices = emptyList<ChatGptClient.Choice>()
    private var voiceBusy = false
    private var setupBusy = false
    private var signingIn = false
    private var foreground = false
    private var pendingTalk = false
    private var pendingSystemAssist = false
    @Volatile private var alive = true

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_assistant)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.assistantRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        try {
            accounts = AccountStore(this); accounts.host()
            client = ChatGptClient(accounts); signIn = BrowserSignIn(accounts, client)
            language = SpeechLanguage.fromTag(preferences.getString("language", null))
            speechModels = SpeechLanguage.entries.associateWith { SpeechModel(this, it) }
            voice = VoiceAssistant(this, client)
        } catch (_: Exception) {
            status("Không mở được kho đăng nhập an toàn. Không thể khởi động trợ lý; hãy thử mở lại app.")
            return
        }
        button(R.id.signIn).setOnClickListener { beginSignIn() }
        button(R.id.accountPicker).setOnClickListener { chooseAccount() }
        button(R.id.signOut).setOnClickListener { logout() }
        button(R.id.reloadModels).setOnClickListener { loadModels() }
        button(R.id.downloadEnglish).setOnClickListener { downloadEnglish() }
        val languagePicker = findViewById<Spinner>(R.id.languagePicker)
        languagePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, SpeechLanguage.entries)
        languagePicker.setSelection(language.ordinal)
        languagePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = SpeechLanguage.entries[position]
                if (next != language && !voiceBusy && !setupBusy) {
                    language = next; preferences.edit().putString("language", next.tag).apply()
                    voice.clearHistory(); text(R.id.transcript, ""); text(R.id.assistantAnswer, "")
                    status("Đã chọn ${next.label}. Tải nhận dạng nếu chưa có và cài giọng đọc cùng ngôn ngữ."); render()
                }
            }
        }
        button(R.id.assistantPermissions).setOnClickListener { permissions(false) }
        button(R.id.talk).setOnClickListener { beginTalk(null) }
        button(R.id.finishSpeech).setOnClickListener { if (voice.listening) voice.finishSpeech() }
        button(R.id.stopAssistant).setOnClickListener {
            if (signingIn) { signIn.cancel(); signingIn = false; setupBusy = false; status("Đã hủy đăng nhập."); render() }
            else voice.cancel()
        }
        button(R.id.sendTyped).setOnClickListener {
            val question = findViewById<EditText>(R.id.typedQuestion).text.toString().trim()
            if (question.isNotBlank()) beginTalk(question) else status("Nhập câu hỏi trước khi gửi.")
        }
        button(R.id.newConversation).setOnClickListener { voice.clearHistory(); text(R.id.transcript, ""); text(R.id.assistantAnswer, ""); status("Đã bắt đầu cuộc trò chuyện mới.") }
        button(R.id.setDefaultAssistant).setOnClickListener {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                @Suppress("DEPRECATION")
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 20)
            } else launch(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }
        button(R.id.ttsSettings).setOnClickListener { launch(Intent("com.android.settings.TTS_SETTINGS")) }
        button(R.id.manageUsage).setOnClickListener { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage"))) }
        button(R.id.openDiagnostics).setOnClickListener { launch(Intent(this, MainActivity::class.java)) }
        button(R.id.testSystemAssistant).setOnClickListener {
            if (!nativeAssistantActive()) { status("Chọn XiaomiGpt làm trợ lý mặc định trước. Nếu đã chọn ở bản cũ, mở cài đặt trợ lý và chọn lại XiaomiGpt."); return@setOnClickListener }
            try {
                startService(Intent(this, GlassesVoiceService::class.java).setAction(GlassesVoiceService.ACTION_TEST))
                status("Đã gửi lệnh thử qua dịch vụ trợ lý Android. Chờ phiên trợ lý mở…")
            } catch (_: Exception) { status("Android chưa mở được dịch vụ trợ lý. Mở cài đặt trợ lý và chọn lại XiaomiGpt.") }
        }
        button(R.id.activationDetails).setOnClickListener {
            val report = activations.report()
            AlertDialog.Builder(this).setTitle("Nhật ký gọi trợ lý").setMessage(report)
                .setPositiveButton("Sao chép") { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("XiaomiGpt activation", report)) }
                .setNeutralButton("Xóa nhật ký") { _, _ -> activations.clear() }.setNegativeButton("Đóng", null).show()
        }
        button(R.id.inferenceDetails).setOnClickListener {
            val details = "XiaomiGpt ${BuildConfig.VERSION_NAME}\n" + (client.lastDiagnostic?.report() ?: "Chưa có phản hồi HTTP của lượt hỏi này.")
            AlertDialog.Builder(this).setTitle("Chi tiết phản hồi hội thoại")
                .setMessage("$details\n\nKhông kèm câu hỏi, câu trả lời, email hoặc token. Bạn có thể sao chép và gửi thông tin này để kiểm tra lỗi.")
                .setPositiveButton("Sao chép") { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("XiaomiGpt response diagnostic", details)) }
                .setNegativeButton("Đóng", null).show()
        }
        pendingSystemAssist = state == null && isAssist(intent)
        if (pendingSystemAssist) { recordIntent(intent); activationGate.accept(SystemClock.elapsedRealtime()) }
        render()
        if (accounts.active()?.optString("access_token")?.isNotBlank() == true) loadModels()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (isAssist(intent)) {
            recordIntent(intent)
            if (activationGate.accept(SystemClock.elapsedRealtime())) {
                if (voiceBusy) { pendingSystemAssist = false; if (voice.listening) voice.finishSpeech() else voice.cancel() }
                else pendingSystemAssist = true
            }
        }
    }
    private fun recordIntent(intent: Intent) {
        activations.record(if (intent.action == Intent.ACTION_VOICE_COMMAND) ActivationJournal.Kind.VOICE_COMMAND_INTENT else ActivationJournal.Kind.ASSIST_INTENT)
    }
    private fun isAssist(intent: Intent) = intent.action == Intent.ACTION_ASSIST || intent.action == Intent.ACTION_VOICE_COMMAND
    override fun onResume() {
        super.onResume(); foreground = true
        if (!::voice.isInitialized) return
        refreshDevices()
        renderActivationStatus()
        if (pendingTalk) { pendingTalk = false; beginTalk(null) }
        consumeSystemAssist()
        showPlanWelcome()
    }
    override fun onPause() { foreground = false; if (::voice.isInitialized) voice.cancel(); super.onPause() }
    override fun onDestroy() {
        alive = false
        if (::signIn.isInitialized) signIn.shutdown()
        if (::voice.isInitialized) voice.shutdown()
        worker.shutdown()
        super.onDestroy()
    }
    private fun permissions(talk: Boolean) {
        val missing = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { pendingTalk = talk; requestPermissions(missing.toTypedArray(), 10) }
        else { refreshDevices(); if (talk) beginTalk(null) }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != 10 || !::voice.isInitialized) return
        refreshDevices()
        if (results.any { it != PackageManager.PERMISSION_GRANTED }) { pendingTalk = false; status("Cần quyền Bluetooth và micro để nói qua kính.") }
        else if (pendingTalk && foreground) { pendingTalk = false; beginTalk(null) }
    }
    private fun refreshDevices() {
        if (voiceBusy) return
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            devices = emptyList(); text(R.id.assistantBluetoothStatus, "Hãy cấp quyền Bluetooth và micro."); return
        }
        val picker = findViewById<Spinner>(R.id.assistantAudioPicker)
        val selected = devices.getOrNull(picker.selectedItemPosition)?.id
        devices = runCatching { getSystemService(AudioManager::class.java).availableCommunicationDevices.filter { AudioProbe.isHeadset(it.type) } }.getOrDefault(emptyList())
        picker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, devices.map { it.productName.toString() })
        devices.indexOfFirst { it.id == selected }.takeIf { it >= 0 }?.let { picker.setSelection(it) }
        picker.visibility = if (devices.size > 1) View.VISIBLE else View.GONE
        text(R.id.assistantBluetoothStatus, when (devices.size) { 0 -> "Chưa thấy kính Bluetooth. Ghép kính và bật âm thanh cuộc gọi trong Cài đặt Bluetooth."; 1 -> "Âm thanh: ${devices[0].productName}"; else -> "Chọn kính Bluetooth cho phiên này:" })
    }
    private fun beginSignIn(previous: org.json.JSONObject? = accounts.active()) {
        if (setupBusy || voiceBusy) return
        setupBusy = true; signingIn = true; status("Hoàn tất đăng nhập trên trình duyệt rồi quay lại XiaomiGpt."); render()
        signIn.start(previous, { uri -> ui {
            if (signingIn) try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
            catch (_: Exception) { signIn.cancel(); signingIn = false; setupBusy = false; status("Không mở được trình duyệt đăng nhập."); render() }
        } }, { result -> ui {
            signingIn = false; setupBusy = false; render()
            if (result.isSuccess) { voice.clearHistory(); choices = emptyList(); status("Đã xác minh tài khoản. Đang kiểm tra quyền dùng ChatGPT…"); showPlanWelcome(); loadModels() }
            else status("Chưa hoàn tất đăng nhập an toàn. Kiểm tra quyền trên trình duyệt và thử lại; bạn có thể hủy phiên chờ bằng Dừng trợ lý.")
        } })
    }
    private fun chooseAccount() {
        val saved = accounts.accounts()
        val labels = saved.mapIndexed { index, a -> "${index + 1}. ${a.optString("email", "ChatGPT")}" } + "Thêm tài khoản / workspace khác"
        AlertDialog.Builder(this).setTitle("Tài khoản ChatGPT").setItems(labels.toTypedArray()) { _, index ->
            if (index == saved.size) beginSignIn(null)
            else { accounts.select(saved[index].getString("client_id")); voice.clearHistory(); choices = emptyList(); render()
                if (saved[index].optString("access_token").isNotBlank()) loadModels() else beginSignIn(saved[index]) }
        }.show()
    }
    private fun logout() {
        setupBusy = true; render()
        worker.execute {
            val revoked = client.signOut()
            ui { setupBusy = false; choices = emptyList(); voice.clearHistory(); render(); status(if (revoked) "Đã đăng xuất." else "Đã đăng xuất trên máy. Chưa xác nhận thu hồi phiên từ xa; bạn có thể ngắt XiaomiGpt trong cài đặt ChatGPT.") }
        }
    }
    private fun loadModels() {
        if (setupBusy || voiceBusy) return
        setupBusy = true; choices = emptyList(); render()
        worker.execute {
            val result = runCatching { client.models() }
            ui { setupBusy = false
                result.onSuccess { choices = it; status("ChatGPT đã sẵn sàng. Tải tiếng Anh nếu chưa có, rồi bấm Nói với trợ lý.") }
                    .onFailure { status(it.message?.takeIf { message -> message.length < 300 && !message.contains("https://") } ?: "Chưa tải được model. Kiểm tra mạng và đăng nhập lại nếu cần.") }
                render()
                consumeSystemAssist()
            }
        }
    }
    private fun downloadEnglish() {
        val speech = speechModels.getValue(language)
        setupBusy = true; render()
        worker.execute {
            var last = -1
            val result = runCatching { speech.install({ alive }) { percent -> if (percent != last) { last = percent; ui { status("Đang tải nhận dạng ${speech.language.label}: $percent%") } } } }
            ui { setupBusy = false; status(if (result.isSuccess) "Nhận dạng ${speech.language.label} đã sẵn sàng trên điện thoại." else "Chưa cài được model ${speech.language.label}. Kiểm tra mạng, dung lượng trống rồi thử lại."); render() }
        }
    }
    private fun beginTalk(typed: String?) {
        if (setupBusy || voiceBusy || !foreground) return
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            (typed == null && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)) { permissions(typed == null); return }
        refreshDevices()
        val device = devices.getOrNull(findViewById<Spinner>(R.id.assistantAudioPicker).selectedItemPosition)
        if (device == null) { status("Kết nối kính Bluetooth trước khi hỏi trợ lý."); return }
        val choice = choices.getOrNull(findViewById<Spinner>(R.id.assistantModelPicker).selectedItemPosition)
        if (choice == null) { status("Đăng nhập và tải danh sách model trước."); return }
        val speech = speechModels.getValue(language)
        if (typed == null && !speech.ready()) { status("Bấm Tải nhận dạng ${language.label} trước khi nói."); return }
        preferences.edit().putString("model.${accounts.active()?.optString("client_id")}", choice.slug).apply()
        voiceBusy = true; text(R.id.transcript, typed.orEmpty()); text(R.id.assistantAnswer, ""); status("Đang chuẩn bị kính…"); render()
        voice.start(device, choice.slug, typed, speech, { message -> ui { status(message); button(R.id.finishSpeech).isEnabled = voice.listening } }, { question -> ui { text(R.id.transcript, question) } },
            { reply -> ui { text(R.id.assistantAnswer, reply) } }, { ui { voiceBusy = false; render(); refreshDevices() } })
    }
    private fun consumeSystemAssist() {
        if (pendingSystemAssist && foreground && !setupBusy && !voiceBusy) {
            pendingSystemAssist = false
            beginTalk(null)
        }
    }
    private fun render() {
        if (!::voice.isInitialized) return
        val account = accounts.active()
        text(R.id.accountStatus, account?.let { "${it.optString("email", "ChatGPT")}\n${if (it.optString("access_token").isBlank()) "Cần đăng nhập lại" else "Tài khoản đã đăng nhập"}" } ?: "Chưa đăng nhập ChatGPT")
        text(R.id.planUsageStatus, if (account?.optString("scope")?.split(' ')?.contains(AssistantProtocol.DIRECT_SCOPE) == true) "Using ChatGPT plan · Dùng hạn mức gói ChatGPT của bạn" else "Chưa cấp quyền dùng gói ChatGPT")
        val speech = speechModels.getValue(language)
        val sizeMb = (language.modelBytes + 500_000) / 1_000_000
        text(R.id.englishModelStatus, if (speech.ready()) "Nhận dạng ${language.label}: đã cài" else "Nhận dạng ${language.label}: chưa cài · tải một lần $sizeMb MB")
        button(R.id.downloadEnglish).text = "Tải nhận dạng ${language.label} · $sizeMb MB"
        val picker = findViewById<Spinner>(R.id.assistantModelPicker)
        val selected = (picker.selectedItem as? ChatGptClient.Choice)?.slug ?: preferences.getString("model.${account?.optString("client_id")}", null)
        picker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        choices.indexOfFirst { it.slug == selected }.takeIf { it >= 0 }?.let { picker.setSelection(it) }
        val idle = !setupBusy && !voiceBusy
        listOf(R.id.signIn, R.id.accountPicker, R.id.reloadModels, R.id.talk, R.id.sendTyped, R.id.newConversation, R.id.assistantPermissions, R.id.setDefaultAssistant, R.id.ttsSettings, R.id.openDiagnostics, R.id.testSystemAssistant).forEach { button(it).isEnabled = idle }
        button(R.id.signOut).isEnabled = idle && account?.optString("access_token")?.isNotBlank() == true
        button(R.id.downloadEnglish).isEnabled = idle && !speech.ready()
        button(R.id.downloadEnglish).visibility = if (speech.ready()) View.GONE else View.VISIBLE
        findViewById<Spinner>(R.id.languagePicker).isEnabled = idle
        picker.isEnabled = idle; findViewById<Spinner>(R.id.assistantAudioPicker).isEnabled = idle
        button(R.id.stopAssistant).visibility = if (voiceBusy || signingIn) View.VISIBLE else View.GONE
        button(R.id.finishSpeech).visibility = if (voiceBusy) View.VISIBLE else View.GONE
        button(R.id.finishSpeech).isEnabled = voice.listening
    }
    private fun nativeAssistantActive() = VoiceInteractionService.isActiveService(this, ComponentName(this, GlassesVoiceService::class.java))
    private fun renderActivationStatus() {
        text(R.id.activationStatus, if (nativeAssistantActive()) "Dịch vụ trợ lý XiaomiGpt: đang được Android chọn. Có thể thử gọi từ kính khi điện thoại đã mở khóa." else "Chưa chọn dịch vụ trợ lý XiaomiGpt. Bấm Đặt làm trợ lý mặc định; nếu đã chọn ở bản cũ, hãy chọn lại trong cài đặt Android.")
    }
    private fun showPlanWelcome() {
        if (!foreground || accounts.planWelcomeSeen() || accounts.active()?.optString("scope")?.split(' ')?.contains(AssistantProtocol.DIRECT_SCOPE) != true) return
        accounts.markPlanWelcomeSeen()
        AlertDialog.Builder(this).setTitle("Bạn đang dùng gói ChatGPT")
            .setMessage("Các yêu cầu AI đủ điều kiện trong XiaomiGpt dùng hạn mức gói ChatGPT của bạn. Bạn có thể quản lý hạn mức và quyền truy cập bằng nút Quản lý Usage.")
            .setPositiveButton("Đã hiểu", null).show()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val diagnosticKeys = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_CAMERA)
        if (event.keyCode in diagnosticKeys && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            activations.record(ActivationJournal.Kind.FOREGROUND_KEY, event.keyCode)
        }
        if (::voice.isInitialized && event.keyCode in setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (activationGate.accept(SystemClock.elapsedRealtime())) {
                    if (voiceBusy) { if (voice.listening) voice.finishSpeech() else voice.cancel() } else beginTalk(null)
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun launch(intent: Intent) { try { startActivity(intent) } catch (_: Exception) { status("Không mở được trang cài đặt này. Hãy mở từ Cài đặt Android.") } }
    private fun status(message: String) { text(R.id.assistantStatus, message) }
    private fun text(id: Int, value: String) { findViewById<TextView>(id).text = value }
    private fun button(id: Int): Button = findViewById(id)
    private fun ui(block: () -> Unit) { runOnUiThread { if (alive && !isDestroyed) block() } }
}
