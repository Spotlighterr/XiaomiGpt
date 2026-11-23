package wtf.mahdi.glassesconnect.accounts

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.spotlighterr.xiaomigpt.AccountStore
import io.github.spotlighterr.xiaomigpt.BrowserSignIn
import io.github.spotlighterr.xiaomigpt.ChatGptClient
import io.github.spotlighterr.xiaomigpt.SpeechLanguage
import io.github.spotlighterr.xiaomigpt.SpeechModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import wtf.mahdi.glassesconnect.ui.theme.AppLang
import java.io.File
import java.util.concurrent.Executors

data class AccountUi(
    val provider: String = "chatgpt", val chatgptAccount: String = "", val model: String = "",
    val models: List<ChatGptClient.Choice> = emptyList(), val bridgeUrl: String = "", val bridgeReady: Boolean = false,
    val bridgeModels: List<String> = emptyList(), val bridgeModel: String = "", val bridgeSupportsImages: Boolean = false,
    val deepseekReady: Boolean = false, val deepseekModel: String = "deepseek-chat",
    val autoFallback: Boolean = true, val speechRate: Float = 1.15f, val deviceActions: Boolean = true,
    val customContext: String = "", val appLang: AppLang = AppLang.VI,
    val language: SpeechLanguage = SpeechLanguage.VIETNAMESE, val speechReady: Boolean = false,
    val cameraMode: String = "auto", val hdCamera: Boolean = true, val busy: Boolean = false, val status: String = "", val question: String = "", val answer: String = "",
) {
    val camera: Boolean get() = cameraMode == "always"
}

class AccountHub(private val context: Context, private val prefs: SharedPreferences) {
    private val store = AccountStore(context)
    private val chatgpt = ChatGptClient(store)
    private val browser = BrowserSignIn(store, chatgpt)
    private val bridge = BridgeClient()
    private val deepseek = DeepSeekClient()
    val chatStore = ChatStore(File(context.noBackupFilesDir, "chat-threads.json"))
    private val photoDir = File(context.noBackupFilesDir, "chat-photos").apply { mkdirs() }
    @Volatile private var lastPhotoBytes: ByteArray? = null
    @Volatile private var lastPhotoAt: Long = 0L
    @Volatile private var lastPhotoThreadId: String = ""
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "account-setup") }
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(AccountUi(
        provider = prefs.getString("account_provider", "chatgpt")!!,
        chatgptAccount = store.active()?.optString("email").orEmpty(), model = prefs.getString("chatgpt_model", "")!!,
        bridgeUrl = prefs.getString("account_bridge_url", "")!!,
        deepseekReady = prefs.getString("deepseek_api_key", "").orEmpty().isNotBlank(),
        deepseekModel = prefs.getString("deepseek_model", "deepseek-chat")!!.takeIf { it in DeepSeekClient.MODELS } ?: "deepseek-chat",
        autoFallback = prefs.getBoolean("account_auto_fallback", true),
        speechRate = prefs.getFloat("speech_rate", 1.15f).coerceIn(0.6f, 2.0f),
        deviceActions = prefs.getBoolean("account_device_actions", true),
        customContext = prefs.getString("account_custom_context", "").orEmpty(),
        appLang = AppLang.fromCode(prefs.getString("app_lang", "vi")),
        language = SpeechLanguage.fromTag(prefs.getString("speech_language", "vi-VN")),
        cameraMode = prefs.getString("account_camera_mode", "auto")!!.takeIf { it in setOf("auto", "always", "off") } ?: "auto",
        hdCamera = prefs.getBoolean("account_hd_camera", true)))
    val state = _state.asStateFlow()
    var onChanged: () -> Unit = {}
    init {
        refreshSpeech()
        if (_state.value.bridgeUrl.isNotBlank() && prefs.getString("account_bridge_token", "").orEmpty().length >= 32) checkBridge()
    }
    fun speechModel() = SpeechModel(context, _state.value.language)
    fun refreshSpeech() { _state.update { it.copy(speechReady = speechModel().ready()) } }
    fun selectProvider(value: String) {
        require(value in setOf("gemini_live", "chatgpt", "gemini", "deepseek")); onChanged()
        prefs.edit().putString("account_provider", value).apply(); _state.update { it.copy(provider = value) }
    }
    fun configureDeepSeek(apiKey: String, model: String) {
        require(model in DeepSeekClient.MODELS)
        onChanged()
        val clean = apiKey.trim()
        val e = prefs.edit().putString("deepseek_model", model)
        if (clean.isNotBlank()) e.putString("deepseek_api_key", clean)
        e.apply()
        val ready = prefs.getString("deepseek_api_key", "").orEmpty().isNotBlank()
        _state.update { it.copy(deepseekReady = ready, deepseekModel = model, status = if (ready) "DeepSeek ($model) đã sẵn sàng." else "Chưa nhập DeepSeek API Key.") }
    }
    fun selectDeepSeekModel(model: String) {
        require(model in DeepSeekClient.MODELS); onChanged()
        prefs.edit().putString("deepseek_model", model).apply()
        _state.update { it.copy(deepseekModel = model) }
    }
    fun speechRate(rate: Float) {
        val clamped = rate.coerceIn(0.6f, 2.0f)
        prefs.edit().putFloat("speech_rate", clamped).apply()
        _state.update { it.copy(speechRate = clamped) }
    }
    fun customContext(value: String) {
        val clean = value.trim().take(2000)
        prefs.edit().putString("account_custom_context", clean).apply()
        _state.update { it.copy(customContext = clean, status = if (clean.isNotBlank()) "Đã lưu ngữ cảnh riêng." else "Đã xóa ngữ cảnh riêng.") }
    }
    fun appLang(lang: AppLang) {
        prefs.edit().putString("app_lang", lang.code).apply()
        _state.update { it.copy(appLang = lang) }
    }
    fun clearConversation() {
        lastPhotoBytes = null; lastPhotoAt = 0L
        chatStore.clearActive()
        _state.update { it.copy(question = "", answer = "", status = "Đã xóa nội dung đoạn chat hiện tại.") }
    }
    fun startNewChat() {
        lastPhotoBytes = null; lastPhotoAt = 0L
        chatStore.newThread()
    }
    fun recentPhotoForFollowUp(question: String): ByteArray? {
        val bytes = lastPhotoBytes ?: return null
        val age = System.currentTimeMillis() - lastPhotoAt
        if (age > 180_000L || lastPhotoThreadId != chatStore.activeThread().id) return null
        return if (age <= 90_000L || SmartCameraIntent.isVisualFollowUp(question)) bytes else null
    }
    fun recordDeviceTurn(question: String, reply: String) {
        chatStore.appendTurn(question, reply, "Device", false)
    }
    fun autoFallback(enabled: Boolean) {
        prefs.edit().putBoolean("account_auto_fallback", enabled).apply()
        _state.update { it.copy(autoFallback = enabled) }
    }
    fun deviceActions(enabled: Boolean) {
        prefs.edit().putBoolean("account_device_actions", enabled).apply()
        _state.update { it.copy(deviceActions = enabled) }
    }
    fun selectModel(value: String) {
        require(state.value.models.any { it.slug == value }); onChanged()
        prefs.edit().putString("chatgpt_model", value).apply(); _state.update { it.copy(model = value) }
    }
    fun selectBridgeModel(value: String) {
        require(value in _state.value.bridgeModels); onChanged()
        prefs.edit().putString("account_bridge_model", value).apply(); _state.update { it.copy(bridgeModel = value) }
    }
    fun cameraMode(mode: String) {
        require(mode in setOf("auto", "always", "off")); onChanged()
        prefs.edit().putString("account_camera_mode", mode).apply(); _state.update { it.copy(cameraMode = mode) }
    }
    fun hdCamera(enabled: Boolean) {
        prefs.edit().putBoolean("account_hd_camera", enabled).apply()
        _state.update { it.copy(hdCamera = enabled) }
    }
    fun language(language: SpeechLanguage) {
        onChanged(); prefs.edit().putString("speech_language", language.tag).apply(); _state.update { it.copy(language = language) }; refreshSpeech()
    }
    fun login(open: (Uri) -> Unit) {
        onChanged()
        _state.update { it.copy(busy = true, status = "Đang mở đăng nhập ChatGPT…") }
        browser.start(store.active(), { uri -> main.post { open(uri) } }) { result ->
            result.fold({ refreshChatGpt() }, { e -> _state.update { it.copy(busy = false, status = e.message ?: "Đăng nhập chưa hoàn tất.") } })
        }
    }
    fun refreshChatGpt() = work {
        val models = chatgpt.models()
        val picked = _state.value.model.takeIf { selected -> models.any { it.slug == selected } } ?: models.first().slug
        prefs.edit().putString("chatgpt_model", picked).apply()
        _state.update { it.copy(chatgptAccount = store.active()?.optString("email").orEmpty(), models = models, model = picked, status = "Tài khoản ChatGPT sẵn sàng.") }
    }
    fun logout() { onChanged(); work { chatgpt.signOut(); _state.update { it.copy(chatgptAccount = "", models = emptyList(), model = "", status = "Đã đăng xuất ChatGPT.") } } }
    @Synchronized fun configureBridge(url: String, token: String) {
        onChanged(); val clean = BridgePolicy.baseUrl(url)
        require(clean == _state.value.bridgeUrl || token.isNotBlank()) { "Nhập mã kết nối khi đổi địa chỉ máy chủ." }
        require(token.isBlank() || token.length >= 32) { "Mã kết nối phải có ít nhất 32 ký tự." }
        val e = prefs.edit().putString("account_bridge_url", clean)
        if (token.isNotBlank()) e.putString("account_bridge_token", token)
        e.apply(); _state.update { it.copy(bridgeUrl = clean, bridgeReady = false) }; checkBridge()
    }
    fun checkBridge() = work {
        val result = bridge.status(_state.value.bridgeUrl, prefs.getString("account_bridge_token", "")!!)
        val provider = result.optJSONObject("providers")?.opt("gemini")
        val ready = when (provider) {
            is org.json.JSONObject -> provider.optBoolean("available", provider.optBoolean("ready", false))
            is Boolean -> provider
            else -> false
        }
        val details = provider as? org.json.JSONObject
        val allowed = details?.optJSONArray("allowed_models")
        val models = if (allowed == null) emptyList() else (0 until allowed.length()).map { allowed.getString(it) }
        val selected = prefs.getString("account_bridge_model", "").orEmpty().takeIf { it in models }
            ?: details?.optString("default_model").orEmpty().takeIf { it in models } ?: models.firstOrNull().orEmpty()
        prefs.edit().putString("account_bridge_model", selected).apply()
        _state.update { it.copy(bridgeReady = ready, bridgeModels = models, bridgeModel = selected,
            bridgeSupportsImages = details?.optBoolean("supports_image", false) == true,
            status = if (ready) "Máy chủ đã kết nối; tài khoản Gemini sẽ được xác nhận khi hỏi." else "Đã kết nối máy chủ; Gemini chưa sẵn sàng.") }
    }
    fun installSpeech() = work { speechModel().install({ !Thread.currentThread().isInterrupted }) { p ->
        _state.update { it.copy(status = "Đang tải bộ nhận dạng: $p%") }
    }; refreshSpeech(); _state.update { it.copy(status = "Nhận dạng giọng nói đã sẵn sàng.") } }
    private fun work(block: () -> Unit) {
        _state.update { it.copy(busy = true) }
        worker.execute { try { block() } catch (e: Exception) { status(e.message ?: "Thao tác chưa thành công.") }
            finally { _state.update { it.copy(busy = false) } } }
    }
    fun status(message: String) { _state.update { it.copy(status = message.take(500)) } }
    fun transcript(question: String) { _state.update { it.copy(question = question, answer = "") } }
    fun answered(answer: String) { _state.update { it.copy(answer = answer, status = "Đã nhận câu trả lời.") } }
    private fun askChatGpt(snapshot: AccountUi, history: List<Pair<String, String>>, question: String, jpeg: ByteArray?, active: () -> Boolean): String {
        val model = snapshot.model.ifBlank { error("Đăng nhập ChatGPT và tải danh sách model trước.") }
        return chatgpt.answer(model, history, question, snapshot.language, jpeg, active)
    }
    private fun askGeminiBridge(snapshot: AccountUi, bridgeToken: String, history: List<Pair<String, String>>, question: String, jpeg: ByteArray?): String {
        check(snapshot.bridgeReady) { "Kết nối máy chủ tài khoản Gemini trước." }
        check(jpeg == null || snapshot.bridgeSupportsImages) { "Máy chủ Gemini chưa xác nhận hỗ trợ ảnh." }
        return bridge.ask(snapshot.bridgeUrl, bridgeToken,
            "${if (snapshot.language == SpeechLanguage.VIETNAMESE) "Trả lời tiếng Việt, ngắn gọn." else "Answer briefly in English."}\n$question", jpeg,
            snapshot.bridgeModel, history)
    }
    fun ask(question: String, jpeg: ByteArray?, active: () -> Boolean): String {
        if (chatStore.rotateIfIdle()) { lastPhotoBytes = null; lastPhotoAt = 0L }
        val (snapshot, bridgeToken, deepseekKey) = synchronized(this) {
            Triple(_state.value, prefs.getString("account_bridge_token", "")!!, prefs.getString("deepseek_api_key", "")!!)
        }
        val activeThreadId = chatStore.activeThread().id
        val effectiveJpeg = jpeg ?: recentPhotoForFollowUp(question)
        val photoIsReused = jpeg == null && effectiveJpeg != null
        if (jpeg != null) {
            lastPhotoBytes = jpeg; lastPhotoAt = System.currentTimeMillis(); lastPhotoThreadId = activeThreadId
        }
        val baseHistory = chatStore.historyPairs(12)
        val history = if (snapshot.customContext.isNotBlank()) {
            listOf("user" to "Ngữ cảnh của tôi: ${snapshot.customContext}", "assistant" to "Đã ghi nhớ ngữ cảnh của bạn.") + baseHistory
        } else baseHistory
        check(active()) { "Đã dừng." }
        var usedProvider = when (snapshot.provider) {
            "chatgpt" -> "ChatGPT"
            "deepseek" -> "DeepSeek"
            else -> "Gemini Bridge"
        }
        val answer = when (snapshot.provider) {
            "deepseek" -> {
                if (effectiveJpeg != null) {
                    if (snapshot.autoFallback && snapshot.model.isNotBlank() && active()) {
                        usedProvider = "ChatGPT"
                        status("Câu hỏi có ảnh, đang tự chuyển sang ChatGPT…")
                        askChatGpt(snapshot, history, question, effectiveJpeg, active)
                    } else if (snapshot.autoFallback && snapshot.bridgeReady && snapshot.bridgeSupportsImages && active()) {
                        usedProvider = "Gemini Bridge"
                        status("Câu hỏi có ảnh, đang tự chuyển sang Gemini Bridge…")
                        askGeminiBridge(snapshot, bridgeToken, history, question, effectiveJpeg)
                    } else {
                        error("DeepSeek chỉ hỗ trợ văn bản. Bật Tự dự phòng hoặc chọn ChatGPT/Gemini cho ảnh.")
                    }
                } else {
                    try {
                        deepseek.ask(deepseekKey, snapshot.deepseekModel, history, question, snapshot.language, active)
                    } catch (primaryError: Exception) {
                        if (snapshot.autoFallback && snapshot.model.isNotBlank() && active()) {
                            usedProvider = "ChatGPT"
                            status("DeepSeek gặp lỗi, đang tự chuyển sang ChatGPT…")
                            askChatGpt(snapshot, history, question, null, active)
                        } else if (snapshot.autoFallback && snapshot.bridgeReady && active()) {
                            usedProvider = "Gemini Bridge"
                            status("DeepSeek gặp lỗi, đang tự chuyển sang Gemini Bridge…")
                            askGeminiBridge(snapshot, bridgeToken, history, question, null)
                        } else throw primaryError
                    }
                }
            }
            "chatgpt" -> {
                try {
                    askChatGpt(snapshot, history, question, effectiveJpeg, active)
                } catch (primaryError: Exception) {
                    if (snapshot.autoFallback && snapshot.bridgeReady && (effectiveJpeg == null || snapshot.bridgeSupportsImages) && active()) {
                        usedProvider = "Gemini Bridge"
                        status("ChatGPT gặp lỗi, đang tự chuyển sang Gemini Bridge…")
                        askGeminiBridge(snapshot, bridgeToken, history, question, effectiveJpeg)
                    } else if (snapshot.autoFallback && effectiveJpeg == null && snapshot.deepseekReady && active()) {
                        usedProvider = "DeepSeek"
                        status("ChatGPT gặp lỗi, đang tự chuyển sang DeepSeek…")
                        deepseek.ask(deepseekKey, snapshot.deepseekModel, history, question, snapshot.language, active)
                    } else throw primaryError
                }
            }
            else -> {
                try {
                    askGeminiBridge(snapshot, bridgeToken, history, question, effectiveJpeg)
                } catch (primaryError: Exception) {
                    if (snapshot.autoFallback && snapshot.model.isNotBlank() && active()) {
                        usedProvider = "ChatGPT"
                        status("Gemini Bridge gặp lỗi, đang tự chuyển sang ChatGPT…")
                        askChatGpt(snapshot, history, question, effectiveJpeg, active)
                    } else if (snapshot.autoFallback && effectiveJpeg == null && snapshot.deepseekReady && active()) {
                        usedProvider = "DeepSeek"
                        status("Gemini Bridge gặp lỗi, đang tự chuyển sang DeepSeek…")
                        deepseek.ask(deepseekKey, snapshot.deepseekModel, history, question, snapshot.language, active)
                    } else throw primaryError
                }
            }
        }
        check(active()) { "Đã dừng." }
        val savedPath = if (jpeg != null) {
            runCatching {
                photoDir.mkdirs()
                val f = File(photoDir, "img_${System.currentTimeMillis()}.jpg")
                f.writeBytes(jpeg)
                f.absolutePath
            }.getOrNull()
        } else null
        chatStore.appendTurn(question, answer, usedProvider, jpeg != null || photoIsReused, savedPath)
        return answer
    }
    fun cancelInference() { chatgpt.cancel(); bridge.cancel(); deepseek.cancel() }
}
