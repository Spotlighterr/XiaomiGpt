package io.github.spotlighterr.xiaomigpt

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.util.concurrent.TimeUnit

class WifiProbe(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var call: Call? = null

    fun cancel() { call?.cancel() }

    @Suppress("DEPRECATION")
    fun run(host: String): String {
        require(ProbePolicy.isPrivateHost(host)) { "Nhập một địa chỉ IPv4 nội bộ, không nhập URL hoặc tên miền." }
        val network = connectivity.allNetworks.firstOrNull { candidate ->
            connectivity.getNetworkCapabilities(candidate)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                connectivity.getLinkProperties(candidate)?.linkAddresses?.any {
                    ProbePolicy.sameSubnet(host, it.address.hostAddress.orEmpty(), it.prefixLength)
                } == true
        } ?: error("Chưa có Wi-Fi cùng mạng với IP này. Kết nối Wi-Fi kính bằng app Xiaomi rồi thử lại.")
        val client = OkHttpClient.Builder()
            .socketFactory(network.socketFactory)
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        try {
            val request = Request.Builder().url("http://$host:8080/v1/filelists")
                .header("Accept", "application/json").get().build()
            val pending = client.newCall(request)
            call = pending
            pending.execute().use { response ->
                // Discard the response body: it can contain personal media names and metadata.
                // A 2xx alone does not prove the schema is an image list or that capture works.
                return when (response.code) {
                    in 200..299 -> "HTTP ${response.code}: endpoint phản hồi. Chưa xác minh cấu trúc danh sách ảnh hoặc tải ảnh."
                    401, 403 -> "HTTP ${response.code}: bị từ chối. Cần xác minh cơ chế xác thực; chưa gửi token."
                    404 -> "HTTP 404: đường dẫn này không được phục vụ. Chưa xác định endpoint đúng."
                    in 300..399 -> "HTTP ${response.code}: chuyển hướng; app không đi theo chuyển hướng."
                    else -> "HTTP ${response.code}: chưa xác định khả năng tương thích."
                }
            }
        } finally {
            call = null
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
