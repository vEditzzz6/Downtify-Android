package com.downtify.app.data.local

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class AndroidDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: NewPipeRequest): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")

        headers.forEach { (key, values) ->
            values.forEach { value ->
                if (key.lowercase() != "user-agent") {
                    requestBuilder.header(key, value)
                }
            }
        }

        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            requestBuilder.header("Origin", "https://www.youtube.com")
            requestBuilder.header("Referer", "https://www.youtube.com/")
            val existingCookie = headers["Cookie"]?.firstOrNull() ?: ""
            if (!existingCookie.contains("CONSENT")) {
                val consentCookie = if (existingCookie.isNotEmpty()) "$existingCookie; " else ""
                requestBuilder.header("Cookie", "${consentCookie}CONSENT=PENDING+987; SOCS=CAISEwgDEgk2OTQ5NjQyMjEaAmVuIAEaBgiA_L-qBg")
            }
        }

        val body = request.dataToSend()
        if (body != null) {
            requestBuilder.method(httpMethod, body.toRequestBody())
        } else {
            requestBuilder.method(httpMethod, null)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = mutableMapOf<String, List<String>>()
            response.headers.names().forEach { name ->
                responseHeaders[name] = response.headers.values(name)
            }

            val responseUrl = response.request.url.toString()

            return Response(
                response.code,
                response.message,
                responseHeaders,
                responseBody,
                responseUrl
            )
        }
    }
}
