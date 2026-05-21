package com.downtify.app.data.local

import android.content.Context
import com.downtify.app.domain.model.Song
import com.downtify.app.util.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageUtils: StorageUtils,
    private val client: OkHttpClient
) {

    companion object {
        private const val BUFFER_SIZE = 1048576
    }

    private val mobileUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"

    suspend fun downloadAudio(
        streamUrl: String,
        song: Song,
        format: String = "mp3",
        progressCallback: suspend (Float, Long, Long) -> Unit = { _, _, _ -> }
    ): File {
        val targetDir = File(context.cacheDir, "downloads").also { it.mkdirs() }
        val fileName = "${song.artists.firstOrNull() ?: "Unknown"} - ${song.name}.$format"
        val sanitizedFileName = storageUtils.sanitizeFileName(fileName)
        val outputFile = File(targetDir, sanitizedFileName)
        if (outputFile.exists()) outputFile.delete()

        return downloadFile(streamUrl, outputFile, progressCallback)
    }

    suspend fun downloadFile(
        url: String,
        outputFile: File,
        progressCallback: suspend (Float, Long, Long) -> Unit = { _, _, _ -> }
    ): File {
        if (outputFile.exists()) outputFile.delete()
        val contentLength = detectContentLength(url)

        if (contentLength > 0) {
            return downloadChunked(url, outputFile, contentLength, outputFile.parentFile!!, outputFile.name, progressCallback)
        }

        return downloadSingle(url, outputFile, -1L, progressCallback)
    }

    private suspend fun detectContentLength(url: String): Long {
        // Try HEAD first
        val headResult = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", mobileUA)
                    .head()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.contentLength() ?: -1L else -1L
                }
            } catch (_: Exception) { -1L }
        }
        if (headResult > 0) return headResult

        // Try a single-byte range probe to get Content-Range header
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", mobileUA)
                    .header("Range", "bytes=0-0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 206) {
                        val contentRange = response.header("Content-Range")
                        if (contentRange != null) {
                            // Format: "bytes 0-0/{total}"
                            val slash = contentRange.lastIndexOf('/')
                            if (slash >= 0) contentRange.substring(slash + 1).toLongOrNull() ?: -1L else -1L
                        } else -1L
                    } else -1L
                }
            } catch (_: Exception) { -1L }
        }
    }

    private suspend fun downloadSingle(
        streamUrl: String,
        outputFile: File,
        contentLength: Long,
        progressCallback: suspend (Float, Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", mobileUA)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Connection", "keep-alive")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
            var downloadedBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            var lastUpdate = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            FileOutputStream(outputFile).use { fos ->
                response.body?.byteStream()?.use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 1000) {
                            val elapsed = (now - lastUpdate) / 1000.0
                            val speed = (bytesSinceLastUpdate / elapsed).toLong()
                            val progress = if (contentLength > 0) (downloadedBytes.toFloat() / contentLength) * 100f else 0f
                            progressCallback(progress.coerceAtMost(95f), speed, 0)
                            lastUpdate = now
                            bytesSinceLastUpdate = 0
                        }
                    }
                }
            }
        }

        progressCallback(100f, 0, 0)
        if (!outputFile.exists() || outputFile.length() == 0L) throw Exception("Failed to save downloaded file")
        outputFile
    }

    private suspend fun downloadChunked(
        streamUrl: String,
        outputFile: File,
        contentLength: Long,
        targetDir: File,
        baseName: String,
        progressCallback: suspend (Float, Long, Long) -> Unit
    ): File = coroutineScope {
        val chunkCount = ((contentLength / (2 * 1048576)) + 1).toInt().coerceIn(2, 6)
        val chunkSize = contentLength / chunkCount
        val totalDownloaded = AtomicLong(0)

        val deferreds = (0 until chunkCount).map { chunkIndex ->
            async(Dispatchers.IO) {
                val start = chunkIndex * chunkSize
                val end = if (chunkIndex == chunkCount - 1) contentLength - 1
                          else (start + chunkSize - 1).coerceAtMost(contentLength - 1)
                val chunkFile = File(targetDir, "${baseName}.part$chunkIndex")
                if (chunkFile.exists()) chunkFile.delete()

                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", mobileUA)
                    .header("Range", "bytes=$start-$end")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) throw Exception("Chunk $chunkIndex failed: ${response.code}")
                    val buffer = ByteArray(BUFFER_SIZE)

                    FileOutputStream(chunkFile).use { fos ->
                        response.body?.byteStream()?.use { inputStream ->
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                                totalDownloaded.addAndGet(bytesRead.toLong())
                            }
                        }
                    }
                }

                chunkFile
            }
        }

        // Progress reporter
        val reporter = async(Dispatchers.IO) {
            var lastUpdate = System.currentTimeMillis()
            var lastBytes = 0L
            while (totalDownloaded.get() < contentLength) {
                delay(500)
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 1000) {
                    val current = totalDownloaded.get()
                    val elapsed = (now - lastUpdate) / 1000.0
                    val speed = if (elapsed > 0) ((current - lastBytes) / elapsed).toLong() else 0L
                    val pct = (current.toFloat() / contentLength) * 100f
                    progressCallback(pct.coerceAtMost(95f), speed, 0)
                    lastUpdate = now
                    lastBytes = current
                }
            }
        }

        val chunkFiles = deferreds.awaitAll()
        reporter.cancel()

        withContext(Dispatchers.IO) {
            FileOutputStream(outputFile).use { fos ->
                for ((index, chunkFile) in chunkFiles.withIndex()) {
                    if (!chunkFile.exists()) throw Exception("Missing chunk $index")
                    chunkFile.inputStream().use { it.copyTo(fos) }
                    chunkFile.delete()
                }
            }
        }

        progressCallback(100f, 0, 0)
        if (outputFile.length() != contentLength) throw Exception("Downloaded size mismatch")
        outputFile
    }
}
