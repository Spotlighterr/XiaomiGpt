package io.github.spotlighterr.xiaomigpt

/** A service accepting a test request is not proof that Android opened the session. */
data class AssistantProbe(val request: Int = 0, val phase: Phase = Phase.IDLE) {
    enum class Phase { IDLE, WAITING_SERVICE, SESSION_REQUESTED, SESSION_OPENED, FAILED, TIMED_OUT }
    val pending get() = phase == Phase.WAITING_SERVICE || phase == Phase.SESSION_REQUESTED
    fun start() = AssistantProbe(request + 1, Phase.WAITING_SERVICE)
    fun serviceResult(id: Int, result: Int): AssistantProbe {
        if (id != request || !pending) return this
        return when (result) {
            WAITING -> this
            REQUESTED -> copy(phase = Phase.SESSION_REQUESTED)
            NOT_ACTIVE, FAILED, SHUT_DOWN -> copy(phase = Phase.FAILED)
            else -> this
        }
    }
    fun opened() = if (pending) copy(phase = Phase.SESSION_OPENED) else this
    fun expire(id: Int) = if (id == request && pending) copy(phase = Phase.TIMED_OUT) else this
    companion object {
        const val EXTRA_REPLY = "io.github.spotlighterr.xiaomigpt.TEST_REPLY"
        const val WAITING = 0
        const val REQUESTED = 1
        const val NOT_ACTIVE = 2
        const val FAILED = 3
        const val SHUT_DOWN = 4
    }
}
