package wtf.mahdi.glassesconnect.ui.ai

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import wtf.mahdi.glassesconnect.ui.LocalNav
import wtf.mahdi.glassesconnect.ui.Route
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.spotlighterr.xiaomigpt.SpeechLanguage
import wtf.mahdi.glassesconnect.R
import wtf.mahdi.glassesconnect.accounts.ChatMessage
import wtf.mahdi.glassesconnect.accounts.ChatThread
import wtf.mahdi.glassesconnect.accounts.DeepSeekClient
import wtf.mahdi.glassesconnect.accounts.SmartCameraIntent
import wtf.mahdi.glassesconnect.ai.AiSettings
import wtf.mahdi.glassesconnect.ui.LocalController
import wtf.mahdi.glassesconnect.ui.components.IconBtn
import wtf.mahdi.glassesconnect.ui.theme.AppLang
import wtf.mahdi.glassesconnect.ui.theme.LocalStrings
import wtf.mahdi.glassesconnect.ui.theme.Xiao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Editorial chat workspace following antislop / antislop-ui discipline:
 * - No sparkle/star AI avatars, no decorative emoji, no radial glows, no uniform pill-everything.
 * - Warm paper/charcoal surfaces with 1dp hairline borders and restrained terracotta accent on primary actions only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen() {
    val controller = LocalController.current
    val hub = controller.accounts
    val state by hub.state.collectAsStateWithLifecycle()
    val glasses by controller.ui.collectAsStateWithLifecycle()
    val threads by hub.chatStore.threads.collectAsStateWithLifecycle()
    val activeId by hub.chatStore.activeId.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val c = Xiao.colors

    val activeThread = remember(threads, activeId) { threads.firstOrNull { it.id == activeId } ?: threads.firstOrNull() }
    val messages = activeThread?.messages.orEmpty()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val providerBadge = when (state.provider) {
        "gemini_live" -> "Gemini Live"
        "chatgpt" -> "ChatGPT"
        "deepseek" -> "DeepSeek"
        else -> "Gemini Bridge"
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        // Editorial top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconBtn(R.drawable.ic_menu, s.chatHistory, { showHistory = true })
            Column(Modifier.weight(1f).clickable { showSettings = true }) {
                Text(
                    text = activeThread?.title?.ifBlank { s.newChat } ?: s.newChat,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (glasses.ready) c.ok else c.warn))
                    Text(
                        text = "$providerBadge · ${if (glasses.ready) s.glassesConnected else s.glassesDisconnected}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                onClick = { hub.appLang(if (state.appLang == AppLang.VI) AppLang.EN else AppLang.VI) },
                shape = RoundedCornerShape(10.dp),
                color = c.surface2,
                border = BorderStroke(1.dp, c.line),
            ) {
                Text(state.appLang.shortCode, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = c.text)
            }
            IconBtn(R.drawable.ic_add, s.newChat, { hub.startNewChat() })
            IconBtn(R.drawable.ic_tune, s.aiSettingsTitle, { showSettings = true })
        }
        HorizontalDivider(color = c.line, thickness = 1.dp)

        // Live status strip (real runtime state only)
        val banner = localError.ifBlank {
            if (glasses.aiState != "Idle") "${glasses.aiState}: ${state.status}"
            else if (state.busy) state.status
            else ""
        }
        if (banner.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().background(c.surface2).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(banner, style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
                if (glasses.aiState != "Idle") {
                    TextButton(onClick = controller::stopAi, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text(s.stop, color = c.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            HorizontalDivider(color = c.line, thickness = 1.dp)
        }

        // Main chat canvas
        if (messages.isEmpty()) {
            EditorialEmptyCanvas(
                modifier = Modifier.weight(1f),
                hasContext = state.customContext.isNotBlank(),
                onSuggestion = { q ->
                    if (glasses.ready && state.provider != "gemini_live") {
                        val cam = state.cameraMode == "always" || (state.cameraMode == "auto" && SmartCameraIntent.shouldCapture(q))
                        controller.askAccount(q, cam)
                    } else input = q
                },
                onOpenSettings = { showSettings = true },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(messages) { msg -> ChatEntry(msg) }
            }
        }

        // Bottom composer
        Surface(color = c.surface, tonalElevation = 0.dp) {
            Column {
                HorizontalDivider(color = c.line, thickness = 1.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val camLabel = when (state.cameraMode) {
                        "auto" -> s.camAuto
                        "always" -> s.camAlways
                        else -> s.camOff
                    }
                    Surface(
                        onClick = {
                            val next = when (state.cameraMode) { "auto" -> "always"; "always" -> "off"; else -> "auto" }
                            hub.cameraMode(next)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = c.surface2,
                        border = BorderStroke(1.dp, c.line),
                    ) {
                        Text(
                            camLabel,
                            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.cameraMode == "off") c.muted else c.text,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(s.inputPlaceholder, style = MaterialTheme.typography.bodyMedium, color = c.muted) },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    val isBusy = glasses.aiState != "Idle"
                    Button(
                        onClick = {
                            if (isBusy) {
                                controller.stopAi()
                            } else {
                                val q = input.trim()
                                if (q.isNotEmpty()) {
                                    localError = ""
                                    if (!glasses.ready) {
                                        localError = s.connectFirstError
                                    } else {
                                        val cam = state.cameraMode == "always" || (state.cameraMode == "auto" && SmartCameraIntent.shouldCapture(q))
                                        controller.askAccount(q, cam)
                                        input = ""
                                    }
                                }
                            }
                        },
                        enabled = isBusy || (input.isNotBlank() && state.provider != "gemini_live"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    ) {
                        Text(if (isBusy) s.stop else s.send, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }, containerColor = c.surface) {
            HistorySheet(
                threads = threads,
                activeId = activeId,
                onSelect = { hub.chatStore.selectThread(it); showHistory = false },
                onNew = { hub.chatStore.newThread(); showHistory = false },
                onDelete = { hub.chatStore.deleteThread(it) },
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = c.surface) {
            AiConfigSheet(onDismiss = { showSettings = false })
        }
    }
}

@Composable
private fun EditorialEmptyCanvas(
    modifier: Modifier,
    hasContext: Boolean,
    onSuggestion: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val s = LocalStrings.current
    val c = Xiao.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.chatGreeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = c.text)
        Text(s.chatSubtitle, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        if (hasContext) {
            OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text(s.contextSection, style = MaterialTheme.typography.labelMedium, color = c.text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(s.suggestion1, s.suggestion2, s.suggestion3).forEach { prompt ->
                Surface(
                    onClick = { onSuggestion(prompt) },
                    shape = RoundedCornerShape(10.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.line),
                ) {
                    Text(prompt, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = c.text)
                }
            }
        }
    }
}

@Composable
private fun ChatEntry(msg: ChatMessage) {
    val c = Xiao.colors
    val s = LocalStrings.current
    val nav = LocalNav.current
    val timeStr = remember(msg.time) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.time)) }
    if (msg.role == "user") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Surface(
                shape = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp),
                color = c.surface2,
                border = BorderStroke(1.dp, c.line),
                modifier = Modifier.widthIn(max = 310.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val imgFile = remember(msg.imagePath) { msg.imagePath?.let { File(it) }?.takeIf { it.exists() } }
                    if (imgFile != null) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = s.attachedPhoto,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp))
                                .clickable { nav.go(Route.AiPhoto(imgFile.absolutePath)) },
                        )
                    } else if (msg.hasImage) {
                        Text(s.attachedPhoto, style = MaterialTheme.typography.labelSmall, color = c.muted, fontWeight = FontWeight.Medium)
                    }
                    SelectionContainer {
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    }
                }
            }
            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(top = 4.dp, end = 4.dp))
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg.provider.ifBlank { "AI" }, style = MaterialTheme.typography.labelMedium, color = c.text, fontWeight = FontWeight.SemiBold)
                Text("· $timeStr", style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
            SelectionContainer {
                Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = c.text)
            }
        }
    }
}

@Composable
private fun HistorySheet(
    threads: List<ChatThread>,
    activeId: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val s = LocalStrings.current
    val c = Xiao.colors
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.chatHistory, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = onNew, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)) {
                Text(s.newChat)
            }
        }
        HorizontalDivider(color = c.line)
        val nonEmpty = threads.filter { it.messages.isNotEmpty() || it.id == activeId }
        if (nonEmpty.isEmpty()) {
            Text(s.noHistoryYet, color = c.muted, modifier = Modifier.padding(vertical = 20.dp))
        } else {
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(nonEmpty, key = { it.id }) { t ->
                    val selected = t.id == activeId
                    Surface(
                        onClick = { onSelect(t.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) c.surface2 else c.bg,
                        border = BorderStroke(1.dp, if (selected) c.text else c.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title.ifBlank { s.newChat }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                Text("${t.messages.size} ${s.messagesCount} · ${dateFmt.format(Date(t.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = c.muted)
                            }
                            TextButton(onClick = { onDelete(t.id) }) { Text(s.delete, color = c.rec) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AiConfigSheet(onDismiss: () -> Unit) {
    val controller = LocalController.current
    val hub = controller.accounts
    val state by hub.state.collectAsStateWithLifecycle()
    val glasses by controller.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = LocalStrings.current
    val c = Xiao.colors

    var url by remember(state.bridgeUrl) { mutableStateOf(state.bridgeUrl) }
    var token by remember { mutableStateOf("") }
    var deepseekKey by remember { mutableStateOf("") }
    var liveKey by remember(glasses.aiSettings.key) { mutableStateOf(glasses.aiSettings.key) }
    var liveLang by remember(glasses.aiSettings.language) { mutableStateOf(glasses.aiSettings.language) }
    var liveVoice by remember(glasses.aiSettings.voice) { mutableStateOf(glasses.aiSettings.voice) }
    var customCtx by remember(state.customContext) { mutableStateOf(state.customContext) }
    var voiceMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var geminiMenu by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    fun action(block: () -> Unit) { localError = ""; try { block() } catch (e: Exception) { localError = e.message ?: "Error" } }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.aiSettingsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onDismiss) { Text(s.close, color = c.text) }
        }

        // App language selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.appLanguage, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLang.entries.forEach { l ->
                    FilterChip(selected = state.appLang == l, onClick = { hub.appLang(l) }, label = { Text(l.label) })
                }
            }
        }
        HorizontalDivider(color = c.line)

        // Provider selector
        Text(s.providerSection, style = MaterialTheme.typography.titleSmall, color = c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.provider == "gemini_live", onClick = { hub.selectProvider("gemini_live") }, label = { Text("Gemini Live") })
            FilterChip(selected = state.provider == "chatgpt", onClick = { hub.selectProvider("chatgpt") }, label = { Text("ChatGPT") })
            FilterChip(selected = state.provider == "gemini", onClick = { hub.selectProvider("gemini") }, label = { Text("Gemini Bridge") })
            FilterChip(selected = state.provider == "deepseek", onClick = { hub.selectProvider("deepseek") }, label = { Text("DeepSeek") })
        }

        when (state.provider) {
            "gemini_live" -> {
                OutlinedTextField(liveKey, { liveKey = it }, label = { Text(s.geminiKeyLabel) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(liveLang, { liveLang = it }, label = { Text(s.liveLangLabel) },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { voiceMenu = true }, shape = RoundedCornerShape(10.dp)) { Text("${s.voiceLabel}: ${liveVoice.ifBlank { s.autoLabel }}") }
                        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                            DropdownMenuItem(text = { Text(s.autoLabel) }, onClick = { liveVoice = ""; voiceMenu = false })
                            AiSettings.VOICES.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { liveVoice = v; voiceMenu = false }) }
                        }
                    }
                    Button(onClick = {
                        action {
                            controller.setAiSettings(glasses.aiSettings.copy(key = liveKey, language = liveLang, voice = liveVoice))
                            hub.status(if (s.lang == AppLang.EN) "Saved Gemini Live settings." else "Đã lưu cài đặt Gemini Live.")
                        }
                    }, shape = RoundedCornerShape(10.dp)) { Text(s.saveBtn) }
                }
            }
            "chatgpt" -> {
                Text(state.chatgptAccount.ifBlank { s.notSignedIn }, style = MaterialTheme.typography.bodySmall, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { action { hub.login { uri -> context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } } }, enabled = !state.busy, shape = RoundedCornerShape(10.dp)) { Text(s.signInChatGpt) }
                    OutlinedButton(onClick = { hub.refreshChatGpt() }, enabled = !state.busy && state.chatgptAccount.isNotBlank(), shape = RoundedCornerShape(10.dp)) { Text(s.loadModels) }
                    TextButton(onClick = { hub.logout() }, enabled = !state.busy && state.chatgptAccount.isNotBlank()) { Text(s.signOut) }
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = state.models.isNotEmpty(), shape = RoundedCornerShape(10.dp)) { Text(state.model.ifBlank { s.selectModel }) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        state.models.forEach { m -> DropdownMenuItem(text = { Text(m.label) }, onClick = { hub.selectModel(m.slug); menu = false }) }
                    }
                }
            }
            "deepseek" -> {
                OutlinedTextField(deepseekKey, { deepseekKey = it }, label = { Text(s.deepseekKeyLabel) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeepSeekClient.MODELS.forEach { m ->
                        FilterChip(selected = state.deepseekModel == m, onClick = { hub.selectDeepSeekModel(m) }, label = { Text(m) })
                    }
                }
                Button(onClick = { action { hub.configureDeepSeek(deepseekKey, state.deepseekModel); deepseekKey = "" } }, enabled = !state.busy, shape = RoundedCornerShape(10.dp)) { Text(s.saveBtn) }
            }
            else -> {
                OutlinedTextField(url, { url = it }, label = { Text(s.bridgeUrlLabel) }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text(s.bridgeTokenLabel) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { action { hub.configureBridge(url, token); token = "" } }, enabled = !state.busy, shape = RoundedCornerShape(10.dp)) { Text(s.connectBridgeBtn) }
                    Box {
                        OutlinedButton(onClick = { geminiMenu = true }, enabled = state.bridgeModels.isNotEmpty(), shape = RoundedCornerShape(10.dp)) { Text(state.bridgeModel.ifBlank { s.selectModel }) }
                        DropdownMenu(expanded = geminiMenu, onDismissRequest = { geminiMenu = false }) {
                            state.bridgeModels.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { hub.selectBridgeModel(m); geminiMenu = false }) }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = c.line)
        Text(s.contextSection, style = MaterialTheme.typography.titleSmall, color = c.muted)
        OutlinedTextField(
            value = customCtx,
            onValueChange = { customCtx = it },
            placeholder = { Text(s.contextHint, style = MaterialTheme.typography.bodySmall) },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { hub.customContext(customCtx) }, shape = RoundedCornerShape(10.dp)) { Text(s.contextSave) }

        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.autoFallback, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = state.autoFallback, onCheckedChange = hub::autoFallback)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.deviceActions, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = state.deviceActions, onCheckedChange = hub::deviceActions)
        }

        HorizontalDivider(color = c.line)
        Text(s.voiceSection, style = MaterialTheme.typography.titleSmall, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeechLanguage.entries.forEach { language ->
                FilterChip(selected = state.language == language, onClick = { hub.language(language) }, label = { Text(language.label) })
            }
        }
        Text("${s.speechRateLabel}: ${String.format(Locale.US, "%.2fx", state.speechRate)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = state.speechRate, onValueChange = hub::speechRate, valueRange = 0.7f..1.8f, steps = 10, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { hub.installSpeech() }, enabled = !state.busy && !state.speechReady, shape = RoundedCornerShape(10.dp)) {
                Text(if (state.speechReady) s.voskReady else s.voskDownload)
            }
            OutlinedButton(onClick = { action { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }, shape = RoundedCornerShape(10.dp)) { Text(s.ttsSettings) }
        }

        Text(s.cameraSection, style = MaterialTheme.typography.titleSmall, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.cameraMode == "auto", onClick = { hub.cameraMode("auto") }, label = { Text(s.camAuto) })
            FilterChip(selected = state.cameraMode == "always", onClick = { hub.cameraMode("always") }, label = { Text(s.camAlways) })
            FilterChip(selected = state.cameraMode == "off", onClick = { hub.cameraMode("off") }, label = { Text(s.camOff) })
        }
        Text(s.camAutoDesc, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.hdCameraLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = state.hdCamera, onCheckedChange = hub::hdCamera)
        }

        if (localError.isNotBlank() || state.status.isNotBlank()) {
            Text(localError.ifBlank { state.status }, style = MaterialTheme.typography.bodySmall, color = c.text)
        }
        TextButton(onClick = { hub.clearConversation(); onDismiss() }) { Text(s.clearChat, color = c.rec) }
        Spacer(Modifier.height(12.dp))
    }
}
