package io.github.spotlighterr.xiaomigpt

sealed interface AudioProbeEvent {
    data object Connecting : AudioProbeEvent
    data class Recording(val inputType: Int) : AudioProbeEvent
    data class Level(val peak: Int) : AudioProbeEvent
    data class Playback(val peak: Int) : AudioProbeEvent
    data class Completed(val inputType: Int, val outputType: Int, val peak: Int) : AudioProbeEvent
    data class Failed(val reason: String) : AudioProbeEvent
    data object Cancelled : AudioProbeEvent
}

data class AudioCheckState(
    val phase: Phase = Phase.IDLE,
    val peak: Int = 0,
    val heardPlayback: Boolean = false,
) {
    enum class Phase { IDLE, CONNECTING, RECORDING, PLAYBACK, COMPLETED, FAILED, CANCELLED }
    val routeVerified get() = phase == Phase.COMPLETED

    fun on(event: AudioProbeEvent): AudioCheckState = when (event) {
        AudioProbeEvent.Connecting -> AudioCheckState(Phase.CONNECTING)
        is AudioProbeEvent.Recording -> copy(phase = Phase.RECORDING, heardPlayback = false)
        is AudioProbeEvent.Level -> if (phase == Phase.RECORDING) copy(peak = event.peak.coerceIn(0, 32768)) else this
        is AudioProbeEvent.Playback -> copy(phase = Phase.PLAYBACK, peak = event.peak, heardPlayback = false)
        is AudioProbeEvent.Completed -> if (phase == Phase.PLAYBACK) copy(phase = Phase.COMPLETED, peak = event.peak) else this
        is AudioProbeEvent.Failed -> AudioCheckState(Phase.FAILED)
        AudioProbeEvent.Cancelled -> AudioCheckState(Phase.CANCELLED)
    }

    fun confirmHeard(heard: Boolean) = copy(heardPlayback = routeVerified && heard)
}
