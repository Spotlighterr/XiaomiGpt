package io.github.spotlighterr.xiaomigpt

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var cameraProbe: CameraProbe
    private lateinit var audioProbe: AudioProbe
    private lateinit var wifiProbe: WifiProbe
    private lateinit var preview: TextureView
    private lateinit var cameraPicker: Spinner
    private lateinit var audioPicker: Spinner
    private val main = Handler(Looper.getMainLooper())
    private val networkWorker = Executors.newSingleThreadExecutor()
    private var cameras = emptyList<ProbeCamera>()
    private var audioDevices = emptyList<AudioDeviceInfo>()
    private var activeCamera: String? = null
    private var photoFile: File? = null
    private var photoSource: String? = null
    private var photoTime: String? = null
    private var cameraOutcome = "Chưa thử camera."
    private var audioOutcome = "Chưa thử micro."
    private var audioCheck = AudioCheckState()
    private var audioUiStatus = "Chưa kiểm tra micro. Bluetooth kết nối được chưa có nghĩa micro đã hoạt động."
    private var audioCheckTime: String? = null
    private var pendingAudio = false
    private var wifiOutcome = "Chưa thử HTTP."
    private var audioBusy = false
    private var wifiBusy = false
    @Volatile private var foreground = false
    @Volatile private var wifiGeneration = 0
    private val keyEvents = ArrayDeque<String>()
    private val cameraManager by lazy { getSystemService(CameraManager::class.java) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val cameraAvailability = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) { if (foreground && activeCamera == null) refreshCameras() }
    }
    private val audioAvailability = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { if (foreground && !audioBusy) refreshAudio() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { if (foreground && !audioBusy) refreshAudio() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        preview = findViewById(R.id.preview)
        cameraPicker = findViewById(R.id.cameraPicker)
        audioPicker = findViewById(R.id.audioPicker)
        cameraProbe = CameraProbe(this, { message, ready -> onUi {
            cameraOutcome = message
            text(R.id.cameraStatus, message)
            button(R.id.capture).isEnabled = ready && foreground
        } }, { file, source -> onUi {
            if (source == activeCamera && foreground) showPhoto(file, source)
        } })
        audioProbe = AudioProbe(this)
        wifiProbe = WifiProbe(this)

        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { closeCamera(); return true }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
        button(R.id.refresh).setOnClickListener { requestProbePermissions() }
        button(R.id.openCamera).setOnClickListener { openCamera() }
        button(R.id.closeCamera).setOnClickListener { closeCamera() }
        button(R.id.capture).setOnClickListener { cameraProbe.capture(displayRotation()) }
        button(R.id.audioTest).setOnClickListener { testAudio() }
        button(R.id.audioStop).setOnClickListener { audioProbe.cancel() }
        button(R.id.wifiTest).setOnClickListener { testWifi() }
        button(R.id.bluetoothSettings).setOnClickListener { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        button(R.id.shareReport).setOnClickListener { shareReport() }
        button(R.id.sharePhoto).setOnClickListener { sharePhoto() }
        button(R.id.advancedToggle).setOnClickListener {
            val panel = findViewById<View>(R.id.advancedPanel)
            val show = panel.visibility != View.VISIBLE
            if (!show) closeCamera()
            panel.visibility = if (show) View.VISIBLE else View.GONE
            button(R.id.advancedToggle).setText(if (show) R.string.advanced_hide else R.string.advanced_show)
        }
        findViewById<CheckBox>(R.id.heardPlayback).setOnCheckedChangeListener { _, heard ->
            audioCheck = audioCheck.confirmHeard(heard)
        }
        findViewById<CheckBox>(R.id.confirmGlasses).setOnCheckedChangeListener { _, checked ->
            if (checked && !ProbePolicy.canConfirmPhoto(photoSource, activeCamera)) {
                findViewById<CheckBox>(R.id.confirmGlasses).isChecked = false
            }
            updateCameraSummary()
        }
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("audioVerified")) {
                audioCheck = AudioCheckState(AudioCheckState.Phase.COMPLETED,
                    savedInstanceState.getInt("audioPeak"), savedInstanceState.getBoolean("heardPlayback"))
                audioOutcome = savedInstanceState.getString("audioOutcome") ?: audioOutcome
                audioUiStatus = savedInstanceState.getString("audioUiStatus") ?: audioUiStatus
                audioCheckTime = savedInstanceState.getString("audioCheckTime")
            }
            photoSource = savedInstanceState.getString("photoSource")
            photoTime = savedInstanceState.getString("photoTime")
            savedInstanceState.getString("photoName")?.let { name ->
                // Accept only this app's generated basename, never an arbitrary saved path.
                if (name.matches(Regex("capture-[0-9]+\\.jpg"))) {
                    val file = File(filesDir, "captures/$name")
                    if (file.isFile) restorePhoto(file)
                }
            }
        }
        renderAudioCheck()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        renderAudioCheck()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        refresh()
        cameraManager.registerAvailabilityCallback(cameraAvailability, main)
        audioManager.registerAudioDeviceCallback(audioAvailability, main)
        if (pendingAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingAudio = false
            testAudio()
        }
    }

    override fun onPause() {
        foreground = false
        cameraManager.unregisterAvailabilityCallback(cameraAvailability)
        audioManager.unregisterAudioDeviceCallback(audioAvailability)
        closeCamera()
        audioProbe.cancel()
        wifiGeneration++
        wifiProbe.cancel()
        wifiBusy = false
        button(R.id.wifiTest).isEnabled = true
        super.onPause()
    }

    override fun onDestroy() {
        cameraProbe.shutdown()
        audioProbe.shutdown()
        wifiProbe.cancel()
        networkWorker.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("photoName", photoFile?.name)
        outState.putString("photoSource", photoSource)
        outState.putString("photoTime", photoTime)
        outState.putBoolean("audioVerified", audioCheck.routeVerified)
        outState.putInt("audioPeak", audioCheck.peak)
        outState.putBoolean("heardPlayback", audioCheck.heardPlayback)
        outState.putString("audioOutcome", audioOutcome)
        outState.putString("audioUiStatus", audioUiStatus)
        outState.putString("audioCheckTime", audioCheckTime)
        super.onSaveInstanceState(outState)
    }

    private fun requestProbePermissions() {
        val missing = listOf(Manifest.permission.BLUETOOTH_CONNECT)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1) else refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        if (requestCode == 3 && pendingAudio) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (foreground) { pendingAudio = false; testAudio() }
            } else pendingAudio = false
        }
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Một số quyền chưa được cấp. Bạn có thể bật trong Cài đặt ứng dụng.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refresh() {
        text(R.id.deviceStatus, "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
            "App Xiaomi: ${versionOf("com.xiaomi.superhexa")}\nChatGPT: ${versionOf("com.openai.chatgpt")}")
        if (activeCamera == null) refreshCameras()
        if (!audioBusy) refreshAudio()
        updateCameraSummary()
    }

    private fun refreshCameras() {
        try {
            val selected = cameras.getOrNull(cameraPicker.selectedItemPosition)?.id
            cameras = cameraProbe.cameras()
            cameraPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cameras)
            val index = cameras.indexOfFirst { it.id == selected }
            if (index >= 0) cameraPicker.setSelection(index)
            if (activeCamera == null) text(R.id.cameraStatus,
                if (cameras.isEmpty()) "Android chưa cung cấp camera nào cho app." else "${cameras.size} camera khả dụng. Chưa xác nhận camera kính.")
        } catch (error: Exception) { text(R.id.cameraStatus, "Không đọc được danh sách camera: ${error.javaClass.simpleName}") }
    }

    private fun refreshAudio() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            text(R.id.connectionStatus, "Cần quyền Bluetooth để tìm kết nối âm thanh.")
            text(R.id.audioStatus, "Bấm Làm mới kết nối và cho phép truy cập thiết bị ở gần.")
            button(R.id.audioTest).isEnabled = false
            return
        }
        try {
            val selected = audioDevices.getOrNull(audioPicker.selectedItemPosition)?.id
            audioDevices = audioProbe.devices()
            val names = audioDevices.map { it.productName.toString() }
            audioPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            val index = audioDevices.indexOfFirst { it.id == selected }
            if (index >= 0) audioPicker.setSelection(index)
            audioPicker.visibility = if (audioDevices.size > 1) View.VISIBLE else View.GONE
            val paired = getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.size ?: 0
            text(R.id.connectionStatus, when (audioDevices.size) {
                0 -> "Chưa thấy kết nối âm thanh của kính."
                1 -> "Âm thanh Bluetooth sẵn sàng\n${audioDevices[0].productName}"
                else -> "Có ${audioDevices.size} thiết bị âm thanh. Chọn kính ở phần micro bên dưới."
            })
            text(R.id.audioStatus, audioUiStatus)
            text(R.id.audioTechnical, "$paired paired devices; ${audioDevices.size} Bluetooth communication routes.\n$audioOutcome")
            button(R.id.audioTest).isEnabled = audioDevices.isNotEmpty()
        } catch (error: Exception) {
            text(R.id.connectionStatus, "Chưa đọc được trạng thái Bluetooth. Thử Làm mới kết nối.")
            text(R.id.audioTechnical, "Bluetooth: ${error.javaClass.simpleName}")
            button(R.id.audioTest).isEnabled = false
        }
    }

    private fun openCamera() {
        if (!foreground) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 2); return
        }
        val selected = cameras.getOrNull(cameraPicker.selectedItemPosition) ?: return
        val texture = preview.surfaceTexture ?: run { text(R.id.cameraStatus, "Khung xem trước chưa sẵn sàng."); return }
        activeCamera = selected.id
        findViewById<CheckBox>(R.id.confirmGlasses).apply { isChecked = false; isEnabled = false }
        cameraPicker.isEnabled = false
        button(R.id.openCamera).isEnabled = false
        cameraProbe.open(selected.id, texture)
    }

    private fun closeCamera() {
        activeCamera = null
        if (::cameraProbe.isInitialized) cameraProbe.close()
        button(R.id.capture).isEnabled = false
        cameraPicker.isEnabled = true
        button(R.id.openCamera).isEnabled = true
        findViewById<CheckBox>(R.id.confirmGlasses).apply { isChecked = false; isEnabled = false }
    }

    private fun showPhoto(file: File, source: String) {
        photoFile = file
        photoSource = source
        photoTime = Instant.now().toString()
        restorePhoto(file)
        findViewById<CheckBox>(R.id.confirmGlasses).apply { isChecked = false; isEnabled = true }
    }

    private fun restorePhoto(file: File) {
        photoFile = file
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return
        val exif = ExifInterface(file)
        val matrix = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        findViewById<ImageView>(R.id.photo).apply { visibility = View.VISIBLE; setImageBitmap(rotated) }
        button(R.id.sharePhoto).isEnabled = true
    }

    private fun testAudio() {
        if (audioBusy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAudio = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3); return
        }
        val device = audioDevices.getOrNull(audioPicker.selectedItemPosition) ?: return
        audioBusy = true
        audioCheckTime = Instant.now().toString()
        audioPicker.isEnabled = false
        button(R.id.audioStop).visibility = View.VISIBLE
        button(R.id.audioTest).isEnabled = false
        audioProbe.test(device, { event -> onUi { handleAudioEvent(event) } }, { onUi {
            audioBusy = false
            audioPicker.isEnabled = true
            button(R.id.audioStop).visibility = View.GONE
            if (foreground) refreshAudio()
        } })
    }

    private fun handleAudioEvent(event: AudioProbeEvent) {
        audioCheck = audioCheck.on(event)
        if (event is AudioProbeEvent.Completed && !audioCheck.routeVerified) return
        when (event) {
            AudioProbeEvent.Connecting -> { audioUiStatus = "Đang chuẩn bị micro của thiết bị…"; audioOutcome = "Connecting" }
            is AudioProbeEvent.Recording -> { audioUiStatus = "Nói ngay: Hello XiaomiGpt. Đang thu trong 3 giây…"; audioOutcome = "Input Bluetooth type=${event.inputType}" }
            is AudioProbeEvent.Level -> Unit
            is AudioProbeEvent.Playback -> { audioUiStatus = "Đang phát lại. Bạn có nghe câu vừa nói qua kính không?"; audioOutcome += "; peak=${event.peak}" }
            is AudioProbeEvent.Completed -> {
                audioUiStatus = "Điện thoại đã dùng micro và đầu ra Bluetooth. Nếu nghe rõ câu vừa nói từ kính, đánh dấu xác nhận bên dưới."
                audioOutcome = "Verified Bluetooth input type=${event.inputType}; output type=${event.outputType}; peak=${event.peak}/32768"
            }
            is AudioProbeEvent.Failed -> { audioUiStatus = "Chưa hoàn tất phép thử. Kiểm tra kết nối và âm thanh cuộc gọi của kính, rồi thử lại."; audioOutcome = event.reason }
            AudioProbeEvent.Cancelled -> { audioUiStatus = "Đã dừng. Bạn có thể thử lại khi sẵn sàng."; audioOutcome = "Cancelled" }
        }
        renderAudioCheck()
    }

    private fun renderAudioCheck() {
        text(R.id.audioStatus, audioUiStatus)
        text(R.id.audioTechnical, audioOutcome)
        findViewById<ProgressBar>(R.id.audioLevel).progress = (audioCheck.peak * 100 / 32768).coerceIn(0, 100)
        findViewById<CheckBox>(R.id.heardPlayback).apply {
            isEnabled = audioCheck.routeVerified
            isChecked = audioCheck.heardPlayback
        }
    }

    private fun updateCameraSummary() {
        val confirmed = findViewById<CheckBox>(R.id.confirmGlasses).isChecked &&
            ProbePolicy.canConfirmPhoto(photoSource, activeCamera)
        text(R.id.cameraSummary, if (confirmed) "Bạn đã xác nhận ảnh từ kính trong lần thử này." else "Chưa truy cập được camera kính.")
    }

    private fun testWifi() {
        if (wifiBusy) return
        val host = findViewById<EditText>(R.id.host).text.toString().trim()
        if (!ProbePolicy.isPrivateHost(host)) {
            text(R.id.wifiStatus, "Nhập IP nội bộ của kính, ví dụ 192.168.43.1; không nhập URL."); return
        }
        val ticket = ++wifiGeneration
        wifiBusy = true
        button(R.id.wifiTest).isEnabled = false
        text(R.id.wifiStatus, "Đang đọc endpoint danh sách file…")
        networkWorker.execute {
            if (!foreground || ticket != wifiGeneration) return@execute
            val result = try { wifiProbe.run(host) } catch (error: Exception) {
                // Error text from HTTP libraries can contain URLs. Export only the class.
                when (error) {
                    is IllegalArgumentException, is IllegalStateException -> error.message.orEmpty()
                    else -> "Chưa kết nối được (${error.javaClass.simpleName}); kiểm tra Wi-Fi và IP kính."
                }
            }
            onUi {
                if (ticket == wifiGeneration) {
                    wifiOutcome = result; wifiBusy = false
                    button(R.id.wifiTest).isEnabled = true
                    text(R.id.wifiStatus, result)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val diagnosticKeys = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS)
        if (event.keyCode in diagnosticKeys && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (keyEvents.size == 12) keyEvents.removeFirst()
            keyEvents.addLast("${Instant.now()} · ${KeyEvent.keyCodeToString(event.keyCode)}")
            text(R.id.keyStatus, keyEvents.joinToString("\n"))
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sharePhoto() {
        val file = photoFile?.takeIf { it.isFile } ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Please describe this photo in English.")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setPackage("com.openai.chatgpt")
        if (intent.resolveActivity(packageManager) != null) launch(intent)
        else Toast.makeText(this, "Chưa có ChatGPT chính thức hoặc bản đã cài không nhận chia sẻ ảnh.", Toast.LENGTH_LONG).show()
    }

    private fun shareReport() {
        val confirmed = findViewById<CheckBox>(R.id.confirmGlasses).isChecked &&
            ProbePolicy.canConfirmPhoto(photoSource, activeCamera)
        val report = buildString {
            appendLine("XiaomiGpt ${BuildConfig.VERSION_NAME} · ${Instant.now()}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}")
            appendLine("Target (user supplied, not detected): M2442G1; firmware 1.8.12.0")
            appendLine("Xiaomi app: ${versionOf("com.xiaomi.superhexa")}; ChatGPT: ${versionOf("com.openai.chatgpt")}")
            appendLine("Cameras exposed to this app: ${cameras.joinToString()}")
            appendLine("Camera: $cameraOutcome")
            appendLine("Photo: ${if (photoFile != null) "captured; source Camera2 id=$photoSource; time=$photoTime" else "not captured"}")
            appendLine("User visually confirmed glasses photo: $confirmed (not automatic hardware identification)")
            appendLine("Audio: $audioOutcome")
            appendLine("Audio check time: $audioCheckTime; route verified: ${audioCheck.routeVerified}; user heard playback through glasses: ${audioCheck.heardPlayback}")
            appendLine("HTTP: $wifiOutcome")
            appendLine("Keys received while foreground (origin not identified):")
            keyEvents.forEach { appendLine(it) }
            appendLine("No photos, audio recordings, device MACs, network passwords, account tokens or file-list bodies attached.")
            appendLine("AI integration and background/glasses activation have not been tested by this build.")
        }
        launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, report), "Xuất báo cáo XiaomiGpt"))
    }

    @Suppress("DEPRECATION")
    private fun versionOf(name: String): String = try { packageManager.getPackageInfo(name, 0).versionName ?: "không rõ" }
        catch (_: PackageManager.NameNotFoundException) { "chưa cài hoặc không nhìn thấy" }

    private fun launch(intent: Intent) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "Không có ứng dụng xử lý thao tác này.", Toast.LENGTH_LONG).show() }
    }

    private fun displayRotation(): Int = when (display?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
    private fun button(id: Int): Button = findViewById(id)
    private fun text(id: Int, message: String) { findViewById<TextView>(id).text = message }
    private fun onUi(action: () -> Unit) { runOnUiThread { if (!isDestroyed) action() } }
}
