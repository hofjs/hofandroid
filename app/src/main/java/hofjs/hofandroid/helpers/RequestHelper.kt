package hofjs.hofandroid.helpers

import android.content.Context
import okhttp3.*
import java.nio.charset.StandardCharsets
import java.util.*

class RequestHelper(val context: Context)  {
    private val client = OkHttpClient()

    fun loadUrl(url: String, credentials: String? = null): String? {
        var request = Request.Builder().url(url)

        if (credentials != null) {
            val encodedBytes = Base64.getEncoder().encode(credentials.toByteArray())
            //val encodedBytes = Base64.encode(credentials.toByteArray(), Base64.NO_WRAP)
            val encodedString = String(encodedBytes, StandardCharsets.UTF_8)

            request = request.addHeader("authorization", "Basic $encodedString")
        }

        val response = client.newCall(request.build()).execute()

        return response.body?.string()
    }
}