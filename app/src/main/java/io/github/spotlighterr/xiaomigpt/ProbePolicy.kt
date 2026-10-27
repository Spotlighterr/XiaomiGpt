package io.github.spotlighterr.xiaomigpt

/** Pure policy shared by the probe and JVM tests. No hostnames, URLs or public targets. */
object ProbePolicy {
    fun ipv4(value: String): Long? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        var result = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
            if (part.length > 1 && part.startsWith('0')) return null
            val number = part.toIntOrNull() ?: return null
            if (number !in 0..255) return null
            result = (result shl 8) or number.toLong()
        }
        return result
    }

    fun isPrivateHost(value: String): Boolean {
        val address = ipv4(value) ?: return false
        val first = (address ushr 24).toInt()
        val second = ((address ushr 16) and 255).toInt()
        val last = (address and 255).toInt()
        return (first == 10 || (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)) && last != 0 && last != 255
    }

    fun sameSubnet(host: String, local: String, prefix: Int): Boolean {
        if (prefix !in 1..32) return false
        val remoteAddress = ipv4(host) ?: return false
        val localAddress = ipv4(local) ?: return false
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        return (remoteAddress and mask) == (localAddress and mask)
    }

    /** Never combine a photo with a new source's label after a camera switch. */
    fun canConfirmPhoto(photoSource: String?, activeSource: String?): Boolean =
        photoSource != null && photoSource == activeSource
}
