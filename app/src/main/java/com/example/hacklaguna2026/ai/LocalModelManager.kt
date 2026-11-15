package com.example.hacklaguna2026.ai

import android.content.Context
import android.system.Os
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ModelDownloadProgress(val downloadedBytes: Long, val totalBytes: Long)

enum class LocalModelStatus { READY, DOWNLOAD_REQUIRED, INCOMPLETE_DOWNLOAD, UNAVAILABLE }

class LocalModelManager(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "offline-companion")
    val modelFile: File get() = File(directory, MODEL_FILE_NAME)
    private val partialFile: File get() = File(directory, "$MODEL_FILE_NAME.part")
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Synchronized
    fun status(): LocalModelStatus = when {
        modelFile.isFile && modelFile.length() == MODEL_SIZE_BYTES -> LocalModelStatus.READY
        modelFile.exists() -> LocalModelStatus.UNAVAILABLE
        partialFile.isFile && partialFile.length() in 1..MODEL_SIZE_BYTES -> LocalModelStatus.INCOMPLETE_DOWNLOAD
        else -> LocalModelStatus.DOWNLOAD_REQUIRED
    }

    fun partialBytes(): Long = partialFile.takeIf(File::isFile)?.length()?.coerceIn(0, MODEL_SIZE_BYTES) ?: 0L

    suspend fun download(onProgress: (ModelDownloadProgress) -> Unit): File {
        if (!directory.exists() && !directory.mkdirs()) throw ModelDownloadException("Ciel couldn't prepare private storage for the offline model.")
        var existingBytes = partialBytes()
        if (existingBytes == MODEL_SIZE_BYTES) {
            if (sha256(partialFile).equals(MODEL_SHA256, ignoreCase = true)) {
                Os.rename(partialFile.absolutePath, modelFile.absolutePath)
                return modelFile
            }
            partialFile.delete()
            existingBytes = 0L
        }
        val bytesToDownload = MODEL_SIZE_BYTES - existingBytes
        if (directory.usableSpace < bytesToDownload + MINIMUM_FREE_STORAGE_BYTES) {
            throw ModelDownloadException("Please free up at least 550 MB of storage before downloading Ciel's offline companion.")
        }

        val request = Request.Builder()
            .url(MODEL_URL)
            .apply { if (existingBytes > 0L) header("Range", "bytes=$existingBytes-") }
            .header("Accept-Encoding", "identity")
            .get()
            .build()
        val call = client.newCall(request)
        val cancellationHandler = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (response.code == 416 && existingBytes == MODEL_SIZE_BYTES && sha256(partialFile) == MODEL_SHA256) {
                    Os.rename(partialFile.absolutePath, modelFile.absolutePath)
                    return modelFile
                }
                if (!response.isSuccessful) {
                    throw ModelDownloadException("The offline model couldn't be downloaded. Check your connection and try again.")
                }
                val append = existingBytes > 0L && response.code == 206
                val body = response.body ?: throw ModelDownloadException("The offline model download was empty. Please try again.")
                var completed = if (append) existingBytes else 0L
                FileOutputStream(partialFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            completed += count
                            onProgress(ModelDownloadProgress(completed.coerceAtMost(MODEL_SIZE_BYTES), MODEL_SIZE_BYTES))
                        }
                    }
                    output.fd.sync()
                }
            }
        } catch (error: ModelDownloadException) {
            throw error
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw ModelDownloadException("The offline model download stopped. Your progress is saved so you can resume it.", error)
        } finally {
            cancellationHandler.dispose()
        }

        if (!partialFile.isFile || partialFile.length() != MODEL_SIZE_BYTES) {
            throw ModelDownloadException("The offline model download is incomplete. Your progress is saved so you can resume it.")
        }
        if (!sha256(partialFile).equals(MODEL_SHA256, ignoreCase = true)) {
            partialFile.delete()
            throw ModelDownloadException("The downloaded model failed its integrity check. Please try the download again.")
        }
        Os.rename(partialFile.absolutePath, modelFile.absolutePath)
        return modelFile
    }

    @Synchronized
    fun remove() {
        modelFile.delete()
        partialFile.delete()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    companion object {
        const val MODEL_DISPLAY_SIZE = "498 MB"
        const val MODEL_SIZE_BYTES = 497_516_544L
        const val MODEL_FILE_NAME = "qwen3_0_6b_mixed_int4.litertlm"
        const val MODEL_LICENSE = "Apache-2.0"
        private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/154c5de0fc0fc5002a7da570ba8c67edffa32ca2/qwen3_0_6b_mixed_int4.litertlm"
        private const val MODEL_SHA256 = "7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1"
        private const val MINIMUM_FREE_STORAGE_BYTES = 24L * 1024 * 1024
    }
}

class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
