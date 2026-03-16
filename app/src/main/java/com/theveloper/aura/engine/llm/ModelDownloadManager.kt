package com.theveloper.aura.engine.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.theveloper.aura.data.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DownloadState {
    data object Idle : DownloadState
    data object WaitingForWifi : DownloadState
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState
    data object Processing : DownloadState
    data object Complete : DownloadState
    data class Error(
        val reason: String,
        val canRetry: Boolean
    ) : DownloadState
}

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val appSettingsRepository: AppSettingsRepository
) {

    private val activeCalls = ConcurrentHashMap<String, Call>()

    fun downloadModel(
        spec: ModelSpec,
        wifiOnly: Boolean = true
    ): Flow<DownloadState> = flow {
        val settings = appSettingsRepository.getSnapshot()
        val accessToken = settings.huggingFaceAccessToken.trim()
        val targetFile = spec.file(context)
        if (spec.installedFile(context) != null) {
            emit(DownloadState.Complete)
            return@flow
        }

        if (spec.requiresAuthentication && accessToken.isBlank()) {
            emit(
                DownloadState.Error(
                    reason = "This model requires accepted access on Hugging Face plus a read token in Settings > Intelligence.",
                    canRetry = false
                )
            )
            return@flow
        }

        if (wifiOnly && !isOnWifi()) {
            emit(DownloadState.WaitingForWifi)
            return@flow
        }

        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.absolutePath + ".part")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val request = Request.Builder()
            .url(spec.downloadUrl)
            .apply {
                if (accessToken.isNotBlank()) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            .build()
        val call = okHttpClient.newCall(request)
        activeCalls[spec.id] = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(
                        DownloadState.Error(
                            reason = response.toDownloadError(spec),
                            canRetry = true
                        )
                    )
                    return@flow
                }

                val body = response.body ?: run {
                    emit(DownloadState.Error("The download response was empty.", canRetry = true))
                    return@flow
                }

                val totalBytes = body.contentLength().takeIf { it > 0 } ?: spec.sizeBytes
                emit(DownloadState.Downloading(0f, 0L, totalBytes))

                var bytesRead = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) {
                                break
                            }
                            output.write(buffer, 0, read)
                            bytesRead += read
                            emit(
                                DownloadState.Downloading(
                                    progress = if (totalBytes > 0) {
                                        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    },
                                    bytesDownloaded = bytesRead,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                }

                emit(DownloadState.Processing)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                emit(DownloadState.Complete)
            }
        } catch (exception: IOException) {
            tempFile.delete()
            val reason = if (call.isCanceled()) "Download cancelled." else exception.message.orEmpty().ifBlank { "Network error." }
            emit(DownloadState.Error(reason, canRetry = true))
        } finally {
            activeCalls.remove(spec.id)
        }
    }.flowOn(Dispatchers.IO)

    fun cancelDownload(spec: ModelSpec) {
        activeCalls.remove(spec.id)?.cancel()
    }

    fun isModelDownloaded(spec: ModelSpec): Boolean = spec.installedFile(context) != null

    fun deleteModel(spec: ModelSpec): Boolean {
        cancelDownload(spec)
        val deletedInstalled = spec.allFiles(context).fold(false) { deletedAny, file ->
            file.delete() || deletedAny
        }
        val deletedPartial = File(spec.file(context).absolutePath + ".part").delete()
        return deletedInstalled || deletedPartial
    }

    fun getModelSizeOnDisk(spec: ModelSpec): Long = spec.installedFile(context)?.length() ?: 0L

    fun getModelPath(spec: ModelSpec): String? = spec.installedFile(context)
        ?.absolutePath

    private fun isOnWifi(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun okhttp3.Response.toDownloadError(spec: ModelSpec): String {
        return when (code) {
            401, 403 -> {
                if (spec.requiresAuthentication) {
                    "Hugging Face rejected the download ($code). Accept model access at ${spec.accessPageUrl} and verify the configured token."
                } else {
                    "Download was rejected with HTTP $code."
                }
            }

            else -> "HTTP $code"
        }
    }
}
