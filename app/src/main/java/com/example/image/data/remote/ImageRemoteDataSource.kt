package com.example.image.data.remote

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ImageRemoteDataSource(
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun downloadTo(url: String, targetFile: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("网络请求失败: ${response.code}")
            }

            val body = response.body ?: throw IOException("响应内容为空")
            targetFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient {
            val dispatcher = Dispatcher().apply {
                maxRequests = 6
                maxRequestsPerHost = 4
            }
            return OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
