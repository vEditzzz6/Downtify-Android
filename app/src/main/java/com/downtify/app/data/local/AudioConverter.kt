package com.downtify.app.data.local

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.downtify.app.domain.model.AudioFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioConverter @Inject constructor() {

    suspend fun convert(
        inputFile: File,
        outputFormat: AudioFormat,
        bitrate: String = "320k"
    ): File = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) {
            throw Exception("Input file does not exist: ${inputFile.absolutePath}")
        }
        if (inputFile.length() == 0L) {
            throw Exception("Input file is empty: ${inputFile.absolutePath}")
        }
        
        val outputDir = inputFile.parentFile ?: throw Exception("No parent directory")
        val outputFileName = if (inputFile.nameWithoutExtension.endsWith("_temp")) {
            inputFile.nameWithoutExtension.substringBeforeLast("_temp")
        } else {
            inputFile.nameWithoutExtension
        }
        val outputFile = File(outputDir, "$outputFileName.${outputFormat.extension}")
        
        if (outputFile.absolutePath == inputFile.absolutePath) {
            // If they are the same file, we need to use a temporary name for the output
            val tempOutputFile = File(outputDir, "${outputFileName}_output.${outputFormat.extension}")
            return@withContext performConversion(inputFile, tempOutputFile, outputFormat, bitrate).also {
                inputFile.delete()
                it.renameTo(outputFile)
            }
        }

        if (outputFile.exists()) outputFile.delete()

        performConversion(inputFile, outputFile, outputFormat, bitrate).also {
            inputFile.delete()
        }
    }

    private fun performConversion(input: File, output: File, format: AudioFormat, bitrate: String): File {
        val ffmpegArguments = buildArguments(input, output, format, bitrate)
        android.util.Log.d("AudioConverter", "Running FFmpeg with arguments: ${ffmpegArguments.joinToString(" ")}")
        
        val session = FFmpegKit.executeWithArguments(ffmpegArguments)
        val returnCode = session.returnCode

        if (ReturnCode.isSuccess(returnCode)) {
            if (!output.exists()) {
                throw Exception("FFmpeg succeeded but output file was not created")
            }
            return output
        } else {
            val logs = session.allLogsAsString
            throw Exception("FFmpeg conversion failed: $logs")
        }
    }

    suspend fun downloadAndConvert(
        url: String,
        outputFormat: AudioFormat,
        bitrate: String = "320k"
    ): File = withContext(Dispatchers.IO) {
        val tempFile = java.io.File.createTempFile("soundcloud_", ".${outputFormat.extension}")
        if (tempFile.exists()) tempFile.delete()

        val args = arrayOf(
            "-y",
            "-protocol_whitelist", "file,http,https,tcp,tls,crypto",
            "-i", url,
            "-codec:a", when (outputFormat) {
                AudioFormat.MP3 -> "libmp3lame"
                AudioFormat.FLAC -> "flac"
                AudioFormat.M4A -> "aac"
                AudioFormat.OGG -> "libvorbis"
                AudioFormat.OPUS -> "libopus"
            },
            "-b:a", bitrate,
            "-shortest",
            tempFile.absolutePath
        )

        android.util.Log.d("AudioConverter", "Running FFmpeg for HLS: ${args.joinToString(" ")}")

        val session = FFmpegKit.executeWithArguments(args)
        val returnCode = session.returnCode

        if (ReturnCode.isSuccess(returnCode)) {
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw Exception("FFmpeg completed but output file is empty")
            }
            tempFile
        } else {
            val logs = session.allLogsAsString
            throw Exception("FFmpeg HLS download failed: $logs")
        }
    }

    private fun buildArguments(input: File, output: File, format: AudioFormat, bitrate: String): Array<String> {
        return when (format) {
            AudioFormat.MP3 -> arrayOf("-y", "-i", input.absolutePath, "-codec:a", "libmp3lame", "-b:a", bitrate, output.absolutePath)
            AudioFormat.FLAC -> arrayOf("-y", "-i", input.absolutePath, "-codec:a", "flac", output.absolutePath)
            AudioFormat.M4A -> arrayOf("-y", "-i", input.absolutePath, "-codec:a", "aac", "-b:a", bitrate, output.absolutePath)
            AudioFormat.OGG -> arrayOf("-y", "-i", input.absolutePath, "-codec:a", "libvorbis", "-b:a", bitrate, output.absolutePath)
            AudioFormat.OPUS -> arrayOf("-y", "-i", input.absolutePath, "-codec:a", "libopus", "-b:a", bitrate, output.absolutePath)
        }
    }
}
