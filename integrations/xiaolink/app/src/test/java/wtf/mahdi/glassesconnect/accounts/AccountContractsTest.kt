package wtf.mahdi.glassesconnect.accounts

import io.github.spotlighterr.xiaomigpt.SpeechLanguage
import io.github.spotlighterr.xiaomigpt.VisualInput
import java.net.InetAddress
import java.nio.ByteBuffer
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AccountContractsTest {
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 0xff.toByte(), 0xd9.toByte())
    private fun packet(type: Int, total: Int, payload: ByteArray) = ByteArray(12).also {
        it[1] = 2; it[5] = type.toByte(); ByteBuffer.wrap(it, 6, 4).putInt(total)
        it[10] = (payload.size shr 8).toByte(); it[11] = payload.size.toByte()
    } + payload
    @Test fun cancellationInvalidatesAllOldTickets() {
        val g = TurnGate(); val first = g.begin(); assertTrue(g.active(first)); g.cancel(); assertFalse(g.active(first))
        val second = g.begin(); assertTrue(g.active(second)); assertFalse(g.active(first))
    }
    @Test fun nonActivationEventsNeverOpenMicrophone() {
        assertTrue(TurnGate.acceptsWake(1)); assertTrue(TurnGate.acceptsWake(2)); assertTrue(TurnGate.acceptsWake(5))
        for (cmd in listOf(-1, 0, 3, 4, 6, 999)) assertFalse(TurnGate.acceptsWake(cmd))
    }
    @Test fun freshJpegActuallyEntersModelPayload() {
        val input = VisualInput.content("Đọc biển báo", jpeg) as JSONArray
        assertEquals("input_text", input.getJSONObject(0).getString("type"))
        val image = input.getJSONObject(1)
        assertEquals("input_image", image.getString("type"))
        val restored = java.util.Base64.getDecoder().decode(image.getString("image_url").substringAfter(','))
        assertArrayEquals(jpeg, restored)
        assertFalse(VisualInput.instructions(SpeechLanguage.VIETNAMESE, true).contains("no image is attached"))
    }
    @Test fun noImageRemainsAnHonestTextRequest() {
        assertEquals("Hello", VisualInput.content("Hello", null))
        assertTrue(VisualInput.instructions(SpeechLanguage.ENGLISH, false).contains("no image is attached"))
    }
    @Test fun incompleteAndOversizedPhotosRejected() {
        assertTrue(runCatching { VisualInput.content("Hi", jpeg.copyOf(4)) }.isFailure)
        assertTrue(runCatching { VisualInput.content("Hi", ByteArray(VisualInput.MAX_JPEG + 1)) }.isFailure)
    }
    @Test fun cameraReassemblesOnlyCompleteNewImage() {
        val c = CameraFrames()
        assertNull(c.feed(packet(3, jpeg.size, jpeg.copyOfRange(0, 3))))
        assertArrayEquals(jpeg, c.feed(packet(4, jpeg.size, jpeg.copyOfRange(3, jpeg.size))))
        assertNull(c.feed(packet(4, jpeg.size, jpeg)))
        // Real firmware continuation packets (type 4) may report total=0 in header bytes 6..9.
        assertNull(c.feed(packet(3, jpeg.size, jpeg.copyOfRange(0, 3))))
        assertArrayEquals(jpeg, c.feed(packet(4, 0, jpeg.copyOfRange(3, jpeg.size))))
    }
    @Test fun cancelledCameraCannotCompleteFromOrphanContinuation() {
        val c = CameraFrames(); c.feed(packet(3, jpeg.size, jpeg.copyOfRange(0, 3))); c.clear()
        assertNull(c.feed(packet(4, jpeg.size, jpeg.copyOfRange(3, jpeg.size))))
    }
    @Test fun cameraRejectsOtherTypesAndLengthMismatch() {
        val c = CameraFrames()
        assertNull(c.feed(packet(9, jpeg.size, jpeg)))
        assertNull(c.feed(packet(3, VisualInput.MAX_JPEG + 1, jpeg)))
        val bad = packet(3, jpeg.size, jpeg); bad[11] = 1
        assertNull(c.feed(bad))
    }
    @Test fun newFirstFrameDropsPreviousPartialImage() {
        val c = CameraFrames(); c.feed(packet(3, jpeg.size, jpeg.copyOfRange(0, 3)))
        assertArrayEquals(jpeg, c.feed(packet(3, jpeg.size, jpeg)))
    }
    @Test fun bridgeRejectsCredentialsQueriesFragmentsAndPaths() {
        for (url in listOf("https://u:p@example.org", "https://example.org/?token=x", "https://example.org/#x",
            "file:///tmp/key", "https://example.org/ask", "http://localhost:0")) {
            assertTrue(url, runCatching { BridgePolicy.baseUrl(url) }.isFailure)
        }
    }
    @Test fun bridgePlainHttpCannotResolvePublicAddress() {
        assertTrue(BridgePolicy.privateAddress(InetAddress.getByName("192.168.1.10")))
        assertTrue(BridgePolicy.privateAddress(InetAddress.getByName("100.64.0.10")))
        assertFalse(BridgePolicy.privateAddress(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun vadEndsAfterSilenceAndCapsDuration() {
        val voice = ByteArray(32000); for (i in voice.indices step 2) { voice[i] = 0; voice[i + 1] = 8 }
        val gate = PcmUtteranceGate(); assertFalse(gate.feed(voice)); assertFalse(gate.feed(ByteArray(32000))); assertTrue(gate.feed(ByteArray(32000)))
        val noSpeech = PcmUtteranceGate(); repeat(7) { assertFalse(noSpeech.feed(ByteArray(32000))) }
        assertTrue(noSpeech.feed(ByteArray(32000)))
    }
    @Test fun smartCameraIntentTriggersOnlyForVisualQuestions() {
        assertTrue(SmartCameraIntent.shouldCapture("Trước mặt tôi là cái gì?"))
        assertTrue(SmartCameraIntent.shouldCapture("Đọc giúp chữ trên màn hình này"))
        assertTrue(SmartCameraIntent.shouldCapture("What is this in front of me?"))
        assertFalse(SmartCameraIntent.shouldCapture("Hôm nay là thứ mấy?"))
        assertFalse(SmartCameraIntent.shouldCapture("Giải thích định lý Pitago"))
    }
    @Test fun deviceActionRouterParsesCommonHandsFreeCommands() {
        assertEquals(DeviceActionRouter.Action.TellTime, DeviceActionRouter.parse("Mấy giờ rồi?"))
        assertEquals(DeviceActionRouter.Action.TellBattery, DeviceActionRouter.parse("Kính còn bao nhiêu pin?"))
        assertEquals(DeviceActionRouter.Action.TellBattery, DeviceActionRouter.parse("Pin còn bao nhiêu phần trăm?"))
        assertEquals(DeviceActionRouter.Action.GlassesPhoto, DeviceActionRouter.parse("Chụp một tấm ảnh"))
        assertEquals(DeviceActionRouter.Action.GlassesVideo(true), DeviceActionRouter.parse("Quay video đi"))
        assertEquals(DeviceActionRouter.Action.GlassesVideo(false), DeviceActionRouter.parse("Dừng quay video"))
        assertEquals(DeviceActionRouter.Action.GlassesSync, DeviceActionRouter.parse("Đồng bộ ảnh"))
        assertEquals(DeviceActionRouter.Action.GlassesStatus, DeviceActionRouter.parse("Trạng thái kính"))
        assertEquals(DeviceActionRouter.Action.NewChat, DeviceActionRouter.parse("Đoạn chat mới"))
        assertEquals(DeviceActionRouter.Action.NewChat, DeviceActionRouter.parse("Chủ đề mới"))
        assertEquals(DeviceActionRouter.Action.Navigate("Hồ Gươm"), DeviceActionRouter.parse("Chỉ đường đến Hồ Gươm"))
        assertEquals(DeviceActionRouter.Action.Call("Mẹ"), DeviceActionRouter.parse("Gọi cho Mẹ"))
        assertEquals(DeviceActionRouter.Action.OpenApp("YouTube"), DeviceActionRouter.parse("Mở YouTube"))
        assertEquals(DeviceActionRouter.Action.Timer(300, "5 phút"), DeviceActionRouter.parse("Hẹn giờ 5 phút"))
        assertEquals(DeviceActionRouter.Action.Alarm(7, 30), DeviceActionRouter.parse("Đặt báo thức 7 giờ 30"))
        assertNull(DeviceActionRouter.parse("Giải thích thuyết tương đối giúp tôi"))
        // Ambiguous AI questions must NOT be misrouted as device actions.
        assertNull(DeviceActionRouter.parse("Gọi là gì trong tiếng Anh?"))
        assertNull(DeviceActionRouter.parse("Mở rộng kiến thức về kinh tế vĩ mô giúp tôi"))
        assertNull(DeviceActionRouter.parse("Pin mặt trời còn dùng được bao nhiêu năm?"))
    }
}
