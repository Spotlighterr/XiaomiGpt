package io.github.spotlighterr.xiaomigpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class ProbeCamera(val id: String, val label: String) {
    override fun toString() = "$id · $label"
}

/** Every camera operation runs on one handler. Generations invalidate late callbacks. */
class CameraProbe(
    private val context: Context,
    private val status: (String, Boolean) -> Unit,
    private val photo: (File, String) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("camera-probe").apply { start() }
    private val handler = Handler(thread.looper)
    private val generation = AtomicInteger()
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var preview: Surface? = null
    private var source: String? = null
    private var capturePending = false

    fun cameras(): List<ProbeCamera> = manager.cameraIdList.map { id ->
        val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        val label = when (facing) {
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External — cần đối chiếu ảnh"
            CameraCharacteristics.LENS_FACING_FRONT -> "Trước — thường là điện thoại"
            CameraCharacteristics.LENS_FACING_BACK -> "Sau — thường là điện thoại"
            else -> "Chưa rõ nguồn"
        }
        ProbeCamera(id, label)
    }

    fun open(id: String, texture: SurfaceTexture) {
        val ticket = generation.incrementAndGet()
        handler.post {
            release()
            try {
                check(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { "Chưa cấp quyền camera." }
                val characteristics = manager.getCameraCharacteristics(id)
                val map = checkNotNull(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
                val jpegSize = jpegSizes.filter { it.width.toLong() * it.height <= 2_000_000 }
                    .maxByOrNull { it.width.toLong() * it.height }
                    ?: jpegSizes.minByOrNull { it.width.toLong() * it.height }
                    ?: error("Camera không cung cấp ảnh JPEG.")
                val previewSizes = map.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
                val previewSize = previewSizes.filter { it.width <= 1280 && it.height <= 960 }
                    .maxByOrNull { it.width.toLong() * it.height }
                    ?: previewSizes.minByOrNull { it.width.toLong() * it.height }
                    ?: Size(640, 480)
                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                preview = Surface(texture)
                source = id
                reader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
                    setOnImageAvailableListener({ images ->
                        try {
                            images.acquireLatestImage()?.use { image ->
                                if (ticket != generation.get() || !capturePending) return@use
                                val bytes = ByteArray(image.planes[0].buffer.remaining())
                                image.planes[0].buffer.get(bytes)
                                val directory = File(context.filesDir, "captures").apply { mkdirs() }
                                val file = File.createTempFile("capture-", ".jpg", directory)
                                file.writeBytes(bytes)
                                capturePending = false
                                photo(file, id)
                                status("Đã chụp từ Camera2 id=$id. Hãy đối chiếu ảnh để xác nhận có phải camera kính.", true)
                            }
                        } catch (error: Exception) {
                            capturePending = false
                            status("Không lưu được ảnh: ${error.javaClass.simpleName}", session != null)
                        }
                    }, handler)
                }
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (ticket != generation.get()) { device.close(); return }
                        camera = device
                        configure(device, ticket)
                    }
                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        if (ticket == generation.get()) { release(); status("Camera đã ngắt kết nối.", false) }
                    }
                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        if (ticket == generation.get()) { release(); status("Không mở được camera (mã $error).", false) }
                    }
                }, handler)
                status("Đang mở Camera2 id=$id…", false)
            } catch (error: Exception) {
                release()
                if (ticket == generation.get()) status("Không mở được camera: ${error.message}", false)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configure(device: CameraDevice, ticket: Int) {
        try {
            val previewSurface = checkNotNull(preview)
            val imageSurface = checkNotNull(reader).surface
            device.createCaptureSession(listOf(previewSurface, imageSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (ticket != generation.get()) { configured.close(); return }
                    try {
                        session = configured
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }.build()
                        configured.setRepeatingRequest(request, null, handler)
                        status("Đang xem Camera2 id=${device.id}. Nguồn kính chưa được xác nhận.", true)
                    } catch (error: Exception) {
                        release(); status("Không chạy được xem trước: ${error.javaClass.simpleName}", false)
                    }
                }
                override fun onConfigureFailed(failed: CameraCaptureSession) {
                    failed.close()
                    if (ticket == generation.get()) { release(); status("Camera không hỗ trợ cấu hình ảnh/xem trước này.", false) }
                }
            }, handler)
        } catch (error: Exception) {
            release(); status("Không tạo được phiên camera: ${error.javaClass.simpleName}", false)
        }
    }

    fun capture(rotationDegrees: Int) {
        val ticket = generation.get()
        handler.post {
            if (ticket != generation.get() || capturePending) return@post
            try {
                val activeCamera = checkNotNull(camera) { "Camera chưa mở." }
                val activeSession = checkNotNull(session) { "Camera chưa sẵn sàng." }
                val characteristics = manager.getCameraCharacteristics(activeCamera.id)
                val sensorRotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                val front = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                val orientation = (sensorRotation + (if (front) rotationDegrees else -rotationDegrees) + 360) % 360
                val request = activeCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(checkNotNull(reader).surface)
                    set(CaptureRequest.JPEG_ORIENTATION, orientation)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                }.build()
                capturePending = true
                status("Đang chụp ảnh…", false)
                activeSession.capture(request, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, failure: CaptureFailure) {
                        if (ticket == generation.get()) { capturePending = false; status("Chụp ảnh thất bại.", true) }
                    }
                    override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) = Unit
                }, handler)
                handler.postDelayed({
                    if (ticket == generation.get() && capturePending) {
                        // Restart to avoid a late JPEG being assigned to the next shutter press.
                        generation.incrementAndGet()
                        release()
                        status("Không nhận được ảnh sau 8 giây. Hãy mở lại camera.", false)
                    }
                }, 8000)
            } catch (error: Exception) {
                capturePending = false
                status("Không chụp được: ${error.message}", session != null)
            }
        }
    }

    fun close() {
        generation.incrementAndGet()
        handler.post { release() }
    }

    fun shutdown() {
        generation.incrementAndGet()
        handler.post { release(); thread.quitSafely() }
    }

    private fun release() {
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        preview?.release(); preview = null
        source = null
        capturePending = false
    }
}
