package io.github.spotlighterr.xiaomigpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AudioProbe(context: Context) {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val worker = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    fun devices(): List<AudioDeviceInfo> = audio.availableCommunicationDevices.filter { isHeadset(it.type) }

    fun test(device: AudioDeviceInfo, update: (AudioProbeEvent) -> Unit, finished: () -> Unit) {
        if (!running.compareAndSet(false, true)) return
        cancelled.set(false)
        update(AudioProbeEvent.Connecting)
        worker.execute {
            val previousMode = audio.mode
            var routeOwned = false
            var focusOwned = false
            var recorder: AudioRecord? = null
            var player: AudioTrack? = null
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                    if (change < 0) cancel()
                }.build()
            try {
                check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Chưa cấp quyền micro." }
                check(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { "Chưa cấp quyền Bluetooth." }
                check(previousMode == AudioManager.MODE_NORMAL) { "Đang có phiên âm thanh khác. Kết thúc cuộc gọi/Voice rồi thử lại." }
                focusOwned = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                check(focusOwned) { "Không lấy được quyền sử dụng âm thanh." }
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                routeOwned = true
                check(audio.setCommunicationDevice(device)) { "Android không chọn được thiết bị này." }
                val routeDeadline = SystemClock.elapsedRealtime() + 5000
                while (audio.communicationDevice?.id != device.id) {
                    checkActive()
                    check(SystemClock.elapsedRealtime() < routeDeadline) { "Quá thời gian chờ đường Bluetooth." }
                    SystemClock.sleep(30)
                }
                val rate = 16000
                val format = AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "Điện thoại không hỗ trợ bản ghi PCM 16 kHz." }
                val activeRecorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum * 2, 4096)).build()
                recorder = activeRecorder
                check(activeRecorder.state == AudioRecord.STATE_INITIALIZED) { "Không khởi tạo được micro." }
                activeRecorder.startRecording()
                val chunk = ByteArray(1024)
                val routeReady = SystemClock.elapsedRealtime() + 3000
                while (activeRecorder.routedDevice?.let { isHeadset(it.type) } != true) {
                    checkActive()
                    check(SystemClock.elapsedRealtime() < routeReady) { "Micro chưa chuyển sang Bluetooth; bản ghi đã bị hủy." }
                    val count = activeRecorder.read(chunk, 0, chunk.size, AudioRecord.READ_NON_BLOCKING)
                    check(count >= 0) { "Lỗi đọc micro: $count" }
                    SystemClock.sleep(20)
                }
                val input = checkNotNull(activeRecorder.routedDevice)
                update(AudioProbeEvent.Recording(input.type))
                val pcm = ByteArrayOutputStream()
                val deadline = SystemClock.elapsedRealtime() + 3000
                var peak = 0
                var nextLevelUpdate = 0L
                while (SystemClock.elapsedRealtime() < deadline) {
                    checkActive()
                    check(audio.communicationDevice?.id == device.id && activeRecorder.routedDevice?.id == input.id) { "Đường micro thay đổi; hủy bản ghi." }
                    val count = activeRecorder.read(chunk, 0, chunk.size, AudioRecord.READ_NON_BLOCKING)
                    check(count >= 0) { "Lỗi đọc micro: $count" }
                    if (count > 0) {
                        pcm.write(chunk, 0, count)
                        var chunkPeak = 0
                        for (offset in 0 until count - 1 step 2) {
                            val sample = ((chunk[offset].toInt() and 255) or (chunk[offset + 1].toInt() shl 8)).toShort().toInt()
                            peak = maxOf(peak, kotlin.math.abs(sample))
                            chunkPeak = maxOf(chunkPeak, kotlin.math.abs(sample))
                        }
                        if (SystemClock.elapsedRealtime() >= nextLevelUpdate) {
                            update(AudioProbeEvent.Level(chunkPeak))
                            nextLevelUpdate = SystemClock.elapsedRealtime() + 150
                        }
                    } else SystemClock.sleep(10)
                }
                activeRecorder.stop()
                val bytes = pcm.toByteArray()
                check(bytes.size >= rate) { "Bản ghi quá ngắn hoặc không có dữ liệu." }
                val activePlayer = AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(bytes.size).build()
                player = activePlayer
                check(activePlayer.state == AudioTrack.STATE_INITIALIZED) { "Không khởi tạo được phát lại." }
                check(activePlayer.setPreferredDevice(device)) { "Không chọn được loa Bluetooth." }
                check(activePlayer.write(bytes, 0, bytes.size) == bytes.size) { "Không nạp đủ bản ghi để phát lại." }
                update(AudioProbeEvent.Playback(peak))
                activePlayer.play()
                val playbackDeadline = SystemClock.elapsedRealtime() + 6000
                while (activePlayer.playbackHeadPosition < bytes.size / 2) {
                    checkActive()
                    check(audio.communicationDevice?.id == device.id) { "Thiết bị Bluetooth đã ngắt." }
                    check(SystemClock.elapsedRealtime() < playbackDeadline) { "Quá thời gian phát lại." }
                    SystemClock.sleep(25)
                }
                val output = activePlayer.routedDevice
                check(output != null && isHeadset(output.type)) { "Không xác nhận được đường phát Bluetooth." }
                update(AudioProbeEvent.Completed(input.type, output.type, peak))
            } catch (error: Exception) {
                update(if (cancelled.get()) AudioProbeEvent.Cancelled else AudioProbeEvent.Failed(error.message.orEmpty()))
            } finally {
                runCatching { recorder?.stop() }; recorder?.release()
                runCatching { player?.stop() }; player?.release()
                if (routeOwned) {
                    runCatching { audio.clearCommunicationDevice() }
                    if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = previousMode
                }
                if (focusOwned) audio.abandonAudioFocusRequest(focus)
                running.set(false)
                finished()
            }
        }
    }

    private fun checkActive() { check(!cancelled.get()) { "Đã hủy." } }
    fun cancel() { cancelled.set(true) }
    fun shutdown() { cancel(); worker.shutdown() }

    companion object {
        fun isHeadset(type: Int): Boolean = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }
}
