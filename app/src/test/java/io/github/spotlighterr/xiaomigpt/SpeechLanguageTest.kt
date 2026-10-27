package io.github.spotlighterr.xiaomigpt

import org.junit.Assert.*
import org.junit.Test

class SpeechLanguageTest {
    @Test fun `fresh or unknown language preference defaults to Vietnamese`() {
        assertEquals(SpeechLanguage.VIETNAMESE, SpeechLanguage.fromTag(null))
        assertEquals(SpeechLanguage.VIETNAMESE, SpeechLanguage.fromTag("unsupported"))
        assertEquals(SpeechLanguage.ENGLISH, SpeechLanguage.fromTag("en-US"))
    }
    @Test fun `switching language uses a distinct speech model and matching TTS locale`() {
        val vi = SpeechLanguage.fromTag("vi-VN")
        val en = SpeechLanguage.fromTag("en-US")
        assertEquals("vi", vi.locale.language)
        assertEquals("en", en.locale.language)
        assertNotEquals(vi.modelName, en.modelName)
        assertNotEquals(vi.sha256, en.sha256)
    }
}
